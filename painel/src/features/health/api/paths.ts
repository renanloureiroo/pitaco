function applicationPath(applicationId: string): string {
  return `/applications/${encodeURIComponent(applicationId)}`;
}

export function sdkVersionsPath(applicationId: string): string {
  return `${applicationPath(applicationId)}/sdk-versions`;
}

export function sdkErrorsPath(applicationId: string): string {
  return `${applicationPath(applicationId)}/sdk-errors`;
}

export function surveyHealthPath(applicationId: string, surveyId: string): string {
  return `${applicationPath(applicationId)}/surveys/${encodeURIComponent(surveyId)}/health`;
}
