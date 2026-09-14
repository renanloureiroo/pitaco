// Casos de borda da feature 4 (exibição e coleta no app), de ponta a ponta sobre o runtime com o
// servidor falso: nada lança, nada aparece quando não deve, e nada se perde nem se duplica.

import type { InteractionEvent } from '../../../catalog/events';
import { jestClock } from '../../../__tests__/support/fakeClock';
import { FakePitacoServer } from '../../../__tests__/support/fakeServer';
import { deliverableSurvey, KEYS, SURVEY_ID, VERSION_ID } from '../../../__tests__/support/fixtures';
import { createLogger } from '../../logger';
import { createMemoryStorage } from '../../storage/memory';
import type { PitacoStorage } from '../../storage/types';
import { systemTimers } from '../../timers';
import { isUuidV4 } from '../../uuid';
import { type AppStateLike, type PitacoListenerEvent, PitacoRuntime, type PitacoRuntimeOptions } from '../runtime';

const NOW = Date.UTC(2026, 8, 12, 13, 0, 0);

interface Setup {
  readonly server?: FakePitacoServer;
  readonly storage?: PitacoStorage;
  readonly options?: Partial<PitacoRuntimeOptions>;
  readonly isDev?: boolean;
  readonly appState?: AppStateLike;
}

function setup({ server = new FakePitacoServer(), storage = createMemoryStorage(), options = {}, isDev = true, appState }: Setup = {}) {
  const events: PitacoListenerEvent[] = [];
  const warnings: string[] = [];
  const runtime = PitacoRuntime.create(
    {
      baseUrl: 'https://pitaco.test/api',
      apiKey: 'pk_test',
      storage,
      onEvent: (event) => events.push(event),
      ...options,
    },
    {
      fetch: server.fetch,
      clock: jestClock,
      timers: systemTimers,
      logger: createLogger({ isDev, sink: (message) => warnings.push(message) }),
      ...(appState ? { appState } : {}),
    },
  );
  if (runtime === null) throw new Error('runtime não criado');
  runtime.attach();
  const interaction = () => events.filter((event): event is InteractionEvent => 'catalogVersion' in event);
  const placements = () => events.filter((event) => !('catalogVersion' in event));
  return { runtime, server, storage, events, interaction, placements, warnings };
}

async function answerEverything(runtime: PitacoRuntime) {
  runtime.present('bottom-sheet');
  runtime.dispatch({ type: 'select', value: 9 });
  runtime.dispatch({ type: 'next' });
  runtime.dispatch({ type: 'select', value: 'pix' });
  runtime.dispatch({ type: 'next' });
  runtime.dispatch({ type: 'select', value: 5 });
  runtime.dispatch({ type: 'next' });
  runtime.dispatch({ type: 'complete' });
  await runtime.settle();
}

beforeEach(() => {
  jest.useFakeTimers({ now: NOW });
});

afterEach(() => {
  jest.useRealTimers();
});

describe('configuração', () => {
  it.each([
    [{ baseUrl: '' }, 'baseUrl é obrigatório'],
    [{ baseUrl: 'pitaco.exemplo.com' }, 'não é um endereço http(s) válido'],
    [{ apiKey: '' }, 'apiKey é obrigatório'],
  ])('inválida (%j) não liga o SDK e diz como corrigir em desenvolvimento', (override, message) => {
    const warnings: string[] = [];
    const runtime = PitacoRuntime.create(
      { baseUrl: 'https://pitaco.test/api', apiKey: 'pk_test', ...override },
      { logger: createLogger({ isDev: true, sink: (text) => warnings.push(text) }) },
    );
    expect(runtime).toBeNull();
    expect(warnings.join('\n')).toContain(message);
  });

  it('inválida em produção é silenciosa', () => {
    const sink = jest.fn();
    expect(PitacoRuntime.create({ baseUrl: '', apiKey: '' }, { logger: createLogger({ isDev: false, sink }) })).toBeNull();
    expect(sink).not.toHaveBeenCalled();
  });

  it('sem storage persistente avisa em desenvolvimento que a fila não sobrevive a reinício', () => {
    const warnings: string[] = [];
    const runtime = PitacoRuntime.create(
      { baseUrl: 'https://pitaco.test/api', apiKey: 'pk_test' },
      { fetch: new FakePitacoServer().fetch, logger: createLogger({ isDev: true, sink: (text) => warnings.push(text) }) },
    );
    runtime?.attach();
    expect(warnings.join('\n')).toContain('não sobrevive a um reinício');
    runtime?.dispose();
  });

  it('valores inválidos de apresentação e timeout caem no padrão', () => {
    const { runtime, warnings } = setup({ options: { presentation: 'popup', eligibilityTimeoutMs: -1 } });
    expect(runtime.config.presentation).toBe('bottom-sheet');
    expect(runtime.config.eligibilityTimeoutMs).toBe(3000);
    expect(warnings.join('\n')).toContain('presentation "popup" não existe');
  });
});

