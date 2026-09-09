/**
 * Núcleo do simulador de API (R9).
 *
 * O `fetch` do painel roda no servidor Node do Next, fora do alcance do `page.route` do
 * Playwright — então o E2E não consegue simular a API pelo navegador. Este servidor ocupa o
 * lugar do backend: estado em memória, roteamento por método e caminho, e respostas fiéis ao
 * contrato em `specs/001-painel-operacao-pesquisas/contracts/backend-api.md`, que é o árbitro
 * sempre que o simulador e o backend divergirem.
 */

export type StubResponse = { status: number; body?: unknown };

export type RouteContext = {
  params: Record<string, string>;
  query: URLSearchParams;
  body: unknown;
};

export type Route = {
  method: string;
  /** Padrão com segmentos dinâmicos, ex.: `/applications/:applicationId/api-keys/:apiKeyId`. */
  pattern: string;
  handler: (context: RouteContext) => StubResponse;
};

export function json(status: number, body?: unknown): StubResponse {
  return { status, body };
}

export function noContent(): StubResponse {
  return { status: 204 };
}

/** Recusa RFC 9457, com o `code` estável que o painel usa para decidir o tratamento. */
export function problem(
  status: number,
  code: string,
  detail: string,
  extra?: { errors?: Record<string, string> },
): StubResponse {
  return {
    status,
    body: {
      type: `https://pitaco.dev/errors/${code.replace(/\./g, "-")}`,
      title: detail,
      status,
      detail,
      code,
      traceId: `stub-${Math.random().toString(36).slice(2, 10)}`,
      ...(extra?.errors !== undefined ? { errors: extra.errors } : {}),
    },
  };
}

export function notFound(code: string, detail: string): StubResponse {
  return problem(404, code, detail);
}

export function conflict(code: string, detail: string): StubResponse {
  return problem(409, code, detail);
}

export function validation(
  code: string,
  detail: string,
  errors: Record<string, string>,
): StubResponse {
  return problem(400, code, detail, { errors });
}

export type PageQuery = { page: number; size: number };

export function readPageQuery(query: URLSearchParams): PageQuery {
  const rawPage = Number.parseInt(query.get("page") ?? "", 10);
  const rawSize = Number.parseInt(query.get("size") ?? "", 10);

  return {
    page: Number.isInteger(rawPage) && rawPage >= 0 ? rawPage : 0,
    size: Number.isInteger(rawSize) && rawSize >= 1 && rawSize <= 100 ? rawSize : 20,
  };
}

/** `PageResponse<T>`: `total` é o do conjunto filtrado inteiro, não o da página. */
export function paginate<T>(items: T[], { page, size }: PageQuery) {
  const total = items.length;
  return {
    items: items.slice(page * size, page * size + size),
    page,
    size,
    total,
    totalPages: total === 0 ? 0 : Math.ceil(total / size),
  };
}

export function matchRoute(
  routes: Route[],
  method: string,
  pathname: string,
): { route: Route; params: Record<string, string> } | undefined {
  const segments = pathname.split("/").filter((segment) => segment !== "");

  for (const route of routes) {
    if (route.method !== method) {
      continue;
    }

    const patternSegments = route.pattern.split("/").filter((segment) => segment !== "");
    if (patternSegments.length !== segments.length) {
      continue;
    }

    const params: Record<string, string> = {};
    let matched = true;

    for (const [index, patternSegment] of patternSegments.entries()) {
      if (patternSegment.startsWith(":")) {
        params[patternSegment.slice(1)] = decodeURIComponent(segments[index]);
      } else if (patternSegment !== segments[index]) {
        matched = false;
        break;
      }
    }

    if (matched) {
      return { route, params };
    }
  }

  return undefined;
}

let sequence = 0;

/** Identificador legível e único por processo — o E2E nunca depende do formato. */
export function nextId(prefix: string): string {
  sequence += 1;
  return `${prefix}-${sequence.toString(36)}${Math.random().toString(36).slice(2, 8)}`;
}

export function nowIso(): string {
  return new Date().toISOString();
}
