import { z } from "zod";

/**
 * A identidade chega como o app a enviou ao SDK: a referência opaca do usuário ou, para quem
 * nunca teve uma, o identificador do dispositivo. Uma das duas, nunca as duas.
 */

export const IDENTITY_KINDS = ["reference", "device"] as const;
export type IdentityKind = (typeof IDENTITY_KINDS)[number];

export const IDENTITY_MAX = 200;

export const eraseRespondentFormSchema = z.object({
  identityKind: z.preprocess(
    (value) => (value === "device" ? "device" : "reference"),
    z.enum(IDENTITY_KINDS),
  ),
  identity: z.preprocess(
    (value) => (typeof value === "string" ? value.trim() : ""),
    z
      .string()
      .min(1, "Informe a identificação do respondente.")
      .max(IDENTITY_MAX, `A identificação pode ter no máximo ${IDENTITY_MAX} caracteres.`),
  ),
});

export type EraseRespondentInput = z.infer<typeof eraseRespondentFormSchema>;
