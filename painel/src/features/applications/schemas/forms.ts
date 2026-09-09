import { z } from "zod";

import { APPLICATION_STATUSES } from "./application";

/**
 * Regras de **forma** conhecidas pelo painel, validadas antes do envio.
 *
 * A invariante "retenção de texto livre nunca maior que a retenção geral" é do backend e não é
 * replicada aqui: o painel exibe a recusa se ela vier. Duplicar regra de negócio criaria duas
 * fontes de verdade que divergem em silêncio.
 *
 * A entrada é um `FormData` achatado: um campo não preenchido chega como string vazia, e um
 * campo que o formulário nem renderizou chega **ausente**. Os dois significam a mesma coisa —
 * "não configurado" — e viram `undefined`, nunca `0` nem string vazia.
 */

export const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

/** Texto de formulário: ausente ou em branco é ausência. */
function optionalText<T extends z.ZodType>(inner: T) {
  return z.preprocess(
    (value) => (typeof value === "string" && value.trim() !== "" ? value.trim() : undefined),
    inner.optional(),
  );
}

const requiredText = z.preprocess(
  (value) => (typeof value === "string" ? value.trim() : ""),
  z.string(),
);

/** Prazo em dias: inteiro de pelo menos 1. Vazio é ausência, não zero. */
const optionalPositiveDays = optionalText(
  z
    .string()
    .regex(/^\d+$/, "Informe um número inteiro de dias.")
    .transform((value) => Number.parseInt(value, 10))
    .refine((value) => value >= 1, "O prazo precisa ser de pelo menos 1 dia."),
);

export const createApplicationFormSchema = z.object({
  name: requiredText.pipe(
    z
      .string()
      .min(1, "Informe o nome da aplicação.")
      .max(120, "O nome pode ter no máximo 120 caracteres."),
  ),
  slug: optionalText(
    z
      .string()
      .max(50, "O slug pode ter no máximo 50 caracteres.")
      .regex(SLUG_PATTERN, "Use apenas letras minúsculas, números e hífens (ex.: minha-app)."),
  ),
  quietPeriodDays: optionalPositiveDays,
  retentionDays: optionalPositiveDays,
  openTextRetentionDays: optionalPositiveDays,
});

export type CreateApplicationForm = z.infer<typeof createApplicationFormSchema>;

/** `searchParams` da listagem. Valor desconhecido vira "todas" em vez de quebrar a tela. */
export const applicationListParamsSchema = z.object({
  status: z.enum(APPLICATION_STATUSES).optional().catch(undefined),
});

export type ApplicationListParams = z.infer<typeof applicationListParamsSchema>;
