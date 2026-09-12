import { z } from "zod";

import { absent } from "@/shared/api";

export const answerStatusSchema = z.enum(["ANSWERED", "SKIPPED", "NOT_APPLICABLE", "EXPIRED"]);
export type AnswerStatus = z.infer<typeof answerStatusSchema>;
export const ANSWER_STATUSES = answerStatusSchema.options;

export const answerSchema = z.object({
  questionKey: z.string(),
  status: answerStatusSchema,
  text: absent(z.string()),
  number: absent(z.number()),
  options: z.array(z.string()),
});

export type Answer = z.infer<typeof answerSchema>;
