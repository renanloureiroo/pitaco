function applicationPath(applicationId: string): string {
  return `/applications/${encodeURIComponent(applicationId)}`;
}

export function respondentsPath(applicationId: string): string {
  return `${applicationPath(applicationId)}/respondents`;
}

export function deletionAuditsPath(applicationId: string): string {
  return `${applicationPath(applicationId)}/deletion-audits`;
}

export function retentionPreviewPath(applicationId: string): string {
  return `${applicationPath(applicationId)}/retention-preview`;
}
