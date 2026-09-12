import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import { eraseRespondent, getRetentionPreview, listDeletionAudits } from "../api/privacy";

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

describe("API de privacidade", () => {
  it("exclui pela referência com DELETE e a identidade na query", async () => {
    const spy = vi.fn(async (_input: string, _init?: RequestInit) =>
      jsonResponse({ deleted: true, displaysDeleted: 2, answersDeleted: 4 }),
    );
    vi.stubGlobal("fetch", spy);

    const result = await eraseRespondent("app-1", { identityKind: "reference", identity: "u 1" });

    expect(spy.mock.calls[0]?.[0]).toBe("http://api.test/api/applications/app-1/respondents?reference=u+1");
    expect(spy.mock.calls[0]?.[1]?.method).toBe("DELETE");
    expect(result.ok && result.data.answersDeleted).toBe(4);
  });

  it("exclui pelo dispositivo quando é esse o tipo", async () => {
    const spy = vi.fn(async (_input: string) =>
      jsonResponse({ deleted: false, displaysDeleted: 0, answersDeleted: 0 }),
    );
    vi.stubGlobal("fetch", spy);

    await eraseRespondent("app-1", { identityKind: "device", identity: "d-1" });

    expect(spy.mock.calls[0]?.[0]).toBe("http://api.test/api/applications/app-1/respondents?deviceId=d-1");
  });

  it("lista os registros com página e lê a prévia", async () => {
    const spy = vi.fn(async (input: string) =>
      input.includes("deletion-audits")
        ? jsonResponse({ items: [], page: 0, size: 20, total: 0, totalPages: 0 })
        : jsonResponse({
            configured: false,
            nextRun: { answers: 0, texts: 0 },
            nextWeek: { answers: 0, texts: 0 },
            firstDiscardPending: true,
          }),
    );
    vi.stubGlobal("fetch", spy);

    await listDeletionAudits("app-1", { page: 1, size: 10 });
    const preview = await getRetentionPreview("app-1");

    expect(spy.mock.calls[0]?.[0]).toBe(
      "http://api.test/api/applications/app-1/deletion-audits?page=1&size=10",
    );
    expect(spy.mock.calls[1]?.[0]).toBe("http://api.test/api/applications/app-1/retention-preview");
    expect(preview.ok && preview.data.configured).toBe(false);
  });
});
