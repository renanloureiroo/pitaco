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
  range?: { min: number; max: number };
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
  createdAt: string;
};

/**
 * Coleta: exibição, resposta e respondente.
 *
 * Estes três não nascem por nenhuma tela — o painel é somente leitura, e quem cria exibição é
 * o SDK. Entram no simulador por semeadura direta (R9 de 002), o que mantém a regra de cada
 * teste criar os próprios dados sem inventar uma tela de escrita que não existe.
 */

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
  status: "ANSWERED" | "SKIPPED" | "EXPIRED";
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
  /** Presente se e somente se o desfecho é final. */
  closedAt?: string;
};

export const store = {
  applications: new Map<string, StubApplication>(),
  apiKeys: new Map<string, StubApiKey>(),
  surveys: new Map<string, StubSurvey>(),
  respondents: new Map<string, StubRespondent>(),
  displays: new Map<string, StubDisplay>(),
};

/**
 * A versão exibida é identificada pelo número dentro da pesquisa; o identificador só existe
 * porque o contrato o devolve. Derivá-lo mantém listagem e detalhe concordando.
 */
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
