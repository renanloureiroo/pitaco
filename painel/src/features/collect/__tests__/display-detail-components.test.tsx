import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { Question } from "@/features/surveys";

import { DisplayAnswers } from "../components/displays/display-answers";
import { DisplayAttributes } from "../components/displays/display-attributes";
import { DisplaySummaryCard } from "../components/displays/display-summary";
import { matchAnswersToQuestions } from "../lib/answers";
import type { Answer } from "../schemas/answer";
import type { DisplayDetail } from "../schemas/display";

function question(key: string, statement: string, type: Question["type"] = "free_text"): Question {
  return { id: `q-${key}`, key, statement, type, position: 0, required: false };
}

function answer(questionKey: string, extra: Partial<Answer> = {}): Answer {
  return { questionKey, status: "ANSWERED", options: [], ...extra };
}

const detail: DisplayDetail = {
  id: "dsp-1",
  respondentId: "rsp-1",
  surveyId: "srv-1",
  versionId: "ver-1",
  versionNumber: 3,
  comparabilityGroup: 2,
  outcome: "COMPLETED",
  sdkVersion: "1.4.0",
  openedAt: "2026-09-08T14:22:31Z",
  closedAt: "2026-09-08T14:23:07Z",
  attributes: { plano: "pro" },
  answers: [],
};

const answers = [
  answer("nota", { number: 9 }),
  answer("motivo", { status: "SKIPPED" }),
  answer("comentario", { status: "EXPIRED" }),
  answer("obs", { text: "" }),
];

const questions = [
  question("nota", "Qual sua nota?", "nps"),
  question("motivo", "Por quê?"),
  question("comentario", "Algo a acrescentar?"),
];

describe("DisplaySummaryCard", () => {
  it("oferece os vínculos para a pesquisa e para o respondente (FR-016)", () => {
    render(<DisplaySummaryCard applicationId="app-1" display={detail} />);

    expect(screen.getByTestId("display-survey-link")).toHaveAttribute(
      "href",
      "/aplicacoes/app-1/pesquisas/srv-1/exibicoes",
    );
    expect(screen.getByTestId("display-respondent-link")).toHaveAttribute(
      "href",
      "/aplicacoes/app-1/respondentes/rsp-1",
    );
  });

  it("exibição aberta mostra fechamento como 'ainda aberta' e SDK ausente como 'não informada'", () => {
    render(
      <DisplaySummaryCard
        applicationId="app-1"
        display={{ ...detail, outcome: "STARTED", closedAt: undefined, sdkVersion: undefined }}
      />,
    );

    const summary = screen.getByTestId("display-summary");

    expect(summary).toHaveTextContent("ainda aberta");
    expect(summary).toHaveTextContent("não informada");
    expect(summary).toHaveTextContent("Em andamento");
    expect(summary).not.toHaveTextContent("Invalid Date");
  });
});

