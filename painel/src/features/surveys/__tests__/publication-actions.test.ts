import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

const revalidatePath = vi.fn();
vi.mock("next/cache", () => ({ refresh: vi.fn(), revalidatePath }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));

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
  revalidatePath.mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("publishSurveyAction", () => {
  it("publica a versão 1 sem exigir natureza de mudança", async () => {
    stubFetch(201, { number: 1, status: "published", comparabilityGroup: 1 });
    const { publishSurveyAction } = await import("../actions");

    const state = await publishSurveyAction("app-1", "srv-1", false, { status: "idle" }, formDataOf({}));

    expect(state).toMatchObject({ status: "success" });
    // Publicar afeta cabeçalho, versões e publicação: o segmento inteiro é revalidado.
    expect(revalidatePath).toHaveBeenCalledWith("/aplicacoes/app-1/pesquisas/srv-1", "layout");
  });

  it("exige natureza de mudança a partir da versão 2, antes de chamar a API", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    const { publishSurveyAction } = await import("../actions");

    const state = await publishSurveyAction("app-1", "srv-1", true, { status: "idle" }, formDataOf({}));

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state.status === "error" && state.fieldErrors.changeKind).toBeDefined();
  });

  it("devolve a recusa por impedimento ao estado da action", async () => {
    stubFetch(409, {
      code: "survey.publication_blocked",
      detail: "A pesquisa ainda tem impedimentos.",
    });
    const { publishSurveyAction } = await import("../actions");

    const state = await publishSurveyAction("app-1", "srv-1", false, { status: "idle" }, formDataOf({}));

    expect(state).toMatchObject({
      status: "error",
      message: "A pesquisa ainda tem impedimentos.",
    });
    expect(revalidatePath).not.toHaveBeenCalled();
  });

  it("preserva o resumo digitado quando a publicação é recusada", async () => {
    stubFetch(409, { code: "survey.publication_blocked", detail: "Impedimentos." });
    const { publishSurveyAction } = await import("../actions");

    const values = { changeKind: "cosmetic", changeSummary: "Ajuste de texto" };
    const state = await publishSurveyAction(
      "app-1",
      "srv-1",
      true,
      { status: "idle" },
      formDataOf(values),
    );

    expect(state).toMatchObject({ status: "error", values });
  });
});

describe("versões de rascunho", () => {
  it("abre um rascunho e revalida o segmento da pesquisa", async () => {
    stubFetch(201, { number: 2, status: "draft", comparabilityGroup: 1 });
    const { openDraftVersionAction } = await import("../actions");

    expect(await openDraftVersionAction("app-1", "srv-1")).toMatchObject({ status: "success" });
    expect(revalidatePath).toHaveBeenCalledWith("/aplicacoes/app-1/pesquisas/srv-1", "layout");
  });

  it("descarta o rascunho e revalida o segmento da pesquisa", async () => {
    stubFetch(204);
    const { discardDraftVersionAction } = await import("../actions");

    expect(await discardDraftVersionAction("app-1", "srv-1")).toMatchObject({ status: "success" });
    expect(revalidatePath).toHaveBeenCalledWith("/aplicacoes/app-1/pesquisas/srv-1", "layout");
  });

  it("exibe a recusa de descarte sem rascunho aberto", async () => {
    stubFetch(409, { code: "survey_version.no_draft", detail: "Não há rascunho aberto." });
    const { discardDraftVersionAction } = await import("../actions");

    expect(await discardDraftVersionAction("app-1", "srv-1")).toMatchObject({
      status: "error",
      message: "Não há rascunho aberto.",
    });
  });
});
