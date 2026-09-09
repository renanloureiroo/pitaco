/**
 * Todo caminho da coleta é escopado pela aplicação — não existe listagem global de exibição
 * nem de respondente. A exibição é alcançada por rota **plana** sob a aplicação (R5): seu
 * identificador é global e ela própria já determina a pesquisa.
 */

function applicationPath(applicationId: string): string {
  return `/applications/${encodeURIComponent(applicationId)}`;
}

export function surveyDisplaysPath(applicationId: string, surveyId: string): string {
  return `${applicationPath(applicationId)}/surveys/${encodeURIComponent(surveyId)}/displays`;
}

export function displayPath(applicationId: string, displayId: string): string {
  return `${applicationPath(applicationId)}/displays/${encodeURIComponent(displayId)}`;
}

export function respondentsPath(applicationId: string): string {
  return `${applicationPath(applicationId)}/respondents`;
}

export function respondentDisplaysPath(applicationId: string, respondentId: string): string {
  return `${respondentsPath(applicationId)}/${encodeURIComponent(respondentId)}/displays`;
}
