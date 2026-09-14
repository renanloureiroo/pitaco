// Fila local persistente. Abertura de exibição, eventos de interação, envio de respostas e
// supressões moram no mesmo armazenamento e seguem a mesma regra (contrato, "Fila local e
// idempotência"):
//
// - grava antes de tentar enviar;
// - envia em ordem, a abertura de uma exibição antes de tudo o que depende dela;
// - 2xx remove; rede, timeout e 5xx mantêm e tentam de novo com backoff exponencial, e sempre que
//   o app abre (flush forçado); 429 pausa a fila inteira até o `Retry-After`; 400, 401, 404, 409 e
//   422 descartam, porque tentar de novo não muda o resultado;
// - nunca duplica: a abertura e o envio são idempotentes pelo `displayId`, os eventos pelo par
//   (`displayId`, `seq`), e a própria fila não aceita duas vezes o mesmo item;
// - tem teto de tamanho e de idade, e descarta o que passa (a idade máxima cabe na janela de
//   aceitação de eventos do servidor, 7 dias).

import type { OpenDisplayRequest, SubmissionRequest, SuppressionRequest } from '../../api/types';
import type { InteractionEvent } from '../../catalog/events';
import type { Clock } from '../clock';
import type { Logger } from '../logger';
import type { SafeStorage } from '../storage/safe';
import type { TimerHandle, Timers } from '../timers';
import type { CollectApi } from '../transport/api';
import type { HttpClient, HttpOutcome } from '../transport/http';

export interface QueueLimits {
  readonly maxItems: number;
  readonly maxAgeMs: number;
  readonly maxEventsPerDisplay: number;
  readonly batchSize: number;
  readonly backoffBaseMs: number;
  readonly backoffMaxMs: number;
}

export const DEFAULT_QUEUE_LIMITS: QueueLimits = {
  maxItems: 200,
  maxAgeMs: 3 * 24 * 60 * 60 * 1000,
  maxEventsPerDisplay: 500,
  batchSize: 100,
  backoffBaseMs: 2000,
  backoffMaxMs: 5 * 60 * 1000,
};

interface ItemBase {
  readonly id: string;
  readonly createdAt: number;
  attempts: number;
  nextAttemptAt: number;
}

export type QueueItem =
  | (ItemBase & { readonly kind: 'open_display'; readonly displayId: string; readonly body: OpenDisplayRequest })
  | (ItemBase & { readonly kind: 'submission'; readonly displayId: string; readonly body: SubmissionRequest })
  | (ItemBase & { readonly kind: 'events'; readonly displayId: string; events: InteractionEvent[] })
  | (ItemBase & { readonly kind: 'suppression'; readonly body: SuppressionRequest });

export type QueueItemKind = QueueItem['kind'];

interface PersistedQueue {
  readonly version: 1;
  readonly items: readonly QueueItem[];
  readonly blockedUntil: number;
}

export interface DeliveryQueueOptions {
  readonly storage: SafeStorage;
  readonly storageKey: string;
  readonly api: CollectApi;
  readonly http: HttpClient;
  readonly clock: Clock;
  readonly timers: Timers;
  readonly uuid: () => string;
  readonly logger: Logger;
  readonly limits?: Partial<QueueLimits>;
  // Descarte por recusa do servidor (400, 404, 409, 422): defeito do SDK, vira relatório de erro.
  readonly onRejected?: (item: QueueItem, outcome: HttpOutcome) => void;
  readonly onUnauthorized?: (outcome: HttpOutcome) => void;
}

const KINDS: ReadonlySet<string> = new Set(['open_display', 'submission', 'events', 'suppression']);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isPersistedItem(value: unknown): value is QueueItem {
  if (!isRecord(value)) return false;
  if (typeof value.id !== 'string' || typeof value.kind !== 'string' || !KINDS.has(value.kind)) return false;
  if (typeof value.createdAt !== 'number' || typeof value.attempts !== 'number') return false;
  if (typeof value.nextAttemptAt !== 'number') return false;
  if (value.kind === 'events') return typeof value.displayId === 'string' && Array.isArray(value.events);
  if (value.kind === 'suppression') return isRecord(value.body);
  return typeof value.displayId === 'string' && isRecord(value.body);
}

function dependsOnDisplay(item: QueueItem): item is Extract<QueueItem, { kind: 'submission' | 'events' }> {
  return item.kind === 'submission' || item.kind === 'events';
}

export class DeliveryQueue {
  private readonly limits: QueueLimits;
  private items: QueueItem[] = [];
  private blockedUntil = 0;
  private loading: Promise<void> | null = null;
  private flushing: Promise<void> | null = null;
  private pendingFlush: { force: boolean } | null = null;
  private wakeTimer: TimerHandle | null = null;
  private active = true;

