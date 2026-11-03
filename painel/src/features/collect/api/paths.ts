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
