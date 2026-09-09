import { describe, expect, it } from "vitest";

import { applicationSchema, applicationSummarySchema } from "../schemas/application";
import { applicationListParamsSchema, createApplicationFormSchema } from "../schemas/forms";

function parseForm(input: Record<string, string>) {
  return createApplicationFormSchema.safeParse(input);
}

function fieldErrors(result: ReturnType<typeof parseForm>): Record<string, string> {
  if (result.success) {
    return {};
  }
  return Object.fromEntries(
    result.error.issues.map((issue) => [String(issue.path[0]), issue.message]),
  );
}

describe("createApplicationFormSchema", () => {
  it("aceita um FormData que só traz o nome — o slug é derivado pelo backend", () => {
    const result = parseForm({ name: "Acme App" });

    expect(result.success).toBe(true);
    expect(result.success && result.data).toEqual({
      name: "Acme App",
      slug: undefined,
      quietPeriodDays: undefined,
      retentionDays: undefined,
      openTextRetentionDays: undefined,
    });
  });

  it("exige o nome", () => {
    expect(fieldErrors(parseForm({ name: "   " })).name).toMatch(/informe o nome/i);
  });

  it("recusa nome acima de 120 caracteres", () => {
    expect(fieldErrors(parseForm({ name: "a".repeat(121) })).name).toMatch(/120/);
  });

  it.each(["Acme App", "acme_app", "-acme", "acme-", "acme--app", "ACME"])(
    "recusa o slug inválido %o",
    (slug) => {
      expect(fieldErrors(parseForm({ name: "Acme", slug })).slug).toBeDefined();
    },
  );

  it.each(["acme", "acme-app", "acme-app-2", "a1"])("aceita o slug válido %o", (slug) => {
    const result = parseForm({ name: "Acme", slug });
    expect(result.success && result.data.slug).toBe(slug);
  });

  it("recusa slug acima de 50 caracteres", () => {
    expect(fieldErrors(parseForm({ name: "Acme", slug: "a".repeat(51) })).slug).toBeDefined();
  });

  it("recusa prazo zero — o mínimo é 1 dia", () => {
    expect(fieldErrors(parseForm({ name: "Acme", retentionDays: "0" })).retentionDays).toMatch(
      /pelo menos 1 dia/i,
    );
  });

  it("preserva a ausência de prazo como ausência, não como zero", () => {
    const result = parseForm({ name: "Acme", retentionDays: "" });

    expect(result.success && result.data.retentionDays).toBeUndefined();
    expect(result.success && result.data.retentionDays).not.toBe(0);
  });

  it("recusa prazo que não é inteiro", () => {
    expect(fieldErrors(parseForm({ name: "Acme", quietPeriodDays: "7.5" })).quietPeriodDays)
      .toBeDefined();
    expect(fieldErrors(parseForm({ name: "Acme", quietPeriodDays: "-3" })).quietPeriodDays)
      .toBeDefined();
  });

  it("converte prazo informado em número", () => {
    const result = parseForm({ name: "Acme", quietPeriodDays: "30" });

    expect(result.success && result.data.quietPeriodDays).toBe(30);
  });

  it("não replica a invariante de backend entre retenções", () => {
    // Texto livre maior que a retenção geral é decidido pelo backend; o painel deixa passar.
    const result = parseForm({ name: "Acme", retentionDays: "30", openTextRetentionDays: "90" });

    expect(result.success).toBe(true);
  });
});

describe("applicationSchema", () => {
  const base = {
    id: "app-1",
    slug: "acme",
    name: "Acme",
    status: "active",
    createdAt: "2026-09-09T12:00:00Z",
    updatedAt: "2026-09-09T12:00:00Z",
  };

  it("aceita detalhe sem nenhum prazo configurado", () => {
    const parsed = applicationSchema.safeParse(base);

    expect(parsed.success).toBe(true);
    expect(parsed.success && parsed.data.retentionDays).toBeUndefined();
  });

  it("preserva prazo zero vindo do backend em vez de tratá-lo como ausência", () => {
    const parsed = applicationSchema.safeParse({ ...base, quietPeriodDays: 0 });

    expect(parsed.success && parsed.data.quietPeriodDays).toBe(0);
  });

  it("recusa situação desconhecida", () => {
    expect(applicationSummarySchema.safeParse({ ...base, status: "archived" }).success).toBe(false);
  });
});

describe("applicationListParamsSchema", () => {
  it("aceita as situações conhecidas", () => {
    expect(applicationListParamsSchema.parse({ status: "active" }).status).toBe("active");
  });

  it("trata situação desconhecida como 'todas' em vez de quebrar", () => {
    expect(applicationListParamsSchema.parse({ status: "arquivada" }).status).toBeUndefined();
    expect(applicationListParamsSchema.parse({}).status).toBeUndefined();
  });
});