describe("DisplayAnswers — nenhuma resposta some (invariante de R2)", () => {
  it("renderiza uma linha por resposta com a versão inteira disponível", () => {
    render(
      <DisplayAnswers
        answers={matchAnswersToQuestions(answers, questions)}
        outcome="COMPLETED"
        versionAvailable
      />,
    );

    expect(screen.getAllByTestId("answer-item")).toHaveLength(answers.length);
    expect(screen.queryByTestId("version-unavailable-note")).not.toBeInTheDocument();
  });

  it("renderiza uma linha por resposta mesmo sem a versão, avisando por quê", () => {
    render(
      <DisplayAnswers
        answers={matchAnswersToQuestions(answers, undefined)}
        outcome="COMPLETED"
        versionAvailable={false}
      />,
    );

    expect(screen.getAllByTestId("answer-item")).toHaveLength(answers.length);
    expect(screen.getByTestId("version-unavailable-note")).toBeInTheDocument();
    expect(screen.getAllByTestId("answer-question")[0]).toHaveTextContent("nota");
  });

  it("renderiza uma linha por resposta quando a chave não existe na versão lida", () => {
    render(
      <DisplayAnswers
        answers={matchAnswersToQuestions(answers, [question("nota", "Qual sua nota?", "nps")])}
        outcome="COMPLETED"
        versionAvailable
      />,
    );

    expect(screen.getAllByTestId("answer-item")).toHaveLength(answers.length);
    expect(screen.getByTestId("display-answers")).toHaveTextContent(
      "pergunta não encontrada nesta versão",
    );
  });

  it("mostra o enunciado da versão exibida e o tipo da pergunta", () => {
    render(
      <DisplayAnswers
        answers={matchAnswersToQuestions([answer("nota", { number: 9 })], questions)}
        outcome="COMPLETED"
        versionAvailable
      />,
    );

    const item = screen.getByTestId("answer-item");

    expect(item).toHaveTextContent("Qual sua nota?");
    expect(item).toHaveTextContent("NPS");
    expect(screen.getByTestId("answer-value")).toHaveTextContent("9");
  });

  it("pulada, expirada e em branco são distinguíveis por quem lê (SC-006)", () => {
    render(
      <DisplayAnswers
        answers={matchAnswersToQuestions(answers, questions)}
        outcome="COMPLETED"
        versionAvailable
      />,
    );

    const pulada = screen.getByTestId("answer-skipped");
    const expirada = screen.getByTestId("answer-expired");
    const branco = screen.getAllByTestId("answer-value").at(-1);

    expect(pulada).toHaveTextContent("Pulada");
    expect(expirada).toHaveTextContent("Texto expirado");
    expect(expirada).toHaveTextContent("retenção");
    expect(branco).toHaveTextContent("sem valor registrado");

    for (const par of [
      [pulada.textContent, expirada.textContent],
      [pulada.textContent, branco?.textContent],
      [expirada.textContent, branco?.textContent],
    ]) {
      expect(par[0]).not.toBe(par[1]);
    }
  });

  it("mostra o zero de uma resposta numérica, que é valor e não ausência", () => {
    render(
      <DisplayAnswers
        answers={matchAnswersToQuestions([answer("nota", { number: 0 })], questions)}
        outcome="COMPLETED"
        versionAvailable
      />,
    );

    expect(screen.getByTestId("answer-value")).toHaveTextContent("0");
    expect(screen.getByTestId("answer-value")).not.toHaveTextContent("sem valor registrado");
  });

  it("exibição aberta e dispensada dizem por que não há resposta, com textos diferentes", () => {
    const { rerender } = render(
      <DisplayAnswers answers={[]} outcome="STARTED" versionAvailable />,
    );
    const aberta = screen.getByTestId("answers-empty").textContent;

    expect(aberta).toContain("ainda está aberta");

    rerender(<DisplayAnswers answers={[]} outcome="DISMISSED" versionAvailable />);
    const dispensada = screen.getByTestId("answers-empty").textContent;

    expect(dispensada).toContain("dispensou");
    expect(dispensada).not.toBe(aberta);
  });
});

describe("DisplayAttributes", () => {
  it("rotula o instantâneo como da exibição, não do respondente (FR-014)", () => {
    render(<DisplayAttributes attributes={{ plano: "pro", cupom: "BLACK" }} />);

    const block = screen.getByTestId("display-attributes");

    expect(block).toHaveTextContent("Atributos desta exibição");
    expect(block).toHaveTextContent("Não é o perfil do respondente");
    expect(screen.getAllByTestId("attribute-item")).toHaveLength(2);
  });

  it("instantâneo vazio tem texto próprio, e não é tratado como erro", () => {
    render(<DisplayAttributes attributes={{}} />);

    expect(screen.getByTestId("attributes-empty")).toHaveTextContent(
      "Nenhum atributo informado nesta exibição",
    );
    expect(screen.queryByTestId("attribute-item")).not.toBeInTheDocument();
  });
});
