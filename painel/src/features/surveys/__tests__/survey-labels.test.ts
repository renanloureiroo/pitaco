import { describe, expect, it } from "vitest";

import {
  QUESTION_TYPE_LABELS,
  RULE_OPERATION_LABELS,
  SURVEY_STATE_LABELS,
  surveyStateVariant,
} from "../lib/survey-labels";
import { QUESTION_TYPES } from "../schemas/question";
import { SURVEY_STATES } from "../schemas/survey";
import { RULE_OPERATIONS } from "../schemas/trigger";

describe("rótulos", () => {
  it("cobre todos os estados de pesquisa", () => {
    for (const state of SURVEY_STATES) {
      expect(SURVEY_STATE_LABELS[state]).toBeTruthy();
    }
    expect(Object.keys(SURVEY_STATE_LABELS)).toHaveLength(SURVEY_STATES.length);
  });

  it("cobre todos os tipos de pergunta", () => {
    for (const type of QUESTION_TYPES) {
      expect(QUESTION_TYPE_LABELS[type]).toBeTruthy();
    }
    expect(Object.keys(QUESTION_TYPE_LABELS)).toHaveLength(QUESTION_TYPES.length);
  });

  it("cobre todas as operações de regra", () => {
    for (const operation of RULE_OPERATIONS) {
      expect(RULE_OPERATION_LABELS[operation]).toBeTruthy();
    }
    expect(Object.keys(RULE_OPERATION_LABELS)).toHaveLength(RULE_OPERATIONS.length);
  });
});

describe("surveyStateVariant", () => {
  it("dá destaque ao que está no ar e alerta ao que está pausado", () => {
    expect(surveyStateVariant("active")).toBe("default");
    expect(surveyStateVariant("paused")).toBe("destructive");
  });

  it("responde para todo estado, sem cair em undefined", () => {
    for (const state of SURVEY_STATES) {
      expect(surveyStateVariant(state)).toBeTruthy();
    }
  });
});
