import { describe, expect, it } from "vitest";

import { duplicateSurveyFormSchema, surveyCreateFormSchema, surveyDetailSchema } from "../schemas/survey";

describe("surveyCreateFormSchema", () => {
  it("em branco não envia modelo", () => {
    expect(surveyCreateFormSchema.parse({ name: "NPS", template: "blank" })).toEqual({ name: "NPS" });
    expect(surveyCreateFormSchema.parse({ name: "NPS" })).toEqual({ name: "NPS" });
  });

  it("aceita os três modelos e recusa o desconhecido", () => {
    expect(surveyCreateFormSchema.parse({ name: "NPS", template: "nps" }).template).toBe("nps");
    expect(surveyCreateFormSchema.safeParse({ name: "NPS", template: "sus" }).success).toBe(false);
  });
});

describe("duplicateSurveyFormSchema", () => {
  it("exige o destino e trata o nome em branco como ausente", () => {
    expect(duplicateSurveyFormSchema.parse({ targetApplicationId: "app-b", name: "  " })).toEqual({
      targetApplicationId: "app-b",
    });

    const semDestino = duplicateSurveyFormSchema.safeParse({ targetApplicationId: " " });
    expect(semDestino.success).toBe(false);
    expect(semDestino.error?.issues[0]?.message).toBe("Escolha a aplicação que recebe a cópia.");
  });

  it("recusa nome acima de 120 caracteres", () => {
    const parsed = duplicateSurveyFormSchema.safeParse({
      targetApplicationId: "app-b",
      name: "x".repeat(121),
    });

    expect(parsed.error?.issues[0]?.message).toBe("O nome pode ter no máximo 120 caracteres.");
  });
});

describe("surveyDetailSchema", () => {
  it("lê o modelo de origem quando existe, e o deixa ausente quando não", () => {
    const base = {
      id: "s",
      applicationId: "a",
      name: "NPS",
      state: "draft",
      createdAt: "2026-09-12T10:00:00Z",
    };

    expect(surveyDetailSchema.parse({ ...base, templateKind: "nps" }).templateKind).toBe("nps");
    expect(surveyDetailSchema.parse(base).templateKind).toBeUndefined();
  });
});
