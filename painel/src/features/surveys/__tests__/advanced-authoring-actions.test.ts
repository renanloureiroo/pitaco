import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

const redirect = vi.fn((path: string) => {
  throw new Error(`NEXT_REDIRECT:${path}`);
});
const refresh = vi.fn();
const revalidatePath = vi.fn();

vi.mock("next/navigation", () => ({ redirect }));
vi.mock("next/cache", () => ({ refresh, revalidatePath }));

function formDataOf(entries: Array<[string, string]>): FormData {
  const formData = new FormData();
  for (const [key, value] of entries) {
    formData.append(key, value);
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

function sentBody(spy: ReturnType<typeof stubFetch>): unknown {
  const [, init] = spy.mock.calls[0];
  return JSON.parse(String(init.body));
}

const created = {
  id: "srv-9",
  applicationId: "app-1",
  name: "NPS",
  state: "draft",
  createdAt: "2026-09-12T12:00:00Z",
};

beforeEach(() => {
  vi.stubEnv(API_URL_ENV_VAR, "http://api.test/api");
  redirect.mockClear();
  refresh.mockClear();
  revalidatePath.mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("createSurveyAction com modelo", () => {
  it("envia o modelo escolhido", async () => {
    const spy = stubFetch(201, { ...created, templateKind: "nps" });
    const { createSurveyAction } = await import("../actions");

    await expect(
      createSurveyAction(
        "app-1",
        { status: "idle" },
        formDataOf([
          ["name", "NPS"],
          ["template", "nps"],
        ]),
      ),
    ).rejects.toThrow("NEXT_REDIRECT:/aplicacoes/app-1/pesquisas/srv-9");

    expect(sentBody(spy)).toEqual({ name: "NPS", template: "nps" });
  });

  it("em branco envia só o nome", async () => {
    const spy = stubFetch(201, created);
    const { createSurveyAction } = await import("../actions");

    await expect(
      createSurveyAction(
        "app-1",
        { status: "idle" },
        formDataOf([
          ["name", "NPS"],
          ["template", "blank"],
        ]),
      ),
    ).rejects.toThrow("NEXT_REDIRECT");

    expect(sentBody(spy)).toEqual({ name: "NPS" });
  });
});

describe("duplicateSurveyAction", () => {
  it("leva à montagem da cópia, sob a aplicação de destino", async () => {
    const spy = stubFetch(201, { ...created, id: "srv-2", applicationId: "app-2", name: "Cópia de NPS" });
    const { duplicateSurveyAction } = await import("../actions");

    await expect(
      duplicateSurveyAction(
        "app-1",
        "srv-1",
        { status: "idle" },
        formDataOf([
          ["targetApplicationId", "app-2"],
          ["name", " "],
        ]),
      ),
    ).rejects.toThrow("NEXT_REDIRECT:/aplicacoes/app-2/pesquisas/srv-2");

    const [url] = spy.mock.calls[0];
    expect(url).toBe("http://api.test/api/applications/app-1/surveys/srv-1/duplicate");
    expect(sentBody(spy)).toEqual({ targetApplicationId: "app-2" });
    expect(revalidatePath).toHaveBeenCalledWith("/aplicacoes/app-2/pesquisas");
  });

  it("recusa do backend vira mensagem e não redireciona", async () => {
    stubFetch(404, { code: "application.not_found", detail: "Aplicação não encontrada." });
    const { duplicateSurveyAction } = await import("../actions");

    const state = await duplicateSurveyAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf([["targetApplicationId", "app-x"]]),
    );

    expect(state).toMatchObject({ status: "error", message: "Aplicação não encontrada." });
    expect(redirect).not.toHaveBeenCalled();
  });

  it("sem destino recusa antes de falar com a API", async () => {
    const spy = stubFetch(201, created);
    const { duplicateSurveyAction } = await import("../actions");

    const state = await duplicateSurveyAction("app-1", "srv-1", { status: "idle" }, formDataOf([]));

    expect(state).toMatchObject({
      status: "error",
      fieldErrors: { targetApplicationId: "Escolha a aplicação que recebe a cópia." },
    });
    expect(spy).not.toHaveBeenCalled();
  });
});

describe("addQuestionAction com condição", () => {
  it("envia a condição montada e mostra a recusa de regra junto do campo", async () => {
    const detail = "Todo valor da condição precisa caber na escala da origem";
    const spy = stubFetch(422, {
      code: "question.condition_value_invalid",
      detail,
      field: "condition.values",
    });
    const { addQuestionAction } = await import("../actions");

    const state = await addQuestionAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf([
        ["statement", "Por quê?"],
        ["type", "free_text"],
        ["required", "on"],
        ["conditionSourceKey", "nota"],
        ["conditionOperator", "between"],
        ["conditionMin", "0"],
        ["conditionMax", "11"],
      ]),
    );

    expect(sentBody(spy)).toEqual({
      statement: "Por quê?",
      type: "free_text",
      required: true,
      condition: { sourceKey: "nota", operator: "between", values: [], min: 0, max: 11 },
    });
    expect(state).toMatchObject({ status: "error", fieldErrors: { "condition.values": detail } });
    expect(refresh).not.toHaveBeenCalled();
  });
});
