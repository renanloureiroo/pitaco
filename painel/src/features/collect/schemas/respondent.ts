import { z } from "zod";

/**
 * Respondente: quem a aplicação já viu.
 *
 * `identityValue` é opaco para o Pitaco e é exibido **como veio** — transformá-lo seria mentir
 * sobre o que a aplicação enviou. Já `identityKind` nunca aparece cru na tela (FR-020).
 */

export const respondentIdentityKindSchema = z.enum(["APP_REFERENCE", "DEVICE"]);
export type RespondentIdentityKind = z.infer<typeof respondentIdentityKindSchema>;
export const RESPONDENT_IDENTITY_KINDS = respondentIdentityKindSchema.options;

export const respondentSchema = z.object({
  id: z.string(),
  identityKind: respondentIdentityKindSchema,
  identityValue: z.string(),
  firstSeenAt: z.string(),
  lastSeenAt: z.string(),
});

export type Respondent = z.infer<typeof respondentSchema>;
