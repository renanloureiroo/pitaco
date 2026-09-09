import type { Question } from "@/features/surveys";

import type { Answer } from "../schemas/answer";

/**
 * Casamento entre a resposta e a pergunta da **versão exibida** (R2).
 *
 * A API devolve cada resposta identificada por `questionKey` — chave estável, sem texto. O
 * enunciado vem da versão que foi exibida, não da atual: mostrar o enunciado de hoje ao lado de
 * uma resposta de ontem seria atribuir à pessoa uma resposta a uma pergunta que ela não viu.
 *
 * **A lista de respostas é a fonte da ordem.** A versão entra só como enriquecimento de texto,
 * e por isso uma divergência entre as duas leituras não reordena nem esconde resposta alguma.
 *
 * Invariante que o teste exercita em toda combinação:
 * `matchAnswersToQuestions(answers, questions).length === answers.length`.
 */

export type ResolvedAnswer = {
  answer: Answer;
  /** Ausente quando a versão não pôde ser lida, ou quando a chave não existe nela. */
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

/**
 * O valor no formato do tipo (FR-013). `undefined` quando não há valor registrado — o que só
 * acontece em resposta pulada, expirada, ou deixada em branco, e cada um desses casos tem texto
 * próprio na tela.
 */
export function answerValueText(answer: Answer): string | undefined {
  if (answer.options.length > 0) {
    return answer.options.join(", ");
  }

  // `0` é resposta legítima em NPS e escala: a checagem é por ausência, nunca por veracidade.
  if (answer.number !== undefined) {
    return String(answer.number);
  }

  return answer.text === undefined || answer.text === "" ? undefined : answer.text;
}
