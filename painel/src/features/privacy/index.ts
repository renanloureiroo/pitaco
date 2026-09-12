/** Fronteira pública da feature de privacidade e retenção (Princípio I). */

export { eraseRespondent, getRetentionPreview, listDeletionAudits } from "./api/privacy";

export { eraseRespondentAction } from "./actions";

export {
  IDENTITY_KINDS,
  eraseRespondentFormSchema,
  type EraseRespondentInput,
  type IdentityKind,
} from "./schemas/forms";

export {
  type DeletionAudit,
  type RespondentErasure,
  type RetentionPreview,
} from "./schemas/privacy";

export {
  AUDIT_NOTE,
  describeRetentionPolicy,
  erasureResultMessage,
  retentionWarning,
} from "./lib/privacy-labels";

export { EraseRespondentForm } from "./components/erase-respondent-form";
export { DeletionAuditsList } from "./components/deletion-audits-list";
export { RetentionPolicyCard } from "./components/retention-policy-card";
