import { describe, expect, it } from "vitest";
import { z } from "zod";

import { absent } from "../optional";

const schema = z.object({ nome: z.string(), prazo: absent(z.number()) });

describe("absent — as duas formas de ausência que a API pode usar", () => {
  it("aceita a chave omitida", () => {
    expect(schema.parse({ nome: "x" })).toEqual({ nome: "x", prazo: undefined });
  });

  it("aceita a chave presente com null — é assim que o backend já serializou", () => {
    // Um `.optional()` puro recusaria isto, e a resposta inteira viraria erro de tela.
    expect(schema.parse({ nome: "x", prazo: null })).toEqual({ nome: "x", prazo: undefined });
  });

  it("produz sempre `undefined`, nunca `null`, para quem lê", () => {
    const parsed = schema.parse({ nome: "x", prazo: null });

    expect(parsed.prazo).toBeUndefined();
    expect(parsed.prazo).not.toBeNull();
  });

  it("preserva o zero e a string vazia, que são valores e não ausências", () => {
    expect(schema.parse({ nome: "x", prazo: 0 }).prazo).toBe(0);
    expect(z.object({ texto: absent(z.string()) }).parse({ texto: "" }).texto).toBe("");
  });

  it("continua recusando o valor de tipo errado", () => {
    expect(schema.safeParse({ nome: "x", prazo: "trinta" }).success).toBe(false);
  });
});
