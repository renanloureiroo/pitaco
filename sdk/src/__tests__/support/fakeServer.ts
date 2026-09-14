// Servidor falso das rotas `/collect`, com a semântica de idempotência do contrato: abertura
// repetida devolve 200, envio repetido devolve 204, eventos repetidos voltam como `duplicated`.

import type { InteractionEvent } from '../../catalog/events';
import type { FetchLike, RequestInitLike, ResponseLike } from '../../core/transport/http';

export type Route = 'eligibility' | 'displays' | 'submission' | 'events' | 'suppressions' | 'sdk-errors';

export interface RecordedRequest {
  readonly route: Route;
  readonly url: string;
  readonly headers: Readonly<Record<string, string>>;
  readonly body: unknown;
  readonly displayId: string | null;
}

export interface FakeReply {
  readonly status: number;
  readonly body?: unknown;
  readonly raw?: string;
  readonly headers?: Readonly<Record<string, string>>;
}

// `down`: rede fora (fetch rejeita). `hang`: nunca responde (só o abort ou o timeout encerram).
// `lost`: o servidor processa, mas a resposta se perde no caminho.
export type Behavior = FakeReply | 'down' | 'hang' | 'lost';

export class FakePitacoServer {
  readonly requests: RecordedRequest[] = [];
  readonly displays = new Map<string, { surveyId: string; versionId: string; body: unknown }>();
  readonly submissions = new Map<string, unknown>();
  readonly events = new Map<string, InteractionEvent>();
  readonly suppressions: unknown[] = [];
  readonly errors: unknown[] = [];

  apiKey = 'pk_test';
  survey: unknown = null;
  // Comportamento geral e por rota; a fila de uma rota é consumida uma resposta por requisição.
  behavior: 'up' | 'down' | 'hang' = 'up';
  private readonly scripted = new Map<Route, Behavior[]>();

  script(route: Route, ...behaviors: Behavior[]): void {
    this.scripted.set(route, [...(this.scripted.get(route) ?? []), ...behaviors]);
  }

  count(route: Route): number {
    return this.requests.filter((request) => request.route === route).length;
  }

  readonly fetch: FetchLike = async (url, init) => {
    const { route, displayId } = parseRoute(url);
    const body = JSON.parse(init.body) as unknown;
    this.requests.push({ route, url, headers: init.headers, body, displayId });

    const scripted = this.scripted.get(route)?.shift();
    const behavior: Behavior | 'up' = scripted ?? this.behavior;

    if (behavior === 'down') throw new TypeError('Network request failed');
    if (behavior === 'hang') return hang(init);
    if (behavior !== 'up' && behavior !== 'lost') return respond(behavior);

    const reply = this.handle(route, displayId, init, body);
    if (behavior === 'lost') throw new TypeError('Network request failed');
    return respond(reply);
  };

  private handle(route: Route, displayId: string | null, init: RequestInitLike, body: unknown): FakeReply {
    if (init.headers['X-Pitaco-Key'] !== this.apiKey) {
      return problem(401, 'api_key.invalid');
    }
    const payload = body as Record<string, unknown>;
    switch (route) {
      case 'eligibility':
        return { status: 200, body: { survey: this.survey } };
      case 'displays': {
        const id = String(payload.displayId);
        const existing = this.displays.get(id);
        if (existing) {
          return existing.versionId === payload.versionId
            ? { status: 200, body: { displayId: id, outcome: 'STARTED' } }
            : problem(409, 'display.identifier_conflict');
        }
        this.displays.set(id, {
          surveyId: String(payload.surveyId),
          versionId: String(payload.versionId),
          body,
        });
        return { status: 201, body: { displayId: id, outcome: 'STARTED' } };
      }
      case 'submission': {
        if (displayId === null || !this.displays.has(displayId)) return problem(404, 'display.not_found');
        const previous = this.submissions.get(displayId);
        if (previous !== undefined) {
          return JSON.stringify(previous) === JSON.stringify(body)
            ? { status: 204 }
            : problem(409, 'display.already_closed');
        }
        this.submissions.set(displayId, body);
        return { status: 204 };
      }
      case 'events': {
        const events = (payload.events as InteractionEvent[]) ?? [];
        if (displayId === null || !this.displays.has(displayId)) {
          return { status: 202, body: receipt(0, 0, events.length) };
        }
        let accepted = 0;
        let duplicated = 0;
        for (const event of events) {
          const key = `${displayId}:${event.seq}`;
          if (this.events.has(key)) {
            duplicated += 1;
          } else {
            this.events.set(key, event);
            accepted += 1;
          }
        }
        return { status: 202, body: receipt(accepted, duplicated, 0) };
      }
      case 'suppressions':
        this.suppressions.push(body);
        return { status: 202 };
      case 'sdk-errors':
        this.errors.push(body);
        return { status: 202 };
    }
  }

  // Eventos gravados de uma exibição, na ordem de `seq`.
  eventsOf(displayId: string): InteractionEvent[] {
    return Array.from(this.events.entries())
      .filter(([key]) => key.startsWith(`${displayId}:`))
      .map(([, event]) => event)
      .sort((left, right) => left.seq - right.seq);
  }
}

function parseRoute(url: string): { route: Route; displayId: string | null } {
  const path = url.replace(/^https?:\/\/[^/]+/, '');
  const display = /\/collect\/displays\/([^/]+)\/(submission|events)$/.exec(path);
  if (display) return { route: display[2] as Route, displayId: decodeURIComponent(display[1] ?? '') };
  if (path.endsWith('/collect/eligibility')) return { route: 'eligibility', displayId: null };
  if (path.endsWith('/collect/displays')) return { route: 'displays', displayId: null };
  if (path.endsWith('/collect/suppressions')) return { route: 'suppressions', displayId: null };
  if (path.endsWith('/collect/sdk-errors')) return { route: 'sdk-errors', displayId: null };
  throw new Error(`rota inesperada: ${url}`);
}

function problem(status: number, code: string): FakeReply {
  return { status, body: { type: 'about:blank', title: 'Erro', status, detail: 'x', instance: '/', code } };
}

function receipt(accepted: number, duplicated: number, unavailable: number) {
  return {
    accepted,
    duplicated,
    discarded: {
      displayUnavailable: unavailable,
      outsideWindow: 0,
      unknownType: 0,
      invalidEnvelope: 0,
      unknownQuestion: 0,
      overLimit: 0,
    },
  };
}

export function respond(reply: FakeReply): ResponseLike {
  const headers = Object.fromEntries(
    Object.entries(reply.headers ?? {}).map(([name, value]) => [name.toLowerCase(), value]),
  );
  const raw = reply.raw ?? (reply.body === undefined ? '' : JSON.stringify(reply.body));
  return {
    status: reply.status,
    headers: { get: (name: string) => headers[name.toLowerCase()] ?? null },
    text: () => Promise.resolve(raw),
  };
}

function hang(init: RequestInitLike): Promise<ResponseLike> {
  return new Promise((_resolve, reject) => {
    const signal = init.signal as { addEventListener?: (type: string, listener: () => void) => void } | undefined;
    signal?.addEventListener?.('abort', () => reject(new Error('aborted')));
  });
}
