import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import { getApiKey, issueApiKey, listApiKeys, revokeApiKey } from "../api";

const key = {
  id: "key-1",
  applicationId: "app-1",
  label: "Produção",
  prefix: "pit_live_abc",
  status: "active",
  createdAt: "2026-09-09T12:00:00Z",
};

function stubFetch(status: number, body?: unknown) {
  const spy = vi.fn(
    async (_input: string, _init: RequestInit) =>
      new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
      }),
  );
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

describe("listApiKeys", () => {
  it("filtra por situação na query", async () => {
    const spy = stubFetch(200, { items: [key], page: 0, size: 20, total: 1, totalPages: 1 });

    await listApiKeys("app-1", { status: "revoked", page: 0, size: 20 });

    expect(spy.mock.calls[0][0]).toBe(
      "http://api.test/api/applications/app-1/api-keys?status=revoked&page=0&size=20",
    );
  });

  it("descarta um segredo que venha por engano na listagem", async () => {
    stubFetch(200, {
      items: [{ ...key, secret: "vazou" }],
      page: 0,
      size: 20,
      total: 1,
      totalPages: 1,
    });

    const result = await listApiKeys("app-1", { page: 0, size: 20 });

    expect(result.ok && result.data.items[0]).not.toHaveProperty("secret");
  });
});

describe("getApiKey", () => {
  it("traduz 404 em not_found", async () => {
    stubFetch(404, { code: "api_key.not_found", detail: "Chave não encontrada." });

    expect(await getApiKey("app-1", "key-9")).toMatchObject({ kind: "not_found" });
  });
});

describe("issueApiKey", () => {
  it("devolve o segredo apenas na emissão", async () => {
    const spy = stubFetch(201, { ...key, secret: "pit_live_abc.segredo" });

    const result = await issueApiKey("app-1", "Produção");

    expect(spy.mock.calls[0][1].method).toBe("POST");
    expect(JSON.parse(String(spy.mock.calls[0][1].body))).toEqual({ label: "Produção" });
    expect(result).toMatchObject({ ok: true, data: { secret: "pit_live_abc.segredo" } });
  });

  it("recusa uma emissão que volte sem segredo", async () => {
    stubFetch(201, key);

    expect(await issueApiKey("app-1", "Produção")).toMatchObject({
      ok: false,
      code: "response.invalid",
    });
  });
});

describe("revokeApiKey", () => {
  it("aceita o 204 da revogação", async () => {
    stubFetch(204);

    expect(await revokeApiKey("app-1", "key-1")).toEqual({ ok: true, data: undefined });
  });

  it("traduz o 409 de chave já revogada em recusa exibível", async () => {
    stubFetch(409, { code: "api_key.already_revoked", detail: "Chave já revogada." });

    expect(await revokeApiKey("app-1", "key-1")).toMatchObject({
      kind: "conflict",
      code: "api_key.already_revoked",
      detail: "Chave já revogada.",
    });
  });
});