describe('elegibilidade e degradação silenciosa', () => {
  it('sem pesquisa: nada fica disponível e nenhuma exibição é aberta', async () => {
    const { runtime, server } = setup();
    await runtime.track('checkout_completed', { plano: 'pro', ativo: true, dias: 3 });
    expect(runtime.getSnapshot()).toBeNull();
    expect(server.requests[0]?.body).toEqual({
      event: 'checkout_completed',
      respondent: { deviceId: expect.stringMatching(/^[0-9a-f-]{36}$/) as unknown },
      attributes: { plano: 'pro', ativo: 'true', dias: '3' },
    });
    expect(server.count('displays')).toBe(0);
  });

  it('API fora do ar: nada aparece, nada lança, e o integrador é avisado uma vez', async () => {
    const server = new FakePitacoServer();
    server.behavior = 'down';
    const { runtime, warnings } = setup({ server });
    await expect(runtime.track('checkout_completed')).resolves.toBeUndefined();
    await expect(runtime.track('checkout_completed')).resolves.toBeUndefined();
    expect(runtime.getSnapshot()).toBeNull();
    expect(warnings.filter((text) => text.includes('Não foi possível falar com'))).toHaveLength(1);
  });

  it('API lenta: a consulta é abandonada em 3 s e nada aparece', async () => {
    const server = new FakePitacoServer();
    server.behavior = 'hang';
    const { runtime } = setup({ server });
    let done = false;
    const tracking = runtime.track('checkout_completed').then(() => {
      done = true;
    });
    await jest.advanceTimersByTimeAsync(2999);
    expect(done).toBe(false);
    await jest.advanceTimersByTimeAsync(1);
    await tracking;
    expect(done).toBe(true);
    expect(runtime.getSnapshot()).toBeNull();
  });

  it('o timeout da elegibilidade é configurável', async () => {
    const server = new FakePitacoServer();
    server.behavior = 'hang';
    const { runtime } = setup({ server, options: { eligibilityTimeoutMs: 1000 } });
    let done = false;
    void runtime.track('checkout_completed').then(() => {
      done = true;
    });
    await jest.advanceTimersByTimeAsync(1000);
    expect(done).toBe(true);
  });

  it.each([
    ['corpo que não é JSON', { status: 200, raw: '<html>erro do gateway</html>' }],
    ['pesquisa sem perguntas', { status: 200, body: { survey: { surveyId: 's', versionId: 'v', questions: 'x' } } }],
    ['corpo que não é objeto', { status: 200, body: [1, 2] }],
  ])('resposta malformada (%s): nada é exibido e o erro vai para o canal do Pitaco', async (_name, reply) => {
    const server = new FakePitacoServer();
    server.script('eligibility', reply);
    const { runtime } = setup({ server });
    await runtime.track('checkout_completed');
    await jest.advanceTimersByTimeAsync(0);
    expect(runtime.getSnapshot()).toBeNull();
    expect(server.errors).toEqual([expect.objectContaining({ kind: 'malformed_response' })]);
  });

  it('relatório de erro desligado não manda nada', async () => {
    const server = new FakePitacoServer();
    server.script('eligibility', { status: 200, raw: 'x' });
    const { runtime } = setup({ server, options: { errorReporting: false } });
    await runtime.track('checkout_completed');
    await jest.advanceTimersByTimeAsync(0);
    expect(server.count('sdk-errors')).toBe(0);
  });

  it('chave inválida: nada aparece, o integrador vê como corrigir e o SDK para de consultar', async () => {
    const server = new FakePitacoServer();
    server.apiKey = 'pk_outra';
    server.survey = deliverableSurvey();
    const { runtime, warnings } = setup({ server });
    await runtime.track('checkout_completed');
    await runtime.track('checkout_completed');
    expect(runtime.getSnapshot()).toBeNull();
    expect(server.count('eligibility')).toBe(1);
    expect(warnings.join('\n')).toContain('A chave foi recusada pelo servidor (401, api_key.invalid)');
  });

  it('chave inválida em produção é silenciosa', async () => {
    const server = new FakePitacoServer();
    server.apiKey = 'pk_outra';
    const { runtime, warnings } = setup({ server, isDev: false });
    await runtime.track('checkout_completed');
    expect(warnings).toEqual([]);
  });

  it('429 na elegibilidade: respeita o Retry-After antes de consultar de novo', async () => {
    const server = new FakePitacoServer();
    server.script('eligibility', { status: 429, headers: { 'Retry-After': '60' } });
    const { runtime } = setup({ server });
    await runtime.track('checkout_completed');
    await runtime.track('checkout_completed');
    expect(server.count('eligibility')).toBe(1);
    await jest.advanceTimersByTimeAsync(60_000);
    await runtime.track('checkout_completed');
    expect(server.count('eligibility')).toBe(2);
  });

  it('erro no onEvent do app não derruba nada', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime, warnings } = setup({
      server,
      options: {
        onEvent: () => {
          throw new Error('analytics quebrado');
        },
      },
    });
    await runtime.track('checkout_completed');
    await answerEverything(runtime);
    expect(server.submissions.size).toBe(1);
    expect(warnings.join('\n')).toContain('O onEvent do PitacoProvider lançou um erro');
  });
});

