import { z } from "zod";
import { absent } from "@/shared/api";
import { appliedFilterSchema, resultQuestionTypeSchema } from "./results";

export const viaCountSchema = z.object({
  via: z.enum(["close_button", "swipe", "backdrop", "hardware_back", "navigation", "programmatic"]),
  count: z.number(),
  share: absent(z.number()),
});

export const dismissalsSchema = z.object({
  total: z.number(),
  byVia: z.array(viaCountSchema),
  unspecified: z.number(),
});

export const activeTimeSchema = z.object({
  samples: z.number(),
  medianMs: absent(z.number()),
  p90Ms: absent(z.number()),
});

export const questionBehaviorSchema = z.object({
  key: z.string(),
  statement: z.string(),
  type: resultQuestionTypeSchema,
  position: z.number(),
  viewed: z.number(),
  answered: z.number(),
  skipped: z.number(),
  abandoned: z.number(),
  activeTime: absent(activeTimeSchema),
  revisited: z.number(),
  revisitRate: absent(z.number()),
  selected: absent(z.number()),
  changed: absent(z.number()),
  changeRate: absent(z.number()),
  validationFailures: absent(z.number()),
  failureRate: absent(z.number()),
});

export const metricDefinitionSchema = z.object({
  metric: z.string(),
  definition: z.string(),
});

export const surveyBehaviorSchema = z.object({
  everPublished: z.boolean(),
  displayed: z.number(),
  instrumented: z.number(),
  questions: z.array(questionBehaviorSchema),
  dismissals: dismissalsSchema,
  filter: appliedFilterSchema,
  definitions: z.array(metricDefinitionSchema),
});

export type MetricDefinition = z.infer<typeof metricDefinitionSchema>;
export type SurveyBehavior = z.infer<typeof surveyBehaviorSchema>;
