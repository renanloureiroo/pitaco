import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";

import { buildUrl, request, requestNoContent } from "../client";
import { API_URL_ENV_VAR, MissingApiUrlError, getApiBaseUrl } from "../env";

// Rede real é proibida (Princípio IV): `fetch` é sempre substituído.
const itemSchema = z.object({ id: z.string(), name: z.string() });

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

function stubFetch(impl: (input: string, init: RequestInit) => Promise<Response>) {
  const spy = vi.fn(impl);
  vi.stubGlobal("fetch", spy);
  return spy;
}

beforeEach(() => {
  vi.stubEnv(API_URL_ENV_VAR, "http://api.test/api");
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("configuração de ambiente", () => {
  it("remove a barra final da base para não duplicar a do caminho", () => {
    vi.stubEnv(API_URL_ENV_VAR, "http://api.test/api/");

    expect(getApiBaseUrl()).toBe("http://api.test/api");
  });

  it("falha rápido com mensagem explícita quando a variável está ausente", () => {
    vi.stubEnv(API_URL_ENV_VAR, "");

    expect(() => getApiBaseUrl()).toThrow(MissingApiUrlError);
    expect(() => getApiBaseUrl()).toThrow(new RegExp(API_URL_ENV_VAR));
  });
});

describe("buildUrl", () => {
  it("omite parâmetros ausentes em vez de mandar string vazia", () => {
    expect(buildUrl("/applications", { status: undefined, page: 0, size: 20 })).toBe(
      "http://api.test/api/applications?page=0&size=20",
    );
  });

  it("não acrescenta '?' quando nenhum parâmetro sobrou", () => {
    expect(buildUrl("/applications", { status: undefined })).toBe("http://api.test/api/applications");
  });
});

describe("request", () => {
  it("devolve o dado validado em caso de sucesso", async () => {
    stubFetch(async () => jsonResponse(200, { id: "a1", name: "Acme" }));

    const result = await request(itemSchema, { path: "/applications/a1" });

    expect(result).toEqual({ ok: true, data: { id: "a1", name: "Acme" } });
  });

  it("nunca envia header de chave de aplicação na superfície administrativa", async () => {
    const spy = stubFetch(async () => jsonResponse(200, { id: "a1", name: "Acme" }));

    await request(itemSchema, { path: "/applications/a1" });

    const [, init] = spy.mock.calls[0];
    const headers = init.headers as Record<string, string>;
    expect(headers).toEqual({ Accept: "application/json" });
    expect(Object.keys(headers).map((key) => key.toLowerCase())).not.toContain("x-pitaco-api-key");
  });

  it("acrescenta Content-Type apenas quando há corpo", async () => {
    const spy = stubFetch(async () => jsonResponse(201, { id: "a1", name: "Acme" }));

    await request(itemSchema, { path: "/applications", method: "POST", body: { name: "Acme" } });

    const [, init] = spy.mock.calls[0];
    expect(init.headers).toEqual({
      Accept: "application/json",
      "Content-Type": "application/json",
    });
    expect(init.body).toBe(JSON.stringify({ name: "Acme" }));
  });

  it("não cacheia leitura administrativa", async () => {
    const spy = stubFetch(async () => jsonResponse(200, { id: "a1", name: "Acme" }));

    await request(itemSchema, { path: "/applications/a1" });

    expect(spy.mock.calls[0][1].cache).toBe("no-store");
  });

  it("traduz 400 com errors em recusa de validação por campo", async () => {
    stubFetch(async () =>
      jsonResponse(400, {
        code: "request.invalid",
        detail: "Requisição inválida.",
        errors: { slug: "Formato inválido." },
      }),
    );

    const result = await request(itemSchema, { path: "/applications", method: "POST", body: {} });

    expect(result).toMatchObject({
      ok: false,
      kind: "validation",
      errors: { slug: "Formato inválido." },
    });
  });

  it("traduz 404 em not_found", async () => {
    stubFetch(async () => jsonResponse(404, { code: "application.not_found", detail: "Sumiu." }));

    const result = await request(itemSchema, { path: "/applications/x" });

    expect(result).toMatchObject({ ok: false, kind: "not_found", code: "application.not_found" });
  });

  it("traduz 409 em conflict", async () => {
    stubFetch(async () => jsonResponse(409, { code: "application.slug_taken", detail: "Em uso." }));

    const result = await request(itemSchema, { path: "/applications", method: "POST", body: {} });

    expect(result).toMatchObject({ ok: false, kind: "conflict", code: "application.slug_taken" });
  });

  it("traduz 403 api_key.forbidden_surface em forbidden", async () => {
    stubFetch(async () =>
      jsonResponse(403, { code: "api_key.forbidden_surface", detail: "Superfície proibida." }),
    );

    const result = await request(itemSchema, { path: "/applications" });

    expect(result).toMatchObject({ ok: false, kind: "forbidden", code: "api_key.forbidden_surface" });
  });

  it("traduz falha de rede em unreachable", async () => {
    stubFetch(async () => {
      throw new TypeError("fetch failed");
    });

    const result = await request(itemSchema, { path: "/applications" });

    expect(result).toEqual({ ok: false, kind: "unreachable" });
  });

  it("recusa resposta que não corresponde ao contrato", async () => {
    stubFetch(async () => jsonResponse(200, { id: 1 }));

    const result = await request(itemSchema, { path: "/applications/a1" });

    expect(result).toMatchObject({ ok: false, kind: "unknown", code: "response.invalid" });
  });

  it("recusa corpo de sucesso que não é JSON", async () => {
    stubFetch(async () => new Response("não é json", { status: 200 }));

    const result = await request(itemSchema, { path: "/applications/a1" });

    expect(result).toMatchObject({ ok: false, kind: "unknown", code: "response.invalid" });
  });

  it("não quebra quando a recusa vem sem corpo", async () => {
    stubFetch(async () => new Response(null, { status: 500 }));

    const result = await request(itemSchema, { path: "/applications" });

    expect(result).toMatchObject({ ok: false, kind: "unknown", code: "unknown" });
  });
});

describe("requestNoContent", () => {
  it("aceita 204 sem tentar ler corpo", async () => {
    stubFetch(async () => new Response(null, { status: 204 }));

    const result = await requestNoContent({ path: "/applications/a1/api-keys/k1", method: "DELETE" });

    expect(result).toEqual({ ok: true, data: undefined });
  });

  it("traduz 409 de recurso já revogado em conflict", async () => {
    stubFetch(async () => jsonResponse(409, { code: "api_key.already_revoked", detail: "Já revogada." }));

    const result = await requestNoContent({ path: "/applications/a1/api-keys/k1", method: "DELETE" });

    expect(result).toMatchObject({ ok: false, kind: "conflict", code: "api_key.already_revoked" });
  });

  it("traduz falha de rede em unreachable", async () => {
    stubFetch(async () => {
      throw new TypeError("fetch failed");
    });

    const result = await requestNoContent({ path: "/applications/a1", method: "DELETE" });

    expect(result).toEqual({ ok: false, kind: "unreachable" });
  });
});