describe('perguntas desconhecidas e supressão', () => {
  it('pergunta desconhecida é pulada e a resposta chega normalmente', async () => {
    const server = new FakePitacoServer();
    const base = deliverableSurvey();
    server.survey = {
      ...base,
      campoNovo: 'ignorado',
      questions: [
        ...(base.questions as unknown[]),
        { key: 'matriz', position: 6, statement: 'Matriz', type: 'MATRIX', required: false },
      ],
    };
    const { runtime, interaction } = setup({ server });
    await runtime.track('checkout_completed');
    expect(runtime.getSnapshot()?.questions).toHaveLength(5);
    await answerEverything(runtime);

    expect(interaction()[0]?.data).toMatchObject({ questionCount: 6, renderableCount: 5 });
    const [displayId] = Array.from(server.submissions.keys());
    const sent = server.submissions.get(displayId ?? '') as { answers: { questionKey: string }[] };
    expect(sent.answers.map((answer) => answer.questionKey)).not.toContain('matriz');
    expect(server.count('suppressions')).toBe(0);
  });

  it('só perguntas desconhecidas: nada é exibido, nenhuma exibição é aberta, e a supressão é enviada', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey({
      questions: [
        { key: 'a', position: 1, statement: 'A', type: 'MATRIX' },
        { key: 'b', position: 2, statement: 'B', type: 'CAROUSEL' },
      ],
    });
    const { runtime, events } = setup({ server, options: { respondent: { reference: 'u-8f1c' } } });
    await runtime.track('checkout_completed');
    await runtime.settle();
    expect(runtime.getSnapshot()).toBeNull();
    expect(server.count('displays')).toBe(0);
    expect(server.suppressions).toEqual([
      {
        surveyId: SURVEY_ID,
        versionId: VERSION_ID,
        reason: 'unknown_question_type',
        questionTypes: ['MATRIX', 'CAROUSEL'],
        features: [],
        deviceId: expect.any(String) as unknown,
        respondentReference: 'u-8f1c',
      },
    ]);
    expect(events).toEqual([
      expect.objectContaining({
        type: 'placement_suppressed',
        data: { reason: 'unknown_question_type', questionTypes: ['MATRIX', 'CAROUSEL'] },
      }),
    ]);
  });
});

