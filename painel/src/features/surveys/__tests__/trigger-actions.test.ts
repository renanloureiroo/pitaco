import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

const refresh = vi.fn();
vi.mock("next/cache", () => ({ refresh, revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));

function formDataOf(values: Record<string, string>): FormData {
  const formData = new FormData();
  for (const [name, value] of Object.entries(values)) {
    formData.set(name, value);
  }
  return formData;
}

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

const trigger = {
  eventName: "checkout.completed",
  windowStart: "2026-09-09T12:00:00Z",
  samplingRate: 0,
  rules: [],
};

beforeEach(() => {
  vi.stubEnv(API_URL_ENV_VAR, "http://api.test/api");
  refresh.mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("defineTriggerAction", () => {
  it("recusa nome de evento fora do padrão sem chamar a API", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    const { defineTriggerAction } = await import("../actions");

    const state = await defineTriggerAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf({ eventName: "Checkout Completed", windowStart: "2026-09-09T12:00" }),
    );

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state.status === "error" && state.fieldErrors.eventName).toBeDefined();
  });

  it("realinha a UI depois de definir o disparo", async () => {
    stubFetch(200, trigger);
    const { defineTriggerAction } = await import("../actions");

    const state = await defineTriggerAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf({ eventName: "checkout.completed", windowStart: "2026-09-09T12:00" }),
    );

    expect(state).toMatchObject({ status: "success" });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("exibe a recusa do backend preservando o formulário", async () => {
    stubFetch(400, {
      code: "trigger.window_invalid",
      detail: "Requisição inválida.",
      errors: { windowEnd: "O fim precisa ser posterior ao início." },
    });
    const { defineTriggerAction } = await import("../actions");

    const values = {
      eventName: "checkout.completed",
      windowStart: "2026-09-10T12:00",
      windowEnd: "2026-09-09T12:00",
    };
    const state = await defineTriggerAction("app-1", "srv-1", { status: "idle" }, formDataOf(values));

    expect(state).toMatchObject({
      status: "error",
      fieldErrors: { windowEnd: "O fim precisa ser posterior ao início." },
      values,
    });
    expect(refresh).not.toHaveBeenCalled();
  });
});

describe("addSegmentationRuleAction", () => {
  it("recusa equals sem valor antes de criar qualquer regra", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    const { addSegmentationRuleAction } = await import("../actions");

    const state = await addSegmentationRuleAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf({ attribute: "plano", operation: "equals" }),
    );

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state.status === "error" && state.fieldErrors.value).toMatch(/exige um valor/i);
  });

  it("recusa present com valor antes de criar qualquer regra", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    const { addSegmentationRuleAction } = await import("../actions");

    const state = await addSegmentationRuleAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf({ attribute: "plano", operation: "present", value: "pro" }),
    );

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state.status === "error" && state.fieldErrors.value).toMatch(/não aceita valor/i);
  });

  it("exibe a recusa do backend sem que nenhuma regra tenha sido criada", async () => {
    stubFetch(409, { code: "segmentation_rule.duplicated", detail: "Regra já existe." });
    const { addSegmentationRuleAction } = await import("../actions");

    const state = await addSegmentationRuleAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf({ attribute: "plano", operation: "equals", value: "pro" }),
    );

    expect(state).toMatchObject({ status: "error", message: "Regra já existe." });
    expect(refresh).not.toHaveBeenCalled();
  });
});

describe("removeSegmentationRuleAction", () => {
  it("realinha a UI depois de remover", async () => {
    stubFetch(204);
    const { removeSegmentationRuleAction } = await import("../actions");

    expect(await removeSegmentationRuleAction("app-1", "srv-1", "rule-1")).toMatchObject({
      status: "success",
    });
    expect(refresh).toHaveBeenCalledTimes(1);
  });
});
