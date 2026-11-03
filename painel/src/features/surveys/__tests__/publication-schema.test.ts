import { describe, expect, it } from "vitest";

import {
  publicationImpedimentSchema,
  publicationImpedimentsSchema,
  publishSurveyFormSchema,
} from "../schemas/publication";
import { surveyVersionSchema, versionComparabilitySchema } from "../schemas/version";

describe("publishSurveyFormSchema", () => {
  it("não exige a natureza da mudança na versão 1", () => {
    const schema = publishSurveyFormSchema(false);
    const result = schema.safeParse({});

    expect(result.success).toBe(true);
    expect(result.success && result.data.changeKind).toBeUndefined();
  });

  it("ignora a natureza da mudança enviada na versão 1", () => {
    const result = publishSurveyFormSchema(false).safeParse({ changeKind: "semantic" });

    expect(result.success && result.data.changeKind).toBeUndefined();
  });

  it("exige a natureza da mudança a partir da versão 2", () => {
    const result = publishSurveyFormSchema(true).safeParse({});

    expect(result.success).toBe(false);
  });

  it("aceita cosmetic e semantic a partir da versão 2", () => {
    for (const changeKind of ["cosmetic", "semantic"] as const) {
      expect(publishSurveyFormSchema(true).parse({ changeKind }).changeKind).toBe(changeKind);
    }
  });

  it("recusa natureza de mudança desconhecida", () => {
    expect(publishSurveyFormSchema(true).safeParse({ changeKind: "outra" }).success).toBe(false);
  });

  it("trata resumo em branco como ausente", () => {
    const result = publishSurveyFormSchema(false).parse({ changeSummary: "   " });

    expect(result.changeSummary).toBeUndefined();
  });
});

describe("publicationImpedimentSchema", () => {
  it("aceita impedimento ligado a uma pergunta", () => {
    const parsed = publicationImpedimentSchema.parse({
      code: "question.options_missing",
      questionKey: "preferida",
    });

    expect(parsed.questionKey).toBe("preferida");
  });

  it("recusa código de impedimento desconhecido", () => {
    expect(publicationImpedimentSchema.safeParse({ code: "survey.sem_nome" }).success).toBe(false);
  });

  it("aceita lista vazia — é o que libera a publicação", () => {
    expect(publicationImpedimentsSchema.parse({ impediments: [] }).impediments).toEqual([]);
  });
});

describe("surveyVersionSchema", () => {
  it("aceita a versão 1 sem natureza nem resumo de mudança", () => {
    const parsed = surveyVersionSchema.parse({
      id: "ver-1",
      number: 1,
      status: "published",
      publishedAt: "2026-09-09T12:00:00Z",
      comparabilityGroup: 1,
    });

    expect(parsed.changeKind).toBeUndefined();
    expect(parsed.changeSummary).toBeUndefined();
  });

  it("aceita rascunho sem data de publicação", () => {
    const parsed = surveyVersionSchema.parse({ id: "ver-2", number: 2, status: "draft", comparabilityGroup: 1 });

    expect(parsed.publishedAt).toBeUndefined();
  });
});

describe("versionComparabilitySchema", () => {
  it("lê os grupos de comparabilidade", () => {
    const parsed = versionComparabilitySchema.parse({
      groups: [
        { group: 1, versions: [1, 2] },
        { group: 2, versions: [3] },
      ],
    });

    expect(parsed.groups).toHaveLength(2);
  });
});
