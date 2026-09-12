import { json, nextId, notFound, nowIso, type Route } from "../http.ts";
import {
  settleWindow,
  store,
  versionIdOf,
  type StubAnswer,
  type StubDisplay,
  type StubSurvey,
} from "../store.ts";

type SeedRespondent = {
  identityKind?: "APP_REFERENCE" | "DEVICE";
  identityValue?: string;
  firstSeenAt?: string;
  lastSeenAt?: string;
};

type SeedDisplay = {
  respondent?: number;
  surveyId?: string;
  versionNumber?: number;
  comparabilityGroup?: number;
  outcome?: StubDisplay["outcome"];
  sdkVersion?: string;
  openedAt?: string;
  closedAt?: string;
  attributes?: Record<string, string>;
  answers?: StubAnswer[];
};

type SeedRequest = {
  applicationId?: string;
  respondents?: SeedRespondent[];
  displays?: SeedDisplay[];
};

type SeedObservedEvent = {
  name: string;
  firstSeenAt?: string;
  lastSeenAt?: string;
};

type SeedObservedAttributesRequest = {
  applicationId?: string;
  attributes?: Array<{ name: string; values?: string[]; lastSeenAt?: string }>;
};

/**
 * No backend real é a conclusão que atinge a cota que encerra a pesquisa. Aqui a conclusão chega
 * pela semeadura, então é ela que confere a cota depois de gravar as exibições.
 */
function closeOnQuota(survey: StubSurvey): void {
  settleWindow(survey);
  if (
    survey.responseQuota === undefined ||
    !["scheduled", "active", "paused"].includes(survey.state)
  ) {
    return;
  }

  const completed = [...store.displays.values()].filter(
    (display) => display.surveyId === survey.id && display.outcome === "COMPLETED",
  ).length;

  if (completed >= survey.responseQuota) {
    survey.transitions.push({
      from: survey.state,
      to: "ended",
      reason: "quota_reached",
      occurredAt: nowIso(),
    });
    survey.state = "ended";
  }
}

type SeedObservedEventsRequest = {
  applicationId?: string;
  events?: SeedObservedEvent[];
};

export const seedRoutes: Route[] = [
  {
    method: "POST",
    pattern: "/stub/collect",
    handler: ({ body }) => {
      const seed = (body ?? {}) as SeedRequest;
      const applicationId = seed.applicationId ?? "";

      if (!store.applications.has(applicationId)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      const respondentIds = (seed.respondents ?? []).map((input, index) => {
        const id = nextId("rsp");
        const seenAt = input.firstSeenAt ?? "2026-09-08T09:00:00Z";

        store.respondents.set(id, {
          id,
          applicationId,
          identityKind: input.identityKind ?? "APP_REFERENCE",
          identityValue: input.identityValue ?? `user-${index + 1}`,
          firstSeenAt: seenAt,
          lastSeenAt: input.lastSeenAt ?? seenAt,
        });

        return id;
      });

      const displayIds = (seed.displays ?? []).map((input) => {
        const id = nextId("dsp");
        const surveyId = input.surveyId ?? "";
        const versionNumber = input.versionNumber ?? 1;
        const respondentId = respondentIds[input.respondent ?? 0] ?? "";

        store.displays.set(id, {
          id,
          applicationId,
          respondentId,
          surveyId,
          versionId: versionIdOf(surveyId, versionNumber),
          versionNumber,
          comparabilityGroup: input.comparabilityGroup ?? 1,
          outcome: input.outcome ?? "STARTED",
          ...(input.sdkVersion !== undefined ? { sdkVersion: input.sdkVersion } : {}),
          attributes: input.attributes ?? {},
          answers: input.answers ?? [],
          openedAt: input.openedAt ?? "2026-09-08T10:00:00Z",
          ...(input.closedAt !== undefined ? { closedAt: input.closedAt } : {}),
        });

        return id;
      });

      for (const surveyId of new Set((seed.displays ?? []).map((input) => input.surveyId ?? ""))) {
        const survey = store.surveys.get(surveyId);
        if (survey !== undefined) {
          closeOnQuota(survey);
        }
      }

      return json(201, { respondentIds, displayIds });
    },
  },
  {
    // Só o SDK alimenta o catálogo de eventos; o E2E o semeia por aqui.
    method: "POST",
    pattern: "/stub/observed-events",
    handler: ({ body }) => {
      const seed = (body ?? {}) as SeedObservedEventsRequest;
      const applicationId = seed.applicationId ?? "";

      if (!store.applications.has(applicationId)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      for (const input of seed.events ?? []) {
        const seenAt = input.firstSeenAt ?? "2026-09-08T09:00:00Z";
        const lastSeenAt = input.lastSeenAt ?? seenAt;
        const existing = store.observedEvents.find(
          (event) => event.applicationId === applicationId && event.name === input.name,
        );

        if (existing === undefined) {
          store.observedEvents.push({
            applicationId,
            name: input.name,
            firstSeenAt: seenAt,
            lastSeenAt,
          });
        } else if (lastSeenAt > existing.lastSeenAt) {
          existing.lastSeenAt = lastSeenAt;
        }
      }

      return json(201, {});
    },
  },
  {
    // Só o SDK alimenta o catálogo de atributos; o E2E o semeia por aqui.
    method: "POST",
    pattern: "/stub/observed-attributes",
    handler: ({ body }) => {
      const seed = (body ?? {}) as SeedObservedAttributesRequest;
      const applicationId = seed.applicationId ?? "";

      if (!store.applications.has(applicationId)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      for (const input of seed.attributes ?? []) {
        const seenAt = input.lastSeenAt ?? "2026-09-08T09:00:00Z";
        let attribute = store.observedAttributes.find(
          (candidate) => candidate.applicationId === applicationId && candidate.name === input.name,
        );

        if (attribute === undefined) {
          attribute = { applicationId, name: input.name, firstSeenAt: seenAt, lastSeenAt: seenAt, values: [] };
          store.observedAttributes.push(attribute);
        }

        for (const value of input.values ?? []) {
          if (!attribute.values.some((known) => known.value === value)) {
            attribute.values.push({ value, lastSeenAt: seenAt });
          }
        }
      }

      return json(201, {});
    },
  },
];
