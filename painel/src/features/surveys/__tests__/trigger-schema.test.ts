import { describe, expect, it } from "vitest";

import {
  requiresValue,
  segmentationRuleFormSchema,
  triggerFormSchema,
  triggerSchema,
} from "../schemas/trigger";

function parseRule(input: Record<string, string>) {
  return segmentationRuleFormSchema.safeParse(input);
}

function issuePaths(result: { success: boolean; error?: { issues: Array<{ path: PropertyKey[] }> } }) {
  return result.success ? [] : (result.error?.issues ?? []).map((issue) => String(issue.path[0]));
}

describe("triggerFormSchema", () => {
  const base = { eventName: "checkout.completed", windowStart: "2026-09-09T12:00" };

  it.each([
    "Checkout",
    "1checkout",
    "check-out",
    "checkout completed",
    "c",
    `a${"b".repeat(80)}`,
  ])("recusa o nome de evento fora do padrão: %o", (eventName) => {
    expect(issuePaths(triggerFormSchema.safeParse({ ...base, eventName }))).toContain("eventName");
  });

  it.each(["checkout.completed", "app_open", "a1", "pedido.item_adicionado"])(
    "aceita o nome de evento %o",
    (eventName) => {
      expect(triggerFormSchema.safeParse({ ...base, eventName }).success).toBe(true);
    },
  );

  it("exige o início da janela", () => {
    expect(issuePaths(triggerFormSchema.safeParse({ ...base, windowStart: "" }))).toContain(
      "windowStart",
    );
  });

  it("trata o fim da janela em branco como janela aberta", () => {
    const result = triggerFormSchema.safeParse({ ...base, windowEnd: "" });

    expect(result.success && result.data.windowEnd).toBeUndefined();
  });

  it("assume 0.0 quando a taxa de amostragem não é informada", () => {
    const result = triggerFormSchema.safeParse(base);

    expect(result.success && result.data.samplingRate).toBe(0);
  });

  it.each(["0", "0.5", "1"])("aceita a taxa %o", (samplingRate) => {
    expect(triggerFormSchema.safeParse({ ...base, samplingRate }).success).toBe(true);
  });

  it.each(["-0.1", "1.1", "2"])("recusa a taxa fora de 0..1: %o", (samplingRate) => {
    expect(issuePaths(triggerFormSchema.safeParse({ ...base, samplingRate }))).toContain(
      "samplingRate",
    );
  });

  it("não bloqueia janela invertida — isso é invariante do backend", () => {
    const result = triggerFormSchema.safeParse({
      ...base,
      windowStart: "2026-09-10T12:00",
      windowEnd: "2026-09-09T12:00",
    });

    expect(result.success).toBe(true);
  });
});

describe("segmentationRuleFormSchema", () => {
  it.each(["equals", "not_equals"] as const)("exige valor em %s", (operation) => {
    expect(requiresValue(operation)).toBe(true);
    expect(issuePaths(parseRule({ attribute: "plano", operation }))).toContain("value");
  });

  it.each(["present", "absent"] as const)("recusa valor em %s", (operation) => {
    expect(requiresValue(operation)).toBe(false);
    expect(
      issuePaths(parseRule({ attribute: "plano", operation, value: "pro" })),
    ).toContain("value");
  });

  it("aceita equals com valor e present sem valor", () => {
    expect(parseRule({ attribute: "plano", operation: "equals", value: "pro" }).success).toBe(true);
    expect(parseRule({ attribute: "plano", operation: "present" }).success).toBe(true);
  });

  it("exige o atributo", () => {
    expect(issuePaths(parseRule({ attribute: " ", operation: "present" }))).toContain("attribute");
  });

  it("recusa operação desconhecida", () => {
    expect(parseRule({ attribute: "plano", operation: "contains" }).success).toBe(false);
  });
});

describe("triggerSchema", () => {
  it("assume lista de regras vazia quando ela não vem", () => {
    const parsed = triggerSchema.parse({
      eventName: "app_open",
      windowStart: "2026-09-09T12:00:00Z",
      samplingRate: 0,
    });

    expect(parsed.rules).toEqual([]);
    expect(parsed.windowEnd).toBeUndefined();
  });
});
