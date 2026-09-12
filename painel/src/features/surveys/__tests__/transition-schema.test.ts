import { describe, expect, it } from "vitest";

import {
  TRANSITION_REASONS,
  TRANSITION_REASON_LABELS,
  allowedManualReasons,
  surveyStateTransitionSchema,
  type SurveyStateTransition,
} from "../schemas/transition";

function transition(
  reason: SurveyStateTransition["reason"],
  from: SurveyStateTransition["from"] = "active",
  to: SurveyStateTransition["to"] = "paused",
): SurveyStateTransition {
  return { from, to, reason, occurredAt: "2026-09-09T12:00:00Z" };
}

describe("surveyStateTransitionSchema", () => {
  it.each(TRANSITION_REASONS)("aceita o motivo %s", (reason) => {
    expect(surveyStateTransitionSchema.safeParse(transition(reason)).success).toBe(true);
  });

  it("cobre os sete motivos previstos no contrato, inclusive o encerramento por cota", () => {
    expect(TRANSITION_REASONS).toHaveLength(7);
    expect(TRANSITION_REASON_LABELS.quota_reached).toBe("Encerrada por cota");
    for (const reason of TRANSITION_REASONS) {
      expect(TRANSITION_REASON_LABELS[reason]).toBeTruthy();
    }
  });

  it("recusa motivo desconhecido", () => {
    expect(
      surveyStateTransitionSchema.safeParse({
        from: "active",
        to: "paused",
        reason: "manual_archive",
        occurredAt: "2026-09-09T12:00:00Z",
      }).success,
    ).toBe(false);
  });

  it("recusa estado desconhecido", () => {
    expect(
      surveyStateTransitionSchema.safeParse({
        from: "archived",
        to: "paused",
        reason: "manual_pause",
        occurredAt: "2026-09-09T12:00:00Z",
      }).success,
    ).toBe(false);
  });
});

describe("allowedManualReasons", () => {
  it("não oferece nada em rascunho nem em encerrada", () => {
    expect(allowedManualReasons("draft")).toEqual([]);
    expect(allowedManualReasons("ended")).toEqual([]);
  });

  it("no ar e agendada oferecem pausar e encerrar, nunca retomar", () => {
    expect(allowedManualReasons("active")).toEqual(["manual_pause", "manual_end"]);
    expect(allowedManualReasons("scheduled")).toEqual(["manual_pause", "manual_end"]);
  });

  it("pausada oferece retomar e encerrar, nunca pausar de novo", () => {
    expect(allowedManualReasons("paused")).toEqual(["manual_resume", "manual_end"]);
  });

  it("encerrar é oferecido em todo estado que ainda pode encerrar", () => {
    for (const state of ["scheduled", "active", "paused"] as const) {
      expect(allowedManualReasons(state)).toContain("manual_end");
    }
  });
});
