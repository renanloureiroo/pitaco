import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import { listRespondentDisplays, listRespondents } from "../api/respondents";

const respondent = {
  id: "rsp-1",
  identityKind: "APP_REFERENCE",
  identityValue: "user-8821",
  firstSeenAt: "2026-08-30T09:11:00Z",
  lastSeenAt: "2026-09-08T14:22:31Z",
};

const display = {
  id: "dsp-1",
  surveyId: "srv-1",
  versionId: "ver-1",
  versionNumber: 3,
  comparabilityGroup: 2,
  outcome: "COMPLETED",
  sdkVersion: null,
  openedAt: "2026-09-08T14:22:31Z",
  closedAt: "2026-09-08T14:23:07Z",
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

describe("listRespondents", () => {
  it("lista os respondentes da aplicação, paginados e sem filtro que a API não tem", async () => {
    const spy = stubFetch(200, page([respondent]));

    const result = await listRespondents("app-1", { page: 1, size: 50 });

    expect(spy.mock.calls[0][0]).toBe(
      "http://api.test/api/applications/app-1/respondents?page=1&size=50",
    );
    expect(result).toMatchObject({ ok: true, data: { items: [{ identityKind: "APP_REFERENCE" }] } });
  });

  it("aceita aplicação sem nenhum contato como página vazia, nunca erro", async () => {
    stubFetch(200, { items: [], page: 0, size: 20, total: 0, totalPages: 0 });

    expect(await listRespondents("app-1", { page: 0, size: 20 })).toMatchObject({
      ok: true,
      data: { total: 0 },
    });
  });

  it("recusa forma de identificação que o painel não sabe rotular", async () => {
    stubFetch(200, page([{ ...respondent, identityKind: "TELEPATIA" }]));

    expect(await listRespondents("app-1", { page: 0, size: 20 })).toMatchObject({
      ok: false,
      code: "response.invalid",
    });
  });
});

describe("listRespondentDisplays", () => {
  it("filtra por desfecho e período, e nunca por versão (FR-022)", async () => {
    const spy = stubFetch(200, page([display]));

    await listRespondentDisplays("app-1", "rsp-1", {
      page: 0,
      size: 20,
      outcome: "COMPLETED",
      from: "2026-09-08T00:00",
      to: "2026-09-08T23:59",
    });

    const url = String(spy.mock.calls[0][0]);

    expect(url).toContain("/applications/app-1/respondents/rsp-1/displays");
    expect(url).toContain("outcome=COMPLETED");
    expect(url).toContain("openedFrom=2026-09-08T03%3A00%3A00.000Z");
    expect(url).not.toContain("versionNumber");
    expect(url).not.toContain("versao");
  });

  it("sem filtro nenhum, manda só o recorte de página", async () => {
    const spy = stubFetch(200, page([display]));

    await listRespondentDisplays("app-1", "rsp-1", { page: 0, size: 20 });

    expect(spy.mock.calls[0][0]).toBe(
      "http://api.test/api/applications/app-1/respondents/rsp-1/displays?page=0&size=20",
    );
  });

  it("carrega a pesquisa de cada exibição — no histórico ela varia", async () => {
    stubFetch(200, page([display]));

    const result = await listRespondentDisplays("app-1", "rsp-1", { page: 0, size: 20 });

    expect(result.ok && result.data.items[0].surveyId).toBe("srv-1");
    expect(result.ok && result.data.items[0].sdkVersion).toBeUndefined();
  });

  it("recusa a exibição sem pesquisa: sem ela o histórico não teria para onde ligar", async () => {
    const { surveyId: _semPesquisa, ...semSurvey } = display;
    stubFetch(200, page([semSurvey]));

    expect(await listRespondentDisplays("app-1", "rsp-1", { page: 0, size: 20 })).toMatchObject({
      ok: false,
      code: "response.invalid",
    });
  });

  it("traduz o 404 de respondente inexistente em recusa tratável", async () => {
    stubFetch(404, { code: "respondent.not_found", detail: "Respondente não encontrado." });

    expect(await listRespondentDisplays("app-1", "rsp-1", { page: 0, size: 20 })).toMatchObject({
      ok: false,
      kind: "not_found",
    });
  });
});
