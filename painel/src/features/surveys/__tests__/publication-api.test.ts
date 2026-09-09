import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import { getPublicationImpediments, publishSurvey } from "../api/publication";
import {
  discardDraftVersion,
  getVersion,
  getVersionComparability,
  listVersions,
  openDraftVersion,
} from "../api/versions";

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

describe("getPublicationImpediments", () => {
  it("desembrulha a lista de impedimentos", async () => {
    const spy = stubFetch(200, {
      impediments: [{ code: "trigger.missing" }, { code: "question.options_missing", questionKey: "p" }],
    });

    const result = await getPublicationImpediments("app-1", "srv-1");

    expect(spy.mock.calls[0][0]).toBe(`${base}/publication-impediments`);
    expect(result).toEqual({
      ok: true,
      data: [
        { code: "trigger.missing" },
        { code: "question.options_missing", questionKey: "p" },
      ],
    });
  });

  it("lista vazia é sucesso, e é o que libera a publicação", async () => {
    stubFetch(200, { impediments: [] });

    expect(await getPublicationImpediments("app-1", "srv-1")).toEqual({ ok: true, data: [] });
  });
});

describe("publishSurvey", () => {
  it("publica a versão 1 sem natureza de mudança", async () => {
    const spy = stubFetch(201, { number: 1, status: "published", comparabilityGroup: 1 });

    const result = await publishSurvey("app-1", "srv-1", {});

    expect(spy.mock.calls[0][1].method).toBe("POST");
    expect(JSON.parse(String(spy.mock.calls[0][1].body))).toEqual({});
    expect(result).toMatchObject({ ok: true, data: { number: 1 } });
  });

  it("envia natureza e resumo a partir da versão 2", async () => {
    const spy = stubFetch(201, {
      number: 2,
      status: "published",
      comparabilityGroup: 2,
      changeKind: "semantic",
    });

    await publishSurvey("app-1", "srv-1", {
      changeKind: "semantic",
      changeSummary: "Trocamos a escala",
    });

    expect(JSON.parse(String(spy.mock.calls[0][1].body))).toEqual({
      changeKind: "semantic",
      changeSummary: "Trocamos a escala",
    });
  });

  it("traduz o 409 de publicação bloqueada em recusa exibível", async () => {
    stubFetch(409, {
      code: "survey.publication_blocked",
      detail: "A pesquisa tem impedimentos.",
    });

    expect(await publishSurvey("app-1", "srv-1", {})).toMatchObject({
      kind: "conflict",
      code: "survey.publication_blocked",
    });
  });
});

describe("versões", () => {
  it("lista versões paginadas", async () => {
    const spy = stubFetch(200, { items: [], page: 0, size: 20, total: 0, totalPages: 0 });

    await listVersions("app-1", "srv-1", { page: 0, size: 20 });

    expect(spy.mock.calls[0][0]).toBe(`${base}/versions?page=0&size=20`);
  });

  it("lê o conteúdo congelado de uma versão", async () => {
    const spy = stubFetch(200, {
      number: 1,
      status: "published",
      comparabilityGroup: 1,
      questions: [],
    });

    const result = await getVersion("app-1", "srv-1", 1);

    expect(spy.mock.calls[0][0]).toBe(`${base}/versions/1`);
    expect(result).toMatchObject({ ok: true, data: { questions: [] } });
  });

  it("traduz 404 de versão inexistente", async () => {
    stubFetch(404, { code: "survey_version.not_found", detail: "Versão não encontrada." });

    expect(await getVersion("app-1", "srv-1", 99)).toMatchObject({ kind: "not_found" });
  });

  it("abre uma nova versão de rascunho", async () => {
    const spy = stubFetch(201, { number: 2, status: "draft", comparabilityGroup: 1 });

    await openDraftVersion("app-1", "srv-1");

    expect(spy.mock.calls[0][0]).toBe(`${base}/versions`);
    expect(spy.mock.calls[0][1].method).toBe("POST");
  });

  it("descarta o rascunho de versão", async () => {
    const spy = stubFetch(204);

    const result = await discardDraftVersion("app-1", "srv-1");

    expect(spy.mock.calls[0][0]).toBe(`${base}/versions/draft`);
    expect(result).toEqual({ ok: true, data: undefined });
  });

  it("lê os grupos de comparabilidade", async () => {
    stubFetch(200, { groups: [{ group: 1, versions: [1, 2] }] });

    expect(await getVersionComparability("app-1", "srv-1")).toMatchObject({
      ok: true,
      data: { groups: [{ group: 1, versions: [1, 2] }] },
    });
  });
});
