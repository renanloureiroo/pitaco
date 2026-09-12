import { z } from "zod";

import { absent } from "@/shared/api";

/**
 * Exposição da pesquisa: prioridade no desempate, cota que a encerra sozinha e isenção do
 * intervalo de descanso da aplicação. É da pesquisa, não da versão — muda sem abrir rascunho.
 */

export const PRIORITY_MIN = -100;
export const PRIORITY_MAX = 100;

export const quotaProgressSchema = z.object({
  responseQuota: absent(z.number()),
  completedResponses: z.number(),
});

export type QuotaProgress = z.infer<typeof quotaProgressSchema>;

/**
 * Prioridade em branco é zero, o padrão; cota em branco é "sem cota" — e a API da feature a
 * envia como `null`, porque o formulário sempre mostra o campo e limpar é remover.
 */
export const exposureFormSchema = z.object({
  priority: z.preprocess(
    (value) => (typeof value === "string" && value.trim() !== "" ? value.trim() : "0"),
    z
      .string()
      .regex(/^-?\d+$/, "Informe um número inteiro.")
      .transform((value) => Number.parseInt(value, 10))
      .refine(
        (value) => value >= PRIORITY_MIN && value <= PRIORITY_MAX,
        `A prioridade vai de ${PRIORITY_MIN} a ${PRIORITY_MAX}.`,
      ),
  ),
  responseQuota: z.preprocess(
    (value) => (typeof value === "string" && value.trim() !== "" ? value.trim() : undefined),
    z
      .string()
      .regex(/^\d+$/, "Informe um número inteiro de respostas.")
      .transform((value) => Number.parseInt(value, 10))
      .refine((value) => value >= 1, "A cota precisa ser de pelo menos 1 resposta.")
      .optional(),
  ),
  ignoresQuietPeriod: z.preprocess((value) => value === "true" || value === "on", z.boolean()),
});

export type ExposureForm = z.infer<typeof exposureFormSchema>;
