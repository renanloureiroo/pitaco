import { z } from "zod";

import { absent } from "@/shared/api";

import { answerSchema } from "./answer";

/**
 * Exibição: em que a pesquisa parou, para quem foi exibida e o que produziu.
 *
 * `closedAt` existe **se e somente se** o desfecho é final. O painel não recalcula isso — exibe
 * o que veio —, mas a regra governa o texto de ausência: sem fechamento e com desfecho
 * `STARTED`, a tela diz "ainda aberta", nunca "sem data" (FR-008).
 */

export const displayOutcomeSchema = z.enum(["STARTED", "COMPLETED", "DISMISSED"]);
export type DisplayOutcome = z.infer<typeof displayOutcomeSchema>;

/**
 * Exatamente os três que o backend aceita filtrar. `ABANDONED` existe no domínio do backend
 * mas nunca é gravado, e oferecê-lo seria oferecer uma opção que sempre devolve vazio (FR-006).
 */
export const DISPLAY_OUTCOMES = displayOutcomeSchema.options;

export const displaySummarySchema = z.object({
  id: z.string(),
  /** Identidade da versão. O painel a recebe e não a exibe: quem identifica na tela é o número. */
  versionId: z.string(),
  versionNumber: z.number(),
  comparabilityGroup: z.number(),
  outcome: displayOutcomeSchema,
  sdkVersion: absent(z.string()),
  openedAt: z.string(),
  closedAt: absent(z.string()),
});

export type DisplaySummary = z.infer<typeof displaySummarySchema>;

/** A mesma exibição vista do eixo do respondente: a pesquisa varia, então precisa aparecer. */
export const respondentDisplaySchema = displaySummarySchema.extend({
  surveyId: z.string(),
});

export type RespondentDisplay = z.infer<typeof respondentDisplaySchema>;

export const displayDetailSchema = displaySummarySchema.extend({
  respondentId: z.string(),
  surveyId: z.string(),
  /** Pertence à **exibição**, não ao respondente (FR-014). Vazio é ausência exibível, não erro. */
  attributes: z.record(z.string(), z.string()),
  /** Vazio na exibição aberta e na dispensada (FR-015). */
  answers: z.array(answerSchema),
});

export type DisplayDetail = z.infer<typeof displayDetailSchema>;
