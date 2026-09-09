import { z } from "zod";

import { absent } from "@/shared/api";

/**
 * Perguntas: leitura e regras de **forma**.
 *
 * O painel só valida antes do envio o que sabe pela forma do tipo — tipo de escolha exige ao
 * menos uma opção (FR-022). Quantidade mínima de opções, duplicidade de `value` e faixa válida
 * são invariantes do backend, e apenas exibidas quando ele recusa: replicá-las criaria duas
 * fontes de verdade que divergem em silêncio.
 */

export const questionTypeSchema = z.enum([
  "single_choice",
  "multiple_choice",
  "rating",
  "scale",
  "nps",
  "free_text",
]);

export type QuestionType = z.infer<typeof questionTypeSchema>;

export const QUESTION_TYPES = questionTypeSchema.options;

/** Tipos em que a lista de opções é exigida. */
export const CHOICE_TYPES = ["single_choice", "multiple_choice"] as const;
/** Tipos em que a faixa numérica é aceita. */
export const RANGE_TYPES = ["rating", "scale"] as const;

export function isQuestionType(value: unknown): value is QuestionType {
  return typeof value === "string" && (QUESTION_TYPES as readonly string[]).includes(value);
}

export function requiresOptions(type: QuestionType): boolean {
  return (CHOICE_TYPES as readonly QuestionType[]).includes(type);
}

export function acceptsRange(type: QuestionType): boolean {
  return (RANGE_TYPES as readonly QuestionType[]).includes(type);
}

export const questionOptionSchema = z.object({
  label: z.string(),
  value: z.string(),
});

export type QuestionOption = z.infer<typeof questionOptionSchema>;

export const questionRangeSchema = z.object({
  min: z.number(),
  max: z.number(),
});

export const questionSchema = z.object({
  id: z.string(),
  /** Chave estável: atravessa versões e nunca muda, nem ao reescrever a pergunta. */
  key: z.string(),
  statement: z.string(),
  type: questionTypeSchema,
  position: z.number(),
  required: z.boolean(),
  options: absent(z.array(questionOptionSchema)),
  range: absent(questionRangeSchema),
});

export type Question = z.infer<typeof questionSchema>;

const optionalNumberText = z.preprocess(
  (value) => (typeof value === "string" && value.trim() !== "" ? value.trim() : undefined),
  z
    .string()
    .regex(/^-?\d+$/, "Informe um número inteiro.")
    .transform((value) => Number.parseInt(value, 10))
    .optional(),
);

/**
 * Entrada do formulário de pergunta. As opções chegam como listas paralelas de rótulo e valor,
 * que é o que um `FormData` com campos repetidos produz.
 */
export const questionFormSchema = z
  .object({
    statement: z.preprocess(
      (value) => (typeof value === "string" ? value.trim() : ""),
      z.string().min(1, "Informe o enunciado da pergunta."),
    ),
    type: questionTypeSchema,
    required: z.preprocess((value) => value === "on" || value === "true", z.boolean()),
    optionLabels: z.array(z.string()).default([]),
    optionValues: z.array(z.string()).default([]),
    rangeMin: optionalNumberText,
    rangeMax: optionalNumberText,
  })
  .transform((input) => {
    const options = input.optionLabels
      .map((label, index) => {
        const trimmedLabel = label.trim();
        const trimmedValue = (input.optionValues[index] ?? "").trim();

        // Valor em branco cai no rótulo: o formulário oferece o valor como refinamento
        // opcional, não como uma segunda obrigação.
        return { label: trimmedLabel, value: trimmedValue === "" ? trimmedLabel : trimmedValue };
      })
      .filter((option) => option.label !== "" || option.value !== "");

    const range =
      input.rangeMin !== undefined && input.rangeMax !== undefined
        ? { min: input.rangeMin, max: input.rangeMax }
        : undefined;

    return {
      statement: input.statement,
      type: input.type,
      required: input.required,
      ...(requiresOptions(input.type) ? { options } : {}),
      ...(acceptsRange(input.type) && range !== undefined ? { range } : {}),
    };
  })
  .superRefine((question, ctx) => {
    if (requiresOptions(question.type) && (question.options ?? []).length === 0) {
      ctx.addIssue({
        code: "custom",
        path: ["options"],
        message: "Perguntas de escolha precisam de pelo menos uma opção.",
      });
    }

    for (const option of question.options ?? []) {
      if (option.label === "" || option.value === "") {
        ctx.addIssue({
          code: "custom",
          path: ["options"],
          message: "Toda opção precisa de rótulo e valor.",
        });
        break;
      }
    }
  });

export type QuestionFormInput = z.input<typeof questionFormSchema>;
export type QuestionFormOutput = z.output<typeof questionFormSchema>;

/** Reordenar envia a permutação **exata** dos identificadores da versão. */
export const reorderQuestionsSchema = z.object({
  questionIds: z.array(z.string()).min(1),
});
