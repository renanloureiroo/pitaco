"use server";

import { refresh, revalidatePath } from "next/cache";
import { redirect } from "next/navigation";

import {
  failureFormState,
  invalidFormState,
  readFormValues,
  type FormState,
} from "@/shared/lib";
import { describeFailure, type Result } from "@/shared/api";

import { movedOrder, addQuestion, removeQuestion, reorderQuestions, updateQuestion } from "./api/questions";
import { createSurvey, discardSurvey, renameSurvey } from "./api/surveys";
import { endSurvey, pauseSurvey, resumeSurvey } from "./api/lifecycle";
import { publishSurvey } from "./api/publication";
import { addSegmentationRule, defineTrigger, removeSegmentationRule } from "./api/trigger";
import { discardDraftVersion, openDraftVersion } from "./api/versions";
import { questionFormSchema } from "./schemas/question";
import { surveyNameFormSchema, type Survey } from "./schemas/survey";
import { publishSurveyFormSchema } from "./schemas/publication";
import { segmentationRuleFormSchema, triggerFormSchema } from "./schemas/trigger";

/**
 * Mutações da pesquisa (contracts/backend-api.md § Revalidação).
 *
 * Regra de revalidação: quando o fluxo termina em outra tela, `redirect`; quando a mudança
 * afeta outra rota, `revalidatePath`; nos demais casos `refresh()`, que realinha o router com
 * o estado real do backend — é o que faz a UI se corrigir sozinha quando alguém alterou a
 * pesquisa em paralelo.
 */

function surveysListPath(applicationId: string): string {
  return `/aplicacoes/${applicationId}/pesquisas`;
}

function assemblyPath(applicationId: string, surveyId: string): string {
  return `${surveysListPath(applicationId)}/${surveyId}`;
}

export async function createSurveyAction(
  applicationId: string,
  _previous: FormState,
  formData: FormData,
): Promise<FormState> {
  const values = readFormValues(formData);
  const parsed = surveyNameFormSchema.safeParse(values);

  if (!parsed.success) {
    return invalidFormState(parsed.error, values);
  }

  const result = await createSurvey(applicationId, parsed.data.name);

  if (!result.ok) {
    return failureFormState(result, values);
  }

  redirect(assemblyPath(applicationId, result.data.id));
}

export async function renameSurveyAction(
  applicationId: string,
  surveyId: string,
  _previous: FormState,
  formData: FormData,
): Promise<FormState> {
  const values = readFormValues(formData);
  const parsed = surveyNameFormSchema.safeParse(values);

  if (!parsed.success) {
    return invalidFormState(parsed.error, values);
  }

  const result = await renameSurvey(applicationId, surveyId, parsed.data.name);

  if (!result.ok) {
    return failureFormState(result, values);
  }

  // O nome aparece na listagem e no cabeçalho: revalidar o segmento cobre as duas.
  revalidatePath(surveysListPath(applicationId));
  return { status: "success", data: undefined };
}

export async function discardSurveyAction(
  applicationId: string,
  surveyId: string,
): Promise<FormState> {
  const result = await discardSurvey(applicationId, surveyId);

  if (!result.ok) {
    return failureFormState(result, {});
  }

  revalidatePath(surveysListPath(applicationId));
  redirect(surveysListPath(applicationId));
}

export async function addQuestionAction(
  applicationId: string,
  surveyId: string,
  _previous: FormState,
  formData: FormData,
): Promise<FormState> {
  const values = readFormValues(formData);
  const parsed = questionFormSchema.safeParse(readQuestionInput(formData));

  if (!parsed.success) {
    return invalidFormState(parsed.error, values);
  }

  const result = await addQuestion(applicationId, surveyId, parsed.data);

  if (!result.ok) {
    return failureFormState(result, values);
  }

  refresh();
  return { status: "success", data: undefined };
}

export async function updateQuestionAction(
  applicationId: string,
  surveyId: string,
  questionId: string,
  _previous: FormState,
  formData: FormData,
): Promise<FormState> {
  const values = readFormValues(formData);
  const parsed = questionFormSchema.safeParse(readQuestionInput(formData));

  if (!parsed.success) {
    return invalidFormState(parsed.error, values);
  }

  const result = await updateQuestion(applicationId, surveyId, questionId, parsed.data);

  if (!result.ok) {
    return failureFormState(result, values);
  }

  refresh();
  return { status: "success", data: undefined };
}

export async function removeQuestionAction(
  applicationId: string,
  surveyId: string,
  questionId: string,
): Promise<FormState> {
  const result = await removeQuestion(applicationId, surveyId, questionId);

  if (!result.ok) {
    return failureFormState(result, {});
  }

  refresh();
  return { status: "success", data: undefined };
}

/**
 * Recebe a ordem **atual completa** exibida na tela e o movimento pedido, e envia a permutação
 * resultante — o backend exige a permutação exata da versão, nunca um subconjunto.
 */
