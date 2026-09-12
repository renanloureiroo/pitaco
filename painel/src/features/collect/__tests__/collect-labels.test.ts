import { describe, expect, it } from "vitest";

import { ANSWER_STATUSES } from "../schemas/answer";
import { DISPLAY_OUTCOMES } from "../schemas/display";
import { RESPONDENT_IDENTITY_KINDS } from "../schemas/respondent";
import {
  ANSWER_BLANK,
  ANSWER_EXPIRED_EXPLANATION,
  ANSWER_NOT_APPLICABLE_EXPLANATION,
  ANSWER_SKIPPED_EXPLANATION,
  ANSWER_STATUS_LABELS,
  DISPLAY_OUTCOME_LABELS,
  DISPLAY_STILL_OPEN,
  RESPONDENT_IDENTITY_KIND_LABELS,
  SDK_VERSION_UNKNOWN,
  displayOutcomeVariant,
} from "../lib/collect-labels";

describe("desfecho", () => {
  it("rotula os três desfechos em português, sem deixar código cru vazar", () => {
    expect(DISPLAY_OUTCOME_LABELS).toEqual({
      STARTED: "Em andamento",
      COMPLETED: "Concluída",
      DISMISSED: "Dispensada",
    });

    for (const outcome of DISPLAY_OUTCOMES) {
      expect(DISPLAY_OUTCOME_LABELS[outcome]).not.toBe(outcome);
    }
  });

  it("não oferece ABANDONED, que o backend nunca grava (FR-006)", () => {
    expect(DISPLAY_OUTCOMES).not.toContain("ABANDONED");
    expect(DISPLAY_OUTCOMES).toHaveLength(3);
  });

  it("dá variante visual a cada desfecho, e distingue concluída de dispensada", () => {
    expect(displayOutcomeVariant("COMPLETED")).not.toBe(displayOutcomeVariant("DISMISSED"));
    expect(displayOutcomeVariant("STARTED")).not.toBe(displayOutcomeVariant("COMPLETED"));
  });
});

describe("forma de identificação (FR-020)", () => {
  it("rotula em linguagem de quem opera o painel, nunca o código cru", () => {
    expect(RESPONDENT_IDENTITY_KIND_LABELS.APP_REFERENCE).toBe("Referência da aplicação");
    expect(RESPONDENT_IDENTITY_KIND_LABELS.DEVICE).toBe("Dispositivo");

    for (const kind of RESPONDENT_IDENTITY_KINDS) {
      expect(RESPONDENT_IDENTITY_KIND_LABELS[kind]).not.toBe(kind);
      expect(RESPONDENT_IDENTITY_KIND_LABELS[kind]).not.toContain("_");
    }
  });
});

describe("situação de resposta (FR-012, SC-006)", () => {
  it("pulada e expirada têm textos distintos entre si", () => {
    expect(ANSWER_STATUS_LABELS.SKIPPED).toBe("Pulada");
    expect(ANSWER_STATUS_LABELS.EXPIRED).toBe("Texto expirado");
    expect(ANSWER_STATUS_LABELS.SKIPPED).not.toBe(ANSWER_STATUS_LABELS.EXPIRED);
  });

  it("nenhuma das duas colapsa em resposta em branco", () => {
    const textos = [ANSWER_STATUS_LABELS.SKIPPED, ANSWER_STATUS_LABELS.EXPIRED, ANSWER_BLANK];

    expect(new Set(textos).size).toBe(3);
  });

  it("explica a expiração pela retenção, e a pulada pela escolha de quem respondeu", () => {
    expect(ANSWER_EXPIRED_EXPLANATION).toContain("retenção");
    expect(ANSWER_EXPIRED_EXPLANATION).toContain("descartado");
    expect(ANSWER_SKIPPED_EXPLANATION).toContain("não responder");
    expect(ANSWER_EXPIRED_EXPLANATION).not.toBe(ANSWER_SKIPPED_EXPLANATION);
  });

  it("respondida não tem rótulo de situação — o que se mostra é o valor", () => {
    expect(ANSWER_STATUSES).toContain("ANSWERED");
    expect(Object.keys(ANSWER_STATUS_LABELS)).toEqual(["SKIPPED", "NOT_APPLICABLE", "EXPIRED"]);
  });

  it("não aplicável não se confunde com pulada: uma a pessoa viu, a outra nunca foi feita", () => {
    expect(ANSWER_STATUS_LABELS.NOT_APPLICABLE).toBe("Não aplicável");
    expect(ANSWER_STATUS_LABELS.NOT_APPLICABLE).not.toBe(ANSWER_STATUS_LABELS.SKIPPED);
    expect(ANSWER_NOT_APPLICABLE_EXPLANATION).toContain("condição");
    expect(ANSWER_NOT_APPLICABLE_EXPLANATION).not.toBe(ANSWER_SKIPPED_EXPLANATION);
  });
});

describe("ausências da coleta (SC-005)", () => {
  it("cada ausência tem texto próprio, e nenhuma é zero, traço ou vazio", () => {
    const ausencias = [DISPLAY_STILL_OPEN, SDK_VERSION_UNKNOWN, ANSWER_BLANK];

    for (const texto of ausencias) {
      expect(texto).not.toBe("");
      expect(texto).not.toBe("-");
      expect(texto).not.toBe("—");
      expect(texto).not.toBe("0");
    }

    expect(new Set(ausencias).size).toBe(ausencias.length);
  });
});
