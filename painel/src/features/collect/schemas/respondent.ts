import { z } from "zod";

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
