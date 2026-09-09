import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import { createSurvey, discardSurvey, listSurveys, renameSurvey } from "../api/surveys";

const survey = {
  id: "srv-1",
  applicationId: "app-1",
  name: "Satisfação",
  state: "draft",
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

describe("listSurveys", () => {
  it("lista apenas as pesquisas da aplicação informada (FR-017)", async () => {
    const spy = stubFetch(200, { items: [survey], page: 0, size: 20, total: 1, totalPages: 1 });

    await listSurveys("app-1", { page: 0, size: 20 });

    expect(spy.mock.calls[0][0]).toBe(
      "http://api.test/api/applications/app-1/surveys?page=0&size=20",
    );
  });
});

describe("createSurvey", () => {
  it("cria pela nomeação e devolve a pesquisa em rascunho", async () => {
    const spy = stubFetch(201, survey);

    const result = await createSurvey("app-1", "Satisfação");

    expect(JSON.parse(String(spy.mock.calls[0][1].body))).toEqual({ name: "Satisfação" });
    expect(result).toMatchObject({ ok: true, data: { state: "draft" } });
  });

  it("aceita pesquisa sem versão publicada como ausência, não como zero", async () => {
    stubFetch(201, survey);

    const result = await createSurvey("app-1", "Satisfação");

    expect(result.ok && result.data.publishedVersionNumber).toBeUndefined();
  });
});

describe("renameSurvey", () => {
  it("renomeia por PATCH", async () => {
    const spy = stubFetch(200, { ...survey, name: "Outro nome" });

    await renameSurvey("app-1", "srv-1", "Outro nome");

    expect(spy.mock.calls[0][0]).toBe("http://api.test/api/applications/app-1/surveys/srv-1");
    expect(spy.mock.calls[0][1].method).toBe("PATCH");
  });
});

describe("discardSurvey", () => {
  it("aceita o 204 do descarte", async () => {
    stubFetch(204);

    await expect(discardSurvey("app-1", "srv-1")).resolves.toEqual({ ok: true, data: undefined });
  });

  it("traduz o 409 de pesquisa já publicada em recusa exibível", async () => {
    stubFetch(409, {
      code: "survey.already_published",
      detail: "Pesquisa já publicada não pode ser descartada.",
    });

    const result = await discardSurvey("app-1", "srv-1");

    expect(result).toMatchObject({
      ok: false,
      kind: "conflict",
      code: "survey.already_published",
    });
  });
});
