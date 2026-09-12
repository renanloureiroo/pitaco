import {
  buildUrl,
  pageResponseSchema,
  request,
  type PageResponse,
  type QueryValue,
  type Result,
} from "@/shared/api";

import type { ResultsQuery } from "../schemas/filters";
import { openAnswerSchema, surveyResultsSchema, type OpenAnswer, type SurveyResults } from "../schemas/results";
import { exportPath, exportRoutePath, openAnswersPath, surveyResultsPath } from "./paths";

const openAnswersPageSchema = pageResponseSchema(openAnswerSchema);

function toQuery(query: ResultsQuery): Record<string, QueryValue> {
  return { ...query };
}

export function getSurveyResults(
  applicationId: string,
  surveyId: string,
  query: ResultsQuery,
): Promise<Result<SurveyResults>> {
  return request(surveyResultsSchema, {
    path: surveyResultsPath(applicationId, surveyId),
    query: toQuery(query),
  });
}

export function listOpenAnswers(
  applicationId: string,
  surveyId: string,
  params: { page: number; size: number; q?: string } & ResultsQuery,
): Promise<Result<PageResponse<OpenAnswer>>> {
  const { page, size, q, ...query } = params;

  return request(openAnswersPageSchema, {
    path: openAnswersPath(applicationId, surveyId),
    query: { ...toQuery(query), ...(q !== undefined ? { q } : {}), page, size },
  });
}

/** URL completa do export no backend, para a rota do painel repassar. */
export function backendExportUrl(applicationId: string, surveyId: string, query: ResultsQuery): string {
  return buildUrl(exportPath(applicationId, surveyId), toQuery(query));
}

/** Endereço que o navegador abre para baixar: a rota do painel com o mesmo recorte. */
export function exportHref(applicationId: string, surveyId: string, query: ResultsQuery): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined) {
      search.set(key, String(value));
    }
  }
  const serialized = search.toString();
  const path = exportRoutePath(applicationId, surveyId);
  return serialized === "" ? path : `${path}?${serialized}`;
}