  constructor(private readonly options: DeliveryQueueOptions) {
    this.limits = { ...DEFAULT_QUEUE_LIMITS, ...options.limits };
  }

  load(): Promise<void> {
    if (this.loading === null) this.loading = this.restore();
    return this.loading;
  }

  // Cópia do conteúdo, para o painel de depuração e os testes.
  snapshot(): readonly QueueItem[] {
    return this.items.map((item) =>
      item.kind === 'events' ? { ...item, events: [...item.events] } : { ...item },
    );
  }

  async enqueueOpenDisplay(displayId: string, body: OpenDisplayRequest): Promise<void> {
    await this.load();
    if (this.items.some((item) => item.kind === 'open_display' && item.displayId === displayId)) return;
    this.items.push({ ...this.base(), kind: 'open_display', displayId, body });
    await this.persist();
  }

  async enqueueEvents(displayId: string, events: readonly InteractionEvent[]): Promise<void> {
    if (events.length === 0) return;
    await this.load();
    let item = this.items.find(
      (candidate): candidate is Extract<QueueItem, { kind: 'events' }> =>
        candidate.kind === 'events' && candidate.displayId === displayId,
    );
    if (!item) {
      item = { ...this.base(), kind: 'events', displayId, events: [] };
      this.items.push(item);
    }
    const seen = new Set(item.events.map((event) => event.seq));
    for (const event of events) {
      if (seen.has(event.seq) || item.events.length >= this.limits.maxEventsPerDisplay) continue;
      seen.add(event.seq);
      item.events.push(event);
    }
    await this.persist();
  }

  async enqueueSubmission(displayId: string, body: SubmissionRequest): Promise<void> {
    await this.load();
    if (this.items.some((item) => item.kind === 'submission' && item.displayId === displayId)) return;
    this.items.push({ ...this.base(), kind: 'submission', displayId, body });
    await this.persist();
  }

  async enqueueSuppression(body: SuppressionRequest): Promise<void> {
    await this.load();
    this.items.push({ ...this.base(), kind: 'suppression', body });
    await this.persist();
  }

  // `force` ignora o backoff (abertura do app, volta do segundo plano), mas nunca o `Retry-After`.
  flush(options: { readonly force?: boolean } = {}): Promise<void> {
    if (!this.active) return Promise.resolve();
    const force = options.force === true;
    if (this.flushing !== null) {
      this.pendingFlush = { force: force || (this.pendingFlush?.force ?? false) };
      return this.flushing;
    }
    const run = this.drain(force)
      .catch((error: unknown) => {
        this.options.logger.debug(`fila interrompida: ${error instanceof Error ? error.name : 'erro'}`);
      })
      .finally(() => {
        this.flushing = null;
        const again = this.pendingFlush;
        this.pendingFlush = null;
        if (again !== null) void this.flush(again);
      });
    this.flushing = run;
    return run;
  }

  // Espera até não haver envio em andamento (inclusive os reenfileirados durante o envio).
  async settle(): Promise<void> {
    while (this.flushing !== null) {
      await this.flushing;
    }
  }

  pause(): void {
    this.active = false;
    this.clearWake();
  }

  resume(): void {
    this.active = true;
  }

  private base(): ItemBase {
    return { id: this.options.uuid(), createdAt: this.options.clock.now().wall, attempts: 0, nextAttemptAt: 0 };
  }

  private async restore(): Promise<void> {
    const raw = await this.options.storage.readJson(this.options.storageKey);
    if (!isRecord(raw) || !Array.isArray(raw.items)) return;
    const restored = raw.items.filter(isPersistedItem);
    // O que já foi enfileirado nesta sessão antes do carregamento vem depois do que estava salvo.
    this.items = [...restored, ...this.items];
    if (typeof raw.blockedUntil === 'number' && Number.isFinite(raw.blockedUntil)) {
      this.blockedUntil = raw.blockedUntil;
      this.options.http.restoreRateLimit(raw.blockedUntil);
    }
  }

  private persist(): Promise<boolean> {
    const state: PersistedQueue = { version: 1, items: this.items, blockedUntil: this.blockedUntil };
    return this.options.storage.writeJson(this.options.storageKey, state);
  }

  private async drain(force: boolean): Promise<void> {
    await this.load();
    const attempted = new Set<string>();

    while (this.active) {
      const now = this.options.clock.now().wall;
      if (this.prune(now)) await this.persist();

      const blockedUntil = Math.max(this.blockedUntil, this.options.http.rateLimitedUntil);
      if (now < blockedUntil) {
        this.scheduleWake(blockedUntil);
        return;
      }

      const item = this.nextSendable(now, force, attempted);
      if (!item) {
        this.scheduleNextAttempt();
        return;
      }
      attempted.add(item.id);

      const { outcome, sentSeqs } = await this.send(item);
      const stop = this.apply(item, outcome, sentSeqs, attempted);
      await this.persist();
      if (stop) {
        this.scheduleNextAttempt();
        return;
      }
    }
  }

