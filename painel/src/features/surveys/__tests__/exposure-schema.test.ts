import { describe, expect, it } from "vitest";

import { exposureFormSchema, quotaProgressSchema } from "../schemas/exposure";
import { surveySchema } from "../schemas/survey";
import { publicationWarningsSchema } from "../schemas/warnings";

describe("exposureFormSchema", () => {
  it("prioridade em branco é zero e cota em branco é ausência", () => {
    const parsed = exposureFormSchema.parse({
      priority: "",
      responseQuota: "  ",
      ignoresQuietPeriod: "false",
    });

    expect(parsed).toEqual({ priority: 0, ignoresQuietPeriod: false });
    expect(parsed.responseQuota).toBeUndefined();
  });

  it("converte os números e a isenção marcada", () => {
    expect(
      exposureFormSchema.parse({ priority: "-5", responseQuota: "100", ignoresQuietPeriod: "true" }),
    ).toEqual({ priority: -5, responseQuota: 100, ignoresQuietPeriod: true });
  });

  it.each([
    [{ priority: "101" }, "priority"],
    [{ priority: "-101" }, "priority"],
    [{ priority: "alta" }, "priority"],
    [{ responseQuota: "0" }, "responseQuota"],
    [{ responseQuota: "1.5" }, "responseQuota"],
  ])("recusa %o apontando o campo", (values, field) => {
    const result = exposureFormSchema.safeParse({ ignoresQuietPeriod: "false", ...values });

    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual([field]);
  });
});

describe("leituras de exposição", () => {
  it("a pesquisa sem os campos de exposição assume o padrão", () => {
    const survey = surveySchema.parse({
      id: "srv-1",
      applicationId: "app-1",
      name: "NPS",
      state: "draft",
      createdAt: "2026-09-09T12:00:00Z",
    });

    expect(survey.priority).toBe(0);
    expect(survey.responseQuota).toBeUndefined();
    expect(survey.ignoresQuietPeriod).toBe(false);
  });

  it("progresso sem cota é cota ausente, nunca zero", () => {
    expect(quotaProgressSchema.parse({ completedResponses: 3 })).toEqual({ completedResponses: 3 });
  });

  it("aviso sem concorrentes vem com a lista vazia", () => {
    const { warnings } = publicationWarningsSchema.parse({
      warnings: [
        { code: "segmentation.no_known_match", ruleId: "rule-1", attribute: "plano" },
        {
          code: "trigger.competing_surveys",
          competingSurveys: [{ surveyId: "srv-2", name: "CSAT", priority: 5 }],
        },
      ],
    });

    expect(warnings[0].competingSurveys).toEqual([]);
    expect(warnings[1].competingSurveys).toHaveLength(1);
  });
});
