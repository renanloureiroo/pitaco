import { describe, expect, it } from "vitest";

import type { Question } from "@/features/surveys";

import type { Answer } from "../schemas/answer";
import { answerValueText, matchAnswersToQuestions } from "../lib/answers";

function question(key: string, position: number): Question {
  return {
    id: `q-${key}`,
    key,
    statement: `Enunciado de ${key}`,
    type: "free_text",
    position,
    required: false,
  };
}

function answer(questionKey: string, extra: Partial<Answer> = {}): Answer {
  return { questionKey, status: "ANSWERED", options: [], ...extra };
}

const answers = [answer("nota"), answer("motivo"), answer("extra")];
const questions = [question("motivo", 0), question("nota", 1)];

describe("matchAnswersToQuestions — nenhuma resposta some (R2)", () => {
  it("mantém o tamanho da lista de respostas em toda combinação", () => {
    const combinacoes: Array<Question[] | undefined> = [
      undefined,
      [],
      questions,
      [question("inexistente", 0)],
    ];

    for (const candidata of combinacoes) {
      expect(matchAnswersToQuestions(answers, candidata)).toHaveLength(answers.length);
    }
  });

  it("preserva a ordem das respostas, mesmo quando a versão diverge dela", () => {
    const resolved = matchAnswersToQuestions(answers, questions);

    expect(resolved.map((item) => item.answer.questionKey)).toEqual([
      "nota",
      "motivo",
      "extra",
    ]);
  });

  it("enriquece com o enunciado quando a chave existe na versão exibida", () => {
    const resolved = matchAnswersToQuestions(answers, questions);

    expect(resolved[0].question?.statement).toBe("Enunciado de nota");
    expect(resolved[1].question?.statement).toBe("Enunciado de motivo");
  });

  it("deixa a pergunta ausente quando a chave não existe naquela versão", () => {
    expect(matchAnswersToQuestions(answers, questions)[2].question).toBeUndefined();
  });

  it("com a versão indisponível, devolve todas as respostas sem enunciado nenhum", () => {
    const resolved = matchAnswersToQuestions(answers, undefined);

    expect(resolved).toHaveLength(3);
    expect(resolved.every((item) => item.question === undefined)).toBe(true);
  });

  it("sem respostas, não inventa linha a partir das perguntas da versão", () => {
    expect(matchAnswersToQuestions([], questions)).toEqual([]);
  });
});

describe("answerValueText — o valor no formato do tipo (FR-013)", () => {
  it("junta as opções escolhidas", () => {
    expect(answerValueText(answer("k", { options: ["Sim", "Talvez"] }))).toBe("Sim, Talvez");
  });

  it("mostra o zero de uma resposta numérica, que é valor e não ausência", () => {
    expect(answerValueText(answer("k", { number: 0 }))).toBe("0");
  });

  it("mostra o texto livre como veio", () => {
    expect(answerValueText(answer("k", { text: "podia exportar em CSV" }))).toBe(
      "podia exportar em CSV",
    );
  });

  it("sem valor algum devolve ausência, para a tela dar o texto próprio do caso", () => {
    expect(answerValueText(answer("k"))).toBeUndefined();
    expect(answerValueText(answer("k", { text: "" }))).toBeUndefined();
    expect(answerValueText(answer("k", { status: "SKIPPED" }))).toBeUndefined();
    expect(answerValueText(answer("k", { status: "EXPIRED" }))).toBeUndefined();
  });
});
