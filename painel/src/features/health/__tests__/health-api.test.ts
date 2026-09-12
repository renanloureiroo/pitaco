import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import { getSdkVersions, getSurveyHealth, listSdkErrors } from "../api/health";

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

beforeEach(() => {
  vi.stubEnv(API_URL_ENV_VAR, "http://api.test/api");
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("API de saúde", () => {
  it("lê as versões do SDK da aplicação", async () => {
    const spy = vi.fn(async (_input: string) =>
      jsonResponse({ recentFrom: "2026-08-29", recentRequests: 0, versions: [] }),
    );
    vi.stubGlobal("fetch", spy);

    const result = await getSdkVersions("app-1");

    expect(spy.mock.calls[0]?.[0]).toBe("http://api.test/api/applications/app-1/sdk-versions");
    expect(result.ok).toBe(true);
  });

  it("lista os erros com página e filtros só quando presentes", async () => {
    const spy = vi.fn(async (_input: string) =>
      jsonResponse({ items: [], page: 0, size: 20, total: 0, totalPages: 0 }),
    );
    vi.stubGlobal("fetch", spy);

    await listSdkErrors("app-1", { page: 0, size: 20 });
    await listSdkErrors("app-1", { page: 1, size: 10, kind: "render_error", sdkVersion: "1.0.0" });

    expect(spy.mock.calls[0]?.[0]).toBe(
      "http://api.test/api/applications/app-1/sdk-errors?page=0&size=20",
    );
    expect(spy.mock.calls[1]?.[0]).toBe(
      "http://api.test/api/applications/app-1/sdk-errors?page=1&size=10&kind=render_error&sdkVersion=1.0.0",
    );
  });

  it("lê a saúde da pesquisa", async () => {
    const spy = vi.fn(async (_input: string) =>
      jsonResponse({
        from: "2026-08-13T00:00:00Z",
        to: "2026-09-12T00:00:00Z",
        displays: 0,
        suppressions: { total: 0, byReason: [], bySdkVersion: [] },
        relevant: false,
      }),
    );
    vi.stubGlobal("fetch", spy);

    const result = await getSurveyHealth("app-1", "srv-1");

    expect(spy.mock.calls[0]?.[0]).toBe(
      "http://api.test/api/applications/app-1/surveys/srv-1/health",
    );
    expect(result).toMatchObject({ ok: true, data: { relevant: false } });
  });
});
