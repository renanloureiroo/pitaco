import { z } from "zod";

import { request, type Result } from "@/shared/api";

import { surveySchema, type Survey } from "../schemas/survey";
import { surveyStateTransitionSchema, type SurveyStateTransition } from "../schemas/transition";
import { surveyPath } from "./paths";

/**
 * O painel oferece **apenas** as transições que esta leitura autoriza — nunca as deriva do
 * estado (FR-035).
 */
export function getTransitions(
  applicationId: string,
  surveyId: string,
): Promise<Result<SurveyStateTransition[]>> {
  return request(z.array(surveyStateTransitionSchema), {
    path: `${surveyPath(applicationId, surveyId)}/transitions`,
  });
}

function transition(
  applicationId: string,
  surveyId: string,
  action: "pause" | "resume" | "end",
): Promise<Result<Survey>> {
  return request(surveySchema, {
    path: `${surveyPath(applicationId, surveyId)}/${action}`,
    method: "POST",
  });
}

export function pauseSurvey(applicationId: string, surveyId: string) {
  return transition(applicationId, surveyId, "pause");
}

export function resumeSurvey(applicationId: string, surveyId: string) {
  return transition(applicationId, surveyId, "resume");
}

/** Encerrar é irreversível: a interface avisa antes, e o backend recusa qualquer volta. */
export function endSurvey(applicationId: string, surveyId: string) {
  return transition(applicationId, surveyId, "end");
}
