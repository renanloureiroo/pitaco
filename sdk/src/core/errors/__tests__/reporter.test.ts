import { jestClock } from '../../../__tests__/support/fakeClock';
import { FakePitacoServer } from '../../../__tests__/support/fakeServer';
import { silentLogger } from '../../logger';
import { systemTimers } from '../../timers';
import { CollectApi } from '../../transport/api';
import { HttpClient } from '../../transport/http';
import { describeError, ErrorReporter, sanitizeContext } from '../reporter';

const NOW = Date.UTC(2026, 8, 12, 13, 0, 0);

function reporter(server: FakePitacoServer, enabled = true, maxPerSession?: number) {
  const http = new HttpClient({
    baseUrl: 'https://pitaco.test/api',
    apiKey: 'pk_test',
    sdkVersion: '1.0.0',
    fetch: server.fetch,
    clock: jestClock,
    timers: systemTimers,
    timeoutMs: 1000,
  });
  return new ErrorReporter({
    enabled,
    api: new CollectApi(http, 3000),
    clock: jestClock,
    logger: silentLogger,
    ...(maxPerSession === undefined ? {} : { maxPerSession }),
  });
}

const flush = () => jest.advanceTimersByTimeAsync(0);

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
});

afterEach(() => {
  jest.useRealTimers();
});

describe('relatório de erro do SDK', () => {
  it('manda tipo, mensagem, contexto e instante para o canal do Pitaco', async () => {
    const server = new FakePitacoServer();
    reporter(server).report('malformed_response', 'eligibility: body_not_object', { route: 'eligibility' });
    await flush();
    expect(server.errors).toEqual([
      {
        kind: 'malformed_response',
        message: 'eligibility: body_not_object',
        context: { route: 'eligibility' },
        occurredAt: '2026-09-12T13:00:00.000Z',
      },
    ]);
  });

  it('desligado, não manda nada', async () => {
    const server = new FakePitacoServer();
    reporter(server, false).report('unknown', 'x');
    await flush();
    expect(server.requests).toEqual([]);
  });

  it('nunca carrega dado de usuário no contexto', () => {
    expect(
      sanitizeContext({
        stage: 'render',
        email: 'a@b.com',
        userId: 'u-1',
        deviceId: 'd',
        answerValue: 'x',
        text: 'oi',
        questionKey: 'q',
        status: 422,
        'chave com espaço': 'x',
        route: 'x'.repeat(200),
      }),
    ).toEqual({ stage: 'render', status: 422, route: 'x'.repeat(80) });
  });

  it('descreve o erro pelo nome, nunca pela mensagem (que pode trazer conteúdo)', () => {
    expect(describeError(new TypeError('falhou para fulano@exemplo.com'))).toBe('TypeError');
    expect(describeError('texto')).toBe('string');
  });

  it('o mesmo erro sai uma vez só, e há teto por sessão', async () => {
    const server = new FakePitacoServer();
    const instance = reporter(server, true, 2);
    instance.report('unknown', 'a');
    instance.report('unknown', 'a');
    instance.report('unknown', 'b');
    instance.report('unknown', 'c');
    await flush();
    expect(server.errors.map((body) => (body as { message: string }).message)).toEqual(['a', 'b']);
  });

  it('falha ao reportar desiste em silêncio e não gera outro relatório', async () => {
    const server = new FakePitacoServer();
    server.behavior = 'down';
    const instance = reporter(server);
    expect(() => instance.report('network_error', 'x')).not.toThrow();
    await flush();
    expect(server.count('sdk-errors')).toBe(1);
    await jest.advanceTimersByTimeAsync(60_000);
    expect(server.count('sdk-errors')).toBe(1);
  });
});
