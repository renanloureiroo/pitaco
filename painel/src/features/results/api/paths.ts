export function surveyResultsPath(applicationId: string, surveyId: string): string {
  return `/applications/${encodeURIComponent(applicationId)}/surveys/${encodeURIComponent(surveyId)}/results`;
}

export function openAnswersPath(applicationId: string, surveyId: string): string {
  return `${surveyResultsPath(applicationId, surveyId)}/open-answers`;
}

export function exportPath(applicationId: string, surveyId: string): string {
  return `${surveyResultsPath(applicationId, surveyId)}/export`;
}

/** Rota do painel que repassa o CSV do backend em fluxo — nada no navegador fala com a API. */
export function exportRoutePath(applicationId: string, surveyId: string): string {
  return `/api/aplicacoes/${encodeURIComponent(applicationId)}/pesquisas/${encodeURIComponent(surveyId)}/resultados/export`;
}
