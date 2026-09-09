import { describe, expect, it } from "vitest";

import {
  apiKeySchema,
  issueApiKeyFormSchema,
  issuedApiKeySchema,
} from "../schemas/api-key";

const key = {
  id: "key-1",
  applicationId: "app-1",
  label: "Produção",
  prefix: "pit_live_abc",
  status: "active",
  createdAt: "2026-09-09T12:00:00Z",
};

describe("apiKeySchema", () => {
  it("não tem onde carregar um segredo: um secret na resposta é descartado (FR-014)", () => {
    const parsed = apiKeySchema.parse({ ...key, secret: "nunca-deveria-vir" });

    expect(parsed).not.toHaveProperty("secret");
    expect(Object.values(parsed)).not.toContain("nunca-deveria-vir");
  });

  it("aceita chave ativa sem data de revogação", () => {
    expect(apiKeySchema.parse(key).revokedAt).toBeUndefined();
  });

  it("lê a data de revogação de uma chave revogada", () => {
    const parsed = apiKeySchema.parse({
      ...key,
      status: "revoked",
      revokedAt: "2026-09-10T12:00:00Z",
    });

    expect(parsed.status).toBe("revoked");
    expect(parsed.revokedAt).toBe("2026-09-10T12:00:00Z");
  });

  it("recusa situação desconhecida", () => {
    expect(apiKeySchema.safeParse({ ...key, status: "expired" }).success).toBe(false);
  });
});

describe("issuedApiKeySchema", () => {
  it("é o único tipo que carrega o segredo", () => {
    const parsed = issuedApiKeySchema.parse({ ...key, secret: "pit_live_abc.segredo" });

    expect(parsed.secret).toBe("pit_live_abc.segredo");
  });

  it("recusa emissão sem segredo — a emissão sem segredo é inútil", () => {
    expect(issuedApiKeySchema.safeParse(key).success).toBe(false);
  });
});

describe("issueApiKeyFormSchema", () => {
  it("exige um rótulo", () => {
    const result = issueApiKeyFormSchema.safeParse({ label: "   " });

    expect(result.success).toBe(false);
    expect(!result.success && result.error.issues[0].message).toMatch(/informe um rótulo/i);
  });

  it("recusa rótulo acima de 80 caracteres", () => {
    expect(issueApiKeyFormSchema.safeParse({ label: "a".repeat(81) }).success).toBe(false);
  });

  it("aceita e apara o rótulo", () => {
    expect(issueApiKeyFormSchema.parse({ label: "  Produção  " }).label).toBe("Produção");
  });
});
