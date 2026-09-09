import { z } from "zod";

import { absent } from "@/shared/api";

/**
 * Disparo e regras de segmentação.
 *
 * Duas regras de **forma** que o painel conhece: `eventName` segue um padrão fixo, e `value` é
 * exigido em `equals`/`not_equals` e recusado em `present`/`absent`. Que `windowEnd` seja
 * posterior a `windowStart` é invariante do backend — aparece como impedimento
 * `trigger.window_invalid`, e o painel não bloqueia por conta própria.
 */

export const EVENT_NAME_PATTERN = /^[a-z][a-z0-9_.]{1,79}$/;

export const ruleOperationSchema = z.enum(["equals", "not_equals", "present", "absent"]);
export type RuleOperation = z.infer<typeof ruleOperationSchema>;
export const RULE_OPERATIONS = ruleOperationSchema.options;

/** Operações em que o valor é exigido; nas demais, o campo nem aparece. */
export const VALUED_OPERATIONS = ["equals", "not_equals"] as const;

export function requiresValue(operation: RuleOperation): boolean {
  return (VALUED_OPERATIONS as readonly RuleOperation[]).includes(operation);
}

export const segmentationRuleSchema = z.object({
  id: z.string(),
  attribute: z.string(),
  operation: ruleOperationSchema,
  value: absent(z.string()),
});

export type SegmentationRule = z.infer<typeof segmentationRuleSchema>;

export const triggerSchema = z.object({
  eventName: z.string(),
  windowStart: z.string(),
  windowEnd: absent(z.string()),
  samplingRate: z.number(),
  rules: z.array(segmentationRuleSchema).default([]),
});

export type Trigger = z.infer<typeof triggerSchema>;

const optionalText = z.preprocess(
  (value) => (typeof value === "string" && value.trim() !== "" ? value.trim() : undefined),
  z.string().optional(),
);

export const triggerFormSchema = z.object({
  eventName: z.preprocess(
    (value) => (typeof value === "string" ? value.trim() : ""),
    z
      .string()
      .min(1, "Informe o nome do evento.")
      .regex(
        EVENT_NAME_PATTERN,
        "Comece por letra minúscula e use apenas letras minúsculas, números, ponto e sublinhado.",
      ),
  ),
  windowStart: z.preprocess(
    (value) => (typeof value === "string" ? value.trim() : ""),
    z.string().min(1, "Informe o início da janela."),
  ),
  windowEnd: optionalText,
  /** Ausente no envio significa `0.0` — o backend trata a ausência assim, e o painel também. */
  samplingRate: z.preprocess(
    (value) => (typeof value === "string" && value.trim() !== "" ? value.trim() : "0"),
    z.coerce
      .number({ error: "Informe um número entre 0 e 1." })
      .min(0, "A taxa vai de 0 a 1.")
      .max(1, "A taxa vai de 0 a 1."),
  ),
});

export type TriggerForm = z.infer<typeof triggerFormSchema>;

export const segmentationRuleFormSchema = z
  .object({
    attribute: z.preprocess(
      (value) => (typeof value === "string" ? value.trim() : ""),
      z.string().min(1, "Informe o atributo."),
    ),
    operation: ruleOperationSchema,
    value: optionalText,
  })
  .superRefine((rule, ctx) => {
    if (requiresValue(rule.operation) && rule.value === undefined) {
      ctx.addIssue({
        code: "custom",
        path: ["value"],
        message: "Esta operação exige um valor de comparação.",
      });
    }

    if (!requiresValue(rule.operation) && rule.value !== undefined) {
      ctx.addIssue({
        code: "custom",
        path: ["value"],
        message: "Esta operação não aceita valor de comparação.",
      });
    }
  });

export type SegmentationRuleForm = z.infer<typeof segmentationRuleFormSchema>;
