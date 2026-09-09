import { describe, expect, it } from "vitest";

import { impedimentMessage } from "../lib/impediment-messages";
import { IMPEDIMENT_CODES } from "../schemas/publication";

describe("impedimentMessage", () => {
  it.each(IMPEDIMENT_CODES)("traduz %s em uma frase acionável", (code) => {
    const message = impedimentMessage({ code });

    expect(message).toBeTruthy();
    // Acionável quer dizer que diz o que fazer, não apenas o que está errado.
    expect(message).toMatch(/adicione|defina|escreva|precisa/i);
    expect(message).not.toContain(code);
  });

  it("acrescenta o campo quando o backend o informa", () => {
    expect(impedimentMessage({ code: "trigger.window_invalid", field: "windowEnd" })).toContain(
      "campo: windowEnd",
    );
  });

  it("não menciona campo nenhum quando ele não vem", () => {
    expect(impedimentMessage({ code: "trigger.missing" })).not.toContain("campo:");
  });
});
