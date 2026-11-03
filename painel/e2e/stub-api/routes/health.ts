import { json, nextId, notFound, nowIso, paginate, readPageQuery, type Route } from "../http.ts";
import {
  publishedVersion,
  store,
  type StubSdkError,
  type StubSdkErrorKind,
  type StubSuppression,
} from "../store.ts";
import { surveyOf } from "./surveys.ts";

/** Tudo que existe hoje nasce na primeira versão do SDK, como no catálogo do backend. */
export const MIN_REQUIRED_VERSION = "1.0.0";

const STALE_AFTER_MS = 14 * 24 * 60 * 60 * 1000;
const RELEVANT_SHARE = 0.05;
const RELEVANT_MINIMUM = 5;

const KINDS: StubSdkErrorKind[] = [
  "render_error",
  "network_error",
  "malformed_response",
  "storage_error",
  "unknown",
];

function core(version: string): number[] {
  return (version.split("-")[0] ?? "").split(".").map((part) => Number.parseInt(part, 10) || 0);
}

/** Só o núcleo numérico: basta aos cenários do simulador. */
export function compareVersions(left: string, right: string): number {
  const a = core(left);
  const b = core(right);
  for (let index = 0; index < 3; index += 1) {
    const difference = (a[index] ?? 0) - (b[index] ?? 0);
    if (difference !== 0) {
      return difference;
    }
  }
  return 0;
}

export function unsupportedShareOf(applicationId: string, required: string): number | undefined {
  const usage = store.sdkVersions.filter((entry) => entry.applicationId === applicationId);
  const total = usage.reduce((sum, entry) => sum + entry.recentRequestCount, 0);
  if (total <= 0) {
    return undefined;
  }
  const unsupported = usage
    .filter((entry) => compareVersions(entry.version, required) < 0)
    .reduce((sum, entry) => sum + entry.recentRequestCount, 0);
  return unsupported / total;
}

type SeedVersions = {
  applicationId?: string;
  versions?: Array<{
    version: string;
    requestCount?: number;
    recentRequestCount?: number;
    firstSeenAt?: string;
    lastSeenAt?: string;
  }>;
};

type SeedErrors = {
  applicationId?: string;
  errors?: Array<{
    kind?: string;
    message?: string;
    sdkVersion?: string;
    context?: Record<string, unknown>;
    occurredAt?: string;
  }>;
};

type SeedSuppressions = {
  applicationId?: string;
  surveyId?: string;
  count?: number;
  reason?: StubSuppression["reason"];
  sdkVersion?: string;
};

