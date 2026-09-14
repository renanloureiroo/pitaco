// Orquestrador: a camada acima da máquina pura. Consulta a elegibilidade, normaliza o schema,
// decide entre supressão e oferta, cria a sessão da exibição, abre a exibição só quando a UI diz
// que o contêiner ficou visível (`survey_presented`), grava tudo na fila antes de enviar, reage ao
// `AppState` e entrega os eventos ao `onEvent` do app.
//
// Nenhum método público lança nem rejeita: toda falha vira silêncio, aviso em desenvolvimento e,
// quando é defeito do SDK, relatório de erro.

import type { OpenDisplayRequest, SuppressionRequest } from '../../api/types';
import type { InteractionEvent, Presentation } from '../../catalog/events';
import type { PlacementEvent, PlacementEventDataMap, PlacementEventType } from '../../catalog/placement';
import { SDK_VERSION } from '../../version';
import { type Clock, createSystemClock, toIsoString } from '../clock';
import {
  type Attributes,
  type ConfigInput,
  type Respondent,
  type ResolvedConfig,
  resolveConfig,
  sanitizeAttributes,
  sanitizeBlockReason,
  sanitizeEventName,
  sanitizeReference,
} from '../config';
import { describeError, ErrorReporter } from '../errors/reporter';
import { DeviceIdentity } from '../identity';
import { createLogger, type Logger } from '../logger';
import type { MachineState } from '../machine/machine';
import { findNextApplicable } from '../machine/path';
import { buildSubmission } from '../machine/submission';
import { DeliveryQueue, type QueueItem, type QueueLimits } from '../queue/queue';
import type { SurveyController, SurveyUserAction } from '../session/controller';
import { SurveySession } from '../session/session';
import type { SurveySnapshot } from '../session/snapshot';
import { createMemoryStorage, isMemoryStorage } from '../storage/memory';
import { SafeStorage } from '../storage/safe';
import type { PitacoStorage } from '../storage/types';
import { parseEligibilityResponse, type Survey } from '../survey/schema';
import { systemTimers, type TimerHandle, type Timers } from '../timers';
import { CollectApi } from '../transport/api';
import { type FetchLike, HttpClient, type HttpOutcome } from '../transport/http';
import { uuidV4 } from '../uuid';
import type { PlacementControls, PlacementGate, SurveyCandidate } from './placement';

export type PitacoListenerEvent = InteractionEvent | PlacementEvent;

export interface AppStateLike {
  addEventListener(type: 'change', listener: (state: string) => void): { remove(): void } | undefined;
}

export interface PitacoRuntimeOptions extends ConfigInput {
  readonly respondent?: Respondent | null;
  readonly attributes?: Attributes;
  readonly storage?: PitacoStorage;
  readonly onEvent?: (event: PitacoListenerEvent) => void;
}

export interface RuntimeDependencies {
  readonly fetch?: FetchLike;
  readonly clock?: Clock;
  readonly timers?: Timers;
  readonly uuid?: () => string;
  readonly appState?: AppStateLike;
  readonly logger?: Logger;
  readonly queueLimits?: Partial<QueueLimits>;
}

export interface RuntimeDiagnostics {
  readonly deviceId: string | null;
  readonly queue: readonly QueueItem[];
  readonly keyRejected: boolean;
  readonly rateLimitedUntil: number;
  // Controle de onde e quando exibir (feature 4). Não é API pública documentada: serve ao painel
  // de depuração do exemplo e aos testes.
  readonly sessionSurveyShown: boolean;
  readonly blockedReasons: readonly string[];
  readonly deferred: boolean;
  readonly held: boolean;
}

// Uma pesquisa candidata retida pelo portão padrão (bloqueio ou adiamento), esperando liberação
// dentro do prazo (`deferTimeoutMs`). `controls` é o mesmo objeto entregue pelo `consider()` que a
// reteve: chamar `offer`/`discard`/`notify` depois, de um timer ou de `unblock`/`release`, ainda
// afeta a candidata certa.
interface HeldSurvey {
  readonly candidate: SurveyCandidate;
  readonly controls: PlacementControls;
  readonly heldAtMono: number;
  readonly cause: 'block' | 'defer';
}

// Enquanto a pessoa responde, os eventos esperam um pouco na fila para sair em lote. A abertura,
// o desfecho e a ida para o segundo plano mandam na hora.
const EVENT_FLUSH_DELAY_MS = 5000;

