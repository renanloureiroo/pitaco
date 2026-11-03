// Requisições e respostas para o painel de depuração. O SDK não tem gancho de rede público (e não
// precisa: é ferramenta de diagnóstico). Ele lê o `fetch` global na hora de cada chamada, então
// basta o exemplo embrulhar `globalThis.fetch` uma vez, antes de montar o Provider. A chave
// `X-Pitaco-Key` nunca aparece inteira no log.
import { createLogStore, useLogEntries } from './logStore';
import { getNetworkConditions, simulatedNetworkError, waitUnlessAborted } from './networkConditions';

export interface NetworkEntry {
  readonly id: string;
  readonly at: string;
  readonly method: string;
  readonly url: string;
  readonly requestHeaders: Readonly<Record<string, string>>;
  readonly requestBody: string | null;
  readonly status: number | null;
  readonly durationMs: number | null;
  readonly responseBody: string | null;
  readonly error: string | null;
}

export const networkLog = createLogStore<NetworkEntry>(300);

const MAX_BODY = 4000;
const truncate = (text: string) => (text.length > MAX_BODY ? `${text.slice(0, MAX_BODY)}… (+${text.length - MAX_BODY})` : text);

function redactHeaders(headers: unknown): Record<string, string> {
  const result: Record<string, string> = {};
  if (headers === null || typeof headers !== 'object') return result;
  const pairs: [string, string][] =
    typeof Headers !== 'undefined' && headers instanceof Headers
      ? Array.from(headers.entries())
      : Array.isArray(headers)
        ? (headers as [string, string][])
        : Object.entries(headers as Record<string, string>);
  for (const [name, value] of pairs) {
    result[name] = name.toLowerCase() === 'x-pitaco-key' ? `${String(value).slice(0, 12)}…` : String(value);
  }
  return result;
}

let installed = false;
let counter = 0;

// Só as chamadas ao Pitaco (qualquer URL com `/collect/`), para o log não se encher com o Metro.
export function installNetworkLogger(isPitacoUrl: (url: string) => boolean = (url) => url.includes('/collect/')): void {
  if (installed || typeof globalThis.fetch !== 'function') return;
  installed = true;
  const original = globalThis.fetch.bind(globalThis);

  globalThis.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    if (!isPitacoUrl(url)) return original(input, init);

    counter += 1;
    const id = `r${counter}`;
    const started = Date.now();
    networkLog.push({
      id,
      at: new Date(started).toISOString(),
      method: init?.method ?? 'GET',
      url,
      requestHeaders: redactHeaders(init?.headers),
      requestBody: typeof init?.body === 'string' ? truncate(init.body) : null,
      status: null,
      durationMs: null,
      responseBody: null,
      error: null,
    });

    try {
      // Condições simuladas pelos cenários 13 e 14 (`networkConditions.ts`); normais fora deles.
      const conditions = getNetworkConditions();
      if (conditions.latencyMs > 0) await waitUnlessAborted(conditions.latencyMs, init?.signal);
      if (conditions.offline) throw simulatedNetworkError();
      const response = await original(input, init);
      const durationMs = Date.now() - started;
      networkLog.update((entry) => entry.id === id, (entry) => ({ ...entry, status: response.status, durationMs }));
      response
        .clone()
        .text()
        .then((body) => networkLog.update((entry) => entry.id === id, (entry) => ({ ...entry, responseBody: truncate(body) })))
        .catch(() => undefined);
      return response;
    } catch (error) {
      const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
      networkLog.update((entry) => entry.id === id, (entry) => ({ ...entry, durationMs: Date.now() - started, error: message }));
      throw error;
    }
  };
}

export function useNetworkLog(): readonly NetworkEntry[] {
  return useLogEntries(networkLog);
}
