import { z } from "zod";

import { absent } from "@/shared/api";

/**
 * Avisos de publicação: informam, não impedem. O código fica aberto (`string`) para que um
 * aviso novo do backend apareça com a mensagem genérica em vez de quebrar a tela.
 */

export const WARNING_CODES = [
  "segmentation.no_known_match",
  "segmentation.contradictory",
  "trigger.competing_surveys",
  "compatibility.unsupported_by_majority",
] as const;

export const competingSurveySchema = z.object({
  surveyId: z.string(),
  name: z.string(),
  priority: z.number(),
});

export type CompetingSurvey = z.infer<typeof competingSurveySchema>;

export const publicationWarningSchema = z.object({
  code: z.string(),
  ruleId: absent(z.string()),
  attribute: absent(z.string()),
  competingSurveys: z
    .array(competingSurveySchema)
    .nullish()
    .transform((value) => value ?? []),
  minRequiredVersion: absent(z.string()),
  unsupportedShare: absent(z.number()),
});

export type PublicationWarning = z.infer<typeof publicationWarningSchema>;

export const publicationWarningsSchema = z.object({
  warnings: z.array(publicationWarningSchema),
});