function hash(text: string): string {
  let value = 5381;
  for (let index = 0; index < text.length; index += 1) {
    value = ((value << 5) + value + text.charCodeAt(index)) | 0;
  }
  return (value >>> 0).toString(36);
}

const lazyGlobalFetch: FetchLike = (url, init) => {
  const fetchFn = (globalThis as { fetch?: unknown }).fetch;
  if (typeof fetchFn !== 'function') return Promise.reject(new TypeError('fetch indisponível'));
  return (fetchFn as FetchLike)(url, init);
};

export class PitacoRuntime implements SurveyController {
  // Devolve `null` quando a configuração não permite ligar o SDK (sem `baseUrl` ou `apiKey`); o
  // aviso com a correção já saiu em desenvolvimento.
  static create(options: PitacoRuntimeOptions, dependencies: RuntimeDependencies = {}): PitacoRuntime | null {
    const logger = dependencies.logger ?? createLogger({ debug: options.debug === true });
    try {
      const config = resolveConfig(options, logger);
      if (config === null) return null;
      return new PitacoRuntime(config, options, dependencies, logger);
    } catch (error) {
      logger.warn(`Não foi possível iniciar o SDK (${describeError(error)}). O app segue sem pesquisas.`);
      return null;
    }
  }

  readonly config: ResolvedConfig;
  private readonly logger: Logger;
  private readonly clock: Clock;
  private readonly timers: Timers;
  private readonly uuid: () => string;
  private readonly appState: AppStateLike | undefined;
  private readonly http: HttpClient;
  private readonly api: CollectApi;
  private readonly reporter: ErrorReporter;
  private readonly storage: SafeStorage;
  private readonly usesMemoryStorage: boolean;
  private readonly identity: DeviceIdentity;
  private readonly queue: DeliveryQueue;
  private onEvent: ((event: PitacoListenerEvent) => void) | undefined;
  private readonly listeners = new Set<() => void>();

  private gate: PlacementGate;
  private reference: string | undefined;
  private attributes: Record<string, string>;
  private session: SurveySession | null = null;
  private unsubscribeSession: (() => void) | null = null;
  private appStateSubscription: { remove(): void } | null = null;
  private booting: Promise<void> | null = null;
  private pipeline: Promise<void> = Promise.resolve();
  private flushTimer: TimerHandle | null = null;
  private attached = false;
  private disposed = false;
  private keyRejected = false;

  // Controle de onde e quando exibir (feature 4) --------------------------------------------

  // Uma pesquisa exibida (exibição aberta) já esgota a sessão de app; `track()` nem consulta o
  // servidor enquanto isto for `true`. Não é persistido: um runtime novo (processo novo) já nasce
  // com o limite zerado, que é a própria definição de "reabrir o app zera o limite" — sem precisar
  // de storage nem de expiração por tempo.
  private sessionSurveyShown = false;
  // Motivo → quantas vezes está ativo (`block()` chamado sem o `unblock()` correspondente ainda).
  // Contagem de referência, não conjunto: dois `<PitacoBlock reason="pagamento" />` montados ao
  // mesmo tempo não se anulam no primeiro a desmontar.
  private readonly blockReasons = new Map<string, number>();
  // `defer()` chamado e ainda sem `release()`: a próxima candidata (ou a que já estiver retida)
  // fica represada por causa do adiamento, além de qualquer bloqueio.
  private deferRequested = false;
  private held: HeldSurvey | null = null;
  private holdTimer: TimerHandle | null = null;

