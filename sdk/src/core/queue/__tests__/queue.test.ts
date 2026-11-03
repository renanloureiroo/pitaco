import type { InteractionEvent } from '../../../catalog/events';
import { jestClock } from '../../../__tests__/support/fakeClock';
import { FakePitacoServer } from '../../../__tests__/support/fakeServer';
import { silentLogger } from '../../logger';
import { createMemoryStorage } from '../../storage/memory';
import { SafeStorage } from '../../storage/safe';
import type { PitacoStorage } from '../../storage/types';
import { systemTimers } from '../../timers';
import { CollectApi } from '../../transport/api';
import type { HttpOutcome } from '../../transport/http';
import { HttpClient } from '../../transport/http';
import { createUuidGenerator } from '../../uuid';
import { DeliveryQueue, type QueueItem, type QueueLimits } from '../queue';

const NOW = Date.UTC(2026, 8, 12, 13, 0, 0);
const KEY = '@pitaco/v1:queue:test';
const DISPLAY = '3b1f0a2c-6c9a-4a1e-9d0b-2c1f7a3e5d90';
const DAY = 24 * 60 * 60 * 1000;

function openBody(displayId = DISPLAY) {
  return { displayId, surveyId: 's', versionId: 'v', respondent: { deviceId: 'd' }, sdkVersion: '1.0.0' };
}

const submission = { outcome: 'COMPLETED' as const, answers: [{ questionKey: 'q', status: 'ANSWERED' as const, value: 4 }] };

function event(seq: number, displayId = DISPLAY): InteractionEvent {
  return {
    catalogVersion: 1,
    type: 'survey_backgrounded',
    displayId,
    seq,
    occurredAt: new Date(NOW).toISOString(),
    elapsedMs: seq,
    data: {},
  };
}

function makeQueue(
  server: FakePitacoServer,
  adapter: PitacoStorage = createMemoryStorage(),
  extra: { limits?: Partial<QueueLimits>; onRejected?: (item: QueueItem, outcome: HttpOutcome) => void; onUnauthorized?: () => void } = {},
) {
  const http = new HttpClient({
    baseUrl: 'https://pitaco.test/api',
    apiKey: 'pk_test',
    sdkVersion: '1.0.0',
    fetch: server.fetch,
    clock: jestClock,
    timers: systemTimers,
    timeoutMs: 10_000,
  });
  const storageErrors: string[] = [];
  const queue = new DeliveryQueue({
    storage: new SafeStorage(adapter, (operation) => storageErrors.push(operation)),
    storageKey: KEY,
    api: new CollectApi(http, 3000),
    http,
    clock: jestClock,
    timers: systemTimers,
    uuid: createUuidGenerator(),
    logger: silentLogger,
    ...extra,
  });
  return { queue, http, storageErrors };
}

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
});

afterEach(() => {
  jest.useRealTimers();
});

