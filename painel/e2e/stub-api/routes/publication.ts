import { conflict, json, notFound, nowIso, type Route } from "../http.ts";
import {
  currentVersion,
  draftVersion,
  publishedVersion,
  versionIdOf,
  type StubSurvey,
  type StubVersion,
} from "../store.ts";
import { surveyOf } from "./surveys.ts";

const CHOICE_TYPES = ["single_choice", "multiple_choice"];

type Impediment = { code: string; field?: string; questionKey?: string };

/**
 * Os impedimentos são calculados a partir do estado em memória, e é a **mesma** lista que a
 * publicação usa para recusar — que é justamente o que o contrato promete.
 */
export function impedimentsOf(survey: StubSurvey): Impediment[] {
  // Leitura não muta estado: consultar impedimentos nunca abre uma versão de rascunho.
  const version = currentVersion(survey);
  const impediments: Impediment[] = [];

  if (version === undefined || version.questions.length === 0) {
    impediments.push({ code: "survey.no_questions" });
  }

  for (const question of version?.questions ?? []) {
    if (question.statement.trim() === "") {
      impediments.push({ code: "question.statement_missing", questionKey: question.key });
    }
    if (CHOICE_TYPES.includes(question.type) && (question.options ?? []).length === 0) {
      impediments.push({ code: "question.options_missing", questionKey: question.key });
    }
  }

  if (version?.trigger === undefined) {
    impediments.push({ code: "trigger.missing" });
  } else if (
    version.trigger.windowEnd !== undefined &&
    version.trigger.windowEnd <= version.trigger.windowStart
  ) {
    impediments.push({ code: "trigger.window_invalid", field: "windowEnd" });
  }

  return impediments;
}

function contentOf(version: StubVersion): string {
  return JSON.stringify({
    questions: version.questions.map((question) => ({
      key: question.key,
      statement: question.statement,
      type: question.type,
      position: question.position,
      required: question.required,
      options: question.options ?? [],
      range: question.range ?? null,
    })),
    trigger:
      version.trigger === undefined
        ? null
        : {
            eventName: version.trigger.eventName,
            windowStart: version.trigger.windowStart,
            windowEnd: version.trigger.windowEnd ?? null,
            samplingRate: version.trigger.samplingRate,
            rules: version.trigger.rules.map((rule) => ({
              attribute: rule.attribute,
              operation: rule.operation,
              value: rule.value ?? null,
            })),
          },
  });
}

function sameContent(published: StubVersion, draft: StubVersion): boolean {
  return contentOf(published) === contentOf(draft);
}

export function toVersion(surveyId: string, version: StubVersion) {
  const { number, status, publishedAt, changeKind, changeSummary, comparabilityGroup } = version;

  return {
    id: versionIdOf(surveyId, number),
    number,
    status,
    ...(publishedAt !== undefined ? { publishedAt } : {}),
    ...(changeKind !== undefined ? { changeKind } : {}),
    ...(changeSummary !== undefined ? { changeSummary } : {}),
    comparabilityGroup,
  };
}

export const publicationRoutes: Route[] = [
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId/publication-impediments",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      return survey === undefined
        ? notFound("survey.not_found", "Pesquisa não encontrada.")
        : json(200, { impediments: impedimentsOf(survey) });
    },
  },
  {
    method: "POST",
    pattern: "/applications/:applicationId/surveys/:surveyId/publication",
    handler: ({ params, body }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const impediments = impedimentsOf(survey);
      if (impediments.length > 0) {
        return conflict(
          "survey.publication_blocked",
          "A pesquisa ainda tem impedimentos de publicação.",
        );
      }

      const draft = draftVersion(survey);
      if (draft === undefined) {
        return conflict("survey_version.no_draft", "Não há rascunho a publicar.");
      }

      const previous = publishedVersion(survey);

      if (previous !== undefined && sameContent(previous, draft)) {
        return conflict(
          "survey_version.no_changes",
          "O rascunho é idêntico à versão publicada: não há o que publicar",
        );
      }

      const input = (body ?? {}) as Record<string, unknown>;
      const changeKind =
        previous === undefined
          ? undefined
          : input.changeKind === "cosmetic" || input.changeKind === "semantic"
            ? input.changeKind
            : undefined;

      if (previous !== undefined && changeKind === undefined) {
        return conflict("request.invalid", "A natureza da mudança é obrigatória a partir da v2.");
      }

      draft.status = "published";
      draft.publishedAt = nowIso();
      // Mudança semântica abre um novo grupo: as respostas deixam de ser somáveis.
      draft.comparabilityGroup =
        previous === undefined
          ? 1
          : changeKind === "semantic"
            ? previous.comparabilityGroup + 1
            : previous.comparabilityGroup;

      if (changeKind !== undefined) {
        draft.changeKind = changeKind;
        if (typeof input.changeSummary === "string" && input.changeSummary.trim() !== "") {
          draft.changeSummary = input.changeSummary.trim();
        }
      }

      survey.state = "scheduled";
      survey.transitions.push({
        from: "draft",
        to: "scheduled",
        reason: "publication",
        occurredAt: nowIso(),
      });

      return json(201, toVersion(survey.id, draft));
    },
  },
];

export { currentVersion };
