import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import { addSegmentationRule, defineTrigger, removeSegmentationRule } from "../api/trigger";

const trigger = {
  eventName: "checkout.completed",
  windowStart: "2026-09-09T12:00:00Z",
  samplingRate: 0.5,
  rules: [],
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

const base = "http://api.test/api/applications/app-1/surveys/srv-1/trigger";

describe("defineTrigger", () => {
  it("usa PUT — redefinir substitui, não duplica (FR-025)", async () => {
    const spy = stubFetch(200, trigger);

    await defineTrigger("app-1", "srv-1", {
      eventName: "checkout.completed",
      windowStart: "2026-09-09T12:00",
      windowEnd: undefined,
      samplingRate: 0.5,
    });

    expect(spy.mock.calls[0][0]).toBe(base);
    expect(spy.mock.calls[0][1].method).toBe("PUT");
  });

  it("omite o fim da janela quando ela é aberta", async () => {
    const spy = stubFetch(200, trigger);

    await defineTrigger("app-1", "srv-1", {
      eventName: "app_open",
      windowStart: "2026-09-09T12:00",
      windowEnd: undefined,
      samplingRate: 0,
    });

    expect(JSON.parse(String(spy.mock.calls[0][1].body))).toEqual({
      eventName: "app_open",
      windowStart: "2026-09-09T12:00",
      samplingRate: 0,
    });
  });

  it("exibe a recusa de janela inválida vinda do backend", async () => {
    stubFetch(400, {
      code: "trigger.window_invalid",
      detail: "Requisição inválida.",
      errors: { windowEnd: "O fim precisa ser posterior ao início." },
    });

    const result = await defineTrigger("app-1", "srv-1", {
      eventName: "app_open",
      windowStart: "2026-09-10T12:00",
      windowEnd: "2026-09-09T12:00",
      samplingRate: 0,
    });

    expect(result).toMatchObject({
      kind: "validation",
      errors: { windowEnd: "O fim precisa ser posterior ao início." },
    });
  });
});

describe("addSegmentationRule", () => {
  it("envia a regra por POST", async () => {
    const spy = stubFetch(201, {
      id: "rule-1",
      attribute: "plano",
      operation: "equals",
      value: "pro",
    });

    await addSegmentationRule("app-1", "srv-1", {
      attribute: "plano",
      operation: "equals",
      value: "pro",
    });

    expect(spy.mock.calls[0][0]).toBe(`${base}/rules`);
    expect(spy.mock.calls[0][1].method).toBe("POST");
  });

  it("omite o valor nas operações que não o aceitam", async () => {
    const spy = stubFetch(201, { id: "rule-2", attribute: "plano", operation: "present" });

    await addSegmentationRule("app-1", "srv-1", {
      attribute: "plano",
      operation: "present",
      value: undefined,
    });

    expect(JSON.parse(String(spy.mock.calls[0][1].body))).toEqual({
      attribute: "plano",
      operation: "present",
    });
  });

  it("exibe a recusa de combinação inválida do backend", async () => {
    stubFetch(400, {
      code: "segmentation_rule.invalid",
      detail: "Requisição inválida.",
      errors: { value: "Esta operação não aceita valor." },
    });

    const result = await addSegmentationRule("app-1", "srv-1", {
      attribute: "plano",
      operation: "present",
      value: "pro",
    });

    expect(result).toMatchObject({ kind: "validation", errors: { value: expect.any(String) } });
  });
});

describe("removeSegmentationRule", () => {
  it("aceita o 204 da remoção", async () => {
    const spy = stubFetch(204);

    const result = await removeSegmentationRule("app-1", "srv-1", "rule-1");

    expect(spy.mock.calls[0][0]).toBe(`${base}/rules/rule-1`);
    expect(result).toEqual({ ok: true, data: undefined });
  });
});
