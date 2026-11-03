import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import { endSurvey, getTransitions, pauseSurvey, resumeSurvey } from "../api/lifecycle";

const survey = {
  id: "srv-1",
  applicationId: "app-1",
  name: "Satisfação",
  state: "paused",
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

const base = "http://api.test/api/applications/app-1/surveys/srv-1";

describe("getTransitions", () => {
  it("lê a lista de transições autorizadas", async () => {
    const spy = stubFetch(200, [
      { from: "active", to: "paused", reason: "manual_pause", occurredAt: "2026-09-09T12:00:00Z" },
    ]);

    const result = await getTransitions("app-1", "srv-1");

    expect(spy.mock.calls[0][0]).toBe(`${base}/transitions`);
    expect(result).toMatchObject({ ok: true, data: [{ reason: "manual_pause" }] });
  });

  it("lista vazia é sucesso — é assim que uma pesquisa encerrada responde", async () => {
    stubFetch(200, []);

    expect(await getTransitions("app-1", "srv-1")).toEqual({ ok: true, data: [] });
  });
});

describe("transições", () => {
  it.each([
    ["pause", pauseSurvey],
    ["resume", resumeSurvey],
    ["end", endSurvey],
  ] as const)("%s usa POST no caminho correspondente", async (action, fn) => {
    const spy = stubFetch(200, survey);

    await fn("app-1", "srv-1");

    expect(spy.mock.calls[0][0]).toBe(`${base}/${action}`);
    expect(spy.mock.calls[0][1].method).toBe("POST");
  });

  it("traduz a recusa de transição não permitida em conflito exibível", async () => {
    stubFetch(409, {
      code: "survey.transition_not_allowed",
      detail: "A pesquisa não está no ar.",
    });

    expect(await pauseSurvey("app-1", "srv-1")).toMatchObject({
      kind: "conflict",
      code: "survey.transition_not_allowed",
      detail: "A pesquisa não está no ar.",
    });
  });
});
