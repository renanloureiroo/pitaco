import { json, nextId, notFound, nowIso, paginate, readPageQuery, validation, type Route } from "../http.ts";
import { store } from "../store.ts";

const DAY = 24 * 60 * 60 * 1000;

/** A mesma agenda do backend: todo dia às 03:47 UTC. */
function nextRunAfter(now: Date): Date {
  const next = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), 3, 47));
  return next.getTime() > now.getTime() ? next : new Date(next.getTime() + DAY);
}

function answeredAtOf(display: { openedAt: string; closedAt?: string }): number {
  return new Date(display.closedAt ?? display.openedAt).getTime();
}

function forecast(applicationId: string, answersBefore?: number, textsBefore?: number) {
  let answers = 0;
  let texts = 0;

  for (const display of store.displays.values()) {
    if (display.applicationId !== applicationId) {
      continue;
    }
    const answeredAt = answeredAtOf(display);
    for (const answer of display.answers) {
      if (answer.status === "EXPIRED") {
        continue;
      }
      if (answersBefore !== undefined && answeredAt < answersBefore) {
        answers++;
      }
      if (textsBefore !== undefined && answer.text !== undefined && answeredAt < textsBefore) {
        texts++;
      }
    }
  }

  return { answers, texts };
}

export const privacyRoutes: Route[] = [
  {
    method: "DELETE",
    pattern: "/applications/:applicationId/respondents",
    handler: ({ params, query }) => {
      if (!store.applications.has(params.applicationId)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      const reference = query.get("reference")?.trim() ?? "";
      const deviceId = query.get("deviceId")?.trim() ?? "";

      if (reference === "" && deviceId === "") {
        return validation("request.invalid", "Requisição inválida.", {
          identified: "É preciso informar a referência do app ou o identificador do dispositivo",
        });
      }
      if (reference !== "" && deviceId !== "") {
        return validation("request.invalid", "Requisição inválida.", {
          unambiguous: "Informe a referência do app ou o identificador do dispositivo, não os dois",
        });
      }
      if (reference.length > 200 || deviceId.length > 200) {
        return validation("request.invalid", "Requisição inválida.", {
          reference: "Identificação do respondente não pode passar de 200 caracteres",
        });
      }

      const kind = reference !== "" ? "APP_REFERENCE" : "DEVICE";
      const value = reference !== "" ? reference : deviceId;
      const respondent = [...store.respondents.values()].find(
        (candidate) =>
          candidate.applicationId === params.applicationId &&
          candidate.identityKind === kind &&
          candidate.identityValue === value,
      );

      if (respondent === undefined) {
        return json(200, { deleted: false, displaysDeleted: 0, answersDeleted: 0 });
      }

      const displays = [...store.displays.values()].filter(
        (display) => display.respondentId === respondent.id,
      );
      const answersDeleted = displays.reduce((total, display) => total + display.answers.length, 0);

      displays.forEach((display) => store.displays.delete(display.id));
      store.respondents.delete(respondent.id);
      store.deletionAudits.push({
        id: nextId("del"),
        applicationId: params.applicationId,
        displaysDeleted: displays.length,
        answersDeleted,
        performedAt: nowIso(),
      });

      return json(200, { deleted: true, displaysDeleted: displays.length, answersDeleted });
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/deletion-audits",
    handler: ({ params, query }) => {
      if (!store.applications.has(params.applicationId)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      const items = store.deletionAudits
        .filter((audit) => audit.applicationId === params.applicationId)
        .sort((a, b) => b.performedAt.localeCompare(a.performedAt))
        .map(({ id, displaysDeleted, answersDeleted, performedAt }) => ({
          id,
          displaysDeleted,
          answersDeleted,
          performedAt,
        }));

      return json(200, paginate(items, readPageQuery(query)));
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/retention-preview",
    handler: ({ params }) => {
      const application = store.applications.get(params.applicationId);
      if (application === undefined) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      const answerDays = application.retentionDays;
      const textDays = application.openTextRetentionDays ?? application.retentionDays;
      const none = { answers: 0, texts: 0 };

      if (answerDays === undefined && textDays === undefined) {
        return json(200, { configured: false, nextRun: none, nextWeek: none, firstDiscardPending: true });
      }

      const nextRunAt = nextRunAfter(new Date());
      const at = (reference: number) =>
        forecast(
          params.applicationId,
          answerDays === undefined ? undefined : reference - answerDays * DAY,
          textDays === undefined ? undefined : reference - textDays * DAY,
        );

      return json(200, {
        configured: true,
        ...(answerDays !== undefined ? { answerRetentionDays: answerDays } : {}),
        ...(textDays !== undefined ? { textRetentionDays: textDays } : {}),
        nextRunAt: nextRunAt.toISOString(),
        nextRun: at(nextRunAt.getTime()),
        nextWeek: at(nextRunAt.getTime() + 7 * DAY),
        firstDiscardPending: true,
      });
    },
  },
  {
    method: "POST",
    pattern: "/stub/retention-snapshots",
    handler: ({ body }) => {
      const input = (body ?? {}) as { surveyId?: string; discardedBefore?: string };
      if (input.surveyId === undefined || !store.surveys.has(input.surveyId)) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      store.retentionSnapshots.push({
        surveyId: input.surveyId,
        discardedBefore: input.discardedBefore ?? nowIso(),
      });
      return json(201, {});
    },
  },
];
