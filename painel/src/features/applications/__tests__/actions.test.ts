import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

const redirect = vi.fn((path: string) => {
  throw new Error(`NEXT_REDIRECT:${path}`);
});
const refresh = vi.fn();

vi.mock("next/navigation", () => ({ redirect }));
vi.mock("next/cache", () => ({ refresh }));

function formDataOf(values: Record<string, string>): FormData {
  const formData = new FormData();
  for (const [key, value] of Object.entries(values)) {
    formData.set(key, value);
  }
  return formData;
}

function stubFetch(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn(
      async () =>
        new Response(JSON.stringify(body), {
          status,
          headers: { "Content-Type": "application/json" },
        }),
    ),
  );
}

beforeEach(() => {
  vi.stubEnv(API_URL_ENV_VAR, "http://api.test/api");
  redirect.mockClear();
  refresh.mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("createApplicationAction", () => {
  async function act(values: Record<string, string>) {
    const { createApplicationAction } = await import("../actions");
    return createApplicationAction({ status: "idle" }, formDataOf(values));
  }

  it("recusa slug inválido antes de chamar a API e devolve o erro no campo", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);

    const state = await act({ name: "Acme", slug: "Acme App" });

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state).toMatchObject({ status: "error" });
    expect(state.status === "error" && state.fieldErrors.slug).toMatch(/letras minúsculas/i);
  });

  it("devolve intacto o que foi digitado, para o formulário não se esvaziar", async () => {
    stubFetch(400, {
      code: "request.invalid",
      detail: "Requisição inválida.",
      errors: { slug: "Slug já em uso." },
    });

    const values = { name: "Acme", slug: "acme", retentionDays: "30" };
    const state = await act(values);

    expect(state).toMatchObject({ status: "error", values });
  });

  it("transforma a recusa por campo do backend em erro por campo", async () => {
    stubFetch(400, {
      code: "request.invalid",
      detail: "Requisição inválida.",
      errors: { openTextRetentionDays: "Não pode superar a retenção geral." },
    });

    const state = await act({ name: "Acme", retentionDays: "30", openTextRetentionDays: "90" });

    expect(state.status === "error" && state.fieldErrors.openTextRetentionDays).toBe(
      "Não pode superar a retenção geral.",
    );
  });

  it("exibe o 409 de slug em conflito como mensagem do formulário", async () => {
    stubFetch(409, { code: "application.slug_already_taken", detail: "Slug já em uso." });

    const state = await act({ name: "Acme", slug: "acme" });

    expect(state).toMatchObject({ status: "error", message: "Slug já em uso." });
  });

  it("leva ao detalhe da aplicação criada", async () => {
    stubFetch(201, { id: "app-9", slug: "acme" });

    await expect(act({ name: "Acme" })).rejects.toThrow("NEXT_REDIRECT:/aplicacoes/app-9");
    expect(redirect).toHaveBeenCalledWith("/aplicacoes/app-9");
  });
});

describe("updateApplicationAction", () => {
  async function act(values: Record<string, string>) {
    const { updateApplicationAction } = await import("../actions");
    return updateApplicationAction("app-1", { status: "idle" }, formDataOf(values));
  }

  it("recusa nome em branco antes de chamar a API", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);

    const state = await act({ name: "  " });

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state.status === "error" && state.fieldErrors.name).toMatch(/informe o nome/i);
  });

  it("exibe a recusa de regra do backend como mensagem do formulário, preservando os valores", async () => {
    stubFetch(422, {
      code: "application.open_text_retention_invalid",
      detail: "Prazo de retenção de texto livre não pode ser maior que o prazo geral",
    });

    const values = { name: "Acme", retentionDays: "30", openTextRetentionDays: "90" };
    const state = await act(values);

    expect(state).toMatchObject({
      status: "error",
      message: /texto livre/,
      values,
    });
    expect(refresh).not.toHaveBeenCalled();
  });

  it("em sucesso, realinha a tela em vez de navegar", async () => {
    stubFetch(200, {
      id: "app-1",
      slug: "acme",
      name: "Acme",
      status: "active",
      createdAt: "2026-09-01T12:00:00Z",
      updatedAt: "2026-09-02T12:00:00Z",
    });

    const state = await act({ name: "Acme" });

    expect(state).toEqual({ status: "success", data: undefined });
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(redirect).not.toHaveBeenCalled();
  });
});

describe("setApplicationStatusAction", () => {
  it("desativa e realinha a tela", async () => {
    const spy = vi.fn(
      async (_input: string, _init: RequestInit) =>
        new Response(
          JSON.stringify({
            id: "app-1",
            slug: "acme",
            name: "Acme",
            status: "inactive",
            createdAt: "2026-09-01T12:00:00Z",
            updatedAt: "2026-09-02T12:00:00Z",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
    );
    vi.stubGlobal("fetch", spy);

    const { setApplicationStatusAction } = await import("../actions");
    const state = await setApplicationStatusAction("app-1", "deactivate");

    expect(spy.mock.calls[0][0]).toBe("http://api.test/api/applications/app-1/deactivate");
    expect(state).toEqual({ status: "success", data: undefined });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("devolve a recusa como estado exibível", async () => {
    stubFetch(404, { code: "application.not_found", detail: "Aplicação não encontrada." });

    const { setApplicationStatusAction } = await import("../actions");
    const state = await setApplicationStatusAction("app-1", "activate");

    expect(state).toMatchObject({ status: "error", message: "Aplicação não encontrada." });
    expect(refresh).not.toHaveBeenCalled();
  });
});
