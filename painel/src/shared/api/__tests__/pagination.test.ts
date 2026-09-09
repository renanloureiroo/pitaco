import { describe, expect, it } from "vitest";
import { z } from "zod";

import {
  DEFAULT_PAGE,
  DEFAULT_SIZE,
  MAX_SIZE,
  pageResponseSchema,
  parsePaginationParams,
  parseStatusParam,
} from "../pagination";

describe("parsePaginationParams", () => {
  it("aplica os padrões quando não há nada na URL", () => {
    expect(parsePaginationParams(undefined)).toEqual({ page: DEFAULT_PAGE, size: DEFAULT_SIZE });
    expect(parsePaginationParams({})).toEqual({ page: DEFAULT_PAGE, size: DEFAULT_SIZE });
  });

  it("lê página e tamanho válidos", () => {
    expect(parsePaginationParams({ page: "3", size: "50" })).toEqual({ page: 3, size: 50 });
  });

  it("aceita o tamanho nos dois limites", () => {
    expect(parsePaginationParams({ size: "1" }).size).toBe(1);
    expect(parsePaginationParams({ size: String(MAX_SIZE) }).size).toBe(MAX_SIZE);
  });

  it("cai no padrão quando o tamanho sai da faixa, em vez de quebrar a tela", () => {
    expect(parsePaginationParams({ size: "0" }).size).toBe(DEFAULT_SIZE);
    expect(parsePaginationParams({ size: "101" }).size).toBe(DEFAULT_SIZE);
  });

  it.each(["abc", "-1", "1.5", "1e3", "", " "])(
    "cai no padrão com o valor inválido %o",
    (value) => {
      expect(parsePaginationParams({ page: value, size: value })).toEqual({
        page: DEFAULT_PAGE,
        size: DEFAULT_SIZE,
      });
    },
  );

  it("usa a primeira ocorrência quando o parâmetro se repete na URL", () => {
    expect(parsePaginationParams({ page: ["2", "9"] }).page).toBe(2);
  });
});

describe("parseStatusParam", () => {
  const allowed = ["active", "inactive"] as const;

  it("aceita os valores conhecidos", () => {
    expect(parseStatusParam("active", allowed)).toBe("active");
    expect(parseStatusParam("inactive", allowed)).toBe("inactive");
  });

  it("trata valor desconhecido ou ausente como 'todas'", () => {
    expect(parseStatusParam("qualquer", allowed)).toBeUndefined();
    expect(parseStatusParam(undefined, allowed)).toBeUndefined();
  });
});

describe("pageResponseSchema", () => {
  const schema = pageResponseSchema(z.object({ id: z.string() }));

  it("valida uma página com itens", () => {
    const parsed = schema.safeParse({
      items: [{ id: "a" }],
      page: 0,
      size: 20,
      total: 1,
      totalPages: 1,
    });

    expect(parsed.success).toBe(true);
  });

  it("aceita página vazia — filtro sem resultado não é erro", () => {
    const parsed = schema.safeParse({ items: [], page: 0, size: 20, total: 0, totalPages: 0 });

    expect(parsed.success).toBe(true);
  });

  it("recusa item que não corresponde ao schema informado", () => {
    const parsed = schema.safeParse({ items: [{ id: 1 }], page: 0, size: 20, total: 1, totalPages: 1 });

    expect(parsed.success).toBe(false);
  });
});
