import { z } from "zod";

import { absent } from "@/shared/api";

import { answerSchema } from "./answer";

export const displayOutcomeSchema = z.enum(["STARTED", "COMPLETED", "DISMISSED"]);
export type DisplayOutcome = z.infer<typeof displayOutcomeSchema>;

export const DISPLAY_OUTCOMES = displayOutcomeSchema.options;

export const displaySummarySchema = z.object({
  id: z.string(),
  versionId: z.string(),
  versionNumber: z.number(),
  comparabilityGroup: z.number(),
  outcome: displayOutcomeSchema,
  sdkVersion: absent(z.string()),
  openedAt: z.string(),
  closedAt: absent(z.string()),
});

export type DisplaySummary = z.infer<typeof displaySummarySchema>;

export const respondentDisplaySchema = displaySummarySchema.extend({
  surveyId: z.string(),
});

export type RespondentDisplay = z.infer<typeof respondentDisplaySchema>;

export const displayDetailSchema = displaySummarySchema.extend({
  respondentId: z.string(),
  surveyId: z.string(),
  attributes: z.record(z.string(), z.string()),
  answers: z.array(answerSchema),
});

export type DisplayDetail = z.infer<typeof displayDetailSchema>;
