import { pageResponseSchema, request, type PageResponse, type Result } from "@/shared/api";

import type { EraseRespondentInput } from "../schemas/forms";
import {
  deletionAuditSchema,
  respondentErasureSchema,
  retentionPreviewSchema,
  type DeletionAudit,
  type RespondentErasure,
  type RetentionPreview,
} from "../schemas/privacy";
import { deletionAuditsPath, respondentsPath, retentionPreviewPath } from "./paths";

const deletionAuditPageSchema = pageResponseSchema(deletionAuditSchema);

/** Irreversível. A identidade vai na query, como o backend a espera. */
export function eraseRespondent(
  applicationId: string,
  form: EraseRespondentInput,
): Promise<Result<RespondentErasure>> {
  return request(respondentErasureSchema, {
    path: respondentsPath(applicationId),
    method: "DELETE",
    query: form.identityKind === "device" ? { deviceId: form.identity } : { reference: form.identity },
  });
}

export function listDeletionAudits(
  applicationId: string,
  params: { page: number; size: number },
): Promise<Result<PageResponse<DeletionAudit>>> {
  return request(deletionAuditPageSchema, {
    path: deletionAuditsPath(applicationId),
    query: { page: params.page, size: params.size },
  });
}

export function getRetentionPreview(applicationId: string): Promise<Result<RetentionPreview>> {
  return request(retentionPreviewSchema, { path: retentionPreviewPath(applicationId) });
}
