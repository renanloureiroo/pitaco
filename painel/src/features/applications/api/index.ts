import { cache } from "react";

import { pageResponseSchema, request, type PageResponse, type Result } from "@/shared/api";

import {
  applicationSchema,
  applicationSummarySchema,
  createdApplicationSchema,
  type Application,
  type ApplicationStatus,
  type CreatedApplication,
  type ApplicationSummary,
} from "../schemas/application";
import type { CreateApplicationForm } from "../schemas/forms";

/**
 * Leituras e escrita de aplicação (contracts/backend-api.md § Aplicações).
 *
 * Nenhuma leitura é cacheada entre requisições — um painel administrativo não pode mostrar
 * dado velho. `React.cache` aqui deduplica apenas **dentro da mesma requisição**: a aplicação
 * é lida pelo breadcrumb do layout e pela página, e não faz sentido pedir duas vezes.
 */

const applicationPageSchema = pageResponseSchema(applicationSummarySchema);

export function listApplications(params: {
  status?: ApplicationStatus;
  page: number;
  size: number;
}): Promise<Result<PageResponse<ApplicationSummary>>> {
  return request(applicationPageSchema, {
    path: "/applications",
    query: { status: params.status, page: params.page, size: params.size },
  });
}

export const getApplication = cache(
  (applicationId: string): Promise<Result<Application>> =>
    request(applicationSchema, { path: `/applications/${encodeURIComponent(applicationId)}` }),
);

export function createApplication(
  form: CreateApplicationForm,
): Promise<Result<CreatedApplication>> {
  return request(createdApplicationSchema, {
    path: "/applications",
    method: "POST",
    // Campos ausentes não são enviados: `undefined` desaparece na serialização, e é assim que
    // o backend distingue "não configurado" de um valor.
    body: form,
  });
}
