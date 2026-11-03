import { z } from "zod";

import { absent } from "@/shared/api";

/**
 * Chaves de acesso.
 *
 * **Nenhum tipo de leitura tem campo de segredo** (FR-014). A ausência é estrutural no backend
 * e é estrutural aqui: `apiKeySchema` é um objeto estrito, então um `secret` que por acidente
 * viesse na resposta seria **descartado** na validação em vez de trafegar pela aplicação.
 * O único tipo que carrega o segredo é `IssuedApiKey`, devolvido apenas pela emissão.
 */

export const apiKeyStatusSchema = z.enum(["active", "revoked"]);
export type ApiKeyStatus = z.infer<typeof apiKeyStatusSchema>;
export const API_KEY_STATUSES = apiKeyStatusSchema.options;

export const API_KEY_STATUS_LABELS: Record<ApiKeyStatus, string> = {
  active: "Ativa",
  revoked: "Revogada",
};

export const apiKeySchema = z.object({
  id: z.string(),
  applicationId: z.string(),
  label: z.string(),
  /** Público e não sensível: é o prefixo que identifica a chave sem revelá-la. */
  prefix: z.string(),
  status: apiKeyStatusSchema,
  createdAt: z.string(),
  /** Ausente enquanto a chave é válida. */
  revokedAt: absent(z.string()),
});

export type ApiKey = z.infer<typeof apiKeySchema>;

export const issuedApiKeySchema = z.object({
  id: z.string(),
  applicationId: z.string(),
  label: z.string(),
  prefix: z.string(),
  createdAt: z.string(),
  secret: z.string(),
});

export type IssuedApiKey = z.infer<typeof issuedApiKeySchema>;

export const issueApiKeyFormSchema = z.object({
  label: z.preprocess(
    (value) => (typeof value === "string" ? value.trim() : ""),
    z
      .string()
      .min(1, "Informe um rótulo para identificar a chave.")
      .max(80, "O rótulo pode ter no máximo 80 caracteres."),
  ),
});

export type IssueApiKeyForm = z.infer<typeof issueApiKeyFormSchema>;