  private constructor(
    config: ResolvedConfig,
    options: PitacoRuntimeOptions,
    dependencies: RuntimeDependencies,
    logger: Logger,
  ) {
    this.config = config;
    this.gate = {
      consider: (candidate, controls) => this.considerPlacement(candidate, controls),
      sessionFinished: () => undefined,
    };
    this.logger = logger;
    this.clock = dependencies.clock ?? createSystemClock();
    this.timers = dependencies.timers ?? systemTimers;
    this.uuid = dependencies.uuid ?? uuidV4;
    this.appState = dependencies.appState;
    this.onEvent = options.onEvent;
    this.reference = sanitizeReference(options.respondent?.reference, logger);
    this.attributes = sanitizeAttributes(options.attributes, logger, 'PitacoProvider');

    this.http = new HttpClient({
      baseUrl: config.baseUrl,
      apiKey: config.apiKey,
      sdkVersion: SDK_VERSION,
      fetch: dependencies.fetch ?? lazyGlobalFetch,
      clock: this.clock,
      timers: this.timers,
      timeoutMs: config.requestTimeoutMs,
    });
    this.api = new CollectApi(this.http, config.eligibilityTimeoutMs);
    this.reporter = new ErrorReporter({
      enabled: config.errorReporting,
      api: this.api,
      clock: this.clock,
      logger,
    });

    const adapter = options.storage ?? createMemoryStorage();
    this.usesMemoryStorage = isMemoryStorage(adapter);
    this.storage = new SafeStorage(adapter, (operation, error) =>
      this.reporter.report('storage_error', `storage ${operation}: ${describeError(error)}`, {
        operation,
      }),
    );
    this.identity = new DeviceIdentity(this.storage, this.uuid);
    this.queue = new DeliveryQueue({
      storage: this.storage,
      storageKey: `@pitaco/v1:queue:${hash(`${config.baseUrl}|${config.apiKey}`)}`,
      api: this.api,
      http: this.http,
      clock: this.clock,
      timers: this.timers,
      uuid: this.uuid,
      logger,
      ...(dependencies.queueLimits ? { limits: dependencies.queueLimits } : {}),
      onRejected: (item, outcome) => this.onQueueRejected(item, outcome),
      onUnauthorized: (outcome) => this.onUnauthorized(outcome),
    });
  }

  // Liga o runtime à vida do app: carrega identidade e fila, reenvia o que ficou pendente (cada
  // abertura do app é uma chance de entregar) e passa a ouvir o `AppState`. Idempotente, e pode
  // voltar depois de `detach` (o StrictMode monta, desmonta e monta de novo).
  attach(): void {
    if (this.attached || this.disposed) return;
    this.attached = true;
    this.queue.resume();
    if (this.usesMemoryStorage) {
      this.logger.warnOnce(
        'memory-storage',
        'Sem storage persistente: a fila de respostas fica em memória e não sobrevive a um reinício do app. Passe storage={createAsyncStorageAdapter(AsyncStorage)} (de "@pitaco/react-native/storage/async-storage") ou storage={createMmkvAdapter(mmkv)} (de "@pitaco/react-native/storage/mmkv").',
      );
    }
    try {
      this.appStateSubscription =
        this.appState?.addEventListener('change', (state) => this.onAppState(state)) ?? null;
    } catch (error) {
      this.reporter.report('unknown', `appstate: ${describeError(error)}`, { stage: 'appstate' });
    }
    void this.boot().then(() => this.queue.flush({ force: true }));
  }

  detach(): void {
    this.attached = false;
    try {
      this.appStateSubscription?.remove();
    } catch {
      // Remover o ouvinte é melhor esforço.
    }
    this.appStateSubscription = null;
    this.clearFlushTimer();
    this.queue.pause();
  }

  // Encerra de vez (o Provider trocou de runtime porque a configuração ou o storage mudou): nada
  // mais sai deste runtime, nem evento para o `onEvent`, nem exibição de uma elegibilidade que
  // ainda estava a caminho. O que já foi gravado na fila persistente fica lá, para o runtime novo
  // entregar.
  dispose(): void {
    this.disposed = true;
    this.detach();
    this.clearHoldTimer();
    this.held = null;
    this.gate.dispose?.();
    this.replaceSession(null);
    this.listeners.clear();
  }

  // O Provider troca o ouvinte quando a prop `onEvent` muda, sem recriar o runtime.
  setEventListener(listener: ((event: PitacoListenerEvent) => void) | undefined): void {
    this.onEvent = listener;
  }

  // Troca o portão de exibição (ponto de extensão do controle de onde e quando exibir). Uma
  // candidata que o portão padrão estivesse retendo (bloqueio ou adiamento) é descartada sem
  // exibição: o portão novo passa a decidir sozinho a partir da próxima candidata, e não faria
  // sentido um temporizador do portão antigo se lembrar dela.
  setPlacementGate(gate: PlacementGate): void {
    this.gate.dispose?.();
    if (this.held !== null) {
      this.logger.debug('portão de exibição trocado com uma candidata retida; descartada sem exibição.');
      this.held.controls.discard('placement_gate_replaced');
      this.clearHoldTimer();
      this.held = null;
    }
    this.gate = gate;
  }

