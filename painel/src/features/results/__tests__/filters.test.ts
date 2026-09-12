import { describe, expect, it } from "vitest";

import { describeFilters, parseResultsFilters, parseTerm, toResultsQuery } from "../schemas/filters";

const NOW = new Date("2026-09-11T12:00:00Z");

describe("recorte de resultados na URL", () => {
  it("atalho de período vence o personalizado e vira `from` relativo a agora", () => {
    const filters = parseResultsFilters({ periodo: "7", de: "2026-01-01T00:00", ate: "2026-02-01T00:00" });

    expect(filters).toEqual({ period: "7" });
    expect(toResultsQuery(filters, NOW)).toEqual({ from: "2026-09-04T12:00:00.000Z" });
  });

  it("período personalizado invertido é descartado, não enviado", () => {
    expect(parseResultsFilters({ de: "2026-09-10T10:00", ate: "2026-09-08T10:00" })).toEqual({});
  });

  it("atributo com valor e atributo ausente são recortes distintos", () => {
    expect(toResultsQuery(parseResultsFilters({ atributo: "plano", valor: "pro" }))).toEqual({
      attribute: "plano",
      attributeValue: "pro",
    });
    expect(toResultsQuery(parseResultsFilters({ atributo: "plano", ausente: "1" }))).toEqual({
      attribute: "plano",
    });
  });

  it("atributo sem valor e sem `ausente` não é recorte nenhum", () => {
    expect(parseResultsFilters({ atributo: "plano" })).toEqual({});
  });

  it("versão inválida cai no padrão e busca longa demais é ignorada", () => {
    expect(parseResultsFilters({ versao: "0" })).toEqual({});
    expect(parseResultsFilters({ versao: "2" })).toEqual({ versionNumber: 2 });
    expect(parseTerm({ q: "x".repeat(201) })).toBeUndefined();
    expect(parseTerm({ q: "  confuso " })).toBe("confuso");
  });

  it("descreve o recorte ativo em português", () => {
    expect(describeFilters({})).toEqual([]);
    expect(
      describeFilters({ period: "30", attribute: "plano", attributeAbsent: true, versionNumber: 2 }),
    ).toEqual(["Últimos 30 dias", 'Sem o atributo "plano"', "Versão 2"]);
    expect(describeFilters({ from: "2026-09-01T00:00", attribute: "plano", attributeValue: "pro" })).toEqual([
      "De 2026-09-01 00:00 até agora",
      "plano = pro",
    ]);
  });
});
