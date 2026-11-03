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

/**
 * Evento que o SDK desta aplicação já consultou. Alimenta a escolha do evento de disparo na
 * autoria, para não depender de digitar o nome de memória.
 */
export const observedEventSchema = z.object({
  name: z.string(),
  firstSeenAt: z.string(),
  lastSeenAt: z.string(),
});

export type ObservedEvent = z.infer<typeof observedEventSchema>;

/**
 * Atributo que o app desta aplicação já enviou, com os valores vistos. Catálogo, não perfil:
 * nada aqui liga um valor a quem o enviou.
 */
export const observedAttributeSchema = z.object({
  name: z.string(),
  firstSeenAt: z.string(),
  lastSeenAt: z.string(),
  values: z.array(z.object({ value: z.string(), lastSeenAt: z.string() })),
});

export type ObservedAttribute = z.infer<typeof observedAttributeSchema>;
