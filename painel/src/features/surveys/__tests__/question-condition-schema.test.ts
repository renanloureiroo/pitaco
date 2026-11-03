import { describe, expect, it } from "vitest";

import { questionFormSchema } from "../schemas/question";

const base = { statement: "Por quê?", type: "free_text", required: "on" };

function issuesOf(input: Record<string, unknown>): Record<string, string> {
  const parsed = questionFormSchema.safeParse(input);
  if (parsed.success) {
    return {};
  }
  return Object.fromEntries(parsed.error.issues.map((issue) => [String(issue.path[0]), issue.message]));
}

describe("questionFormSchema — condição", () => {
  it("sem origem não há condição no envio", () => {
    const parsed = questionFormSchema.parse({ ...base, conditionSourceKey: "" });

    expect(parsed).not.toHaveProperty("condition");
  });

  it("igual com um valor vira a condição do contrato", () => {
    const parsed = questionFormSchema.parse({
      ...base,
      conditionSourceKey: "gostou",
      conditionOperator: "equals",
      conditionValues: [" no "],
    });

    expect(parsed.condition).toEqual({ sourceKey: "gostou", operator: "equals", values: ["no"] });
  });

  it("faixa leva mínimo e máximo, e nenhum valor", () => {
    const parsed = questionFormSchema.parse({
      ...base,
      conditionSourceKey: "nota",
      conditionOperator: "between",
      conditionValues: ["7"],
      conditionMin: "0",
      conditionMax: "6",
    });

    expect(parsed.condition).toEqual({
      sourceKey: "nota",
      operator: "between",
      values: [],
      min: 0,
      max: 6,
    });
  });

  it("aponta cada falta de forma no campo que a causou", () => {
    expect(issuesOf({ ...base, conditionSourceKey: "nota" })).toEqual({
      "condition.operator": "Escolha como comparar a resposta.",
    });
    expect(issuesOf({ ...base, conditionSourceKey: "nota", conditionOperator: "in" })).toEqual({
      "condition.values": "Escolha ao menos um valor para comparar.",
    });
    expect(
      issuesOf({ ...base, conditionSourceKey: "nota", conditionOperator: "between", conditionMin: "0" }),
    ).toEqual({ "condition.values": "Informe o mínimo e o máximo da faixa." });
    expect(
      issuesOf({
        ...base,
        conditionSourceKey: "gostou",
        conditionOperator: "equals",
        conditionValues: ["yes", "no"],
      }),
    ).toEqual({ "condition.values": "Igual e diferente comparam com um valor só." });
  });

  it("valor fora da escala não é recusado aqui: a regra é do backend", () => {
    const parsed = questionFormSchema.safeParse({
      ...base,
      conditionSourceKey: "nota",
      conditionOperator: "between",
      conditionMin: "0",
      conditionMax: "11",
    });

    expect(parsed.success).toBe(true);
  });
});

describe("questionFormSchema — rótulos da escala", () => {
  it("avaliação leva os rótulos junto da faixa", () => {
    const parsed = questionFormSchema.parse({
      statement: "Avalie",
      type: "rating",
      required: "on",
      rangeMin: "1",
      rangeMax: "5",
      rangeMinLabel: " Péssimo ",
      rangeMaxLabel: "",
    });

    expect(parsed.range).toEqual({ min: 1, max: 5, minLabel: "Péssimo" });
  });

  it("NPS só envia faixa quando há rótulo, e sempre 0 a 10", () => {
    expect(questionFormSchema.parse({ statement: "Nota", type: "nps", required: "on" })).not.toHaveProperty(
      "range",
    );
    expect(
      questionFormSchema.parse({
        statement: "Nota",
        type: "nps",
        required: "on",
        rangeMaxLabel: "Extremamente provável",
      }).range,
    ).toEqual({ min: 0, max: 10, maxLabel: "Extremamente provável" });
  });

  it("rótulo acima de 60 caracteres é recusado", () => {
    expect(
      issuesOf({
        statement: "Avalie",
        type: "rating",
        required: "on",
        rangeMin: "1",
        rangeMax: "5",
        rangeMinLabel: "x".repeat(61),
      }),
    ).toEqual({ rangeMinLabel: "O rótulo pode ter no máximo 60 caracteres." });
  });

  it("texto livre ignora rótulo que tenha vindo no formulário", () => {
    const parsed = questionFormSchema.parse({ ...base, rangeMinLabel: "Solto" });

    expect(parsed).not.toHaveProperty("range");
  });
});
