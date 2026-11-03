import { cache } from "react";

import { pageResponseSchema, request, type PageResponse, type Result } from "@/shared/api";

import {
  applicationSchema,
  applicationSummarySchema,
  createdApplicationSchema,
  observedAttributeSchema,
  observedEventSchema,
  type Application,
  type ObservedAttribute,
  type ObservedEvent,
  type ApplicationStatus,
  type CreatedApplication,
  type ApplicationSummary,
} from "../schemas/application";
import type { CreateApplicationForm, UpdateApplicationForm } from "../schemas/forms";

/**
 * Leituras e escrita de aplicação (contracts/backend-api.md § Aplicações).
 *
 * Nenhuma leitura é cacheada entre requisições — um painel administrativo não pode mostrar
 * dado velho. `React.cache` aqui deduplica apenas **dentro da mesma requisição**: a aplicação
 * é lida pelo breadcrumb do layout e pela página, e não faz sentido pedir duas vezes.
 */

const applicationPageSchema = pageResponseSchema(applicationSummarySchema);
const observedEventPageSchema = pageResponseSchema(observedEventSchema);
const observedAttributePageSchema = pageResponseSchema(observedAttributeSchema);

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

/**
 * O PATCH distingue campo ausente (não mexe) de `null` (remove). Como o formulário de edição
 * sempre mostra os três prazos, o que ficou em branco vira `null` de propósito — é a única forma
 * de o pesquisador limpar um prazo pela tela.
 */
export function updateApplication(
  applicationId: string,
  form: UpdateApplicationForm,
): Promise<Result<Application>> {
  return request(applicationSchema, {
    path: `/applications/${encodeURIComponent(applicationId)}`,
    method: "PATCH",
    body: {
      name: form.name,
      quietPeriodDays: form.quietPeriodDays ?? null,
      retentionDays: form.retentionDays ?? null,
      openTextRetentionDays: form.openTextRetentionDays ?? null,
    },
  });
}

function transition(
  applicationId: string,
  action: "activate" | "deactivate",
): Promise<Result<Application>> {
  return request(applicationSchema, {
    path: `/applications/${encodeURIComponent(applicationId)}/${action}`,
    method: "POST",
  });
}

/** Desativar interrompe a entrega pelo SDK; o histórico continua legível. Reversível. */
export function deactivateApplication(applicationId: string) {
  return transition(applicationId, "deactivate");
}

export function activateApplication(applicationId: string) {
  return transition(applicationId, "activate");
}

/**
 * Catálogo dos eventos já vistos na aplicação, do mais recente para o mais antigo. É sugestão
 * para a autoria, não restrição: o evento de disparo pode ainda não ter sido visto.
 */
export function listObservedEvents(
  applicationId: string,
  params: { page: number; size: number },
): Promise<Result<PageResponse<ObservedEvent>>> {
  return request(observedEventPageSchema, {
    path: `/applications/${encodeURIComponent(applicationId)}/events`,
    query: { page: params.page, size: params.size },
  });
}

/** Catálogo dos atributos já enviados pelo app, com os valores vistos, para montar regras. */
export function listObservedAttributes(
  applicationId: string,
  params: { page: number; size: number },
): Promise<Result<PageResponse<ObservedAttribute>>> {
  return request(observedAttributePageSchema, {
    path: `/applications/${encodeURIComponent(applicationId)}/attributes`,
    query: { page: params.page, size: params.size },
  });
}