describe('exibição e envio', () => {
  it('a exibição só abre quando o contêiner fica visível', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime, events } = setup({ server, options: { attributes: { plano: 'pro' }, respondent: { reference: 'u-1' } } });
    await runtime.track('checkout_completed');
    await runtime.settle();
    expect(runtime.getSnapshot()).toMatchObject({ status: 'ready', question: { key: KEYS.nps } });
    expect(events.map((event) => event.type)).toEqual(['placement_available']);
    expect(server.count('displays')).toBe(0);

    runtime.present();
    await runtime.settle();
    expect(server.count('displays')).toBe(1);
    const opened = server.requests.find((request) => request.route === 'displays')?.body as Record<string, unknown>;
    expect(isUuidV4(opened.displayId)).toBe(true);
    expect(opened).toMatchObject({
      surveyId: SURVEY_ID,
      versionId: VERSION_ID,
      respondent: { reference: 'u-1' },
      attributes: { plano: 'pro' },
      sdkVersion: '1.0.0',
    });
    expect(server.eventsOf(String(opened.displayId)).map((event) => event.type)).toEqual([
      'survey_presented',
      'question_viewed',
    ]);
  });

  it('os eventos que o servidor recebe são os mesmos que o onEvent recebe', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime, interaction } = setup({ server });
    await runtime.track('checkout_completed');
    await answerEverything(runtime);
    const displayId = runtime.getSnapshot()?.displayId ?? '';
    expect(server.eventsOf(displayId)).toEqual(interaction());
    expect(runtime.getSnapshot()?.status).toBe('completed');
    expect(runtime.diagnostics().queue).toEqual([]);
  });

  it('enquanto a pessoa responde, os eventos saem em lote alguns segundos depois', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime } = setup({ server });
    await runtime.track('checkout_completed');
    runtime.present();
    await runtime.settle();
    const afterOpen = server.count('events');
    runtime.dispatch({ type: 'select', value: 9 });
    runtime.dispatch({ type: 'select', value: 8 });
    await runtime.settle();
    expect(server.count('events')).toBe(afterOpen);
    await jest.advanceTimersByTimeAsync(5000);
    await runtime.settle();
    expect(server.count('events')).toBe(afterOpen + 1);
  });

  it('resposta sem rede é enviada exatamente uma vez ao reabrir o app', async () => {
    const storage = createMemoryStorage();
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const first = setup({ server, storage });
    await first.runtime.track('checkout_completed');
    server.behavior = 'down';
    await answerEverything(first.runtime);
    const displayId = first.runtime.getSnapshot()?.displayId ?? '';
    expect(server.submissions.size).toBe(0);
    expect(first.runtime.diagnostics().queue.length).toBeGreaterThan(0);
    first.runtime.dispose();

    server.behavior = 'up';
    const reopened = setup({ server, storage });
    await reopened.runtime.settle();
    await jest.advanceTimersByTimeAsync(0);
    await reopened.runtime.settle();
    expect(server.displays.size).toBe(1);
    expect(server.submissions.size).toBe(1);
    expect(server.eventsOf(displayId)).toEqual(first.interaction());
    expect(reopened.runtime.diagnostics().queue).toEqual([]);
    reopened.runtime.dispose();

    const requests = server.requests.length;
    const third = setup({ server, storage });
    await third.runtime.settle();
    expect(server.requests.length).toBe(requests);
  });

  it('reenvio sem duplicar: a resposta perdida no caminho é reenviada e continua uma só', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    server.script('submission', 'lost');
    const { runtime } = setup({ server });
    await runtime.track('checkout_completed');
    await answerEverything(runtime);
    expect(server.submissions.size).toBe(1);
    await jest.advanceTimersByTimeAsync(2000);
    await runtime.settle();
    expect(server.count('submission')).toBe(2);
    expect(server.submissions.size).toBe(1);
    expect(runtime.diagnostics().queue).toEqual([]);
  });

  it('dispensa na segunda pergunta: registrada como dispensada, com a primeira resposta preservada', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime, interaction } = setup({ server });
    await runtime.track('checkout_completed');
    runtime.present();
    runtime.dispatch({ type: 'select', value: 8 });
    runtime.dispatch({ type: 'next' });
    runtime.dispatch({ type: 'dismiss', via: 'close_button' });
    await runtime.settle();
    const [submission] = Array.from(server.submissions.values());
    expect(submission).toEqual({
      outcome: 'DISMISSED',
      answers: [
        { questionKey: KEYS.nps, status: 'ANSWERED', value: 8 },
        { questionKey: KEYS.reason, status: 'NOT_APPLICABLE' },
        { questionKey: KEYS.features, status: 'SKIPPED' },
      ],
    });
    expect(interaction().at(-1)).toMatchObject({ type: 'survey_dismissed', data: { via: 'close_button', answeredCount: 1 } });
  });

  it('dispensar antes de o contêiner ficar visível não abre exibição nem envia nada', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime } = setup({ server });
    await runtime.track('checkout_completed');
    runtime.dispatch({ type: 'dismiss', via: 'navigation' });
    await runtime.settle();
    expect(runtime.getSnapshot()?.status).toBe('discarded');
    expect(server.count('displays') + server.count('submission') + server.count('events')).toBe(0);
  });

  it('com uma pesquisa em andamento, outra que chegar é ignorada', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime } = setup({ server });
    await runtime.track('checkout_completed');
    const displayId = runtime.getSnapshot()?.displayId;
    await runtime.track('outro_evento');
    expect(runtime.getSnapshot()?.displayId).toBe(displayId);
  });

  it('AppState: segundo plano vira evento e manda a fila; a volta também', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    let listener: ((state: string) => void) | undefined;
    const appState: AppStateLike = {
      addEventListener: (_type, callback) => {
        listener = callback;
        return { remove: () => undefined };
      },
    };
    const { runtime, interaction } = setup({ server, appState });
    await runtime.track('checkout_completed');
    runtime.present();
    await runtime.settle();
    listener?.('background');
    await jest.advanceTimersByTimeAsync(4000);
    listener?.('active');
    await runtime.settle();
    const types = interaction().map((event) => event.type);
    expect(types.slice(-2)).toEqual(['survey_backgrounded', 'survey_foregrounded']);
    expect(interaction().at(-1)?.data).toEqual({ backgroundMs: 4000 });
    expect(server.eventsOf(runtime.getSnapshot()?.displayId ?? '').map((event) => event.type)).toContain(
      'survey_backgrounded',
    );
  });

  it('reset (logout) dispensa a pesquisa aberta e troca o deviceId', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime, interaction } = setup({ server, options: { respondent: { reference: 'u-1' } } });
    await runtime.track('checkout_completed');
    runtime.present();
    const before = runtime.diagnostics().deviceId;
    await runtime.reset();
    await runtime.settle();
    expect(interaction().at(-1)).toMatchObject({ type: 'survey_dismissed', data: { via: 'programmatic' } });
    expect(runtime.diagnostics().deviceId).not.toBe(before);
    server.survey = null;
    // reset() não zera o limite de uma pesquisa por sessão de app (feature 4: é sessão de uso do
    // app, não de login); simula a reabertura para este track() voltar a consultar o servidor.
    runtime.simulateAppReopen();
    await runtime.track('checkout_completed');
    expect(server.requests.at(-1)?.body).toMatchObject({ respondent: { deviceId: runtime.diagnostics().deviceId } });
    expect(JSON.stringify(server.requests.at(-1)?.body)).not.toContain('u-1');
  });
});

