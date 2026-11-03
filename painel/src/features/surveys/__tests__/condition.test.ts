import { describe, expect, it } from "vitest";

import {
  conditionSources,
  describeCondition,
  operatorsFor,
  scaleOf,
} from "../lib/condition";
import type { Question } from "../schemas/question";

const escolha: Question = {
  id: "q-1",
  key: "gostou",
  statement: "Gostou?",
  type: "single_choice",
  position: 1,
  required: true,
  options: [
    { label: "Sim", value: "yes" },
    { label: "Não", value: "no" },
  ],
};

const nota: Question = {
  id: "q-2",
  key: "nota",
  statement: "De 0 a 10?",
  type: "nps",
  position: 2,
  required: true,
  range: { min: 0, max: 10 },
};

const texto: Question = {
  id: "q-3",
  key: "comente",
  statement: "Comente",
  type: "free_text",
  position: 3,
  required: false,
};

const canais: Question = {
  id: "q-4",
  key: "canais",
  statement: "Canais",
  type: "multiple_choice",
  position: 4,
  required: false,
  options: [
    { label: "E-mail", value: "email" },
    { label: "Chat", value: "chat" },
  ],
};

const perguntas = [escolha, nota, texto, canais];

describe("conditionSources", () => {
  it("oferece só as anteriores, sem texto livre e sem a própria pergunta", () => {
    expect(conditionSources(perguntas, canais).map((question) => question.key)).toEqual([
      "gostou",
      "nota",
    ]);
    expect(conditionSources(perguntas, escolha)).toEqual([]);
  });

  it("para uma pergunta nova, que entra no fim, todas as existentes servem", () => {
    expect(conditionSources(perguntas).map((question) => question.key)).toEqual([
      "gostou",
      "nota",
      "canais",
    ]);
  });
});

describe("operatorsFor e scaleOf", () => {
  it("faixa só nas escalas", () => {
    expect(operatorsFor("nps")).toContain("between");
    expect(operatorsFor("single_choice")).not.toContain("between");
  });

  it("o NPS tem a escala fixa de 0 a 10", () => {
    expect(scaleOf({ ...nota, range: undefined })).toEqual({ min: 0, max: 10 });
  });
});

describe("describeCondition", () => {
  it("faixa numérica pela posição da origem", () => {
    expect(describeCondition({ sourceKey: "nota", operator: "between", values: [], min: 0, max: 6 }, perguntas)).toBe(
      "Exibida se P2 for de 0 a 6",
    );
  });

  it("escolha única pelo rótulo da opção, em igual e diferente", () => {
    expect(describeCondition({ sourceKey: "gostou", operator: "equals", values: ["no"] }, perguntas)).toBe(
      'Exibida se P1 for "Não"',
    );
    expect(describeCondition({ sourceKey: "gostou", operator: "not_equals", values: ["no"] }, perguntas)).toBe(
      'Exibida se P1 não for "Não"',
    );
  });

  it("múltipla escolha fala em incluir", () => {
    expect(describeCondition({ sourceKey: "canais", operator: "in", values: ["email", "chat"] }, perguntas)).toBe(
      'Exibida se P4 incluir "E-mail" ou "Chat"',
    );
  });

  it("conjunto numérico sem aspas", () => {
    expect(describeCondition({ sourceKey: "nota", operator: "in", values: ["9", "10"] }, perguntas)).toBe(
      "Exibida se P2 for 9 ou 10",
    );
  });

  it("origem que sumiu da lista não quebra o texto", () => {
    expect(describeCondition({ sourceKey: "outra", operator: "equals", values: ["x"] }, perguntas)).toBe(
      'Exibida se a pergunta de origem for "x"',
    );
  });
});
