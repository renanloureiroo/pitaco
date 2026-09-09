import { createServer } from "node:http";

import { matchRoute, type Route, type StubResponse } from "./http.ts";
import { routes } from "./routes/index.ts";

/**
 * Servidor HTTP do simulador — sem nenhuma dependência de npm.
 *
 * O `context-path` do backend é `/api`, e a variável `PITACO_API_URL` do painel já o inclui;
 * o simulador aceita tanto `/api/...` quanto o caminho sem prefixo, para que apontar o painel
 * para ele seja só uma questão de URL.
 */

const PORT = Number.parseInt(process.env.STUB_API_PORT ?? "4010", 10);
const CONTEXT_PATH = "/api";

function stripContextPath(pathname: string): string {
  return pathname.startsWith(CONTEXT_PATH) ? pathname.slice(CONTEXT_PATH.length) || "/" : pathname;
}

function send(
  response: import("node:http").ServerResponse,
  { status, body }: StubResponse,
): void {
  if (body === undefined) {
    response.writeHead(status).end();
    return;
  }

  const payload = JSON.stringify(body);
  const isProblem = status >= 400;
  response
    .writeHead(status, {
      "Content-Type": isProblem ? "application/problem+json" : "application/json",
      "Content-Length": Buffer.byteLength(payload),
    })
    .end(payload);
}

async function readBody(request: import("node:http").IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  for await (const chunk of request) {
    chunks.push(chunk as Buffer);
  }

  if (chunks.length === 0) {
    return undefined;
  }

  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    return undefined;
  }
}

export function createStubServer(routeTable: Route[] = routes) {
  return createServer((request, response) => {
    void (async () => {
      const url = new URL(request.url ?? "/", `http://localhost:${PORT}`);
      const pathname = stripContextPath(url.pathname);

      // Rota de saúde: o Playwright espera por ela antes de subir o Next.
      if (pathname === "/health") {
        send(response, { status: 200, body: { status: "up" } });
        return;
      }

      const matched = matchRoute(routeTable, request.method ?? "GET", pathname);

      if (matched === undefined) {
        send(response, {
          status: 404,
          body: {
            type: "https://pitaco.dev/errors/route-not-found",
            title: "Rota não encontrada",
            status: 404,
            detail: `O simulador não implementa ${request.method} ${pathname}.`,
            code: "route.not_found",
          },
        });
        return;
      }

      try {
        send(
          response,
          matched.route.handler({
            params: matched.params,
            query: url.searchParams,
            body: await readBody(request),
          }),
        );
      } catch (error) {
        send(response, {
          status: 500,
          body: {
            title: "Falha no simulador",
            status: 500,
            detail: error instanceof Error ? error.message : "Erro desconhecido.",
            code: "stub.failure",
          },
        });
      }
    })();
  });
}

createStubServer().listen(PORT, () => {
  console.log(`[stub-api] ouvindo em http://localhost:${PORT}${CONTEXT_PATH}`);
});
