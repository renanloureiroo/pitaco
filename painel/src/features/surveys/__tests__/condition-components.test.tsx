import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { FormState } from "@/shared/lib";

import type { Question } from "../schemas/question";

const actions = vi.hoisted(() => ({
  addQuestionAction: vi.fn(),
  updateQuestionAction: vi.fn(),
  removeQuestionAction: vi.fn(),
  moveQuestionAction: vi.fn(),
  reorderQuestionsAction: vi.fn(),
}));

vi.mock("../actions", () => actions);

const { QuestionForm } = await import("../components/questions/question-form");
const { QuestionsList } = await import("../components/questions/questions-list");

const escolha: Question = {
  id: "q-1",
  key: "gostou",
  statement: "Gostou?",
  type: "single_choice",
  position: 1,
  required: true,
  options: [
    { label: "Sim", value: "yes" },
    { label: "Não", value: "no" },
  ],
};

const motivo: Question = {
  id: "q-2",
  key: "motivo",
  statement: "O que faltou?",
  type: "free_text",
  position: 2,
  required: false,
  condition: { sourceKey: "gostou", operator: "equals", values: ["no"] },
};

const success: FormState = { status: "success", data: undefined };

beforeEach(() => {
  for (const action of Object.values(actions)) {
    action.mockReset();
    action.mockResolvedValue(success);
  }
});

describe("QuestionForm — condição de exibição", () => {
  it("sem pergunta anterior que sirva de origem, explica em vez de oferecer a condição", () => {
    render(<QuestionForm applicationId="app" surveyId="srv" questions={[]} />);

    expect(screen.getByTestId("question-condition-section")).toHaveTextContent(
      "Nenhuma pergunta anterior pode servir de origem",
    );
    expect(screen.queryByTestId("condition-toggle")).not.toBeInTheDocument();
  });

  it("ligar a condição mostra origem, operador e valor, e desligar tira os campos do envio", async () => {
    const user = userEvent.setup();
    const { container } = render(
      <QuestionForm applicationId="app" surveyId="srv" questions={[escolha]} />,
    );

    await user.click(screen.getByTestId("condition-toggle"));

    expect(screen.getByTestId("condition-fields")).toBeInTheDocument();
    expect(screen.getByTestId("condition-source-select")).toHaveTextContent("P1 — Gostou?");
    expect(screen.getByTestId("condition-operator-select")).toHaveTextContent("for igual a");
    expect(container.querySelector('input[name="conditionSourceKey"]')).toHaveValue("gostou");

    await user.click(screen.getByTestId("condition-toggle"));

    expect(container.querySelector('input[name="conditionSourceKey"]')).toBeNull();
  });

  it("ao editar, a condição existente vem montada", () => {
    const { container } = render(
      <QuestionForm applicationId="app" surveyId="srv" question={motivo} questions={[escolha, motivo]} />,
    );

    expect(screen.getByTestId("condition-toggle")).toBeChecked();
    expect(container.querySelector('input[name="conditionValues"]')).toHaveValue("no");
  });

  it("perguntas de escala oferecem rótulos para os extremos", async () => {
    const user = userEvent.setup();
    render(<QuestionForm applicationId="app" surveyId="srv" questions={[]} />);

    expect(screen.queryByTestId("question-labels")).not.toBeInTheDocument();

    await user.click(screen.getByTestId("question-type-select"));
    await user.click(screen.getByRole("option", { name: "NPS" }));

    expect(screen.getByTestId("question-labels")).toBeInTheDocument();
  });
});

describe("QuestionsList — resumo da condição", () => {
  it("mostra a condição em linguagem de gente na pergunta condicionada", () => {
    render(<QuestionsList questions={[escolha, motivo]} />);

    expect(screen.getByTestId("question-condition")).toHaveTextContent('Exibida se P1 for "Não"');
  });
});
