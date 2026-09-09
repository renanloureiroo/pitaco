import { describe, expect, it } from "vitest";

import {
  PERIOD_ERROR,
  hasAnyFilter,
  parseDisplayFilters,
  periodError,
  toDisplayFilterQuery,
  toLocalInput,
  toUtcInstant,
} from "../schemas/filters";

describe("parseDisplayFilters — valor desconhecido cai no padrão", () => {
  it("ignora desfecho que o backend não aceita, em vez de quebrar a tela", () => {
    expect(parseDisplayFilters({ desfecho: "ABANDONED" }).outcome).toBeUndefined();
    expect(parseDisplayFilters({ desfecho: "qualquer" }).outcome).toBeUndefined();
    expect(parseDisplayFilters({ desfecho: "COMPLETED" }).outcome).toBe("COMPLETED");
  });

  it("ignora número de versão malformado, zero ou negativo — versão começa em 1", () => {
    expect(parseDisplayFilters({ versao: "3" }).versionNumber).toBe(3);
    expect(parseDisplayFilters({ versao: "0" }).versionNumber).toBeUndefined();
    expect(parseDisplayFilters({ versao: "-1" }).versionNumber).toBeUndefined();
    expect(parseDisplayFilters({ versao: "1e3" }).versionNumber).toBeUndefined();
    expect(parseDisplayFilters({ versao: "três" }).versionNumber).toBeUndefined();
  });

  it("ignora data-hora malformada e data que não existe no calendário", () => {
    expect(parseDisplayFilters({ de: "ontem" }).from).toBeUndefined();
    expect(parseDisplayFilters({ de: "2026-09-08" }).from).toBeUndefined();
    expect(parseDisplayFilters({ de: "2026-02-31T10:00" }).from).toBeUndefined();
    expect(parseDisplayFilters({ de: "2026-09-08T10:00" }).from).toBe("2026-09-08T10:00");
  });

  it("lê o primeiro valor quando o parâmetro se repete na URL", () => {
    expect(parseDisplayFilters({ versao: ["2", "9"] }).versionNumber).toBe(2);
  });

  it("sem nenhum parâmetro, não há recorte aplicado", () => {
    const filters = parseDisplayFilters(undefined);

    expect(filters).toEqual({});
    expect(hasAnyFilter(filters)).toBe(false);
    expect(hasAnyFilter(parseDisplayFilters({ desfecho: "STARTED" }))).toBe(true);
  });
});

describe("período incoerente — duas validações, propósitos diferentes", () => {
  const from = "2026-09-10T10:00";
  const to = "2026-09-08T10:00";

  it("no formulário é recusado com mensagem, para a pessoa poder corrigir (FR-007)", () => {
    expect(periodError(from, to)).toBe(PERIOD_ERROR);
  });

  it("no servidor é ignorado inteiro, e o resto do recorte sobrevive", () => {
    const filters = parseDisplayFilters({ de: from, ate: to, desfecho: "COMPLETED" });

    expect(filters.from).toBeUndefined();
    expect(filters.to).toBeUndefined();
    expect(filters.outcome).toBe("COMPLETED");
  });

  it("não recusa o que ainda não é um par, nem o período de um instante só", () => {
    expect(periodError(from, undefined)).toBeUndefined();
    expect(periodError(undefined, to)).toBeUndefined();
    expect(periodError("", "")).toBeUndefined();
    expect(periodError(to, to)).toBeUndefined();
  });
});

describe("conversão do fuso de referência (R3)", () => {
  it("interpreta o que a pessoa digita no fuso de referência, não em UTC", () => {
    // Meia-noite de 8 de setembro em Brasília é 03:00Z — filtrar por "8 de setembro" precisa
    // casar com o que a tela mostra como 8 de setembro.
    expect(toUtcInstant("2026-09-08T00:00")).toBe("2026-09-08T03:00:00.000Z");
  });

  it("volta e ida devolvem o mesmo valor", () => {
    const local = "2026-09-08T14:22";

    expect(toLocalInput(toUtcInstant(local))).toBe(local);
    expect(toUtcInstant(toLocalInput("2026-09-08T17:22:00Z"))).toBe("2026-09-08T17:22:00.000Z");
  });

  it("entrada malformada não vira instante inventado", () => {
    expect(toUtcInstant("ontem")).toBeUndefined();
    expect(toUtcInstant(undefined)).toBeUndefined();
    expect(toLocalInput("nem data")).toBeUndefined();
    expect(toLocalInput(undefined)).toBeUndefined();
  });
});

describe("toDisplayFilterQuery", () => {
  it("traduz o recorte para o vocabulário da API", () => {
    const query = toDisplayFilterQuery({
      versionNumber: 3,
      outcome: "DISMISSED",
      from: "2026-09-08T00:00",
      to: "2026-09-08T23:59",
    });

    expect(query).toEqual({
      versionNumber: 3,
      outcome: "DISMISSED",
      openedFrom: "2026-09-08T03:00:00.000Z",
      openedTo: "2026-09-09T02:59:00.000Z",
    });
  });

  it("filtro ausente não vira chave alguma — ausente não restringe", () => {
    expect(toDisplayFilterQuery({})).toEqual({});
  });
});
