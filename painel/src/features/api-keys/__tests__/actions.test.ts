import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

const refresh = vi.fn();
vi.mock("next/cache", () => ({ refresh }));

const key = {
  id: "key-1",
  applicationId: "app-1",
  label: "Produção",
  prefix: "pit_live_abc",
  status: "active",
  createdAt: "2026-09-09T12:00:00Z",
};

function formDataOf(values: Record<string, string>): FormData {
  const formData = new FormData();
  for (const [name, value] of Object.entries(values)) {
    formData.set(name, value);
  }
  return formData;
}

function stubFetch(status: number, body?: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn(
      async () =>
        new Response(body === undefined ? null : JSON.stringify(body), {
          status,
          headers: { "Content-Type": "application/json" },
        }),
    ),
  );
}

beforeEach(() => {
  vi.stubEnv(API_URL_ENV_VAR, "http://api.test/api");
  refresh.mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("issueApiKeyAction", () => {
  it("devolve o segredo no estado da action, e em nenhum outro lugar", async () => {
    stubFetch(201, { ...key, secret: "pit_live_abc.segredo" });
    const { issueApiKeyAction } = await import("../actions");

    const state = await issueApiKeyAction(
      "app-1",
      { status: "idle" },
      formDataOf({ label: "Produção" }),
    );

    expect(state).toMatchObject({
      status: "success",
      data: { secret: "pit_live_abc.segredo" },
    });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("recusa rótulo em branco antes de chamar a API", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    const { issueApiKeyAction } = await import("../actions");

    const state = await issueApiKeyAction("app-1", { status: "idle" }, formDataOf({ label: " " }));

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state.status === "error" && state.fieldErrors.label).toMatch(/informe um rótulo/i);
  });

  it("não expõe segredo nenhum quando a emissão é recusada", async () => {
    stubFetch(409, { code: "api_key.limit_reached", detail: "Limite de chaves atingido." });
    const { issueApiKeyAction } = await import("../actions");

    const state = await issueApiKeyAction(
      "app-1",
      { status: "idle" },
      formDataOf({ label: "Produção" }),
    );

    expect(state).toMatchObject({ status: "error", message: "Limite de chaves atingido." });
    expect(JSON.stringify(state)).not.toContain("secret");
    expect(refresh).not.toHaveBeenCalled();
  });
});

describe("revokeApiKeyAction", () => {
  it("realinha a UI depois de revogar", async () => {
    stubFetch(204);
    const { revokeApiKeyAction } = await import("../actions");

    expect(await revokeApiKeyAction("app-1", "key-1")).toMatchObject({ status: "success" });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("trata o 409 de chave já revogada como recusa exibível, não como falha da tela", async () => {
    stubFetch(409, { code: "api_key.already_revoked", detail: "Chave já revogada." });
    const { revokeApiKeyAction } = await import("../actions");

    const state = await revokeApiKeyAction("app-1", "key-1");

    expect(state).toMatchObject({ status: "error", message: "Chave já revogada." });
    expect(refresh).not.toHaveBeenCalled();
  });
});
