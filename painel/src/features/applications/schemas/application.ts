import { z } from "zod";

import { absent } from "@/shared/api";

/**
 * Schemas de leitura de aplicação.
 *
 * Prazo não configurado chega **ausente** — nunca `null`, nunca `0`. O schema usa
 * `.optional()`, e não `.nullable()`, justamente para que um `0` que venha do backend seja um
 * zero de verdade e apareça como tal na tela (FR-011).
 */

export const applicationStatusSchema = z.enum(["active", "inactive"]);
export type ApplicationStatus = z.infer<typeof applicationStatusSchema>;

export const APPLICATION_STATUSES = applicationStatusSchema.options;

export const applicationSummarySchema = z.object({
  id: z.string(),
  slug: z.string(),
  name: z.string(),
  status: applicationStatusSchema,
  createdAt: z.string(),
});

export type ApplicationSummary = z.infer<typeof applicationSummarySchema>;

export const applicationSchema = applicationSummarySchema.extend({
  quietPeriodDays: absent(z.number()),
  retentionDays: absent(z.number()),
  openTextRetentionDays: absent(z.number()),
  updatedAt: z.string(),
});

export type Application = z.infer<typeof applicationSchema>;

/** `POST /applications` devolve apenas o identificador e o slug definitivo. */
export const createdApplicationSchema = z.object({
  id: z.string(),
  slug: z.string(),
});

export type CreatedApplication = z.infer<typeof createdApplicationSchema>;

export const APPLICATION_STATUS_LABELS: Record<ApplicationStatus, string> = {
  active: "Ativa",
  inactive: "Inativa",
};
