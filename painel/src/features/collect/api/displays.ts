import {
  pageResponseSchema,
  request,
  type PageResponse,
  type Result,
} from "@/shared/api";

import {
  displayDetailSchema,
  displaySummarySchema,
  type DisplayDetail,
  type DisplaySummary,
} from "../schemas/display";
import { toDisplayFilterQuery, type DisplayFilters } from "../schemas/filters";
import { displayPath, surveyDisplaysPath } from "./paths";

const displayPageSchema = pageResponseSchema(displaySummarySchema);

export function listSurveyDisplays(
  applicationId: string,
  surveyId: string,
  params: { page: number; size: number } & DisplayFilters,
): Promise<Result<PageResponse<DisplaySummary>>> {
  const { page, size, ...filters } = params;

  return request(displayPageSchema, {
    path: surveyDisplaysPath(applicationId, surveyId),
    query: { ...toDisplayFilterQuery(filters), page, size },
  });
}

export function getDisplay(
  applicationId: string,
  displayId: string,
): Promise<Result<DisplayDetail>> {
  return request(displayDetailSchema, { path: displayPath(applicationId, displayId) });
}
