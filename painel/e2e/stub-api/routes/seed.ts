import { json, nextId, notFound, type Route } from "../http.ts";
import { store, versionIdOf, type StubAnswer, type StubDisplay } from "../store.ts";

/**
 * Semeadura de coleta — **não faz parte do contrato do backend**.
 *
 * Exibição, resposta e respondente não nascem por nenhuma tela: o painel é somente leitura, e
 * quem os cria é o SDK. Esta rota existe só no simulador, sob o prefixo `/stub`, para que o
 * E2E de coleta crie os próprios dados (R9). Nada em `src/` a conhece.
 */

type SeedRespondent = {
  identityKind?: "APP_REFERENCE" | "DEVICE";
  identityValue?: string;
  firstSeenAt?: string;
  lastSeenAt?: string;
};

type SeedDisplay = {
  /** Índice do respondente semeado no mesmo pedido. */
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

      return json(201, { respondentIds, displayIds });
    },
  },
];
