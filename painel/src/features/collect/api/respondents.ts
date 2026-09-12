import {
  pageResponseSchema,
  request,
  type PageResponse,
  type Result,
} from "@/shared/api";

import { respondentDisplaySchema, type RespondentDisplay } from "../schemas/display";
import { toDisplayFilterQuery, type DisplayFilters } from "../schemas/filters";
import { respondentSchema, type Respondent } from "../schemas/respondent";
import { respondentDisplaysPath, respondentsPath } from "./paths";

const respondentPageSchema = pageResponseSchema(respondentSchema);
const respondentDisplayPageSchema = pageResponseSchema(respondentDisplaySchema);

export function listRespondents(
  applicationId: string,
  params: { page: number; size: number },
): Promise<Result<PageResponse<Respondent>>> {
  return request(respondentPageSchema, {
    path: respondentsPath(applicationId),
    query: { page: params.page, size: params.size },
  });
}

export function listRespondentDisplays(
  applicationId: string,
  respondentId: string,
  params: { page: number; size: number } & Omit<DisplayFilters, "versionNumber">,
): Promise<Result<PageResponse<RespondentDisplay>>> {
  const { page, size, ...filters } = params;

  const { outcome, openedFrom, openedTo } = toDisplayFilterQuery(filters);

  return request(respondentDisplayPageSchema, {
    path: respondentDisplaysPath(applicationId, respondentId),
    query: { outcome, openedFrom, openedTo, page, size },
  });
}