// Fase 4 — controle de onde e quando exibir: uma pesquisa por sessão de app, bloqueio,
// adiamento, e a interação entre os dois.
describe('sessão de app: no máximo uma pesquisa exibida', () => {
  it('depois de exibida (mesmo dispensada sem responder), nenhuma outra aparece na mesma sessão', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime, placements } = setup({ server });
    await runtime.track('checkout_completed');
    runtime.present();
    runtime.dispatch({ type: 'dismiss', via: 'close_button' });
    await runtime.settle();
    expect(runtime.diagnostics().sessionSurveyShown).toBe(true);

    await runtime.track('outro_evento');
    expect(server.count('eligibility')).toBe(1); // track() nem consultou o servidor
    expect(placements().at(-1)).toMatchObject({
      type: 'placement_session_limited',
      triggerEvent: 'outro_evento',
      surveyId: null,
      versionId: null,
      data: {},
    });
  });

  it('reabrir o app (simulateAppReopen) zera o limite', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime } = setup({ server });
    await runtime.track('checkout_completed');
    runtime.present();
    runtime.dispatch({ type: 'dismiss', via: 'close_button' });
    await runtime.settle();

    runtime.simulateAppReopen();
    expect(runtime.diagnostics().sessionSurveyShown).toBe(false);
    await runtime.track('outro_evento');
    expect(server.count('eligibility')).toBe(2);
    expect(runtime.getSnapshot()).toMatchObject({ status: 'ready' });
  });

  it('uma pesquisa oferecida mas nunca apresentada não esgota a sessão', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime } = setup({ server });
    await runtime.track('checkout_completed');
    expect(runtime.getSnapshot()?.status).toBe('ready');
    expect(runtime.diagnostics().sessionSurveyShown).toBe(false);
  });
});