  // Bloqueia a exibição enquanto o motivo estiver ativo (telas em que nada pode aparecer, como
  // pagamento). Motivos acumulam por contagem de referência: dois `block()` (ou dois
  // `<PitacoBlock />`) com o mesmo motivo exigem dois `unblock()` para desbloquear de fato. Uma
  // pesquisa já aberta (`presented`) não é interrompida; uma que chegar enquanto bloqueado fica
  // retida (ver `considerPlacement`).
  block(reason?: string): void {
    const key = sanitizeBlockReason(reason, this.logger);
    const wasBlocked = this.isBlocked();
    this.blockReasons.set(key, (this.blockReasons.get(key) ?? 0) + 1);
    if (!wasBlocked) {
      this.emitPlacement('placement_blocked', null, { reasons: Array.from(this.blockReasons.keys()) });
    }
  }

  // Desfaz um `block(reason)`. Motivo nunca bloqueado (ou já totalmente desbloqueado) é um erro de
  // uso do app, não do SDK: avisa em desenvolvimento e não faz nada, sem lançar.
  unblock(reason?: string): void {
    const key = sanitizeBlockReason(reason, this.logger);
    const count = this.blockReasons.get(key);
    if (count === undefined) {
      this.logger.warnOnce(
        `unblock-sem-block:${key}`,
        `unblock(${JSON.stringify(key)}) chamado sem um block(${JSON.stringify(key)}) correspondente. Ignorado.`,
      );
      return;
    }
    if (count <= 1) this.blockReasons.delete(key);
    else this.blockReasons.set(key, count - 1);
    if (!this.isBlocked()) this.tryReleaseHeld();
  }

  // Represa a próxima pesquisa disponível (ou a que já estiver retida) sem exibir. Chamar de novo
  // sem `release()` não faz nada — não empilha.
  defer(): void {
    this.deferRequested = true;
  }

  // Libera uma pesquisa represada por `defer()`. Com bloqueio ainda ativo, continua retida (dentro
  // do prazo) até o último `unblock()`. `release()` sem `defer()` correspondente é erro de uso do
  // app: avisa em desenvolvimento e não faz nada.
  release(): void {
    if (!this.deferRequested) {
      this.logger.warnOnce('release-sem-defer', 'release() chamado sem defer() correspondente. Ignorado.');
      return;
    }
    this.deferRequested = false;
    if (!this.isBlocked()) this.tryReleaseHeld();
  }

  // Zera só o limite de uma pesquisa por sessão de app (feature 6). Não é API pública documentada:
  // existe para o painel de depuração do exemplo simular "app reaberto" sem recriar o runtime
  // inteiro (perdendo identidade e fila). Bloqueios e adiamentos pendentes não são afetados — são
  // escopo de tela, não de sessão de app.
  simulateAppReopen(): void {
    this.sessionSurveyShown = false;
  }

  async track(eventName: string, attributes?: Attributes): Promise<void> {
    try {
      const event = sanitizeEventName(eventName, this.logger);
      if (event === null) return;
      const extra = attributes === undefined ? {} : sanitizeAttributes(attributes, this.logger, 'track');
      if (this.keyRejected) {
        this.logger.debug(`track("${event}") ignorado: a chave foi recusada pelo servidor.`);
        return;
      }
      // Uma pesquisa por sessão de app (feature 6), mesmo depois de dispensada: nem consulta o
      // servidor enquanto o limite estiver em vigor — economiza a chamada, como o prompt pede.
      if (this.sessionSurveyShown) {
        this.logger.debug(`track("${event}") ignorado: já houve uma pesquisa exibida nesta sessão de app.`);
        this.emitPlacement('placement_session_limited', null, {}, event);
        return;
      }

      await this.boot();
      const deviceId = await this.identity.get();
      const merged = { ...this.attributes, ...extra };
      const outcome = await this.api.eligibility({
        event,
        respondent: this.respondentPayload(deviceId),
        ...(Object.keys(merged).length > 0 ? { attributes: merged } : {}),
      });

      const survey = this.readEligibility(outcome, event);
      if (survey !== null) this.consider(survey, event, deviceId);
    } catch (error) {
      this.reporter.report('unknown', `track: ${describeError(error)}`, { stage: 'track' });
    }
  }

  setRespondent(respondent: Respondent | null | undefined): void {
    this.reference = sanitizeReference(respondent?.reference, this.logger);
  }

