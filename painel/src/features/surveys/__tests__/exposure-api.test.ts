import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import { getPublicationWarnings } from "../api/publication";
import { getQuotaProgress, updateSurveyExposure } from "../api/surveys";

const survey = {
  id: "srv-1",
  applicationId: "app-1",
  name: "Satisfação",
  state: "active",
  priority: 7,
  responseQuota: 50,
  ignoresQuietPeriod: true,
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

describe("updateSurveyExposure", () => {
  it("envia os três campos, com a cota em branco como remoção explícita", async () => {
    const spy = stubFetch(200, { ...survey, responseQuota: undefined });

    await updateSurveyExposure("app-1", "srv-1", { priority: 7, ignoresQuietPeriod: true });

    expect(spy.mock.calls[0][0]).toBe("http://api.test/api/applications/app-1/surveys/srv-1");
    expect(spy.mock.calls[0][1].method).toBe("PATCH");
    expect(JSON.parse(String(spy.mock.calls[0][1].body))).toEqual({
      priority: 7,
      responseQuota: null,
      ignoresQuietPeriod: true,
    });
  });

  it("devolve a pesquisa com a exposição gravada", async () => {
    stubFetch(200, survey);

    const result = await updateSurveyExposure("app-1", "srv-1", {
      priority: 7,
      responseQuota: 50,
      ignoresQuietPeriod: true,
    });

    expect(result).toMatchObject({ ok: true, data: { priority: 7, responseQuota: 50 } });
  });
});

describe("getQuotaProgress", () => {
  it("lê o progresso da cota na rota da pesquisa", async () => {
    const spy = stubFetch(200, { responseQuota: 10, completedResponses: 4 });

    const result = await getQuotaProgress("app-1", "srv-1");

    expect(spy.mock.calls[0][0]).toBe(
      "http://api.test/api/applications/app-1/surveys/srv-1/quota-progress",
    );
    expect(result).toEqual({ ok: true, data: { responseQuota: 10, completedResponses: 4 } });
  });
});

describe("getPublicationWarnings", () => {
  it("desembrulha a lista de avisos", async () => {
    const spy = stubFetch(200, {
      warnings: [{ code: "segmentation.no_known_match", ruleId: "r-1", attribute: "plano" }],
    });

    const result = await getPublicationWarnings("app-1", "srv-1");

    expect(spy.mock.calls[0][0]).toBe(
      "http://api.test/api/applications/app-1/surveys/srv-1/publication-warnings",
    );
    expect(result).toMatchObject({
      ok: true,
      data: [{ code: "segmentation.no_known_match", attribute: "plano", competingSurveys: [] }],
    });
  });
});
