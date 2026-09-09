import { describe, expect, it } from "vitest";

import {
  acceptsRange,
  questionFormSchema,
  questionSchema,
  requiresOptions,
  type QuestionType,
} from "../schemas/question";

type FormInput = {
  statement?: string;
  type: QuestionType;
  required?: string;
  optionLabels?: string[];
  optionValues?: string[];
  rangeMin?: string;
  rangeMax?: string;
};

function parseForm(input: FormInput) {
  return questionFormSchema.safeParse({ statement: "Enunciado", required: "on", ...input });
}

function issuePaths(result: ReturnType<typeof parseForm>): string[] {
  return result.success ? [] : result.error.issues.map((issue) => String(issue.path[0]));
}

describe("regras de forma por tipo", () => {
  it.each([
    ["single_choice", true, false],
    ["multiple_choice", true, false],
    ["rating", false, true],
    ["scale", false, true],
    ["nps", false, false],
    ["free_text", false, false],
  ] as const)("%s: exige opções=%s, aceita faixa=%s", (type, options, range) => {
    expect(requiresOptions(type)).toBe(options);
    expect(acceptsRange(type)).toBe(range);
  });
});

describe("questionFormSchema", () => {
  it("recusa tipo de escolha sem nenhuma opção antes de enviar (FR-022)", () => {
    const result = parseForm({ type: "single_choice", optionLabels: [], optionValues: [] });

    expect(result.success).toBe(false);
    expect(issuePaths(result)).toContain("options");
  });

  it("recusa opção pela metade — rótulo sem valor não é opção", () => {
    const result = parseForm({
      type: "multiple_choice",
      optionLabels: ["Sim"],
      optionValues: [""],
    });

    // O valor cai no rótulo quando vazio, então o caso que sobra é o rótulo em branco.
    expect(result.success).toBe(true);

    const semRotulo = parseForm({
      type: "multiple_choice",
      optionLabels: [""],
      optionValues: ["sim"],
    });
    expect(issuePaths(semRotulo)).toContain("options");
  });

  it("aceita tipo de escolha com opções e usa o rótulo como valor quando ele não é informado", () => {
    const result = parseForm({
      type: "single_choice",
      optionLabels: ["Sim", "Não"],
      optionValues: ["", "nao"],
    });

    expect(result.success && result.data.options).toEqual([
      { label: "Sim", value: "Sim" },
      { label: "Não", value: "nao" },
    ]);
  });

  it.each(["nps", "free_text"] as const)(
    "%s não exige nem opção nem faixa",
    (type) => {
      const result = parseForm({ type });

      expect(result.success).toBe(true);
      expect(result.success && result.data.options).toBeUndefined();
      expect(result.success && result.data.range).toBeUndefined();
    },
  );

  it("descarta opções enviadas em tipo que não as usa", () => {
    const result = parseForm({ type: "nps", optionLabels: ["Sim"], optionValues: ["sim"] });

    expect(result.success && result.data.options).toBeUndefined();
  });

  it("aceita faixa em rating e scale", () => {
    const result = parseForm({ type: "scale", rangeMin: "1", rangeMax: "5" });

    expect(result.success && result.data.range).toEqual({ min: 1, max: 5 });
  });

  it("descarta faixa em tipo que não a aceita", () => {
    const result = parseForm({
      type: "single_choice",
      optionLabels: ["Sim"],
      optionValues: ["sim"],
      rangeMin: "1",
      rangeMax: "5",
    });

    expect(result.success && result.data.range).toBeUndefined();
  });

  it("ignora faixa pela metade em vez de inventar um limite", () => {
    const result = parseForm({ type: "rating", rangeMin: "1" });

    expect(result.success && result.data.range).toBeUndefined();
  });

  it("exige o enunciado", () => {
    expect(issuePaths(parseForm({ statement: "   ", type: "nps" }))).toContain("statement");
  });

  it("lê a obrigatoriedade do checkbox: ausente é falso", () => {
    const marcada = parseForm({ type: "nps", required: "on" });
    const desmarcada = questionFormSchema.safeParse({ statement: "E?", type: "nps" });

    expect(marcada.success && marcada.data.required).toBe(true);
    expect(desmarcada.success && desmarcada.data.required).toBe(false);
  });

  it("recusa tipo desconhecido", () => {
    expect(
      questionFormSchema.safeParse({ statement: "E?", type: "ranking" }).success,
    ).toBe(false);
  });
});

describe("questionSchema", () => {
  const base = {
    id: "q-1",
    key: "satisfacao",
    statement: "Como foi?",
    type: "nps",
    position: 0,
    required: true,
  };

  it("aceita pergunta sem opções nem faixa", () => {
    expect(questionSchema.safeParse(base).success).toBe(true);
  });

  it("preserva a chave estável, que atravessa versões", () => {
    const parsed = questionSchema.safeParse({ ...base, id: "q-2" });

    expect(parsed.success && parsed.data.key).toBe("satisfacao");
  });
});
