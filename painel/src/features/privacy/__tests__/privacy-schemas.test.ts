import { describe, expect, it } from "vitest";

import {
  describeRetentionPolicy,
  erasureResultMessage,
  lastRunMessage,
  retentionWarning,
} from "../lib/privacy-labels";
import { eraseRespondentFormSchema } from "../schemas/forms";
import { retentionPreviewSchema, type RetentionPreview } from "../schemas/privacy";

function preview(overrides: Partial<RetentionPreview> = {}): RetentionPreview {
  return {
    configured: true,
    answerRetentionDays: 30,
    textRetentionDays: 7,
    nextRunAt: "2026-09-13T03:47:00Z",
    nextRun: { answers: 0, texts: 0 },
    nextWeek: { answers: 0, texts: 0 },
    firstDiscardPending: true,
    ...overrides,
  };
}

describe("formulário de exclusão", () => {
  it("apara a identificação e usa a referência quando o tipo não é dispositivo", () => {
    const parsed = eraseRespondentFormSchema.parse({ identity: "  u-1  " });

    expect(parsed).toEqual({ identityKind: "reference", identity: "u-1" });
  });

  it("aceita o dispositivo e recusa identificação vazia", () => {
    expect(eraseRespondentFormSchema.parse({ identityKind: "device", identity: "d-1" }).identityKind).toBe(
      "device",
    );

    const result = eraseRespondentFormSchema.safeParse({ identity: "   " });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe("Informe a identificação do respondente.");
  });
});

describe("prévia de retenção", () => {
  it("lê prazo ausente como sem prazo, nunca como zero", () => {
    const parsed = retentionPreviewSchema.parse({
      configured: false,
      nextRun: { answers: 0, texts: 0 },
      nextWeek: { answers: 0, texts: 0 },
      firstDiscardPending: true,
    });

    expect(parsed.answerRetentionDays).toBeUndefined();
    expect(parsed.nextRunAt).toBeUndefined();
  });

  it("sem política, diz que nada é descartado e não avisa", () => {
    const none = preview({ configured: false, answerRetentionDays: undefined, textRetentionDays: undefined });

    expect(describeRetentionPolicy(none)).toContain("nada é descartado");
    expect(retentionWarning(none)).toBeUndefined();
  });

  it("descreve os dois prazos quando o de texto é mais curto", () => {
    const text = describeRetentionPolicy(preview());

    expect(text).toContain("30 dias");
    expect(text).toContain("7 dias");
  });

  it("avisa o que sai na próxima execução, com a data e o convite para exportar", () => {
    const warning = retentionWarning(preview({ nextRun: { answers: 1, texts: 3 } }));

    expect(warning).toContain("1 resposta e 3 textos livres serão descartados");
    expect(warning).toContain("Exporte antes");
    expect(warning).toMatch(/\d{2}\/\d{2}\/\d{4}/);
  });

  it("sem nada na próxima execução, avisa o que vence na semana seguinte", () => {
    const warning = retentionWarning(preview({ nextWeek: { answers: 2, texts: 0 } }));

    expect(warning).toContain("2 respostas e 0 textos livres terão sido descartados");
  });

  it("com o descarte desligado, diz isso em vez de prever data", () => {
    expect(retentionWarning(preview({ nextRunAt: undefined }))).toContain("desligado");
  });

  it("nada vence, nada a avisar", () => {
    expect(retentionWarning(preview())).toBeUndefined();
  });

  it("diz se já houve descarte", () => {
    expect(lastRunMessage(preview())).toContain("Nenhum descarte");
    expect(lastRunMessage(preview({ lastRunAt: "2026-09-01T03:47:00Z" }))).toContain("Último descarte");
  });
});

describe("resultado da exclusão", () => {
  it("conta o que saiu, no singular e no plural", () => {
    expect(erasureResultMessage({ deleted: true, displaysDeleted: 1, answersDeleted: 4 })).toBe(
      "Excluído: 1 exibição e 4 respostas apagadas.",
    );
  });

  it("pedido sem alvo não é erro: diz que nada foi apagado", () => {
    expect(erasureResultMessage({ deleted: false, displaysDeleted: 0, answersDeleted: 0 })).toContain(
      "Nada foi apagado",
    );
  });
});
