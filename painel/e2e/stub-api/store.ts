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

export const store = {
  applications: new Map<string, StubApplication>(),
  apiKeys: new Map<string, StubApiKey>(),
  surveys: new Map<string, StubSurvey>(),
};

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