  private nextSendable(now: number, force: boolean, attempted: ReadonlySet<string>): QueueItem | undefined {
    return this.items.find((item) => {
      if (attempted.has(item.id)) return false;
      if (!force && item.nextAttemptAt > now) return false;
      if (dependsOnDisplay(item)) {
        const opening = this.items.some(
          (other) => other.kind === 'open_display' && other.displayId === item.displayId,
        );
        if (opening) return false;
      }
      return true;
    });
  }

  private async send(item: QueueItem): Promise<{ outcome: HttpOutcome; sentSeqs: readonly number[] }> {
    const { api } = this.options;
    switch (item.kind) {
      case 'open_display':
        return { outcome: await api.openDisplay(item.body), sentSeqs: [] };
      case 'submission':
        return { outcome: await api.submit(item.displayId, item.body), sentSeqs: [] };
      case 'suppression':
        return { outcome: await api.suppress(item.body), sentSeqs: [] };
      case 'events': {
        const batch = [...item.events].sort((left, right) => left.seq - right.seq).slice(0, this.limits.batchSize);
        return { outcome: await api.sendEvents(item.displayId, batch), sentSeqs: batch.map((event) => event.seq) };
      }
    }
  }

  // Devolve `true` quando a passada deve parar (rede fora, 5xx, 429).
  private apply(
    item: QueueItem,
    outcome: HttpOutcome,
    sentSeqs: readonly number[],
    attempted: Set<string>,
  ): boolean {
    const now = this.options.clock.now().wall;
    switch (outcome.kind) {
      case 'success':
      case 'malformed':
        if (item.kind === 'events') {
          const sent = new Set(sentSeqs);
          item.events = item.events.filter((event) => !sent.has(event.seq));
          if (item.events.length > 0) {
            // O resto do lote sai na mesma passada.
            attempted.delete(item.id);
            return false;
          }
        }
        this.remove(item);
        return false;
      case 'retry':
        item.attempts += 1;
        item.nextAttemptAt = now + this.backoff(item.attempts);
        return true;
      case 'rate_limited':
        this.blockedUntil = Math.max(this.blockedUntil, now + outcome.retryAfterMs);
        return true;
      case 'blocked':
        this.blockedUntil = Math.max(this.blockedUntil, outcome.until);
        return true;
      case 'unauthorized':
        this.discard(item);
        this.options.onUnauthorized?.(outcome);
        return false;
      case 'rejected':
        this.discard(item);
        this.options.onRejected?.(item, outcome);
        return false;
    }
  }

  private backoff(attempts: number): number {
    const exponent = Math.min(20, Math.max(0, attempts - 1));
    return Math.min(this.limits.backoffMaxMs, this.limits.backoffBaseMs * 2 ** exponent);
  }

  private remove(item: QueueItem) {
    this.items = this.items.filter((candidate) => candidate !== item);
  }

  // Abertura descartada leva junto o que dependia dela: a exibição nunca vai existir no servidor.
  private discard(item: QueueItem) {
    this.items = this.items.filter(
      (candidate) =>
        candidate !== item &&
        !(item.kind === 'open_display' && dependsOnDisplay(candidate) && candidate.displayId === item.displayId),
    );
  }

  private prune(now: number): boolean {
    const before = this.items.length;
    for (const item of [...this.items]) {
      if (now - item.createdAt > this.limits.maxAgeMs && this.items.includes(item)) {
        this.discard(item);
      }
    }
    while (this.items.length > this.limits.maxItems) {
      const oldest = this.items[0];
      if (!oldest) break;
      this.discard(oldest);
    }
    const removed = before - this.items.length;
    if (removed > 0) this.options.logger.debug(`fila: ${removed} item(ns) descartado(s) pelo teto de idade ou tamanho.`);
    return removed > 0;
  }

  private scheduleNextAttempt() {
    const now = this.options.clock.now().wall;
    const blockedUntil = Math.max(this.blockedUntil, this.options.http.rateLimitedUntil);
    const upcoming = this.items
      .map((item) => Math.max(item.nextAttemptAt, blockedUntil))
      .filter((at) => at > now);
    if (upcoming.length === 0) {
      this.clearWake();
      return;
    }
    this.scheduleWake(Math.min(...upcoming));
  }

  private scheduleWake(at: number) {
    this.clearWake();
    if (!this.active) return;
    const delay = Math.max(0, at - this.options.clock.now().wall);
    this.wakeTimer = this.options.timers.set(() => {
      this.wakeTimer = null;
      void this.flush();
    }, Math.min(delay, this.limits.backoffMaxMs * 12));
  }

  private clearWake() {
    if (this.wakeTimer !== null) {
      this.options.timers.clear(this.wakeTimer);
      this.wakeTimer = null;
    }
  }
}
