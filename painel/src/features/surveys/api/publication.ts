import { request, type Result } from "@/shared/api";

import {
  publicationImpedimentsSchema,
  type PublicationImpediment,
  type PublishSurveyForm,
} from "../schemas/publication";
import { surveyVersionSchema, type SurveyVersion } from "../schemas/version";
import { publicationWarningsSchema, type PublicationWarning } from "../schemas/warnings";
import { surveyPath } from "./paths";

export async function getPublicationImpediments(
  applicationId: string,
  surveyId: string,
): Promise<Result<PublicationImpediment[]>> {
  const result = await request(publicationImpedimentsSchema, {
    path: `${surveyPath(applicationId, surveyId)}/publication-impediments`,
  });

  return result.ok ? { ok: true, data: result.data.impediments } : result;
}

/** Avisos que não bloqueiam: competição pelo mesmo evento e regra que não alcança ninguém. */
export async function getPublicationWarnings(
  applicationId: string,
  surveyId: string,
): Promise<Result<PublicationWarning[]>> {
  const result = await request(publicationWarningsSchema, {
    path: `${surveyPath(applicationId, surveyId)}/publication-warnings`,
  });

  return result.ok ? { ok: true, data: result.data.warnings } : result;
}

export function publishSurvey(
  applicationId: string,
  surveyId: string,
  form: PublishSurveyForm,
): Promise<Result<SurveyVersion>> {
  return request(surveyVersionSchema, {
    path: `${surveyPath(applicationId, surveyId)}/publication`,
    method: "POST",
    body: form,
  });
}