  setAttributes(attributes: Attributes | null | undefined): void {
    this.attributes = sanitizeAttributes(attributes, this.logger, 'setAttributes');
  }

  // Logout: dispensa a pesquisa aberta, esquece respondente e atributos e troca o `deviceId`. A
  // fila continua: o que o usuário anterior respondeu ainda precisa chegar.
  //
  // Uma candidata retida (bloqueio ou adiamento) é descartada sem exibição: ela foi consultada
  // para o respondente que está saindo, e oferecê-la depois do `reset()` misturaria uma decisão de
  // elegibilidade de uma identidade com a exibição de outra. Bloqueios ativos e o pedido de
  // `defer()` em si continuam — são escopo de tela (quem chamou `block()`/`defer()` normalmente
  // segue montado depois do logout), não do respondente. O limite de uma pesquisa por sessão de
  // app também continua: é sessão de uso do app, não de login.
  async reset(): Promise<void> {
    try {
      this.session?.dispatch({ type: 'dismiss', via: 'programmatic' });
      if (this.held !== null) {
        this.logger.debug('reset(): candidata retida descartada sem exibição.');
        this.held.controls.discard('reset');
        this.clearHoldTimer();
        this.held = null;
      }
      this.reference = undefined;
      this.attributes = {};
      await this.identity.rotate();
    } catch (error) {
      this.reporter.report('unknown', `reset: ${describeError(error)}`, { stage: 'reset' });
    }
  }

  reportRenderError(error: unknown, context: Readonly<Record<string, string>> = {}): void {
    this.reporter.report('render_error', `render: ${describeError(error)}`, { stage: 'render', ...context });
  }

  diagnostics(): RuntimeDiagnostics {
    return {
      deviceId: this.identity.current,
      queue: this.queue.snapshot(),
      keyRejected: this.keyRejected,
      rateLimitedUntil: this.http.rateLimitedUntil,
      sessionSurveyShown: this.sessionSurveyShown,
      blockedReasons: Array.from(this.blockReasons.keys()),
      deferred: this.deferRequested,
      held: this.held !== null,
    };
  }

  // Espera a fila esvaziar o que der (testes e painel de depuração).
  async settle(): Promise<void> {
    await this.pipeline;
    await this.queue.settle();
  }

  // SurveyController ---------------------------------------------------------------------------

  readonly getSnapshot = (): SurveySnapshot | null => this.session?.getSnapshot() ?? null;

  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  readonly present = (presentation?: Presentation): void => {
    this.session?.dispatch({ type: 'present', presentation: presentation ?? this.config.presentation });
  };

  readonly dispatch = (action: SurveyUserAction): void => {
    this.session?.dispatch(action);
  };

  // Internos ------------------------------------------------------------------------------------

  private boot(): Promise<void> {
    if (this.booting === null) {
      this.booting = Promise.all([this.identity.get(), this.queue.load()])
        .then(() => undefined)
        .catch((error: unknown) => {
          this.reporter.report('storage_error', `boot: ${describeError(error)}`, { stage: 'boot' });
        });
    }
    return this.booting;
  }

  private respondentPayload(deviceId: string) {
    return this.reference === undefined ? { deviceId } : { reference: this.reference, deviceId };
  }

