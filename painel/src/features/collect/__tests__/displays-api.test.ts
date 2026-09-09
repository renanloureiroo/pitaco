import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import { getDisplay, listSurveyDisplays } from "../api/displays";

const summary = {
  id: "dsp-1",
  versionId: "ver-1",
  versionNumber: 3,
  comparabilityGroup: 2,
  outcome: "COMPLETED",
  sdkVersion: "1.4.0",
  openedAt: "2026-09-08T14:22:31Z",
  closedAt: "2026-09-08T14:23:07Z",
};

const detail = {
  ...summary,
  respondentId: "rsp-1",
  surveyId: "srv-1",
  attributes: { plano: "pro" },
  answers: [
    { questionKey: "nota", status: "ANSWERED", text: null, number: 9, options: [] },
  ],
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

function page(items: unknown[]) {
  return { items, page: 0, size: 20, total: items.length, totalPages: 1 };
}

beforeEach(() => {
  vi.stubEnv(API_URL_ENV_VAR, "http://api.test/api");
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("listSurveyDisplays", () => {
  it("monta a query com o número da versão, o desfecho e o período em UTC", async () => {
    const spy = stubFetch(200, page([summary]));

    await listSurveyDisplays("app-1", "srv-1", {
      page: 0,
      size: 20,
      versionNumber: 3,
      outcome: "DISMISSED",
      from: "2026-09-08T00:00",
      to: "2026-09-08T23:59",
    });

    expect(spy.mock.calls[0][0]).toBe(
      "http://api.test/api/applications/app-1/surveys/srv-1/displays" +
        "?versionNumber=3&outcome=DISMISSED" +
        "&openedFrom=2026-09-08T03%3A00%3A00.000Z&openedTo=2026-09-09T02%3A59%3A00.000Z" +
        "&page=0&size=20",
    );
  });

  it("filtro ausente não vira parâmetro — ausente não restringe", async () => {
    const spy = stubFetch(200, page([]));

    await listSurveyDisplays("app-1", "srv-1", { page: 0, size: 20 });

    expect(spy.mock.calls[0][0]).toBe(
      "http://api.test/api/applications/app-1/surveys/srv-1/displays?page=0&size=20",
    );
  });

  it("escapa identificadores no caminho, em vez de montar URL quebrada", async () => {
    const spy = stubFetch(200, page([]));

    await listSurveyDisplays("app/1", "srv 1", { page: 0, size: 20 });

    expect(spy.mock.calls[0][0]).toContain("/applications/app%2F1/surveys/srv%201/displays");
  });

  it("valida o corpo: página fora do contrato vira recusa, não tela com dado inventado", async () => {
    stubFetch(200, { items: [{ id: "dsp-1" }], page: 0, size: 20, total: 1, totalPages: 1 });

    const result = await listSurveyDisplays("app-1", "srv-1", { page: 0, size: 20 });

    expect(result).toMatchObject({ ok: false, code: "response.invalid" });
  });

  it("número de versão sem exibição alguma é página vazia, nunca 404", async () => {
    stubFetch(200, { items: [], page: 0, size: 20, total: 0, totalPages: 0 });

    const result = await listSurveyDisplays("app-1", "srv-1", { page: 0, size: 20, versionNumber: 99 });

    expect(result).toMatchObject({ ok: true, data: { items: [], total: 0 } });
  });

  it("traduz o 404 de pesquisa inexistente em recusa que a tela sabe tratar", async () => {
    stubFetch(404, { code: "survey.not_found", detail: "Pesquisa não encontrada." });

    const result = await listSurveyDisplays("app-1", "srv-1", { page: 0, size: 20 });

    expect(result).toMatchObject({ ok: false, kind: "not_found", code: "survey.not_found" });
  });
});

describe("campos ausentes na fronteira", () => {
  it("aceita o campo omitido e o campo nulo, e devolve os dois como undefined", async () => {
    stubFetch(
      200,
      page([
        { ...summary, sdkVersion: null, closedAt: null, outcome: "STARTED" },
        { id: "dsp-2", versionId: "ver-1", versionNumber: 1, comparabilityGroup: 1, outcome: "STARTED", openedAt: "2026-09-08T10:00:00Z" },
      ]),
    );

    const result = await listSurveyDisplays("app-1", "srv-1", { page: 0, size: 20 });

    expect(result.ok).toBe(true);
    if (result.ok) {
      for (const item of result.data.items) {
        expect(item.closedAt).toBeUndefined();
        expect(item.sdkVersion).toBeUndefined();
        // Ausência é ausência: nunca `null`, nunca string vazia, nunca zero.
        expect(item.closedAt).not.toBeNull();
      }
    }
  });
});

describe("getDisplay", () => {
  it("lê o detalhe pela rota plana sob a aplicação (R5)", async () => {
    const spy = stubFetch(200, detail);

    const result = await getDisplay("app-1", "dsp-1");

    expect(spy.mock.calls[0][0]).toBe("http://api.test/api/applications/app-1/displays/dsp-1");
    expect(result).toMatchObject({ ok: true, data: { respondentId: "rsp-1", surveyId: "srv-1" } });
  });

  it("aceita instantâneo de atributos vazio como ausência exibível, não como erro", async () => {
    stubFetch(200, { ...detail, attributes: {}, answers: [] });

    const result = await getDisplay("app-1", "dsp-1");

    expect(result).toMatchObject({ ok: true, data: { attributes: {}, answers: [] } });
  });

  it("traduz o 404 de exibição de outra aplicação na mesma recusa de inexistente", async () => {
    stubFetch(404, { code: "display.not_found", detail: "Exibição não encontrada." });

    const result = await getDisplay("app-1", "dsp-1");

    expect(result).toMatchObject({ ok: false, kind: "not_found" });
  });

  it("preserva o zero de uma resposta numérica, que é valor e não ausência", async () => {
    stubFetch(200, {
      ...detail,
      answers: [{ questionKey: "nota", status: "ANSWERED", number: 0, options: [] }],
    });

    const result = await getDisplay("app-1", "dsp-1");

    expect(result.ok && result.data.answers[0].number).toBe(0);
  });
});