describe('bloqueio: block/unblock', () => {
  it('motivo repetido acumula por contagem de referência; só o primeiro block emite placement_blocked', () => {
    const { runtime, placements } = setup();
    runtime.block('pagamento');
    runtime.block('pagamento');
    expect(runtime.diagnostics().blockedReasons).toEqual(['pagamento']);
    expect(placements().filter((event) => event.type === 'placement_blocked')).toHaveLength(1);

    runtime.unblock('pagamento');
    expect(runtime.diagnostics().blockedReasons).toEqual(['pagamento']); // ainda um block pendente
    runtime.unblock('pagamento');
    expect(runtime.diagnostics().blockedReasons).toEqual([]);
  });

  it('motivos diferentes coexistem e se desbloqueiam independentemente', () => {
    const { runtime } = setup();
    runtime.block('pagamento');
    runtime.block('onboarding');
    expect(runtime.diagnostics().blockedReasons.slice().sort()).toEqual(['onboarding', 'pagamento']);
    runtime.unblock('pagamento');
    expect(runtime.diagnostics().blockedReasons).toEqual(['onboarding']);
  });

  it('unblock sem block correspondente não lança e avisa em desenvolvimento', () => {
    const { runtime, warnings } = setup();
    expect(() => runtime.unblock('nunca-bloqueado')).not.toThrow();
    expect(warnings.join('\n')).toContain('chamado sem um block');
  });

  it('pesquisa chegando durante bloqueio fica retida; desbloquear a exibe', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime, placements } = setup({ server });
    runtime.block('pagamento');
    await runtime.track('checkout_completed');
    expect(runtime.getSnapshot()).toBeNull();
    expect(server.count('displays')).toBe(0);
    expect(placements().map((event) => event.type)).toEqual(['placement_blocked', 'placement_survey_held']);

    runtime.unblock('pagamento');
    expect(runtime.getSnapshot()).toMatchObject({ status: 'ready' });
    expect(placements().at(-1)).toMatchObject({ type: 'placement_available' });
    // sucesso do bloqueio não tem evento próprio: só o `placement_available` de sempre.
    expect(placements().map((event) => event.type)).not.toContain('placement_released');
  });

  it('bloqueio que começa com a pesquisa já aberta não a interrompe', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime } = setup({ server });
    await runtime.track('checkout_completed');
    runtime.present();
    await runtime.settle();
    runtime.block('pagamento');
    expect(runtime.getSnapshot()?.status).toBe('presented');
  });

  it('pesquisa retida por bloqueio além do prazo é descartada sem abrir exibição, sem consumir o limite da sessão', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime, placements } = setup({ server, options: { deferTimeoutMs: 1000 } });
    runtime.block('pagamento');
    await runtime.track('checkout_completed');
    await jest.advanceTimersByTimeAsync(1000);
    expect(runtime.getSnapshot()).toBeNull();
    expect(server.count('displays')).toBe(0);
    expect(placements().at(-1)).toMatchObject({ type: 'placement_survey_discarded', data: { heldMs: 1000 } });
    expect(runtime.diagnostics().sessionSurveyShown).toBe(false);

    runtime.unblock('pagamento');
    expect(runtime.getSnapshot()).toBeNull(); // já descartada; desbloquear não a traz de volta

    server.survey = deliverableSurvey();
    await runtime.track('outro_evento');
    expect(server.count('eligibility')).toBe(2);
    expect(runtime.getSnapshot()).toMatchObject({ status: 'ready' });
  });
});

