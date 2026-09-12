import { conflict, json, notFound, nowIso, type Route } from "../http.ts";
import type { StubSurvey, StubTransition } from "../store.ts";
import { surveyOf, toSurvey } from "./surveys.ts";

function allowedNow(survey: StubSurvey): StubTransition["reason"][] {
  switch (survey.state) {
    case "scheduled":
      return ["manual_end"];
    case "active":
      return ["manual_pause", "manual_end"];
    case "paused":
      return ["manual_resume", "manual_end"];
    case "draft":
    case "ended":
      return [];
  }
}

function transitionsPayload(survey: StubSurvey) {
  return survey.transitions;
}

function targetOf(reason: StubTransition["reason"]): StubTransition["to"] {
  switch (reason) {
    case "manual_pause":
      return "paused";
    case "manual_resume":
      return "active";
    case "manual_end":
    case "quota_reached":
    case "window_closed":
      return "ended";
    case "window_opened":
      return "active";
    case "publication":
      return "scheduled";
  }
}

function applyTransition(
  survey: StubSurvey,
  reason: StubTransition["reason"],
): ReturnType<typeof json> {
  if (!allowedNow(survey).includes(reason)) {
    return conflict(
      "survey.transition_not_allowed",
      "Esta transição não é permitida no estado atual da pesquisa.",
    );
  }

  const from = survey.state;
  const to = targetOf(reason);

  survey.state = to;
  survey.transitions.push({ from, to, reason, occurredAt: nowIso() });

  return json(200, toSurvey(survey));
}

export const lifecycleRoutes: Route[] = [
  {
    method: "GET",
    pattern: "/applications/:applicationId/surveys/:surveyId/transitions",
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      return json(200, transitionsPayload(survey));
    },
  },
  ...(["pause", "resume", "end"] as const).map<Route>((action) => ({
    method: "POST",
    pattern: `/applications/:applicationId/surveys/:surveyId/${action}`,
    handler: ({ params }) => {
      const survey = surveyOf(params.applicationId, params.surveyId);
      if (survey === undefined) {
        return notFound("survey.not_found", "Pesquisa não encontrada.");
      }

      const reason =
        action === "pause" ? "manual_pause" : action === "resume" ? "manual_resume" : "manual_end";

      return applyTransition(survey, reason);
    },
  })),
];
