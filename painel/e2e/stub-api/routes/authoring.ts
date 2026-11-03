import { json, nextId, notFound, nowIso, validation, type Route } from "../http.ts";
import {
  currentVersion,
  publishedVersion,
  store,
  type StubQuestion,
  type StubSurvey,
  type StubTrigger,
} from "../store.ts";
import { surveyOf, toSurvey } from "./surveys.ts";

/**
 * Duplicação com as mesmas regras do backend: parte da versão publicada, ou do rascunho de quem
 * nunca publicou; chaves novas, condições reapontadas; janela encerrada vira aberta desde agora.
 */

function duplicatedQuestions(questions: StubQuestion[]): StubQuestion[] {
  const keys = new Map(questions.map((question) => [question.key, `${question.key}_${nextId("c").slice(2, 8)}`]));

  return questions.map((question) => ({
    ...question,
    id: nextId("q"),
    key: keys.get(question.key) as string,
    ...(question.condition !== undefined
      ? {
          condition: {
            ...question.condition,
            sourceKey: keys.get(question.condition.sourceKey) ?? question.condition.sourceKey,
          },
        }
      : {}),
  }));
}

function reopenedIfClosed(trigger: StubTrigger, now: string): StubTrigger {
  const rules = trigger.rules.map((rule) => ({ ...rule, id: nextId("rule") }));

  if (trigger.windowEnd === undefined || trigger.windowEnd > now) {
    return { ...trigger, rules };
  }

  const { windowEnd: _closed, ...open } = trigger;
  return { ...open, windowStart: now, rules };
}

export const authoringRoutes: Route[] = [
  {
    method: "POST",
    pattern: "/applications/:applicationId/surveys/:surveyId/duplicate",
    handler: ({ params, body }) => {
      const source = surveyOf(params.applicationId, params.surveyId);
      if (source === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const input = (body ?? {}) as { targetApplicationId?: unknown; name?: unknown };
      const target =
        typeof input.targetApplicationId === "string" && input.targetApplicationId.trim() !== ""
          ? input.targetApplicationId
          : params.applicationId;

      if (!store.applications.has(target)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      if (
        input.name !== undefined &&
        input.name !== null &&
        (typeof input.name !== "string" || input.name.trim() === "")
      ) {
        return validation("request.invalid", "Requisição inválida.", {
          name: "Nome não pode ser vazio",
        });
      }

      const content = publishedVersion(source) ?? currentVersion(source);
      const now = new Date().toISOString();
      const name =
        typeof input.name === "string"
          ? input.name.trim()
          : `Cópia de ${source.name}`.slice(0, 120).trim();

      const copy: StubSurvey = {
        id: nextId("srv"),
        applicationId: target,
        name,
        state: "draft",
        versions: [
          {
            number: 1,
            status: "draft",
            comparabilityGroup: 1,
            questions: duplicatedQuestions(content?.questions ?? []),
            ...(content?.trigger !== undefined
              ? { trigger: reopenedIfClosed(content.trigger, now) }
              : {}),
          },
        ],
        transitions: [],
        ...(source.priority !== undefined ? { priority: source.priority } : {}),
        ...(source.responseQuota !== undefined ? { responseQuota: source.responseQuota } : {}),
        ...(source.ignoresQuietPeriod !== undefined
          ? { ignoresQuietPeriod: source.ignoresQuietPeriod }
          : {}),
        ...(source.templateKind !== undefined ? { templateKind: source.templateKind } : {}),
        ...(source.freeTextNoticeEnabled !== undefined
          ? { freeTextNoticeEnabled: source.freeTextNoticeEnabled }
          : {}),
        ...(source.freeTextNoticeText !== undefined
          ? { freeTextNoticeText: source.freeTextNoticeText }
          : {}),
        createdAt: nowIso(),
      };

      store.surveys.set(copy.id, copy);
      return json(201, toSurvey(copy));
    },
  },
];
