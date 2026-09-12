import { afterEach, describe, expect, it, vi } from "vitest";

import { exportHref, getSurveyResults, listOpenAnswers } from "../api/results";

const results = {
  everPublished: true,
  responseRate: {
    displayed: 5,
    completed: 2,
    dismissed: 1,
    abandoned: 1,
    inProgress: 1,
    rate: 0.4,
    definition: "Taxa = concluídas ÷ exibidas.",
    timeline: [{ day: "2026-09-01", displayed: 5, completed: 2 }],
  },
  questions: [
    {
      key: "q1",
      statement: "De 0 a 10?",
      type: "nps",
      position: 1,
      answered: 2,
      skipped: 0,
      notApplicable: 0,
      aggregate: { kind: "nps", promoters: 1, passives: 0, detractors: 1, score: 0, distribution: [] },
    },
    { key: "q2", statement: "O que achou?", type: "free_text", position: 2, answered: 0, skipped: 1, notApplicable: 0 },
  ],
  sampleSize: 2,
  smallSample: true,
  filter: { attributeAbsent: false },
  attributes: [{ name: "plano", values: [{ value: "pro", count: 3 }] }],
};

function okJson(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("leitura de resultados", () => {
  it("monta a query no formato do backend e valida a resposta", async () => {
    vi.stubEnv("PITACO_API_URL", "http://api.test/api");
    const fetchMock = vi.fn().mockResolvedValue(okJson(results));
    vi.stubGlobal("fetch", fetchMock);

    const result = await getSurveyResults("app-1", "srv-1", {
      from: "2026-09-01T00:00:00.000Z",
      attribute: "plano",
      attributeValue: "pro",
      version: 2,
    });

    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.data.questions[1].aggregate).toBeUndefined();
      expect(result.data.responseRate.rate).toBe(0.4);
    }
    const url = new URL(fetchMock.mock.calls[0][0] as string);
    expect(url.pathname).toBe("/api/applications/app-1/surveys/srv-1/results");
    expect(Object.fromEntries(url.searchParams)).toEqual({
      from: "2026-09-01T00:00:00.000Z",
      attribute: "plano",
      attributeValue: "pro",
      version: "2",
    });
  });

  it("lista respostas abertas com busca e página", async () => {
    vi.stubEnv("PITACO_API_URL", "http://api.test/api");
    const fetchMock = vi.fn().mockResolvedValue(
      okJson({ items: [], page: 1, size: 20, total: 0, totalPages: 0 }),
    );
    vi.stubGlobal("fetch", fetchMock);

    const result = await listOpenAnswers("app-1", "srv-1", { page: 1, size: 20, q: "confuso" });

    expect(result.ok).toBe(true);
    const url = new URL(fetchMock.mock.calls[0][0] as string);
    expect(url.pathname).toBe("/api/applications/app-1/surveys/srv-1/results/open-answers");
    expect(url.searchParams.get("q")).toBe("confuso");
    expect(url.searchParams.get("page")).toBe("1");
  });

  it("o endereço do export é a rota do painel com o mesmo recorte", () => {
    expect(exportHref("app-1", "srv-1", {})).toBe(
      "/api/aplicacoes/app-1/pesquisas/srv-1/resultados/export",
    );
    expect(exportHref("app-1", "srv-1", { attribute: "plano", version: 2 })).toBe(
      "/api/aplicacoes/app-1/pesquisas/srv-1/resultados/export?attribute=plano&version=2",
    );
  });
});
