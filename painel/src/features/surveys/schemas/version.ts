import { z } from "zod";

import { questionSchema } from "./question";
import { changeKindSchema } from "./publication";
import { triggerSchema } from "./trigger";

/** Versões: o registro do que foi publicado, com o conteúdo congelado no momento. */

export const surveyVersionSchema = z.object({
  /** Identidade da versão. Quem a identifica na tela é o número; o id casa com o que a
   *  exibição carrega. */
  id: z.string(),
  number: z.number(),
  status: z.enum(["draft", "published"]),
  /** Ausente no rascunho. */
  publishedAt: z.string().optional(),
  /** Ausente na versão 1: não havia mudança a classificar. */
  changeKind: changeKindSchema.optional(),
  changeSummary: z.string().optional(),
  /** Versões no mesmo grupo têm respostas somáveis entre si. */
  comparabilityGroup: z.number(),
});

export type SurveyVersion = z.infer<typeof surveyVersionSchema>;

export const surveyVersionDetailSchema = surveyVersionSchema.extend({
  questions: z.array(questionSchema),
  trigger: triggerSchema.optional(),
});

export type SurveyVersionDetail = z.infer<typeof surveyVersionDetailSchema>;

export const versionComparabilitySchema = z.object({
  groups: z.array(
    z.object({
      group: z.number(),
      versions: z.array(z.number()),
    }),
  ),
});

export type VersionComparability = z.infer<typeof versionComparabilitySchema>;