export const healthRoutes: Route[] = [
  {
    method: "GET",
    pattern: "/applications/:applicationId/sdk-versions",
    handler: ({ params }) => {
      if (!store.applications.has(params.applicationId)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      const usage = store.sdkVersions.filter(
        (entry) => entry.applicationId === params.applicationId,
      );
      const recentRequests = usage.reduce((sum, entry) => sum + entry.recentRequestCount, 0);
      const staleBefore = Date.now() - STALE_AFTER_MS;

      return json(200, {
        recentFrom: new Date(Date.now() - STALE_AFTER_MS).toISOString().slice(0, 10),
        recentRequests,
        versions: [...usage]
          .sort((a, b) => compareVersions(b.version, a.version))
          .map((entry) => ({
            version: entry.version,
            requestCount: entry.requestCount,
            recentRequestCount: entry.recentRequestCount,
            ...(recentRequests > 0 ? { recentShare: entry.recentRequestCount / recentRequests } : {}),
            firstSeenAt: entry.firstSeenAt,
            lastSeenAt: entry.lastSeenAt,
            stale: Date.parse(entry.lastSeenAt) < staleBefore,
          })),
      });
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/sdk-errors",
    handler: ({ params, query }) => {
      if (!store.applications.has(params.applicationId)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      const kind = query.get("kind");
      const version = query.get("sdkVersion");
      const matching = store.sdkErrors
        .filter((error) => error.applicationId === params.applicationId)
        .filter((error) => kind === null || error.kind === kind)
        .filter((error) => version === null || error.sdkVersion === version)
        .sort((a, b) => b.receivedAt.localeCompare(a.receivedAt));

      return json(200, paginate(matching, readPageQuery(query)));
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId/health",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const to = new Date();
      const from = new Date(to.getTime() - 30 * 24 * 60 * 60 * 1000);
      const displays = [...store.displays.values()].filter(
        (display) => display.surveyId === survey.id,
      ).length;
      const suppressions = store.suppressions.filter((entry) => entry.surveyId === survey.id);
      const total = suppressions.length;
      const byVersion = new Map<string | undefined, number>();
      for (const entry of suppressions) {
        byVersion.set(entry.sdkVersion, (byVersion.get(entry.sdkVersion) ?? 0) + 1);
      }
      const reached = displays + total;
      const share = reached === 0 ? undefined : total / reached;
      const published = publishedVersion(survey);
      const eventName = published?.trigger?.eventName;
      const seen = store.observedEvents.find(
        (event) => event.applicationId === survey.applicationId && event.name === eventName,
      );

      return json(200, {
        from: from.toISOString(),
        to: to.toISOString(),
        displays,
        suppressions: {
          total,
          byReason: (["unknown_question_type", "unsupported_feature"] as const).map((reason) => ({
            reason,
            count: suppressions.filter((entry) => entry.reason === reason).length,
          })),
          bySdkVersion: [...byVersion.entries()]
            .sort(([a], [b]) => (a === undefined ? 1 : b === undefined ? -1 : compareVersions(b, a)))
            .map(([sdkVersion, count]) => ({ ...(sdkVersion !== undefined ? { sdkVersion } : {}), count })),
        },
        ...(share !== undefined ? { suppressionShare: share } : {}),
        relevant: share !== undefined && share >= RELEVANT_SHARE && total >= RELEVANT_MINIMUM,
        ...(published !== undefined ? { minRequiredVersion: MIN_REQUIRED_VERSION } : {}),
        ...(eventName !== undefined ? { eventName } : {}),
        ...(seen !== undefined ? { eventLastSeenAt: seen.lastSeenAt } : {}),
      });
    },
  },
  {
    method: "POST",
    pattern: "/stub/sdk-versions",
    handler: ({ body }) => {
      const seed = (body ?? {}) as SeedVersions;
      const applicationId = seed.applicationId ?? "";
      if (!store.applications.has(applicationId)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      for (const input of seed.versions ?? []) {
        const lastSeenAt = input.lastSeenAt ?? nowIso();
        store.sdkVersions.push({
          applicationId,
          version: input.version,
          requestCount: input.requestCount ?? 1,
          recentRequestCount: input.recentRequestCount ?? input.requestCount ?? 1,
          firstSeenAt: input.firstSeenAt ?? lastSeenAt,
          lastSeenAt,
        });
      }

      return json(200, {});
    },
  },
  {
    method: "POST",
    pattern: "/stub/sdk-errors",
    handler: ({ body }) => {
      const seed = (body ?? {}) as SeedErrors;
      const applicationId = seed.applicationId ?? "";
      if (!store.applications.has(applicationId)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      (seed.errors ?? []).forEach((input, index) => {
        const receivedAt = new Date(Date.now() + index).toISOString();
        const kind = KINDS.find((candidate) => candidate === input.kind) ?? "unknown";
        const error: StubSdkError = {
          id: nextId("err"),
          applicationId,
          ...(input.sdkVersion !== undefined ? { sdkVersion: input.sdkVersion } : {}),
          kind,
          message: input.message ?? "",
          context: input.context ?? {},
          occurredAt: input.occurredAt ?? receivedAt,
          receivedAt,
        };
        store.sdkErrors.push(error);
      });

      return json(200, {});
    },
  },
  {
    method: "POST",
    pattern: "/stub/suppressions",
    handler: ({ body }) => {
      const seed = (body ?? {}) as SeedSuppressions;
      const survey = surveyOf(seed.applicationId ?? "", seed.surveyId ?? "");
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      for (let index = 0; index < (seed.count ?? 1); index += 1) {
        store.suppressions.push({
          applicationId: survey.applicationId,
          surveyId: survey.id,
          reason: seed.reason ?? "unknown_question_type",
          ...(seed.sdkVersion !== undefined ? { sdkVersion: seed.sdkVersion } : {}),
          occurredAt: nowIso(),
        });
      }

      return json(200, {});
    },
  },
];
