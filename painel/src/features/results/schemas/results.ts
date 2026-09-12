import { z } from "zod";

import { absent } from "@/shared/api";

/**
 * Resultado de uma pesquisa, como o backend o apura (contrato `/results`).
 *
 * `aggregate` ausente é "ninguém respondeu" — distinto de zero em cada opção, que vem com o
 * agregado presente e contagens zeradas. `rate` ausente é "sem exibição", nunca `0`.
 */

export const resultQuestionTypeSchema = z.enum([
  "single_choice",
  "multiple_choice",
  "rating",
  "scale",
  "nps",
  "free_text",
]);

export type ResultQuestionType = z.infer<typeof resultQuestionTypeSchema>;

export const optionShareSchema = z.object({
  value: z.string(),
  label: z.string(),
  count: z.number(),
  share: z.number(),
});

export type OptionShare = z.infer<typeof optionShareSchema>;

export const valueShareSchema = z.object({
  value: z.number(),
  count: z.number(),
  share: z.number(),
});

export type ValueShare = z.infer<typeof valueShareSchema>;

export const questionAggregateSchema = z.object({
  kind: z.enum(["choice", "numeric", "nps", "text"]),
  options: absent(z.array(optionShareSchema)),
  average: absent(z.number()),
  distribution: absent(z.array(valueShareSchema)),
  promoters: absent(z.number()),
  passives: absent(z.number()),
  detractors: absent(z.number()),
  score: absent(z.number()),
});

export type QuestionAggregate = z.infer<typeof questionAggregateSchema>;

/**
 * Só no consolidado. Falso quando tipo, opções, faixa ou condição mudaram entre as versões que
 * receberam exibição no recorte: somar essas respostas pode enganar.
 */
export const comparabilitySchema = z.object({
  comparable: z.boolean(),
  versions: z.array(z.number()),
});

export type Comparability = z.infer<typeof comparabilitySchema>;

export const questionResultSchema = z.object({
  key: z.string(),
  statement: z.string(),
  type: resultQuestionTypeSchema,
  position: z.number(),
  answered: z.number(),
  skipped: z.number(),
  notApplicable: z.number(),
  aggregate: absent(questionAggregateSchema),
  comparability: absent(comparabilitySchema),
});

export type QuestionResult = z.infer<typeof questionResultSchema>;

export const timelinePointSchema = z.object({
  day: z.string(),
  displayed: z.number(),
  completed: z.number(),
});

export type TimelinePoint = z.infer<typeof timelinePointSchema>;

export const responseRateSchema = z.object({
  displayed: z.number(),
  completed: z.number(),
  dismissed: z.number(),
  abandoned: z.number(),
  inProgress: z.number(),
  rate: absent(z.number()),
  definition: z.string(),
  timeline: z.array(timelinePointSchema),
});

export type ResponseRate = z.infer<typeof responseRateSchema>;

export const appliedFilterSchema = z.object({
  from: absent(z.string()),
  to: absent(z.string()),
  attribute: absent(z.string()),
  attributeValue: absent(z.string()),
  attributeAbsent: z.boolean(),
  version: absent(z.number()),
});

export type AppliedFilter = z.infer<typeof appliedFilterSchema>;

export const attributeCatalogSchema = z.object({
  name: z.string(),
  values: z.array(z.object({ value: z.string(), count: z.number() })),
});

export type AttributeCatalog = z.infer<typeof attributeCatalogSchema>;

/** NPS no topo, só em pesquisa criada a partir do modelo. `score` ausente é "sem resposta". */
export const npsSummarySchema = z.object({
  questionKey: z.string(),
  respondents: z.number(),
  promoters: z.number(),
  passives: z.number(),
  detractors: z.number(),
  score: absent(z.number()),
});

export type NpsSummary = z.infer<typeof npsSummarySchema>;

/**
 * Presente quando a retenção já descartou respostas desta pesquisa. `snapshotApplied` diz se o
 * agregado guardado entrou nos números deste recorte.
 */
export const retentionSchema = z.object({
  snapshotApplied: z.boolean(),
  discardedBefore: z.string(),
  note: z.string(),
});

export type Retention = z.infer<typeof retentionSchema>;

export const surveyResultsSchema = z.object({
  everPublished: z.boolean(),
  responseRate: responseRateSchema,
  questions: z.array(questionResultSchema),
  sampleSize: z.number(),
  smallSample: z.boolean(),
  filter: appliedFilterSchema,
  attributes: z.array(attributeCatalogSchema),
  nps: absent(npsSummarySchema),
  retention: absent(retentionSchema),
});

export type SurveyResults = z.infer<typeof surveyResultsSchema>;

export const answerContextSchema = z.object({
  questionKey: z.string(),
  statement: z.string(),
  value: z.string(),
});

export type AnswerContext = z.infer<typeof answerContextSchema>;

export const openAnswerSchema = z.object({
  displayId: z.string(),
  questionKey: z.string(),
  statement: z.string(),
  text: z.string(),
  answeredAt: z.string(),
  context: z.array(answerContextSchema),
});

export type OpenAnswer = z.infer<typeof openAnswerSchema>;