  private readEligibility(outcome: HttpOutcome, event: string): Survey | null {
    switch (outcome.kind) {
      case 'success': {
        const parsed = parseEligibilityResponse(outcome.body);
        if (parsed.kind === 'none') {
          this.logger.debug(`sem pesquisa para "${event}".`);
          return null;
        }
        if (parsed.kind === 'malformed') {
          this.warnMalformed();
          this.reporter.report('malformed_response', `eligibility: ${parsed.reason}`, {
            route: 'eligibility',
            reason: parsed.reason,
          });
          return null;
        }
        if (parsed.survey.malformedQuestionCount > 0) {
          this.reporter.report('malformed_response', 'eligibility: malformed_question', {
            route: 'eligibility',
            count: parsed.survey.malformedQuestionCount,
          });
        }
        return parsed.survey;
      }
      case 'malformed':
        this.warnMalformed();
        this.reporter.report('malformed_response', 'eligibility: body_not_json', { route: 'eligibility' });
        return null;
      case 'unauthorized':
        this.onUnauthorized(outcome);
        return null;
      case 'rate_limited':
      case 'blocked':
        this.logger.debug('elegibilidade adiada: limite de requisições (429) em vigor.');
        return null;
      case 'retry':
        if (outcome.reason === 'timeout') {
          this.logger.debug(
            `elegibilidade abandonada depois de ${this.config.eligibilityTimeoutMs} ms; nada é exibido.`,
          );
        } else {
          this.logger.warnOnce(
            'unreachable',
            `Não foi possível falar com ${this.config.baseUrl} (${outcome.reason === 'server' ? `status ${outcome.status ?? 'desconhecido'}` : 'rede'}). Confira o baseUrl: ele aponta para o prefixo que serve /collect (no simulador iOS use localhost, no emulador Android 10.0.2.2, em aparelho físico o IP da máquina). Nenhuma pesquisa aparece enquanto isso.`,
          );
        }
        return null;
      case 'rejected':
        this.logger.warn(
          `A elegibilidade foi recusada (${outcome.status}${outcome.code ? `, ${outcome.code}` : ''}). Confira o nome do evento e os atributos enviados ao track.`,
        );
        this.reporter.report('unknown', 'eligibility: rejected', {
          route: 'eligibility',
          status: outcome.status,
          code: outcome.code ?? 'none',
        });
        return null;
    }
  }

  private warnMalformed() {
    this.logger.warn(
      'A elegibilidade respondeu num formato que o SDK não entende. Se o baseUrl aponta para um gateway, ele precisa repassar o corpo JSON do Pitaco sem alterar. Nada foi exibido.',
    );
  }

  private onUnauthorized(outcome: HttpOutcome) {
    this.keyRejected = true;
    const detail =
      outcome.kind === 'unauthorized' ? `${outcome.status}${outcome.code ? `, ${outcome.code}` : ''}` : '401';
    this.logger.warnOnce(
      'unauthorized',
      `A chave foi recusada pelo servidor (${detail}). Confira o apiKey do PitacoProvider: ele precisa ser a chave ativa da aplicação, exatamente como emitida no painel. Nenhuma pesquisa aparece até isso ser corrigido.`,
    );
  }

  private onQueueRejected(item: QueueItem, outcome: HttpOutcome) {
    if (outcome.kind !== 'rejected') return;
    this.reporter.report('unknown', `delivery: ${item.kind} rejected`, {
      route: item.kind,
      status: outcome.status,
      code: outcome.code ?? 'none',
    });
  }

  private consider(survey: Survey, triggerEvent: string, deviceId: string) {
    if (this.disposed) return;
    const status = this.session?.getSnapshot().status;
    if (status === 'ready' || status === 'presented') {
      this.logger.debug('já há uma pesquisa em andamento; a nova foi ignorada.');
      return;
    }
    if (this.held !== null) {
      this.logger.debug('já há uma candidata retida pelo controle de exibição; a nova foi ignorada.');
      return;
    }

    const candidate: SurveyCandidate = { survey, triggerEvent };
    if (findNextApplicable(survey.questions, {}, -1).index === -1) {
      this.suppress(candidate, deviceId);
      return;
    }

    let settled = false;
    try {
      this.gate.consider(candidate, {
        offer: () => {
          if (settled) return;
          settled = true;
          this.offer(candidate);
        },
        discard: (reason) => {
          settled = true;
          this.logger.debug(`pesquisa ${survey.surveyId} descartada antes de exibir: ${reason}.`);
        },
        notify: (type, data) => this.emitPlacement(type, candidate, data),
      });
    } catch (error) {
      this.reporter.report('unknown', `placement: ${describeError(error)}`, { stage: 'placement' });
    }
  }

  // Sem pergunta renderizável: nada é exibido, nenhuma exibição é aberta e o servidor fica
  // sabendo, para a tela de saúde da pesquisa.
  private suppress(candidate: SurveyCandidate, deviceId: string) {
    const { survey } = candidate;
    const questionTypes = survey.unknownQuestionTypes.slice(0, 20);
    const body: SuppressionRequest = {
      surveyId: survey.surveyId,
      versionId: survey.versionId,
      reason: 'unknown_question_type',
      questionTypes,
      features: [],
      deviceId,
      ...(this.reference === undefined ? {} : { respondentReference: this.reference }),
    };
    this.logger.debug(
      `pesquisa ${survey.surveyId} suprimida: nenhuma pergunta renderizável (${questionTypes.join(', ') || 'formato inválido'}).`,
    );
    this.emitPlacement('placement_suppressed', candidate, { reason: 'unknown_question_type', questionTypes });
    this.enqueue(async () => {
      await this.queue.enqueueSuppression(body);
      await this.queue.flush();
    });
  }

