import { describe, expect, it } from "vitest";

import {
  TRANSITION_REASONS,
  TRANSITION_REASON_LABELS,
  allowedManualReasons,
  registeredTransitions,
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

  it("cobre os seis motivos previstos no contrato", () => {
    expect(TRANSITION_REASONS).toHaveLength(6);
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
  it("lista vazia não autoriza nada — é o caso da pesquisa encerrada", () => {
    expect(allowedManualReasons([], "ended")).toEqual([]);
  });

  it("autoriza exatamente o que a API oferece a partir do estado atual", () => {
    const transitions = [
      transition("manual_pause", "active", "paused"),
      transition("manual_end", "active", "ended"),
    ];

    expect(allowedManualReasons(transitions, "active")).toEqual(["manual_pause", "manual_end"]);
  });

  it("não oferece transição que parte de outro estado — isso é histórico", () => {
    const transitions = [
      transition("publication", "draft", "scheduled"),
      transition("manual_pause", "active", "paused"),
    ];

    // A pesquisa está pausada: pausar de novo não é oferecido, ainda que conste da lista.
    expect(allowedManualReasons(transitions, "paused")).toEqual([]);
  });

  it("ignora transições automáticas, que não são ações de quem opera", () => {
    const transitions = [
      transition("window_opened", "active", "active"),
      transition("publication", "active", "scheduled"),
    ];

    expect(allowedManualReasons(transitions, "active")).toEqual([]);
  });

  it("não oferece nada quando a API não lista transição a partir do estado atual", () => {
    expect(allowedManualReasons([transition("manual_pause", "active", "paused")], "ended")).toEqual(
      [],
    );
  });
});

describe("registeredTransitions", () => {
  it("mostra como histórico o que partiu de outros estados", () => {
    const historico = transition("publication", "draft", "scheduled");
    const oferecida = transition("manual_pause", "active", "paused");

    expect(registeredTransitions([historico, oferecida], "active")).toEqual([historico]);
  });

  it("devolve lista vazia quando só há transições possíveis", () => {
    expect(registeredTransitions([transition("manual_pause", "active", "paused")], "active")).toEqual(
      [],
    );
  });
});
