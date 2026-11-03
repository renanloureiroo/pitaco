import { describe, expect, it } from "vitest";

import { warningMessage } from "../lib/warning-messages";
import { publicationWarningSchema } from "../schemas/warnings";

describe("aviso de compatibilidade do SDK", () => {
  it("lê a versão mínima e a proporção sem suporte, e diz as duas na mensagem", () => {
    const warning = publicationWarningSchema.parse({
      code: "compatibility.unsupported_by_majority",
      minRequiredVersion: "1.2.0",
      unsupportedShare: 0.8,
    });

    expect(warning.minRequiredVersion).toBe("1.2.0");
    expect(warning.competingSurveys).toEqual([]);
    expect(warningMessage(warning)).toContain("SDK 1.2.0 ou mais novo");
    expect(warningMessage(warning)).toContain("80%");
  });

  it("os avisos antigos continuam sem os campos novos", () => {
    const warning = publicationWarningSchema.parse({
      code: "trigger.competing_surveys",
      competingSurveys: [],
    });

    expect(warning.minRequiredVersion).toBeUndefined();
    expect(warning.unsupportedShare).toBeUndefined();
  });
});