  private offer(candidate: SurveyCandidate) {
    const displayId = this.uuid();
    const session: SurveySession = new SurveySession({
      survey: candidate.survey,
      displayId,
      triggerEvent: candidate.triggerEvent,
      clock: this.clock,
      timers: this.timers,
      onTransition: (events, state) => this.onTransition(candidate, events, state),
      onError: (error, stage) =>
        this.reporter.report('unknown', `session ${stage}: ${describeError(error)}`, { stage }),
    });
    this.replaceSession(session);
    this.emitPlacement('placement_available', candidate, {});
  }

  private replaceSession(session: SurveySession | null) {
    this.unsubscribeSession?.();
    this.session?.dispose();
    this.session = session;
    this.unsubscribeSession = session === null ? null : session.subscribe(() => this.notify());
    this.notify();
  }

  private onTransition(candidate: SurveyCandidate, events: readonly InteractionEvent[], state: MachineState) {
    const presented = events.some((event) => event.type === 'survey_presented');
    const finished = state.status === 'completed' || state.status === 'dismissed';
    // A exibição abriu de fato: esgota o limite de uma pesquisa por sessão de app (feature 6),
    // mesmo que a pessoa dispense em seguida sem responder nada.
    if (presented) this.sessionSurveyShown = true;

    // A abertura leva o respondente e os atributos do instante em que a pesquisa apareceu.
    const reference = this.reference;
    const attributes = { ...this.attributes };

    if (presented || events.length > 0 || finished) {
      this.enqueue(async () => {
        if (presented) {
          const deviceId = await this.identity.get();
          const body: OpenDisplayRequest = {
            displayId: state.displayId,
            surveyId: state.survey.surveyId,
            versionId: state.survey.versionId,
            respondent: reference === undefined ? { deviceId } : { reference, deviceId },
            ...(Object.keys(attributes).length > 0 ? { attributes } : {}),
            sdkVersion: SDK_VERSION,
          };
          await this.queue.enqueueOpenDisplay(state.displayId, body);
        }
        await this.queue.enqueueEvents(state.displayId, events);
        if (finished) {
          const submission = buildSubmission(state);
          if (submission !== null) await this.queue.enqueueSubmission(state.displayId, submission);
        }
        if (presented || finished) {
          this.clearFlushTimer();
          await this.queue.flush();
        } else {
          this.scheduleFlush();
        }
      });
    }

    for (const event of events) this.deliver(event);

    if (finished || state.status === 'discarded') {
      try {
        this.gate.sessionFinished?.(candidate);
      } catch (error) {
        this.reporter.report('unknown', `placement: ${describeError(error)}`, { stage: 'placement' });
      }
    }
  }

  private enqueue(work: () => Promise<void>) {
    this.pipeline = this.pipeline.then(work).catch((error: unknown) => {
      this.reporter.report('unknown', `queue: ${describeError(error)}`, { stage: 'queue' });
    });
  }

  private scheduleFlush() {
    if (this.flushTimer !== null) return;
    this.flushTimer = this.timers.set(() => {
      this.flushTimer = null;
      void this.queue.flush();
    }, EVENT_FLUSH_DELAY_MS);
  }

  private clearFlushTimer() {
    if (this.flushTimer !== null) {
      this.timers.clear(this.flushTimer);
      this.flushTimer = null;
    }
  }

  private onAppState(state: string) {
    if (state === 'background') {
      this.session?.dispatch({ type: 'background' });
      this.clearFlushTimer();
      this.enqueue(() => this.queue.flush());
    } else if (state === 'active') {
      this.session?.dispatch({ type: 'foreground' });
      this.enqueue(() => this.queue.flush({ force: true }));
    }
  }

  // `candidate` é `null` para os dois tipos sem pesquisa para identificar (`placement_blocked` e
  // `placement_session_limited`, ver `catalog/placement.ts`). `triggerEvent` só é usado quando não
  // há candidata: com candidata, o dela vale sempre.
  private emitPlacement<T extends PlacementEventType>(
    type: T,
    candidate: SurveyCandidate | null,
    data: PlacementEventDataMap[T],
    triggerEvent?: string,
  ) {
    this.deliver({
      type,
      occurredAt: toIsoString(this.clock.now().wall),
      surveyId: candidate?.survey.surveyId ?? null,
      versionId: candidate?.survey.versionId ?? null,
      triggerEvent: candidate?.triggerEvent ?? triggerEvent ?? null,
      data,
    } as unknown as PlacementEvent);
  }

