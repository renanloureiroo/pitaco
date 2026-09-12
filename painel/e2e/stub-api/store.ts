/**
 * Estado em memória do simulador.
 *
 * Não há reset global: cada teste E2E cria a **sua** aplicação e trabalha só dentro dela, o
 * que dá isolamento natural sob `fullyParallel: true` (R9). Como aplicação é a raiz de todo o
 * resto, dois testes nunca se enxergam.
 */

export type StubApplication = {
  id: string;
  slug: string;
  name: string;
  status: "active" | "inactive";
  quietPeriodDays?: number;
  retentionDays?: number;
  openTextRetentionDays?: number;
  createdAt: string;
  updatedAt: string;
};

export type StubApiKey = {
  id: string;
  applicationId: string;
  label: string;
  prefix: string;
  /** Guardado só para provar que a leitura nunca o devolve. */
  secret: string;
  status: "active" | "revoked";
  createdAt: string;
  revokedAt?: string;
};

export type StubQuestionOption = { label: string; value: string };

export type StubQuestion = {
  id: string;
  key: string;
  statement: string;
  type: "single_choice" | "multiple_choice" | "rating" | "scale" | "nps" | "free_text";
  position: number;
  required: boolean;
  options?: StubQuestionOption[];
  range?: { min: number; max: number; minLabel?: string; maxLabel?: string };
  condition?: StubCondition;
};

export type StubCondition = {
  sourceKey: string;
  operator: "equals" | "not_equals" | "in" | "between";
  values: string[];
  min?: number;
  max?: number;
};

export type StubSegmentationRule = {
  id: string;
  attribute: string;
  operation: "equals" | "not_equals" | "present" | "absent";
  value?: string;
};

export type StubTrigger = {
  eventName: string;
  windowStart: string;
  windowEnd?: string;
  samplingRate: number;
  rules: StubSegmentationRule[];
};

export type StubVersion = {
  number: number;
  status: "draft" | "published";
  publishedAt?: string;
  changeKind?: "cosmetic" | "semantic";
  changeSummary?: string;
  comparabilityGroup: number;
  questions: StubQuestion[];
  trigger?: StubTrigger;
};

export type StubTransition = {
  from: "draft" | "scheduled" | "active" | "paused" | "ended";
  to: "draft" | "scheduled" | "active" | "paused" | "ended";
  reason:
    | "publication"
    | "manual_pause"
    | "manual_resume"
    | "manual_end"
    | "quota_reached"
    | "window_opened"
    | "window_closed";
  occurredAt: string;
};

export type StubSurvey = {
  id: string;
  applicationId: string;
  name: string;
  state: "draft" | "scheduled" | "active" | "paused" | "ended";
  versions: StubVersion[];
  transitions: StubTransition[];
  /** Exposição: ausente equivale ao padrão — prioridade zero e respeitando o descanso. */
  priority?: number;
  responseQuota?: number;
  ignoresQuietPeriod?: boolean;
  /** Modelo de origem; ausente é pesquisa em branco. */
  templateKind?: "nps" | "csat" | "ces";
  /** Aviso de texto livre: ausente é ligado, com o texto padrão. */
  freeTextNoticeEnabled?: boolean;
  freeTextNoticeText?: string;
  createdAt: string;
};

export type StubRespondent = {
  id: string;
  applicationId: string;
  identityKind: "APP_REFERENCE" | "DEVICE";
  identityValue: string;
  firstSeenAt: string;
  lastSeenAt: string;
};

export type StubAnswer = {
  questionKey: string;
  status: "ANSWERED" | "SKIPPED" | "NOT_APPLICABLE" | "EXPIRED";
  text?: string;
  number?: number;
  options: string[];
};

export type StubDisplay = {
  id: string;
  applicationId: string;
  respondentId: string;
  surveyId: string;
  versionId: string;
  versionNumber: number;
  comparabilityGroup: number;
  outcome: "STARTED" | "COMPLETED" | "DISMISSED";
  sdkVersion?: string;
  attributes: Record<string, string>;
  answers: StubAnswer[];
  openedAt: string;
  closedAt?: string;
};

