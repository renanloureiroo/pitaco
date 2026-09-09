import type { z } from "zod";

import { getApiBaseUrl } from "./env";
import { toApiFailure, unreachableFailure, type Result } from "./errors";

/**
 * O único ponto do painel que fala HTTP com o backend (R1, R5).
 *
 * Cabeçalhos enviados: `Accept: application/json` sempre e `Content-Type: application/json`
 * apenas quando há corpo. **Nada mais** — em especial, nunca o header de chave de aplicação:
 * toda rota sob `/applications/**` é superfície administrativa, e o backend recusa com
 * `403 api_key.forbidden_surface` quem apresentar chave ali (FR-007).
 *
 * Nenhuma leitura é cacheada: um painel administrativo nunca deve mostrar dado velho.
 */

export type QueryValue = string | number | boolean | undefined;

export type RequestOptions = {
  path: string;
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  query?: Record<string, QueryValue>;
  body?: unknown;
};

const RESPONSE_INVALID_CODE = "response.invalid";

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = `${getApiBaseUrl()}${path}`;

  if (query === undefined) {
    return url;
  }

  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) {
      search.set(key, String(value));
    }
  }

  const serialized = search.toString();
  return serialized === "" ? url : `${url}?${serialized}`;
}

async function send(options: RequestOptions): Promise<Response> {
  const { path, method = "GET", query, body } = options;

  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  return fetch(buildUrl(path, query), {
    method,
    headers,
    cache: "no-store",
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
}

/** Corpo de recusa: RFC 9457 quando existe, `undefined` quando ausente ou ilegível. */
async function readProblemBody(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return undefined;
  }
}

/** Requisição cuja resposta de sucesso carrega corpo, validado pelo schema informado. */
export async function request<T>(
  schema: z.ZodType<T>,
  options: RequestOptions,
): Promise<Result<T>> {
  let response: Response;

  try {
    response = await send(options);
  } catch {
    return unreachableFailure;
  }

  if (!response.ok) {
    return toApiFailure(response.status, await readProblemBody(response));
  }

  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    return {
      ok: false,
      kind: "unknown",
      code: RESPONSE_INVALID_CODE,
      detail: "A API respondeu com um corpo que não é JSON válido.",
    };
  }

  const parsed = schema.safeParse(payload);
  if (!parsed.success) {
    return {
      ok: false,
      kind: "unknown",
      code: RESPONSE_INVALID_CODE,
      detail: "A resposta da API não corresponde ao contrato esperado pelo painel.",
    };
  }

  return { ok: true, data: parsed.data };
}

/** Requisição cuja resposta de sucesso não tem corpo (`204 No Content`). */
export async function requestNoContent(options: RequestOptions): Promise<Result<void>> {
  let response: Response;

  try {
    response = await send(options);
  } catch {
    return unreachableFailure;
  }

  if (!response.ok) {
    return toApiFailure(response.status, await readProblemBody(response));
  }

  return { ok: true, data: undefined };
}
