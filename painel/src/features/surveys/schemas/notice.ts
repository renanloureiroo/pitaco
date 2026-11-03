import { z } from "zod";

/**
 * O aviso de texto livre sempre mostra o texto; em branco é "volta ao padrão", e a API da
 * feature envia isso como `null` de propósito.
 */

export const FREE_TEXT_NOTICE_MAX = 200;

export const freeTextNoticeFormSchema = z.object({
  enabled: z.preprocess((value) => value === "true" || value === "on", z.boolean()),
  customText: z.preprocess(
    (value) => (typeof value === "string" && value.trim() !== "" ? value.trim() : undefined),
    z
      .string()
      .max(FREE_TEXT_NOTICE_MAX, `O aviso pode ter no máximo ${FREE_TEXT_NOTICE_MAX} caracteres.`)
      .optional(),
  ),
});

export type FreeTextNoticeForm = z.infer<typeof freeTextNoticeFormSchema>;