export type StubObservedEvent = {
  applicationId: string;
  name: string;
  firstSeenAt: string;
  lastSeenAt: string;
};

export type StubObservedAttribute = {
  applicationId: string;
  name: string;
  firstSeenAt: string;
  lastSeenAt: string;
  values: Array<{ value: string; lastSeenAt: string }>;
};

export type StubSdkVersion = {
  applicationId: string;
  version: string;
  requestCount: number;
  recentRequestCount: number;
  firstSeenAt: string;
  lastSeenAt: string;
};

export type StubSdkErrorKind =
  | "render_error"
  | "network_error"
  | "malformed_response"
  | "storage_error"
  | "unknown";

export type StubSdkError = {
  id: string;
  applicationId: string;
  sdkVersion?: string;
  kind: StubSdkErrorKind;
  message: string;
  context: Record<string, unknown>;
  occurredAt: string;
  receivedAt: string;
};

export type StubSuppression = {
  applicationId: string;
  surveyId: string;
  reason: "unknown_question_type" | "unsupported_feature";
  sdkVersion?: string;
  occurredAt: string;
};

export const DEFAULT_FREE_TEXT_NOTICE =
  "Evite escrever dados pessoais, como nome, telefone ou e-mail.";

export type StubDeletionAudit = {
  id: string;
  applicationId: string;
  displaysDeleted: number;
  answersDeleted: number;
  performedAt: string;
};

/** O que a retenção congelou de uma pesquisa; o simulador só guarda quando foi. */
export type StubRetentionSnapshot = {
  surveyId: string;
  discardedBefore: string;
};

export const store = {
  applications: new Map<string, StubApplication>(),
  /** Catálogo: uma entrada por (aplicação, nome), nunca duas. */
  observedEvents: [] as StubObservedEvent[],
  /** Catálogo: um nome por aplicação e um valor por nome. */
  observedAttributes: [] as StubObservedAttribute[],
  apiKeys: new Map<string, StubApiKey>(),
  surveys: new Map<string, StubSurvey>(),
  respondents: new Map<string, StubRespondent>(),
  displays: new Map<string, StubDisplay>(),
  /** Rollup por (aplicação, versão), como no backend. */
  sdkVersions: [] as StubSdkVersion[],
  sdkErrors: [] as StubSdkError[],
  suppressions: [] as StubSuppression[],
  deletionAudits: [] as StubDeletionAudit[],
  retentionSnapshots: [] as StubRetentionSnapshot[],
};

export function versionIdOf(surveyId: string, versionNumber: number): string {
  return `ver-${surveyId}-${versionNumber}`;
}

export function publishedVersion(survey: StubSurvey): StubVersion | undefined {
  return [...survey.versions].reverse().find((version) => version.status === "published");
}

export function draftVersion(survey: StubSurvey): StubVersion | undefined {
  return survey.versions.find((version) => version.status === "draft");
}

/** Versão que a montagem edita: o rascunho aberto, ou a publicada em leitura. */
export function currentVersion(survey: StubSurvey): StubVersion | undefined {
  return draftVersion(survey) ?? publishedVersion(survey);
}

/**
 * A abertura da janela é automática no backend real (um relógio). Aqui ela é resolvida em
 * **toda** leitura da pesquisa: se ficasse só na leitura de transições, um mesmo carregamento
 * de tela poderia ler o estado antigo e as transições novas.
 */
export function settleWindow(survey: StubSurvey): StubSurvey {
  if (survey.state !== "scheduled") {
    return survey;
  }

  const trigger = publishedVersion(survey)?.trigger;
  if (trigger === undefined || trigger.windowStart > new Date().toISOString()) {
    return survey;
  }

  survey.state = "active";
  survey.transitions.push({
    from: "scheduled",
    to: "active",
    reason: "window_opened",
    occurredAt: new Date().toISOString(),
  });

  return survey;
}