describe('adiamento: defer/release', () => {
  it('defer() represa a próxima pesquisa disponível; release() a exibe', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime, placements } = setup({ server });
    runtime.defer();
    await runtime.track('checkout_completed');
    expect(runtime.getSnapshot()).toBeNull();
    expect(server.count('displays')).toBe(0);
    expect(placements().map((event) => event.type)).toEqual(['placement_deferred']);

    runtime.release();
    expect(runtime.getSnapshot()).toMatchObject({ status: 'ready' });
    expect(placements().slice(-2).map((event) => event.type)).toEqual(['placement_released', 'placement_available']);
  });

  it('release() sem defer() correspondente não lança e avisa em desenvolvimento', () => {
    const { runtime, warnings } = setup();
    expect(() => runtime.release()).not.toThrow();
    expect(warnings.join('\n')).toContain('release() chamado sem defer()');
  });

  it('prazo do adiamento estourado descarta sem abrir exibição, sem consumir o limite da sessão', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime, placements } = setup({ server, options: { deferTimeoutMs: 2000 } });
    runtime.defer();
    await runtime.track('checkout_completed');
    await jest.advanceTimersByTimeAsync(2000);
    expect(runtime.getSnapshot()).toBeNull();
    expect(server.count('displays')).toBe(0);
    expect(placements().at(-1)).toMatchObject({ type: 'placement_expired', data: { heldMs: 2000 } });
    expect(runtime.diagnostics().sessionSurveyShown).toBe(false);
  });

  it('defer() + block(): release() com bloqueio ativo mantém a pesquisa retida até desbloquear', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime, placements } = setup({ server });
    runtime.defer();
    await runtime.track('checkout_completed');
    runtime.block('pagamento');
    runtime.release();
    expect(runtime.getSnapshot()).toBeNull();
    expect(placements().map((event) => event.type)).not.toContain('placement_released');

    runtime.unblock('pagamento');
    expect(runtime.getSnapshot()).toMatchObject({ status: 'ready' });
    expect(placements().slice(-2).map((event) => event.type)).toEqual(['placement_released', 'placement_available']);
  });
});

describe('reset (logout) e o controle de onde e quando exibir', () => {
  it('candidata retida é descartada sem exibição; bloqueios pendentes e o limite de sessão continuam', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime } = setup({ server });
    runtime.block('pagamento');
    await runtime.track('checkout_completed');
    expect(runtime.diagnostics().held).toBe(true);

    await runtime.reset();
    expect(runtime.diagnostics().held).toBe(false);
    expect(runtime.diagnostics().blockedReasons).toEqual(['pagamento']);
  });

  it('reset não zera o limite de uma pesquisa por sessão de app', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime } = setup({ server });
    await runtime.track('checkout_completed');
    runtime.present();
    runtime.dispatch({ type: 'dismiss', via: 'close_button' });
    await runtime.settle();

    await runtime.reset();
    await runtime.settle();
    await runtime.track('outro_evento');
    expect(server.count('eligibility')).toBe(1);
  });
});

describe('eventos placement_ ficam só no onEvent', () => {
  it('nunca entram na fila local nem são enviados ao servidor', async () => {
    const server = new FakePitacoServer();
    server.survey = deliverableSurvey();
    const { runtime, placements } = setup({ server });
    runtime.block('pagamento');
    await runtime.track('checkout_completed');
    runtime.unblock('pagamento');
    await runtime.settle();

    expect(placements().length).toBeGreaterThan(0);
    const queueDump = JSON.stringify(runtime.diagnostics().queue);
    expect(queueDump).not.toMatch(/placement_/);
    const requestsDump = JSON.stringify(server.requests.map((request) => request.body));
    expect(requestsDump).not.toMatch(/placement_/);
  });
});
