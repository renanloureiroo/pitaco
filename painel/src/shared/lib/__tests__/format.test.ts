import { describe, expect, it } from "vitest";

import {
  NOT_CONFIGURED,
  REFERENCE_TIME_ZONE,
  TIMEZONE_NOTE,
  formatDate,
  formatDateTime,
  formatDays,
  formatSamplingRate,
  orNotConfigured,
} from "../format";

describe("orNotConfigured", () => {
  it("exibe ausente como 'não configurado'", () => {
    expect(orNotConfigured(undefined)).toBe(NOT_CONFIGURED);
    expect(orNotConfigured(null)).toBe(NOT_CONFIGURED);
  });

  it("preserva o zero — 0 é um valor, não uma ausência", () => {
    expect(orNotConfigured(0)).toBe("0");
    expect(orNotConfigured(0)).not.toBe(NOT_CONFIGURED);
  });

  it("preserva string vazia sem confundi-la com ausência", () => {
    expect(orNotConfigured("")).toBe("");
  });
});

describe("formatDays", () => {
  it("exibe prazo ausente como 'não configurado'", () => {
    expect(formatDays(undefined)).toBe(NOT_CONFIGURED);
  });

  it("exibe zero dias em vez de tratá-lo como ausência", () => {
    expect(formatDays(0)).toBe("0 dias");
  });

  it("pluraliza", () => {
    expect(formatDays(1)).toBe("1 dia");
    expect(formatDays(30)).toBe("30 dias");
  });
});

describe("formatSamplingRate", () => {
  it("exibe a taxa zero, que é diferente de não configurada", () => {
    expect(formatSamplingRate(0)).toBe("0%");
    expect(formatSamplingRate(undefined)).toBe(NOT_CONFIGURED);
  });

  it("converte a fração em percentual", () => {
    expect(formatSamplingRate(1)).toBe("100%");
    expect(formatSamplingRate(0.25)).toBe("25%");
  });
});

describe("formatDateTime e formatDate", () => {
  it("formata ISO UTC em pt-BR", () => {
    expect(formatDate("2026-09-09T15:30:00Z")).toBe("09/09/2026");
    expect(formatDateTime("2026-09-09T15:30:00Z")).toMatch(/^09\/09\/2026/);
  });

  it("devolve a entrada quando ela não é uma data — não inventa 'Invalid Date' na tela", () => {
    expect(formatDate("nem data")).toBe("nem data");
    expect(formatDateTime("nem data")).toBe("nem data");
  });
});

describe("rótulo do fuso de referência (R3)", () => {
  it("anuncia o mesmo deslocamento que formatDateTime aplica", () => {
    // 15:30Z vira 12:30 no fuso de referência: o rótulo precisa dizer exatamente esse −3.
    const [, time] = formatDateTime("2026-09-09T15:30:00Z").split(", ");

    expect(time).toBe("12:30");
    expect(TIMEZONE_NOTE).toContain("UTC−3");
  });

  it("nomeia o fuso que os formatadores usam, e não um fuso qualquer", () => {
    const offset = new Intl.DateTimeFormat("en-US", {
      timeZone: REFERENCE_TIME_ZONE,
      timeZoneName: "longOffset",
    })
      .formatToParts(new Date("2026-09-09T15:30:00Z"))
      .find((part) => part.type === "timeZoneName")?.value;

    expect(offset).toBe("GMT-03:00");
  });

  it("diz o fuso por extenso — um instante sem fuso declarado é ambíguo para quem lê", () => {
    expect(TIMEZONE_NOTE).not.toBe("");
    expect(TIMEZONE_NOTE.toLowerCase()).toContain("horários");
  });
});
