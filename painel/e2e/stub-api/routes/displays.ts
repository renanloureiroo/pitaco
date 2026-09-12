import {
  json,
  notFound,
  paginate,
  readPageQuery,
  validation,
  type Route,
  type StubResponse,
} from "../http.ts";
import { store, type StubDisplay } from "../store.ts";
import { surveyOf } from "./surveys.ts";

const OUTCOMES = ["STARTED", "COMPLETED", "DISMISSED"];

export function orderDisplays(displays: StubDisplay[]): StubDisplay[] {
  return [...displays].sort(
    (a, b) => b.openedAt.localeCompare(a.openedAt) || b.id.localeCompare(a.id),
  );
}

export type DisplayFilter = {
  versionNumber?: number;
  outcome?: string;
  openedFrom?: string;
  openedTo?: string;
};

export function readDisplayFilter(
  query: URLSearchParams,
  { acceptsVersion }: { acceptsVersion: boolean },
): DisplayFilter | StubResponse {
  const filter: DisplayFilter = {};

  const rawVersion = query.get("versionNumber");
  if (acceptsVersion && rawVersion !== null) {
    const versionNumber = Number.parseInt(rawVersion, 10);
    if (!/^\d+$/.test(rawVersion) || versionNumber < 1) {
      return validation("request.invalid", "Requisição inválida.", {
        versionNumber: "Número de versão deve ser maior ou igual a 1",
      });
    }
    filter.versionNumber = versionNumber;
  }

  const outcome = query.get("outcome");
  if (outcome !== null) {
    if (!OUTCOMES.includes(outcome)) {
      return validation("request.invalid", "Requisição inválida.", {
        outcome: "Desfecho desconhecido.",
      });
    }
    filter.outcome = outcome;
  }

  for (const [param, key] of [
    ["openedFrom", "openedFrom"],
    ["openedTo", "openedTo"],
  ] as const) {
    const raw = query.get(param);
    if (raw === null) {
      continue;
    }
    if (Number.isNaN(Date.parse(raw))) {
      return validation("request.invalid", "Requisição inválida.", {
        [param]: "Instante malformado.",
      });
    }
    filter[key] = new Date(raw).toISOString();
  }

  if (
    filter.openedFrom !== undefined &&
    filter.openedTo !== undefined &&
    filter.openedFrom > filter.openedTo
  ) {
    return validation("request.invalid", "Requisição inválida.", {
      periodOrdered: "Início do período não pode ser posterior ao fim",
    });
  }

  return filter;
}

export function matchesFilter(display: StubDisplay, filter: DisplayFilter): boolean {
  const openedAt = new Date(display.openedAt).toISOString();

  return (
    (filter.versionNumber === undefined || display.versionNumber === filter.versionNumber) &&
    (filter.outcome === undefined || display.outcome === filter.outcome) &&
    (filter.openedFrom === undefined || openedAt >= filter.openedFrom) &&
    (filter.openedTo === undefined || openedAt <= filter.openedTo)
  );
}

export function toSummary(display: StubDisplay) {
  return {
    id: display.id,
    versionId: display.versionId,
    versionNumber: display.versionNumber,
    comparabilityGroup: display.comparabilityGroup,
    outcome: display.outcome,
    ...(display.sdkVersion !== undefined ? { sdkVersion: display.sdkVersion } : {}),
    openedAt: display.openedAt,
    ...(display.closedAt !== undefined ? { closedAt: display.closedAt } : {}),
  };
}

export const displayRoutes: Route[] = [
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId/displays",
    handler: ({ params, query }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const filter = readDisplayFilter(query, { acceptsVersion: true });
      if ("status" in filter) {
        return filter;
      }

      const items = orderDisplays(
        [...store.displays.values()].filter(
          (display) =>
            display.applicationId === params.applicationId &&
            display.surveyId === params.surveyId &&
            matchesFilter(display, filter),
        ),
      ).map(toSummary);

      return json(200, paginate(items, readPageQuery(query)));
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/displays/:displayId",
    handler: ({ params }) => {
      const display = store.displays.get(params.displayId);

      if (display === undefined || display.applicationId !== params.applicationId) {
        return notFound("display.not_found", "Exibição não encontrada.");
      }

      return json(200, {
        ...toSummary(display),
        respondentId: display.respondentId,
        surveyId: display.surveyId,
        attributes: display.attributes,
        answers: display.answers.map((answer) => ({
          questionKey: answer.questionKey,
          status: answer.status,
          ...(answer.text !== undefined ? { text: answer.text } : {}),
          ...(answer.number !== undefined ? { number: answer.number } : {}),
          options: answer.options,
        })),
      });
    },
  },
];
