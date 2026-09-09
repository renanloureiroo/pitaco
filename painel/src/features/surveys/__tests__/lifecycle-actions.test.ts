import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

const revalidatePath = vi.fn();
vi.mock("next/cache", () => ({ refresh: vi.fn(), revalidatePath }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));

const survey = {
  id: "srv-1",
  applicationId: "app-1",
  name: "Satisfação",
  state: "paused",
  createdAt: "2026-09-09T12:00:00Z",
};

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

const SEGMENT = "/aplicacoes/app-1/pesquisas/srv-1";

describe("ações de ciclo de vida", () => {
  it.each(["pauseSurveyAction", "resumeSurveyAction", "endSurveyAction"] as const)(
    "%s revalida o segmento inteiro da pesquisa",
    async (name) => {
      stubFetch(200, survey);
      const actions = await import("../actions");

      const state = await actions[name]("app-1", "srv-1");

      expect(state).toMatchObject({ status: "success" });
      expect(revalidatePath).toHaveBeenCalledWith(SEGMENT, "layout");
    },
  );

  it("exibe a recusa por estado alterado por terceiros e realinha a UI mesmo assim", async () => {
    stubFetch(409, {
      code: "survey.transition_not_allowed",
      detail: "A pesquisa já foi encerrada por outra pessoa.",
    });
    const { pauseSurveyAction } = await import("../actions");

    const state = await pauseSurveyAction("app-1", "srv-1");

    expect(state).toMatchObject({
      status: "error",
      message: "A pesquisa já foi encerrada por outra pessoa.",
    });
    // Mesmo recusada, a revalidação acontece: o estado real mudou e a tela precisa refletir isso.
    expect(revalidatePath).toHaveBeenCalledWith(SEGMENT, "layout");
  });

  it("trata a indisponibilidade da API como recusa exibível, não como tela quebrada", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new TypeError("fetch failed");
      }),
    );
    const { endSurveyAction } = await import("../actions");

    const state = await endSurveyAction("app-1", "srv-1");

    expect(state.status === "error" && state.message).toMatch(/não foi possível falar com a api/i);
  });
});
