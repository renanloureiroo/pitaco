import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import {
  addQuestion,
  movedOrder,
  removeQuestion,
  reorderQuestions,
  updateQuestion,
} from "../api/questions";
import type { QuestionFormOutput } from "../schemas/question";

const question = {
  id: "q-1",
  key: "satisfacao",
  statement: "Como foi?",
  type: "nps",
  position: 0,
  required: true,
};

const form: QuestionFormOutput = { statement: "Como foi?", type: "nps", required: true };

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

const base = "http://api.test/api/applications/app-1/surveys/srv-1/questions";

describe("addQuestion", () => {
  it("envia a pergunta por POST", async () => {
    const spy = stubFetch(201, question);

    await addQuestion("app-1", "srv-1", form);

    expect(spy.mock.calls[0][0]).toBe(base);
    expect(spy.mock.calls[0][1].method).toBe("POST");
    expect(JSON.parse(String(spy.mock.calls[0][1].body))).toEqual(form);
  });

  it("expõe a recusa de conteúdo do backend em vez de escondê-la", async () => {
    stubFetch(400, {
      code: "question.options_invalid",
      detail: "Opções duplicadas.",
      errors: { options: "Valores repetidos." },
    });

    const result = await addQuestion("app-1", "srv-1", form);

    expect(result).toMatchObject({ kind: "validation", errors: { options: "Valores repetidos." } });
  });
});

describe("updateQuestion", () => {
  it("reescreve a pergunta por PUT no identificador", async () => {
    const spy = stubFetch(200, question);

    await updateQuestion("app-1", "srv-1", "q-1", form);

    expect(spy.mock.calls[0][0]).toBe(`${base}/q-1`);
    expect(spy.mock.calls[0][1].method).toBe("PUT");
  });
});

describe("removeQuestion", () => {
  it("aceita o 204 da remoção", async () => {
    stubFetch(204);

    await expect(removeQuestion("app-1", "srv-1", "q-1")).resolves.toEqual({
      ok: true,
      data: undefined,
    });
  });
});

describe("reorderQuestions", () => {
  it("envia a permutação completa dos identificadores, não um subconjunto", async () => {
    const spy = stubFetch(200, [question]);

    await reorderQuestions("app-1", "srv-1", ["q-2", "q-1", "q-3"]);

    expect(spy.mock.calls[0][0]).toBe(`${base}/order`);
    expect(spy.mock.calls[0][1].method).toBe("PUT");
    expect(JSON.parse(String(spy.mock.calls[0][1].body))).toEqual({
      questionIds: ["q-2", "q-1", "q-3"],
    });
  });

  it("traduz a recusa de permutação incompleta do backend", async () => {
    stubFetch(400, { code: "question.order_invalid", detail: "Permutação incompleta." });

    const result = await reorderQuestions("app-1", "srv-1", ["q-1"]);

    expect(result).toMatchObject({ kind: "validation", code: "question.order_invalid" });
  });
});

describe("movedOrder", () => {
  const ids = ["q-1", "q-2", "q-3"];

  it("troca com o vizinho e devolve a lista inteira", () => {
    expect(movedOrder(ids, "q-2", -1)).toEqual(["q-2", "q-1", "q-3"]);
    expect(movedOrder(ids, "q-2", 1)).toEqual(["q-1", "q-3", "q-2"]);
  });

  it("não mexe em nada nos extremos", () => {
    expect(movedOrder(ids, "q-1", -1)).toEqual(ids);
    expect(movedOrder(ids, "q-3", 1)).toEqual(ids);
  });

  it("preserva o comprimento — mover nunca perde pergunta", () => {
    expect(movedOrder(ids, "q-3", -1)).toHaveLength(3);
    expect(new Set(movedOrder(ids, "q-3", -1))).toEqual(new Set(ids));
  });

  it("ignora identificador que não está na lista", () => {
    expect(movedOrder(ids, "q-9", -1)).toEqual(ids);
  });
});
