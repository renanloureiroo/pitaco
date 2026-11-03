import { json, notFound, paginate, readPageQuery, type Route } from "../http.ts";
import { currentVersion, publishedVersion, settleWindow, store, type StubSurvey } from "../store.ts";
import { MIN_REQUIRED_VERSION, unsupportedShareOf } from "./health.ts";
import { surveyOf } from "./surveys.ts";

/** Mesma regra do backend: avisa quando quem suporta a pesquisa não é maioria estrita. */
function compatibility(survey: StubSurvey): Array<Record<string, unknown>> {
  if (currentVersion(survey) === undefined) {
    return [];
  }
  const unsupported = unsupportedShareOf(survey.applicationId, MIN_REQUIRED_VERSION);
  return unsupported === undefined || unsupported < 0.5
    ? []
    : [
        {
          code: "compatibility.unsupported_by_majority",
          minRequiredVersion: MIN_REQUIRED_VERSION,
          unsupportedShare: unsupported,
        },
      ];
}

/** Mesma leitura do backend: rascunho quando existe, publicada caso contrário. */
function warningsOf(survey: StubSurvey) {
  const trigger = currentVersion(survey)?.trigger;
  if (trigger === undefined) {
    return compatibility(survey);
  }

  const warnings: Array<Record<string, unknown>> = [];

  for (const rule of trigger.rules) {
    if (rule.operation === "absent") {
      continue;
    }
    const known = store.observedAttributes.find(
      (attribute) =>
        attribute.applicationId === survey.applicationId && attribute.name === rule.attribute,
    );
    const valueUnseen =
      rule.operation === "equals" && !(known?.values ?? []).some((v) => v.value === rule.value);

    if (known === undefined || valueUnseen) {
      warnings.push({
        code: "segmentation.no_known_match",
        ruleId: rule.id,
        attribute: rule.attribute,
      });
    }
  }

  const competing = [...store.surveys.values()]
    .filter((other) => other.id !== survey.id && other.applicationId === survey.applicationId)
    .map(settleWindow)
    .filter((other) => other.state === "active" || other.state === "scheduled")
    .filter((other) => publishedVersion(other)?.trigger?.eventName === trigger.eventName)
    .map((other) => ({ surveyId: other.id, name: other.name, priority: other.priority ?? 0 }));

  if (competing.length > 0) {
    warnings.push({ code: "trigger.competing_surveys", competingSurveys: competing });
  }

  warnings.push(...compatibility(survey));

  return warnings;
}

export const exposureRoutes: Route[] = [
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId/quota-progress",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const completedResponses = [...store.displays.values()].filter(
        (display) => display.surveyId === survey.id && display.outcome === "COMPLETED",
      ).length;

      return json(200, {
        ...(survey.responseQuota !== undefined ? { responseQuota: survey.responseQuota } : {}),
        completedResponses,
      });
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId/publication-warnings",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      return survey === undefined
        ? notFound("survey.not_found", "Pesquisa não encontrada.")
        : json(200, { warnings: warningsOf(survey) });
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/attributes",
    handler: ({ params, query }) => {
      if (!store.applications.has(params.applicationId)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      const items = store.observedAttributes
        .filter((attribute) => attribute.applicationId === params.applicationId)
        .sort(
          (a, b) => b.lastSeenAt.localeCompare(a.lastSeenAt) || a.name.localeCompare(b.name),
        )
        .map((attribute) => ({
          name: attribute.name,
          firstSeenAt: attribute.firstSeenAt,
          lastSeenAt: attribute.lastSeenAt,
          values: [...attribute.values].sort((a, b) => a.value.localeCompare(b.value)),
        }));

      return json(200, paginate(items, readPageQuery(query)));
    },
  },
];
