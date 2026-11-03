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

describe("createSurveyAction", () => {
  it("leva à montagem da pesquisa criada", async () => {
    stubFetch(201, {
      id: "srv-9",
      applicationId: "app-1",
      name: "Satisfação",
      state: "draft",
      createdAt: "2026-09-09T12:00:00Z",
    });
    const { createSurveyAction } = await import("../actions");

    await expect(
      createSurveyAction("app-1", { status: "idle" }, formDataOf([["name", "Satisfação"]])),
    ).rejects.toThrow("NEXT_REDIRECT:/aplicacoes/app-1/pesquisas/srv-9");
  });

  it("exibe a recusa sem perder o que foi digitado", async () => {
    stubFetch(409, { code: "survey.name_taken", detail: "Já existe uma pesquisa com esse nome." });
    const { createSurveyAction } = await import("../actions");

    const state = await createSurveyAction(
      "app-1",
      { status: "idle" },
      formDataOf([["name", "Satisfação"]]),
    );

    expect(state).toMatchObject({
      status: "error",
      message: "Já existe uma pesquisa com esse nome.",
      values: { name: "Satisfação" },
    });
  });

  it("recusa nome em branco antes de chamar a API", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    const { createSurveyAction } = await import("../actions");

    const state = await createSurveyAction("app-1", { status: "idle" }, formDataOf([["name", "  "]]));

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state.status === "error" && state.fieldErrors.name).toMatch(/informe o nome/i);
  });
});

describe("discardSurveyAction", () => {
  it("volta para a listagem após descartar", async () => {
    stubFetch(204);
    const { discardSurveyAction } = await import("../actions");

    await expect(discardSurveyAction("app-1", "srv-1")).rejects.toThrow(
      "NEXT_REDIRECT:/aplicacoes/app-1/pesquisas",
    );
    expect(revalidatePath).toHaveBeenCalledWith("/aplicacoes/app-1/pesquisas");
  });

  it("exibe o 409 de pesquisa já publicada em vez de navegar", async () => {
    stubFetch(409, { code: "survey.already_published", detail: "Já publicada." });
    const { discardSurveyAction } = await import("../actions");

    const state = await discardSurveyAction("app-1", "srv-1");

    expect(state).toMatchObject({ status: "error", message: "Já publicada." });
    expect(redirect).not.toHaveBeenCalled();
  });
});

describe("addQuestionAction", () => {
  it("recusa tipo de escolha sem opção antes de chamar a API", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    const { addQuestionAction } = await import("../actions");

    const state = await addQuestionAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf([
        ["statement", "Qual sua preferida?"],
        ["type", "single_choice"],
      ]),
    );

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state.status === "error" && state.fieldErrors.options).toMatch(/pelo menos uma opção/i);
  });

  it("monta as opções a partir dos campos repetidos e realinha a UI ao concluir", async () => {
    const spy = stubFetch(201, {
      id: "q-1",
      key: "preferida",
      statement: "Qual sua preferida?",
      type: "single_choice",
      position: 0,
      required: true,
      options: [{ label: "A", value: "a" }],
    });
    const { addQuestionAction } = await import("../actions");

    const state = await addQuestionAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf([
        ["statement", "Qual sua preferida?"],
        ["type", "single_choice"],
        ["required", "on"],
        ["optionLabels", "A"],
        ["optionValues", "a"],
        ["optionLabels", "B"],
        ["optionValues", "b"],
      ]),
    );

    expect(JSON.parse(String(spy.mock.calls[0][1].body))).toEqual({
      statement: "Qual sua preferida?",
      type: "single_choice",
      required: true,
      options: [
        { label: "A", value: "a" },
        { label: "B", value: "b" },
      ],
    });
    expect(state).toMatchObject({ status: "success" });
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("devolve a recusa do backend sem perder o formulário", async () => {
    stubFetch(400, {
      code: "question.options_invalid",
      detail: "Requisição inválida.",
      errors: { options: "Valores repetidos." },
    });
    const { addQuestionAction } = await import("../actions");

    const state = await addQuestionAction(
      "app-1",
      "srv-1",
      { status: "idle" },
      formDataOf([
        ["statement", "Qual?"],
        ["type", "single_choice"],
        ["optionLabels", "A"],
        ["optionValues", "a"],
      ]),
    );

    expect(state).toMatchObject({
      status: "error",
      fieldErrors: { options: "Valores repetidos." },
      values: { statement: "Qual?" },
    });
    expect(refresh).not.toHaveBeenCalled();
  });
});

describe("moveQuestionAction", () => {
  it("envia a permutação completa, e não apenas as perguntas movidas", async () => {
    const spy = stubFetch(200, []);
    const { moveQuestionAction } = await import("../actions");

    await moveQuestionAction("app-1", "srv-1", ["q-1", "q-2", "q-3"], "q-3", -1);

    expect(JSON.parse(String(spy.mock.calls[0][1].body))).toEqual({
      questionIds: ["q-1", "q-3", "q-2"],
    });
  });

  it("não chama a API quando o movimento não é possível", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    const { moveQuestionAction } = await import("../actions");

    const state = await moveQuestionAction("app-1", "srv-1", ["q-1", "q-2"], "q-1", -1);

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(state).toMatchObject({ status: "success" });
  });

  it("exibe a recusa da reordenação sem realinhar a UI", async () => {
    stubFetch(400, { code: "question.order_invalid", detail: "Permutação incompleta." });
    const { moveQuestionAction } = await import("../actions");

    const state = await moveQuestionAction("app-1", "srv-1", ["q-1", "q-2"], "q-1", 1);

    expect(state).toMatchObject({ status: "error", message: "Permutação incompleta." });
    expect(refresh).not.toHaveBeenCalled();
  });
});

describe("removeQuestionAction", () => {
  it("realinha a UI depois de remover", async () => {
    stubFetch(204);
    const { removeQuestionAction } = await import("../actions");

    const state = await removeQuestionAction("app-1", "srv-1", "q-1");

    expect(state).toMatchObject({ status: "success" });
    expect(refresh).toHaveBeenCalledTimes(1);
  });
});
