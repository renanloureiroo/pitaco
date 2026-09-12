import { cache } from "react";

import type { FreeTextNoticeForm } from "../schemas/notice";
import {
  pageResponseSchema,
  request,
  requestNoContent,
  type PageResponse,
  type Result,
} from "@/shared/api";

import { quotaProgressSchema, type ExposureForm, type QuotaProgress } from "../schemas/exposure";
import {
  surveyDetailSchema,
  surveySchema,
  type DuplicateSurveyForm,
  type Survey,
  type SurveyDetail,
  type SurveyTemplate,
} from "../schemas/survey";
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

export function createSurvey(
  applicationId: string,
  name: string,
  template?: SurveyTemplate,
): Promise<Result<Survey>> {
  return request(surveySchema, {
    path: surveysPath(applicationId),
    method: "POST",
    body: template === undefined ? { name } : { name, template },
  });
}

/** A cópia pode ir para outra aplicação; o `Location` e o corpo dizem onde ela nasceu. */
export function duplicateSurvey(
  applicationId: string,
  surveyId: string,
  form: DuplicateSurveyForm,
): Promise<Result<Survey>> {
  return request(surveySchema, {
    path: `${surveyPath(applicationId, surveyId)}/duplicate`,
    method: "POST",
    body: {
      targetApplicationId: form.targetApplicationId,
      ...(form.name !== undefined ? { name: form.name } : {}),
    },
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

/**
 * O PATCH distingue cota ausente (não mexe) de `null` (remove). O formulário de exposição sempre
 * mostra a cota, então o que ficou em branco vira `null` de propósito.
 */
export function updateSurveyExposure(
  applicationId: string,
  surveyId: string,
  form: ExposureForm,
): Promise<Result<Survey>> {
  return request(surveySchema, {
    path: surveyPath(applicationId, surveyId),
    method: "PATCH",
    body: {
      priority: form.priority,
      responseQuota: form.responseQuota ?? null,
      ignoresQuietPeriod: form.ignoresQuietPeriod,
    },
  });
}

/** Texto em branco volta ao padrão: vai como `null`, que o PATCH entende como remoção. */
export function updateFreeTextNotice(
  applicationId: string,
  surveyId: string,
  form: FreeTextNoticeForm,
): Promise<Result<Survey>> {
  return request(surveySchema, {
    path: surveyPath(applicationId, surveyId),
    method: "PATCH",
    body: {
      freeTextNoticeEnabled: form.enabled,
      freeTextNoticeText: form.customText ?? null,
    },
  });
}

/** Concluídas rumo à cota; a contagem é da coleta, por isso não vem na leitura da pesquisa. */
export function getQuotaProgress(
  applicationId: string,
  surveyId: string,
): Promise<Result<QuotaProgress>> {
  return request(quotaProgressSchema, {
    path: `${surveyPath(applicationId, surveyId)}/quota-progress`,
  });
}

/** Só é oferecido enquanto a pesquisa nunca foi publicada; o `409` cobre a corrida (FR-020). */
export function discardSurvey(
  applicationId: string,
  surveyId: string,
): Promise<Result<void>> {
  return requestNoContent({ path: surveyPath(applicationId, surveyId), method: "DELETE" });
}
