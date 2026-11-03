import { z } from "zod";

import { absent } from "@/shared/api";

/**
 * Condição de exibição: a pergunta só aparece quando a resposta a uma anterior satisfaz a regra.
 *
 * O painel valida só a forma — que a origem foi escolhida e que o operador tem o que comparar.
 * Se o valor cabe nas opções ou na escala da origem é invariante do backend, e a recusa dele
 * volta com o `field` que o formulário usa para mostrar o erro junto do controle certo.
 */

export const conditionOperatorSchema = z.enum(["equals", "not_equals", "in", "between"]);
export type ConditionOperator = z.infer<typeof conditionOperatorSchema>;
export const CONDITION_OPERATORS = conditionOperatorSchema.options;

export const conditionSchema = z.object({
  sourceKey: z.string(),
  operator: conditionOperatorSchema,
  values: z.array(z.string()).default([]),
  min: absent(z.number()),
  max: absent(z.number()),
});

export type Condition = z.infer<typeof conditionSchema>;

/** Nomes de campo que o backend devolve em `field`, e que o formulário usa como chave de erro. */
export const CONDITION_FIELDS = {
  source: "condition.sourceKey",
  operator: "condition.operator",
  values: "condition.values",
  whole: "condition",
} as const;