describe('fila local', () => {
  it('grava antes de enviar', async () => {
    const server = new FakePitacoServer();
    const log: string[] = [];
    const memory = createMemoryStorage();
    const adapter: PitacoStorage = {
      getItem: (key) => memory.getItem(key),
      setItem: (key, value) => {
        log.push('grava');
        memory.setItem(key, value);
      },
      removeItem: (key) => memory.removeItem(key),
    };
    const originalFetch = server.fetch;
    const spyServer = Object.assign(server, {
      fetch: (url: string, init: Parameters<typeof originalFetch>[1]) => {
        log.push('envia');
        return originalFetch(url, init);
      },
    });
    const { queue } = makeQueue(spyServer, adapter);
    await queue.enqueueOpenDisplay(DISPLAY, openBody());
    await queue.flush();
    expect(log.slice(0, 2)).toEqual(['grava', 'envia']);
    expect(JSON.parse(memory.getItem(KEY) ?? '{}')).toMatchObject({ items: [] });
  });

  it('manda a abertura antes do que depende dela, e remove cada item no 2xx', async () => {
    const server = new FakePitacoServer();
    const { queue } = makeQueue(server);
    await queue.enqueueOpenDisplay(DISPLAY, openBody());
    await queue.enqueueEvents(DISPLAY, [event(1), event(2)]);
    await queue.enqueueSubmission(DISPLAY, submission);
    await queue.flush();
    expect(server.requests.map((request) => request.route)).toEqual(['displays', 'events', 'submission']);
    expect(queue.snapshot()).toEqual([]);
  });

  it('enquanto a abertura não passa, nada que depende dela sai', async () => {
    const server = new FakePitacoServer();
    server.behavior = 'down';
    const { queue } = makeQueue(server);
    await queue.enqueueOpenDisplay(DISPLAY, openBody());
    await queue.enqueueSubmission(DISPLAY, submission);
    await queue.flush();
    expect(server.requests.map((request) => request.route)).toEqual(['displays']);
    expect(queue.snapshot()[0]).toMatchObject({ kind: 'open_display', attempts: 1, nextAttemptAt: NOW + 2000 });
  });

  it('reenvia com backoff exponencial', async () => {
    const server = new FakePitacoServer();
    server.behavior = 'down';
    const { queue } = makeQueue(server);
    await queue.enqueueSuppression({ surveyId: 's', versionId: 'v', reason: 'unknown_question_type' });
    await queue.flush();
    expect(server.count('suppressions')).toBe(1);
    await jest.advanceTimersByTimeAsync(1999);
    expect(server.count('suppressions')).toBe(1);
    await jest.advanceTimersByTimeAsync(1);
    await queue.settle();
    expect(server.count('suppressions')).toBe(2);
    await jest.advanceTimersByTimeAsync(3999);
    expect(server.count('suppressions')).toBe(2);
    await jest.advanceTimersByTimeAsync(1);
    await queue.settle();
    expect(server.count('suppressions')).toBe(3);
    server.behavior = 'up';
    await jest.advanceTimersByTimeAsync(8000);
    await queue.settle();
    expect(server.suppressions).toHaveLength(1);
    expect(queue.snapshot()).toEqual([]);
  });

  it('resposta sem rede é enviada exatamente uma vez ao reabrir o app', async () => {
    const storage = createMemoryStorage();
    const offline = new FakePitacoServer();
    offline.behavior = 'down';
    const first = makeQueue(offline, storage).queue;
    await first.enqueueOpenDisplay(DISPLAY, openBody());
    await first.enqueueEvents(DISPLAY, [event(1)]);
    await first.enqueueSubmission(DISPLAY, submission);
    await first.flush();
    first.pause();

    const online = new FakePitacoServer();
    const reopened = makeQueue(online, storage).queue;
    await reopened.flush({ force: true });
    expect(online.displays.size).toBe(1);
    expect(online.submissions.size).toBe(1);
    expect(online.events.size).toBe(1);
    expect(reopened.snapshot()).toEqual([]);

    const again = makeQueue(online, storage).queue;
    const before = online.requests.length;
    await again.flush({ force: true });
    expect(online.requests.length).toBe(before);
  });

  it('reenvio de item já entregue não duplica: a resposta perdida volta como 204 e eventos como duplicados', async () => {
    const server = new FakePitacoServer();
    server.script('submission', 'lost');
    server.script('events', 'lost');
    const { queue } = makeQueue(server);
    await queue.enqueueOpenDisplay(DISPLAY, openBody());
    await queue.enqueueEvents(DISPLAY, [event(1), event(2)]);
    await queue.enqueueSubmission(DISPLAY, submission);
    await queue.flush();
    await queue.flush({ force: true });
    await queue.flush({ force: true });
    expect(server.count('submission')).toBe(2);
    expect(server.count('events')).toBe(2);
    expect(server.submissions.size).toBe(1);
    expect(server.events.size).toBe(2);
    expect(queue.snapshot()).toEqual([]);
  });

  it('a fila não aceita o mesmo item duas vezes', async () => {
    const server = new FakePitacoServer();
    server.behavior = 'down';
    const { queue } = makeQueue(server);
    await queue.enqueueOpenDisplay(DISPLAY, openBody());
    await queue.enqueueOpenDisplay(DISPLAY, openBody());
    await queue.enqueueSubmission(DISPLAY, submission);
    await queue.enqueueSubmission(DISPLAY, submission);
    await queue.enqueueEvents(DISPLAY, [event(1), event(2)]);
    await queue.enqueueEvents(DISPLAY, [event(2), event(3)]);
    const items = queue.snapshot();
    expect(items.map((item) => item.kind)).toEqual(['open_display', 'submission', 'events']);
    const events = items.find((item) => item.kind === 'events');
    expect(events?.kind === 'events' ? events.events.map((item) => item.seq) : []).toEqual([1, 2, 3]);
  });

  it('item velho demais é descartado sem alarde', async () => {
    const server = new FakePitacoServer();
    server.behavior = 'down';
    const { queue } = makeQueue(server);
    await queue.enqueueOpenDisplay(DISPLAY, openBody());
    await queue.enqueueSubmission(DISPLAY, submission);
    await queue.flush();
    queue.pause();
    jest.setSystemTime(NOW + 3 * DAY + 1);
    queue.resume();
    server.behavior = 'up';
    const before = server.requests.length;
    await queue.flush({ force: true });
    expect(queue.snapshot()).toEqual([]);
    expect(server.requests.length).toBe(before);
  });

  it('teto de tamanho: descarta os mais antigos', async () => {
    const server = new FakePitacoServer();
    server.behavior = 'down';
    const { queue } = makeQueue(server, createMemoryStorage(), { limits: { maxItems: 3 } });
    for (const versionId of ['v1', 'v2', 'v3', 'v4', 'v5']) {
      await queue.enqueueSuppression({ surveyId: 's', versionId, reason: 'unknown_question_type' });
    }
    await queue.flush();
    const versions = queue.snapshot().map((item) => (item.kind === 'suppression' ? item.body.versionId : ''));
    expect(versions).toEqual(['v3', 'v4', 'v5']);
  });

  it('teto de eventos por exibição', async () => {
    const server = new FakePitacoServer();
    server.behavior = 'down';
    const { queue } = makeQueue(server, createMemoryStorage(), { limits: { maxEventsPerDisplay: 5 } });
    await queue.enqueueEvents(DISPLAY, Array.from({ length: 8 }, (_, index) => event(index + 1)));
    const item = queue.snapshot()[0];
    expect(item?.kind === 'events' ? item.events.length : 0).toBe(5);
  });

  it('eventos saem em lotes de até 100', async () => {
    const server = new FakePitacoServer();
    const { queue } = makeQueue(server);
    await queue.enqueueOpenDisplay(DISPLAY, openBody());
    await queue.enqueueEvents(DISPLAY, Array.from({ length: 250 }, (_, index) => event(index + 1)));
    await queue.flush();
    const batches = server.requests
      .filter((request) => request.route === 'events')
      .map((request) => (request.body as { events: unknown[] }).events.length);
    expect(batches).toEqual([100, 100, 50]);
    expect(server.events.size).toBe(250);
    expect(queue.snapshot()).toEqual([]);
  });

  it('429 pausa a fila inteira até o Retry-After, inclusive depois de reabrir o app', async () => {
    const storage = createMemoryStorage();
    const server = new FakePitacoServer();
    server.script('suppressions', { status: 429, headers: { 'Retry-After': '30' } });
    const { queue } = makeQueue(server, storage);
    await queue.enqueueSuppression({ surveyId: 's', versionId: 'v1', reason: 'unknown_question_type' });
    await queue.enqueueSuppression({ surveyId: 's', versionId: 'v2', reason: 'unknown_question_type' });
    await queue.flush();
    expect(server.count('suppressions')).toBe(1);
    await queue.flush({ force: true });
    expect(server.count('suppressions')).toBe(1);
    queue.pause();

    const reopened = makeQueue(server, storage).queue;
    await reopened.flush({ force: true });
    expect(server.count('suppressions')).toBe(1);

    await jest.advanceTimersByTimeAsync(30_000);
    await reopened.settle();
    expect(server.suppressions).toHaveLength(2);
    expect(reopened.snapshot()).toEqual([]);
  });

  it('recusa descarta: abertura com 404 leva junto o que dependia dela, e o motivo é informado', async () => {
    const server = new FakePitacoServer();
    server.script('displays', { status: 404, body: { code: 'survey_version.not_found' } });
    const rejected = jest.fn();
    const { queue } = makeQueue(server, createMemoryStorage(), { onRejected: rejected });
    await queue.enqueueOpenDisplay(DISPLAY, openBody());
    await queue.enqueueSubmission(DISPLAY, submission);
    await queue.flush();
    expect(queue.snapshot()).toEqual([]);
    expect(server.count('submission')).toBe(0);
    expect(rejected).toHaveBeenCalledWith(
      expect.objectContaining({ kind: 'open_display' }),
      expect.objectContaining({ kind: 'rejected', status: 404, code: 'survey_version.not_found' }),
    );
  });

  it('401 descarta e avisa quem criou a fila', async () => {
    const server = new FakePitacoServer();
    server.apiKey = 'outra';
    const unauthorized = jest.fn();
    const { queue } = makeQueue(server, createMemoryStorage(), { onUnauthorized: unauthorized });
    await queue.enqueueSuppression({ surveyId: 's', versionId: 'v', reason: 'unknown_question_type' });
    await queue.flush();
    expect(queue.snapshot()).toEqual([]);
    expect(unauthorized).toHaveBeenCalledTimes(1);
  });

  it('conteúdo corrompido no armazenamento é ignorado', async () => {
    const storage = createMemoryStorage({ [KEY]: '{isso não é json' });
    const { queue, storageErrors } = makeQueue(new FakePitacoServer(), storage);
    await queue.load();
    expect(queue.snapshot()).toEqual([]);
    expect(storageErrors).toEqual(['parse']);

    const partial = createMemoryStorage({
      [KEY]: JSON.stringify({ version: 1, blockedUntil: 0, items: [{ kind: 'desconhecido' }, 42] }),
    });
    const second = makeQueue(new FakePitacoServer(), partial).queue;
    await second.load();
    expect(second.snapshot()).toEqual([]);
  });
});
