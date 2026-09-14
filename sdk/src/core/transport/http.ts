// Cliente HTTP do SDK. Toda requisição leva `X-Pitaco-Key` e `X-Pitaco-Sdk-Version`, tem timeout
// e termina num resultado classificado. Nada lança: rede fora, timeout, corpo ilegível e status
// de erro viram valores que o chamador trata.

import type { Clock } from '../clock';
import type { TimerHandle, Timers } from '../timers';

export interface ResponseLike {
  readonly status: number;
  readonly headers?: { get(name: string): string | null } | null;
  text(): Promise<string>;
}

export interface RequestInitLike {
  readonly method: 'POST';
  readonly headers: Record<string, string>;
  readonly body: string;
  readonly signal?: unknown;
}

export type FetchLike = (url: string, init: RequestInitLike) => Promise<ResponseLike>;

export type HttpOutcome =
  | { readonly kind: 'success'; readonly status: number; readonly body: unknown }
  // 2xx cujo corpo deveria ser JSON e não é.
  | { readonly kind: 'malformed'; readonly status: number }
  | { readonly kind: 'retry'; readonly reason: 'network' | 'timeout' | 'server'; readonly status?: number }
  | { readonly kind: 'rate_limited'; readonly retryAfterMs: number }
  | { readonly kind: 'unauthorized'; readonly status: number; readonly code: string | null }
  | { readonly kind: 'rejected'; readonly status: number; readonly code: string | null; readonly problem: unknown }
  // Não saiu: ainda dentro do `Retry-After` de um 429 anterior.
  | { readonly kind: 'blocked'; readonly until: number };

export interface HttpClientOptions {
  readonly baseUrl: string;
  readonly apiKey: string;
  readonly sdkVersion: string;
  readonly fetch: FetchLike | undefined;
  readonly clock: Clock;
  readonly timers: Timers;
  readonly timeoutMs: number;
}

export interface RequestOptions {
  readonly timeoutMs?: number;
  readonly expectJson?: boolean;
}

const DEFAULT_RETRY_AFTER_MS = 30_000;
const MAX_RETRY_AFTER_MS = 60 * 60 * 1000;

interface AbortControllerLike {
  readonly signal: unknown;
  abort(): void;
}

function createAbortController(): AbortControllerLike | null {
  const Controller = (globalThis as { AbortController?: new () => AbortControllerLike }).AbortController;
  if (typeof Controller !== 'function') return null;
  try {
    return new Controller();
  } catch {
    return null;
  }
}

export function parseRetryAfter(value: string | null | undefined, nowWall: number): number {
  if (value === null || value === undefined || value.trim() === '') return DEFAULT_RETRY_AFTER_MS;
  const trimmed = value.trim();
  let ms: number;
  if (/^\d+$/.test(trimmed)) {
    ms = Number(trimmed) * 1000;
  } else {
    const date = Date.parse(trimmed);
    if (Number.isNaN(date)) return DEFAULT_RETRY_AFTER_MS;
    ms = date - nowWall;
  }
  return Math.min(MAX_RETRY_AFTER_MS, Math.max(1000, ms));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readHeader(response: ResponseLike, name: string): string | null {
  try {
    return response.headers?.get(name) ?? null;
  } catch {
    return null;
  }
}

export class HttpClient {
  private blockedUntil = 0;

  constructor(private readonly options: HttpClientOptions) {}

  get rateLimitedUntil(): number {
    return this.blockedUntil;
  }

  // Um 429 recebido antes de um reinício continua valendo (a fila persiste o prazo).
  restoreRateLimit(until: number): void {
    if (Number.isFinite(until) && until > this.blockedUntil) this.blockedUntil = until;
  }

  async post(path: string, body: unknown, request: RequestOptions = {}): Promise<HttpOutcome> {
    const { clock, timers } = this.options;
    const startedAt = clock.now().wall;
    if (startedAt < this.blockedUntil) return { kind: 'blocked', until: this.blockedUntil };

    const fetchFn = this.options.fetch;
    if (typeof fetchFn !== 'function') return { kind: 'retry', reason: 'network' };

    let payload: string;
    try {
      payload = JSON.stringify(body);
    } catch {
      return { kind: 'rejected', status: 0, code: 'sdk.serialization', problem: null };
    }

    const controller = createAbortController();
    const timeoutMs = request.timeoutMs ?? this.options.timeoutMs;
    const timer: { handle: TimerHandle | null } = { handle: null };
    const timeout = new Promise<'timeout'>((resolve) => {
      timer.handle = timers.set(() => resolve('timeout'), timeoutMs);
    });

    const work = (async (): Promise<HttpOutcome> => {
      const response = await fetchFn(`${this.options.baseUrl}${path}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          'X-Pitaco-Key': this.options.apiKey,
          'X-Pitaco-Sdk-Version': this.options.sdkVersion,
        },
        body: payload,
        ...(controller ? { signal: controller.signal } : {}),
      });
      return this.interpret(response, request.expectJson === true);
    })();
    // Se o timeout vencer, a promessa perdedora ainda pode rejeitar: nunca sem tratamento.
    work.catch(() => undefined);

    try {
      const winner = await Promise.race([work, timeout]);
      if (winner === 'timeout') {
        try {
          controller?.abort();
        } catch {
          // Abortar é só cortesia com a conexão.
        }
        return { kind: 'retry', reason: 'timeout' };
      }
      return winner;
    } catch {
      return { kind: 'retry', reason: 'network' };
    } finally {
      if (timer.handle !== null) timers.clear(timer.handle);
    }
  }

  private async interpret(response: ResponseLike, expectJson: boolean): Promise<HttpOutcome> {
    const status = typeof response.status === 'number' ? response.status : 0;

    if (status >= 200 && status < 300) {
      if (!expectJson) return { kind: 'success', status, body: null };
      let text: string;
      try {
        text = await response.text();
      } catch {
        return { kind: 'retry', reason: 'network' };
      }
      try {
        return { kind: 'success', status, body: JSON.parse(text) as unknown };
      } catch {
        return { kind: 'malformed', status };
      }
    }

    if (status === 429) {
      const now = this.options.clock.now().wall;
      const retryAfterMs = parseRetryAfter(readHeader(response, 'Retry-After'), now);
      this.blockedUntil = Math.max(this.blockedUntil, now + retryAfterMs);
      return { kind: 'rate_limited', retryAfterMs };
    }

    if (status >= 500 || status === 408 || status === 0) {
      return { kind: 'retry', reason: 'server', status };
    }

    const problem = await readProblem(response);
    const code = isRecord(problem) && typeof problem.code === 'string' ? problem.code : null;
    if (status === 401 || status === 403) return { kind: 'unauthorized', status, code };
    return { kind: 'rejected', status, code, problem };
  }
}

async function readProblem(response: ResponseLike): Promise<unknown> {
  try {
    const text = await response.text();
    return text.trim() === '' ? null : (JSON.parse(text) as unknown);
  } catch {
    return null;
  }
}
