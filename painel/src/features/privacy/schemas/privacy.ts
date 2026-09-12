import { z } from "zod";

import { absent } from "@/shared/api";

/**
 * Leituras de privacidade. Nenhuma delas carrega quem foi excluído: a exclusão devolve só
 * quanto saiu, e o registro só quando e quanto.
 */

export const respondentErasureSchema = z.object({
  deleted: z.boolean(),
  displaysDeleted: z.number(),
  answersDeleted: z.number(),
});

export type RespondentErasure = z.infer<typeof respondentErasureSchema>;

export const deletionAuditSchema = z.object({
  id: z.string(),
  displaysDeleted: z.number(),
  answersDeleted: z.number(),
  performedBy: absent(z.string()),
  performedAt: z.string(),
});

export type DeletionAudit = z.infer<typeof deletionAuditSchema>;

export const retentionForecastSchema = z.object({
  answers: z.number(),
  texts: z.number(),
});

export type RetentionForecast = z.infer<typeof retentionForecastSchema>;

/** Prazo ausente é "sem prazo": o padrão é não apagar. */
export const retentionPreviewSchema = z.object({
  configured: z.boolean(),
  answerRetentionDays: absent(z.number()),
  textRetentionDays: absent(z.number()),
  nextRunAt: absent(z.string()),
  nextRun: retentionForecastSchema,
  nextWeek: retentionForecastSchema,
  lastRunAt: absent(z.string()),
  firstDiscardPending: z.boolean(),
});

export type RetentionPreview = z.infer<typeof retentionPreviewSchema>;
