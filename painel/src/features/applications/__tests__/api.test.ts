import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import {
  activateApplication,
  createApplication,
  deactivateApplication,
  listApplications,
  updateApplication,
} from "../api";

const summary = {
  id: "app-1",
  slug: "acme",
  name: "Acme",
  status: "active",
  createdAt: "2026-09-09T12:00:00Z",
};

function stubFetch(status: number, body: unknown) {
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

describe("listApplications", () => {
  it("envia filtro e paginação na query", async () => {
    const spy = stubFetch(200, { items: [summary], page: 1, size: 10, total: 11, totalPages: 2 });

    const result = await listApplications({ status: "active", page: 1, size: 10 });

    expect(spy.mock.calls[0][0]).toBe("http://api.test/api/applications?status=active&page=1&size=10");
    expect(result).toMatchObject({ ok: true, data: { total: 11 } });
  });

  it("omite o filtro da query quando é 'todas'", async () => {
    const spy = stubFetch(200, { items: [], page: 0, size: 20, total: 0, totalPages: 0 });

    await listApplications({ page: 0, size: 20 });

    expect(spy.mock.calls[0][0]).toBe("http://api.test/api/applications?page=0&size=20");
  });

  it("trata página vazia como sucesso, não como erro", async () => {
    stubFetch(200, { items: [], page: 0, size: 20, total: 0, totalPages: 0 });

    const result = await listApplications({ page: 0, size: 20 });

    expect(result).toMatchObject({ ok: true, data: { items: [], total: 0 } });
  });
});

describe("getApplication", () => {
  it("traduz 404 application.not_found em recusa not_found", async () => {
    stubFetch(404, { code: "application.not_found", detail: "Não existe." });
    const { getApplication } = await import("../api");

    const result = await getApplication("nao-existe");

    expect(result).toMatchObject({ ok: false, kind: "not_found", code: "application.not_found" });
  });
});

describe("createApplication", () => {
  it("envia o cadastro e devolve o identificador e o slug definitivo", async () => {
    const spy = stubFetch(201, { id: "app-9", slug: "acme-app" });

    const result = await createApplication({
      name: "Acme App",
      slug: undefined,
      quietPeriodDays: undefined,
      retentionDays: undefined,
      openTextRetentionDays: undefined,
    });

    const [, init] = spy.mock.calls[0] as unknown as [string, RequestInit];
    expect(init.method).toBe("POST");
    // Campo ausente não vai no corpo: é assim que o backend deriva o slug do nome.
    expect(JSON.parse(String(init.body))).toEqual({ name: "Acme App" });
    expect(result).toEqual({ ok: true, data: { id: "app-9", slug: "acme-app" } });
  });

  it("traduz 409 de slug em conflito em recusa exibível", async () => {
    stubFetch(409, { code: "application.slug_already_taken", detail: "Slug já em uso." });

    const result = await createApplication({
      name: "Acme",
      slug: "acme",
      quietPeriodDays: undefined,
      retentionDays: undefined,
      openTextRetentionDays: undefined,
    });

    expect(result).toMatchObject({
      ok: false,
      kind: "conflict",
      code: "application.slug_already_taken",
      detail: "Slug já em uso.",
    });
  });

  it("traduz 400 em mensagens por campo", async () => {
    stubFetch(400, {
      code: "request.invalid",
      detail: "Requisição inválida.",
      errors: { slug: "Formato inválido." },
    });

    const result = await createApplication({
      name: "Acme",
      slug: "acme",
      quietPeriodDays: undefined,
      retentionDays: undefined,
      openTextRetentionDays: undefined,
    });

    expect(result).toMatchObject({ kind: "validation", errors: { slug: "Formato inválido." } });
  });
});

const detail = {
  ...summary,
  updatedAt: "2026-09-10T12:00:00Z",
};

describe("updateApplication", () => {
  it("envia PATCH com prazo em branco como null, para que o backend o remova", async () => {
    const spy = stubFetch(200, detail);

    await updateApplication("app-1", {
      name: "Acme",
      quietPeriodDays: undefined,
      retentionDays: 30,
      openTextRetentionDays: undefined,
    });

    const [url, init] = spy.mock.calls[0];
    expect(url).toBe("http://api.test/api/applications/app-1");
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body as string)).toEqual({
      name: "Acme",
      quietPeriodDays: null,
      retentionDays: 30,
      openTextRetentionDays: null,
    });
  });

  it("devolve a aplicação alterada validada pelo schema", async () => {
    stubFetch(200, { ...detail, name: "Acme Brasil" });

    const result = await updateApplication("app-1", {
      name: "Acme Brasil",
      quietPeriodDays: undefined,
      retentionDays: undefined,
      openTextRetentionDays: undefined,
    });

    expect(result).toMatchObject({ ok: true, data: { name: "Acme Brasil" } });
  });
});

describe("deactivateApplication / activateApplication", () => {
  it("chama as transições por POST sem corpo", async () => {
    const spy = stubFetch(200, { ...detail, status: "inactive" });

    const result = await deactivateApplication("app-1");

    expect(spy.mock.calls[0][0]).toBe("http://api.test/api/applications/app-1/deactivate");
    expect(spy.mock.calls[0][1].method).toBe("POST");
    expect(spy.mock.calls[0][1].body).toBeUndefined();
    expect(result).toMatchObject({ ok: true, data: { status: "inactive" } });

    stubFetch(200, detail);
    await activateApplication("app-1");
  });

  it("traduz 404 em recusa tipada", async () => {
    stubFetch(404, { code: "application.not_found", detail: "Aplicação não encontrada." });

    const result = await activateApplication("app-x");

    expect(result).toMatchObject({ ok: false, kind: "not_found", code: "application.not_found" });
  });
});
