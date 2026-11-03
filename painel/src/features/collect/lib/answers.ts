import type { Question } from "@/features/surveys";

import type { Answer } from "../schemas/answer";

export type ResolvedAnswer = {
  answer: Answer;
  question?: Question;
};

export function matchAnswersToQuestions(
  answers: Answer[],
  questions: Question[] | undefined,
): ResolvedAnswer[] {
  const byKey = new Map((questions ?? []).map((question) => [question.key, question]));

  return answers.map((answer) => {
    const question = byKey.get(answer.questionKey);
    return question === undefined ? { answer } : { answer, question };
  });
}

export function answerValueText(answer: Answer): string | undefined {
  if (answer.options.length > 0) {
    return answer.options.join(", ");
  }

  if (answer.number !== undefined) {
    return String(answer.number);
  }

  return answer.text === undefined || answer.text === "" ? undefined : answer.text;
}
