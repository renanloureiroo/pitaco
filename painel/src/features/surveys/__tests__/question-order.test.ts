import { describe, expect, it } from "vitest";

import { draggedOrder } from "../lib/question-order";

const ids = ["a", "b", "c", "d"];

describe("draggedOrder", () => {
  it("move a pergunta para a posição da pergunta sobre a qual foi solta, descendo", () => {
    expect(draggedOrder(ids, "a", "c")).toEqual(["b", "c", "a", "d"]);
  });

  it("move a pergunta para a posição da pergunta sobre a qual foi solta, subindo", () => {
    expect(draggedOrder(ids, "d", "b")).toEqual(["a", "d", "b", "c"]);
  });

  it("sempre devolve a permutação completa, nunca um subconjunto", () => {
    const reordered = draggedOrder(ids, "b", "d");

    expect(reordered).toHaveLength(ids.length);
    expect([...(reordered ?? [])].sort()).toEqual([...ids].sort());
  });

  it("não há o que enviar quando é solta fora da lista ou sobre si mesma", () => {
    expect(draggedOrder(ids, "a", undefined)).toBeUndefined();
    expect(draggedOrder(ids, "a", "a")).toBeUndefined();
  });

  it("ignora identificadores que a tela não conhece", () => {
    expect(draggedOrder(ids, "zz", "a")).toBeUndefined();
    expect(draggedOrder(ids, "a", "zz")).toBeUndefined();
  });

  it("não altera a lista recebida", () => {
    const original = [...ids];

    draggedOrder(ids, "a", "d");

    expect(ids).toEqual(original);
  });
});
