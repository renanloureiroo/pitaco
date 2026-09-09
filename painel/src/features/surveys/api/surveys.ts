import { cache } from "react";

import {
  pageResponseSchema,
  request,
  requestNoContent,
  type PageResponse,
  type Result,
} from "@/shared/api";

import { surveyDetailSchema, surveySchema, type Survey, type SurveyDetail } from "../schemas/survey";
import { surveyPath, surveysPath } from "./paths";

const surveyPageSchema = pageResponseSchema(surveySchema);

export function listSurveys(
  applicationId: string,
  params: { page: number; size: number },
): Promise<Result<PageResponse<Survey>>> {
  return request(surveyPageSchema, {
    path: surveysPath(applicationId),
    query: { page: params.page, size: params.size },
  });
}

/**
 * Deduplicado por requisição: o cabeçalho da pesquisa (no layout) e a página pedem o mesmo
 * detalhe. `React.cache` tem escopo de requisição, então não há dado velho entre navegações.
 */
export const getSurvey = cache(
  (applicationId: string, surveyId: string): Promise<Result<SurveyDetail>> =>
    request(surveyDetailSchema, { path: surveyPath(applicationId, surveyId) }),
);

export function createSurvey(applicationId: string, name: string): Promise<Result<Survey>> {
  return request(surveySchema, {
    path: surveysPath(applicationId),
    method: "POST",
    body: { name },
  });
}

export function renameSurvey(
  applicationId: string,
  surveyId: string,
  name: string,
): Promise<Result<Survey>> {
  return request(surveySchema, {
    path: surveyPath(applicationId, surveyId),
    method: "PATCH",
    body: { name },
  });
}

/** Só é oferecido enquanto a pesquisa nunca foi publicada; o `409` cobre a corrida (FR-020). */
export function discardSurvey(
  applicationId: string,
  surveyId: string,
): Promise<Result<void>> {
  return requestNoContent({ path: surveyPath(applicationId, surveyId), method: "DELETE" });
}
