import {
  pageResponseSchema,
  request,
  requestNoContent,
  type PageResponse,
  type Result,
} from "@/shared/api";

import {
  surveyVersionDetailSchema,
  surveyVersionSchema,
  versionComparabilitySchema,
  type SurveyVersion,
  type SurveyVersionDetail,
  type VersionComparability,
} from "../schemas/version";
import { surveyPath } from "./paths";

function versionsPath(applicationId: string, surveyId: string): string {
  return `${surveyPath(applicationId, surveyId)}/versions`;
}

const versionPageSchema = pageResponseSchema(surveyVersionSchema);

export function listVersions(
  applicationId: string,
  surveyId: string,
  params: { page: number; size: number },
): Promise<Result<PageResponse<SurveyVersion>>> {
  return request(versionPageSchema, {
    path: versionsPath(applicationId, surveyId),
    query: { page: params.page, size: params.size },
  });
}

export function getVersion(
  applicationId: string,
  surveyId: string,
  number: number,
): Promise<Result<SurveyVersionDetail>> {
  return request(surveyVersionDetailSchema, {
    path: `${versionsPath(applicationId, surveyId)}/${number}`,
  });
}

/** Abrir rascunho é o que permite editar sem alterar o que já está no ar. */
export function openDraftVersion(
  applicationId: string,
  surveyId: string,
): Promise<Result<SurveyVersion>> {
  return request(surveyVersionSchema, {
    path: versionsPath(applicationId, surveyId),
    method: "POST",
  });
}

export function discardDraftVersion(
  applicationId: string,
  surveyId: string,
): Promise<Result<void>> {
  return requestNoContent({
    path: `${versionsPath(applicationId, surveyId)}/draft`,
    method: "DELETE",
  });
}

export function getVersionComparability(
  applicationId: string,
  surveyId: string,
): Promise<Result<VersionComparability>> {
  return request(versionComparabilitySchema, {
    path: `${versionsPath(applicationId, surveyId)}/comparability`,
  });
}
