import { z } from "zod";

import { request, requestNoContent, type Result } from "@/shared/api";

import { questionSchema, type Question, type QuestionFormOutput } from "../schemas/question";
import { surveyPath } from "./paths";

function questionsPath(applicationId: string, surveyId: string): string {
  return `${surveyPath(applicationId, surveyId)}/questions`;
}

export function addQuestion(
  applicationId: string,
  surveyId: string,
  question: QuestionFormOutput,
): Promise<Result<Question>> {
  return request(questionSchema, {
    path: questionsPath(applicationId, surveyId),
    method: "POST",
    body: question,
  });
}

/** Editar reescreve a pergunta: a `key` estável e a `position` não mudam. */
export function updateQuestion(
  applicationId: string,
  surveyId: string,
  questionId: string,
  question: QuestionFormOutput,
): Promise<Result<Question>> {
  return request(questionSchema, {
    path: `${questionsPath(applicationId, surveyId)}/${encodeURIComponent(questionId)}`,
    method: "PUT",
    body: question,
  });
}

export function removeQuestion(
  applicationId: string,
  surveyId: string,
  questionId: string,
): Promise<Result<void>> {
  return requestNoContent({
    path: `${questionsPath(applicationId, surveyId)}/${encodeURIComponent(questionId)}`,
    method: "DELETE",
  });
}

/**
 * `questionIds` é a **permutação exata** dos identificadores da versão: o painel monta a lista
 * completa a partir do que está exibindo, nunca um subconjunto.
 */
export function reorderQuestions(
  applicationId: string,
  surveyId: string,
  questionIds: string[],
): Promise<Result<Question[]>> {
  return request(z.array(questionSchema), {
    path: `${questionsPath(applicationId, surveyId)}/order`,
    method: "PUT",
    body: { questionIds },
  });
}

/** Move uma pergunta uma posição e devolve a permutação completa resultante. */
export function movedOrder(questionIds: string[], questionId: string, delta: -1 | 1): string[] {
  const index = questionIds.indexOf(questionId);
  const target = index + delta;

  if (index === -1 || target < 0 || target >= questionIds.length) {
    return questionIds;
  }

  const reordered = [...questionIds];
  [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
  return reordered;
}
