/** Todo caminho da API é escopado pela aplicação — não existe listagem global de pesquisa. */
export function surveysPath(applicationId: string): string {
  return `/applications/${encodeURIComponent(applicationId)}/surveys`;
}

export function surveyPath(applicationId: string, surveyId: string): string {
  return `${surveysPath(applicationId)}/${encodeURIComponent(surveyId)}`;
}
