import { pageResponseSchema, request, type PageResponse, type Result } from "@/shared/api";

import type { SdkErrorFilters } from "../schemas/filters";
import {
  sdkErrorReportSchema,
  sdkVersionsSchema,
  surveyHealthSchema,
  type SdkErrorReport,
  type SdkVersions,
  type SurveyHealth,
} from "../schemas/health";
import { sdkErrorsPath, sdkVersionsPath, surveyHealthPath } from "./paths";

const sdkErrorsPageSchema = pageResponseSchema(sdkErrorReportSchema);

export function getSdkVersions(applicationId: string): Promise<Result<SdkVersions>> {
  return request(sdkVersionsSchema, { path: sdkVersionsPath(applicationId) });
}

export function listSdkErrors(
  applicationId: string,
  params: { page: number; size: number } & SdkErrorFilters,
): Promise<Result<PageResponse<SdkErrorReport>>> {
  const { page, size, kind, sdkVersion } = params;

  return request(sdkErrorsPageSchema, {
    path: sdkErrorsPath(applicationId),
    query: {
      page,
      size,
      ...(kind !== undefined ? { kind } : {}),
      ...(sdkVersion !== undefined ? { sdkVersion } : {}),
    },
  });
}

export function getSurveyHealth(
  applicationId: string,
  surveyId: string,
): Promise<Result<SurveyHealth>> {
  return request(surveyHealthSchema, { path: surveyHealthPath(applicationId, surveyId) });
}
