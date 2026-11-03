import { z } from "zod";

import { absent } from "@/shared/api";

export const SDK_ERROR_KINDS = [
  "render_error",
  "network_error",
  "malformed_response",
  "storage_error",
  "unknown",
] as const;

export type SdkErrorKind = (typeof SDK_ERROR_KINDS)[number];

export const SUPPRESSION_REASONS = ["unknown_question_type", "unsupported_feature"] as const;

export type SuppressionReason = (typeof SUPPRESSION_REASONS)[number];

export function isSdkErrorKind(value: string): value is SdkErrorKind {
  return (SDK_ERROR_KINDS as readonly string[]).includes(value);
}

export function isSuppressionReason(value: string): value is SuppressionReason {
  return (SUPPRESSION_REASONS as readonly string[]).includes(value);
}

export const sdkVersionUsageSchema = z.object({
  version: z.string(),
  requestCount: z.number(),
  recentRequestCount: z.number(),
  recentShare: absent(z.number()),
  firstSeenAt: z.string(),
  lastSeenAt: z.string(),
  stale: z.boolean(),
});

export type SdkVersionUsage = z.infer<typeof sdkVersionUsageSchema>;

export const sdkVersionsSchema = z.object({
  recentFrom: z.string(),
  recentRequests: z.number(),
  versions: z.array(sdkVersionUsageSchema),
});

export type SdkVersions = z.infer<typeof sdkVersionsSchema>;

/** Tipo novo vindo de um backend mais recente vira "desconhecido" em vez de quebrar a lista. */
export const sdkErrorReportSchema = z.object({
  id: z.string(),
  sdkVersion: absent(z.string()),
  kind: z.enum(SDK_ERROR_KINDS).catch("unknown"),
  message: z.string(),
  context: z.record(z.string(), z.unknown()).catch({}),
  occurredAt: z.string(),
  receivedAt: z.string(),
});

export type SdkErrorReport = z.infer<typeof sdkErrorReportSchema>;

export const surveyHealthSchema = z.object({
  from: z.string(),
  to: z.string(),
  displays: z.number(),
  suppressions: z.object({
    total: z.number(),
    byReason: z.array(z.object({ reason: z.string(), count: z.number() })),
    bySdkVersion: z.array(z.object({ sdkVersion: absent(z.string()), count: z.number() })),
  }),
  suppressionShare: absent(z.number()),
  relevant: z.boolean(),
  minRequiredVersion: absent(z.string()),
  eventName: absent(z.string()),
  eventLastSeenAt: absent(z.string()),
});

export type SurveyHealth = z.infer<typeof surveyHealthSchema>;