export async function moveQuestionAction(
  applicationId: string,
  surveyId: string,
  questionIds: string[],
  questionId: string,
  delta: -1 | 1,
): Promise<FormState> {
  const reordered = movedOrder(questionIds, questionId, delta);

  if (reordered === questionIds) {
    return { status: "success", data: undefined };
  }

  const result = await reorderQuestions(applicationId, surveyId, reordered);

  if (!result.ok) {
    return { status: "error", message: describeFailure(result), fieldErrors: {}, values: {} };
  }

  refresh();
  return { status: "success", data: undefined };
}

/** As opções chegam como campos repetidos; `getAll` preserva a ordem em que foram digitadas. */
function readQuestionInput(formData: FormData) {
  return {
    statement: formData.get("statement"),
    type: formData.get("type"),
    required: formData.get("required"),
    optionLabels: formData.getAll("optionLabels").map(String),
    optionValues: formData.getAll("optionValues").map(String),
    rangeMin: formData.get("rangeMin"),
    rangeMax: formData.get("rangeMax"),
  };
}

export async function defineTriggerAction(
  applicationId: string,
  surveyId: string,
  _previous: FormState,
  formData: FormData,
): Promise<FormState> {
  const values = readFormValues(formData);
  const parsed = triggerFormSchema.safeParse(values);

  if (!parsed.success) {
    return invalidFormState(parsed.error, values);
  }

  const result = await defineTrigger(applicationId, surveyId, parsed.data);

  if (!result.ok) {
    return failureFormState(result, values);
  }

  refresh();
  return { status: "success", data: undefined };
}

export async function addSegmentationRuleAction(
  applicationId: string,
  surveyId: string,
  _previous: FormState,
  formData: FormData,
): Promise<FormState> {
  const values = readFormValues(formData);
  const parsed = segmentationRuleFormSchema.safeParse(values);

  if (!parsed.success) {
    return invalidFormState(parsed.error, values);
  }

  const result = await addSegmentationRule(applicationId, surveyId, parsed.data);

  if (!result.ok) {
    return failureFormState(result, values);
  }

  refresh();
  return { status: "success", data: undefined };
}

export async function removeSegmentationRuleAction(
  applicationId: string,
  surveyId: string,
  ruleId: string,
): Promise<FormState> {
  const result = await removeSegmentationRule(applicationId, surveyId, ruleId);

  if (!result.ok) {
    return failureFormState(result, {});
  }

  refresh();
  return { status: "success", data: undefined };
}

/**
 * Publicar afeta o cabeçalho, a listagem de versões e a própria tela de publicação — por isso
 * o segmento inteiro da pesquisa é revalidado, e não apenas a rota atual.
 */
export async function publishSurveyAction(
  applicationId: string,
  surveyId: string,
  hasPublishedVersion: boolean,
  _previous: FormState,
  formData: FormData,
): Promise<FormState> {
  const values = readFormValues(formData);
  const parsed = publishSurveyFormSchema(hasPublishedVersion).safeParse(values);

  if (!parsed.success) {
    return invalidFormState(parsed.error, values);
  }

  const result = await publishSurvey(applicationId, surveyId, parsed.data);

  if (!result.ok) {
    return failureFormState(result, values);
  }

  revalidatePath(assemblyPath(applicationId, surveyId), "layout");
  return { status: "success", data: undefined };
}

export async function openDraftVersionAction(
  applicationId: string,
  surveyId: string,
): Promise<FormState> {
  const result = await openDraftVersion(applicationId, surveyId);

  if (!result.ok) {
    return failureFormState(result, {});
  }

  revalidatePath(assemblyPath(applicationId, surveyId), "layout");
  return { status: "success", data: undefined };
}

export async function discardDraftVersionAction(
  applicationId: string,
  surveyId: string,
): Promise<FormState> {
  const result = await discardDraftVersion(applicationId, surveyId);

  if (!result.ok) {
    return failureFormState(result, {});
  }

  revalidatePath(assemblyPath(applicationId, surveyId), "layout");
  return { status: "success", data: undefined };
}

/**
 * Pausar, retomar e encerrar afetam o cabeçalho, o histórico e o que a montagem permite — por
 * isso o segmento inteiro da pesquisa é revalidado. Uma recusa por estado alterado por
 * terceiros vira mensagem exibível, e a revalidação seguinte realinha a UI com a verdade.
 */
async function transitionAction(
  applicationId: string,
  surveyId: string,
  run: (applicationId: string, surveyId: string) => Promise<Result<Survey>>,
): Promise<FormState> {
  const result = await run(applicationId, surveyId);

  // Revalida também na recusa: se o backend negou a transição, é porque o estado real mudou —
  // e a UI precisa se realinhar com ele em vez de continuar oferecendo o que já não existe.
  revalidatePath(assemblyPath(applicationId, surveyId), "layout");

  return result.ok ? { status: "success", data: undefined } : failureFormState(result, {});
}

export async function pauseSurveyAction(
  applicationId: string,
  surveyId: string,
): Promise<FormState> {
  return transitionAction(applicationId, surveyId, pauseSurvey);
}

export async function resumeSurveyAction(
  applicationId: string,
  surveyId: string,
): Promise<FormState> {
  return transitionAction(applicationId, surveyId, resumeSurvey);
}

export async function endSurveyAction(
  applicationId: string,
  surveyId: string,
): Promise<FormState> {
  return transitionAction(applicationId, surveyId, endSurvey);
}
