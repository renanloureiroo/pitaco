import { z } from "zod";
const data = require("../survey_output.json");
// The easiest way is to use zod in a small script that mimics the schemas from the painel.

function absent(schema) {
  return schema
    .nullish()
    .transform((value) => value ?? undefined)
    .optional();
}

const surveyStateSchema = z.enum(["draft", "scheduled", "active", "paused", "ended"]);
const surveyTemplateSchema = z.enum(["nps", "csat", "ces"]);
const freeTextNoticeSchema = z.object({
  enabled: z.boolean(),
  customText: absent(z.string()),
  text: z.string(),
  defaultText: z.string(),
});

const questionTypeSchema = z.enum(["single_choice", "multiple_choice", "rating", "scale", "nps", "free_text"]);
const questionOptionSchema = z.object({
  label: z.string(),
  value: z.string(),
});
const questionRangeSchema = z.object({
  min: z.number(),
  max: z.number(),
  minLabel: absent(z.string()),
  maxLabel: absent(z.string()),
});
const conditionOperatorSchema = z.enum(["equals", "not_equals", "in", "between"]);
const conditionSchema = z.object({
  sourceKey: z.string(),
  operator: conditionOperatorSchema,
  values: z.array(z.string()).default([]),
  min: absent(z.number()),
  max: absent(z.number()),
});
const questionSchema = z.object({
  id: z.string(),
  key: z.string(),
  statement: z.string(),
  type: questionTypeSchema,
  position: z.number(),
  required: z.boolean(),
  options: absent(z.array(questionOptionSchema)),
  range: absent(questionRangeSchema),
  condition: absent(conditionSchema),
});

const ruleOperationSchema = z.enum(["equals", "not_equals", "present", "absent"]);
const segmentationRuleSchema = z.object({
  id: z.string(),
  attribute: z.string(),
  operation: ruleOperationSchema,
  value: absent(z.string()),
});
const triggerSchema = z.object({
  eventName: z.string(),
  windowStart: z.string(),
  windowEnd: absent(z.string()),
  samplingRate: z.number(),
  rules: z.array(segmentationRuleSchema).default([]),
});

const surveyContentSchema = z.object({
  source: z.enum(["draft", "published"]),
  versionNumber: z.number(),
  questions: z.array(questionSchema),
  trigger: absent(triggerSchema),
});

const surveyDetailSchema = z.object({
  id: z.string(),
  applicationId: z.string(),
  name: z.string(),
  state: surveyStateSchema,
  publishedVersionNumber: absent(z.number()),
  draftVersionNumber: absent(z.number()),
  priority: z.number().default(0),
  responseQuota: absent(z.number()),
  ignoresQuietPeriod: z.boolean().default(false),
  templateKind: absent(surveyTemplateSchema),
  freeTextNotice: freeTextNoticeSchema.optional(),
  createdAt: z.string(),
  content: absent(surveyContentSchema),
});

const result = surveyDetailSchema.safeParse(data);
if (!result.success) {
  console.log(JSON.stringify(result.error.issues, null, 2));
} else {
  console.log("Success!");
}