  // Portão padrão de exibição (feature 4): bloqueio e adiamento sobre a mesma interface de
  // extensão que `setPlacementGate` permite substituir por inteiro. -----------------------------

  private isBlocked(): boolean {
    return this.blockReasons.size > 0;
  }

  private considerPlacement(candidate: SurveyCandidate, controls: PlacementControls): void {
    if (this.isBlocked()) {
      this.holdCandidate(candidate, controls, 'block');
      return;
    }
    if (this.deferRequested) {
      this.holdCandidate(candidate, controls, 'defer');
      return;
    }
    controls.offer();
  }

  private holdCandidate(candidate: SurveyCandidate, controls: PlacementControls, cause: 'block' | 'defer'): void {
    this.held = { candidate, controls, heldAtMono: this.clock.now().mono, cause };
    if (cause === 'block') controls.notify('placement_survey_held', {});
    else controls.notify('placement_deferred', {});
    this.scheduleHoldTimeout();
  }

  private scheduleHoldTimeout(): void {
    this.clearHoldTimer();
    this.holdTimer = this.timers.set(() => {
      this.holdTimer = null;
      this.expireHeld();
    }, this.config.deferTimeoutMs);
  }

  private clearHoldTimer(): void {
    if (this.holdTimer !== null) {
      this.timers.clear(this.holdTimer);
      this.holdTimer = null;
    }
  }

  // O prazo (`deferTimeoutMs`) venceu sem `unblock()`/`release()` suficiente: descarta sem abrir
  // exibição. Não consome o limite de sessão (feature 6) — este método nunca chama `offer()`.
  private expireHeld(): void {
    const held = this.held;
    if (held === null) return;
    this.held = null;
    const heldMs = Math.max(0, Math.round(this.clock.now().mono - held.heldAtMono));
    if (held.cause === 'block') held.controls.notify('placement_survey_discarded', { heldMs });
    else held.controls.notify('placement_expired', { heldMs });
    held.controls.discard(held.cause === 'block' ? 'block_hold_timeout' : 'defer_timeout');
  }

  // `unblock()` ou `release()` tiraram a última condição que segurava a candidata: oferece agora,
  // se ainda dentro do prazo (senão `expireHeld` já teria descartado). Continua retida se a outra
  // condição (bloqueio ou adiamento) ainda estiver ativa — é a interação decidida entre as duas
  // (por exemplo `release()` com bloqueio ainda ativo: continua retida até o `unblock()`).
  //
  // `placement_released` só é emitido quando a causa que reteve foi o adiamento: para o bloqueio,
  // o catálogo só promete `placement_blocked`/`placement_survey_held`/`placement_survey_discarded`
  // (ver `catalog/placement.ts`), e o sucesso já fica visível no `placement_available` de sempre,
  // emitido por `offer()` logo abaixo. A causa é a que reteve a candidata primeiro; se as duas
  // condições chegarem a se sobrepor, é ela quem decide qual evento de sucesso sai.
  private tryReleaseHeld(): void {
    const held = this.held;
    if (held === null || this.isBlocked() || this.deferRequested) return;
    this.held = null;
    this.clearHoldTimer();
    if (held.cause === 'defer') {
      const heldMs = Math.max(0, Math.round(this.clock.now().mono - held.heldAtMono));
      held.controls.notify('placement_released', { heldMs });
    }
    held.controls.offer();
  }

  private deliver(event: PitacoListenerEvent) {
    if (this.onEvent === undefined || this.disposed) return;
    try {
      this.onEvent(event);
    } catch {
      // O ouvinte é do app: o erro não é do SDK e não vai para o relatório.
      this.logger.warnOnce(
        'on-event',
        'O onEvent do PitacoProvider lançou um erro. O SDK ignorou e seguiu; trate o erro dentro do seu callback.',
      );
    }
  }

  private notify() {
    for (const listener of Array.from(this.listeners)) {
      try {
        listener();
      } catch {
        // Ouvinte quebrado não interrompe os outros.
      }
    }
  }
}
