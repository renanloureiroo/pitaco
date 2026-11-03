import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

const refresh = vi.fn();
vi.mock("next/cache", () => ({ refresh, revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));

function formDataOf(values: Record<string, string>): FormData {
  const formData = new FormData();
  for (const [name, value] of Object.entries(values)) {
    formData.set(name, value);
  }
  return formData;
}

beforeEach(() => {
  vi.stubEnv(API_URL_ENV_VAR, "http://api.test/api");
  refresh.mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("updateExposureAction", () => {
  it("recusa prioridade fora da faixa sem chamar a API", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    const { updateExposureAction } = await import("../actions");

    const state = await updateExposureAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf({ priority: "500", responseQuota: "", ignoresQuietPeriod: "false" }),
    );

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state.status === "error" && state.fieldErrors.priority).toBeDefined();
  });

  it("grava e realinha a tela", async () => {
    const fetchSpy = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            id: "srv-1",
            applicationId: "app-1",
            name: "NPS",
            state: "active",
            priority: 3,
            responseQuota: 20,
            ignoresQuietPeriod: false,
            createdAt: "2026-09-09T12:00:00Z",
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
    );
    vi.stubGlobal("fetch", fetchSpy);
    const { updateExposureAction } = await import("../actions");

    const state = await updateExposureAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf({ priority: "3", responseQuota: "20", ignoresQuietPeriod: "false" }),
    );

    expect(state.status).toBe("success");
    expect(refresh).toHaveBeenCalledOnce();
  });

  it("mostra a recusa do backend sem realinhar", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(
        async () =>
          new Response(
            JSON.stringify({ code: "survey.not_found", detail: "Pesquisa não encontrada" }),
            { status: 404, headers: { "Content-Type": "application/problem+json" } },
          ),
      ),
    );
    const { updateExposureAction } = await import("../actions");

    const state = await updateExposureAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf({ priority: "0", responseQuota: "", ignoresQuietPeriod: "true" }),
    );

    expect(state.status).toBe("error");
    expect(refresh).not.toHaveBeenCalled();
  });
});
