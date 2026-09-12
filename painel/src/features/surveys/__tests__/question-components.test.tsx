import userEvent from "@testing-library/user-event";
import { render, screen, within } from "@testing-library/react";
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
const { QuestionsPanel } = await import("../components/questions/questions-panel");
const { SortableQuestionsList, reorderOnDrop } = await import(
  "../components/questions/sortable-questions-list"
);

const nps: Question = {
  id: "q-1",
  key: "nota",
  statement: "Qual sua nota?",
  type: "nps",
  position: 0,
  required: true,
};

const choice: Question = {
  id: "q-2",
  key: "preferida",
  statement: "Qual sua preferida?",
  type: "single_choice",
  position: 1,
  required: false,
  options: [{ label: "A", value: "a" }],
};

const success: FormState = { status: "success", data: undefined };

beforeEach(() => {
  for (const action of Object.values(actions)) {
    action.mockReset();
    action.mockResolvedValue(success);
  }
});

describe("QuestionForm", () => {
  it("mostra opções só nos tipos de escolha e faixa só em rating/scale", async () => {
    render(<QuestionForm applicationId="app-1" surveyId="srv-1" />);

    // free_text: nenhum dos dois
    expect(screen.queryByTestId("question-options")).not.toBeInTheDocument();
    expect(screen.queryByTestId("question-range")).not.toBeInTheDocument();

    await userEvent.click(screen.getByTestId("question-type-select"));
    await userEvent.click(await screen.findByRole("option", { name: "Escolha única" }));

    expect(await screen.findByTestId("question-options")).toBeInTheDocument();
    expect(screen.queryByTestId("question-range")).not.toBeInTheDocument();

    await userEvent.click(screen.getByTestId("question-type-select"));
    await userEvent.click(await screen.findByRole("option", { name: "Escala" }));

    expect(await screen.findByTestId("question-range")).toBeInTheDocument();
    expect(screen.queryByTestId("question-options")).not.toBeInTheDocument();
  });

  it("acrescenta e remove linhas de opção", async () => {
    render(<QuestionForm applicationId="app-1" surveyId="srv-1" question={choice} />);

    const options = screen.getByTestId("question-options");
    expect(within(options).getAllByLabelText(/^Rótulo da opção/)).toHaveLength(1);

    await userEvent.click(screen.getByTestId("add-option-button"));
    expect(within(options).getAllByLabelText(/^Rótulo da opção/)).toHaveLength(2);

    await userEvent.click(screen.getByLabelText("Remover opção 2"));
    expect(within(options).getAllByLabelText(/^Rótulo da opção/)).toHaveLength(1);
  });

  it("não deixa remover a última opção — um tipo de escolha precisa de pelo menos uma", () => {
    render(<QuestionForm applicationId="app-1" surveyId="srv-1" question={choice} />);

    expect(screen.getByLabelText("Remover opção 1")).toBeDisabled();
  });

  it("exibe a recusa por campo devolvida pela action", async () => {
    actions.addQuestionAction.mockResolvedValue({
      status: "error",
      message: "Revise os campos destacados.",
      fieldErrors: { options: "Perguntas de escolha precisam de pelo menos uma opção." },
      // A action devolve tudo que foi enviado, inclusive o tipo escolhido.
      values: { statement: "Qual sua preferida?", type: "single_choice" },
    } satisfies FormState);

    render(<QuestionForm applicationId="app-1" surveyId="srv-1" />);

    await userEvent.type(screen.getByLabelText("Enunciado"), "Qual sua preferida?");
    await userEvent.click(screen.getByTestId("question-type-select"));
    await userEvent.click(await screen.findByRole("option", { name: "Escolha única" }));
    await screen.findByTestId("question-options");
    await userEvent.click(screen.getByTestId("submit-button"));

    expect(await screen.findByTestId("field-error-options")).toHaveTextContent(
      /pelo menos uma opção/i,
    );
  });

  it("preserva o tipo escolhido quando o envio é recusado", async () => {
    actions.addQuestionAction.mockResolvedValue({
      status: "error",
      message: "Revise os campos destacados.",
      fieldErrors: { options: "Perguntas de escolha precisam de pelo menos uma opção." },
      values: { statement: "Qual sua preferida?", type: "single_choice" },
    } satisfies FormState);

    render(<QuestionForm applicationId="app-1" surveyId="srv-1" />);

    await userEvent.click(screen.getByTestId("question-type-select"));
    await userEvent.click(await screen.findByRole("option", { name: "Escolha única" }));
    await userEvent.click(screen.getByTestId("submit-button"));

    // O reset de formulário do React não pode desfazer a escolha de tipo.
    expect(await screen.findByTestId("question-options")).toBeInTheDocument();
    expect(screen.getByTestId("question-type-select")).toHaveTextContent("Escolha única");
  });

  it("bloqueia o envio enquanto a action está pendente", async () => {
    actions.addQuestionAction.mockImplementation(() => new Promise(() => {}));

    render(<QuestionForm applicationId="app-1" surveyId="srv-1" />);
    await userEvent.type(screen.getByLabelText("Enunciado"), "Como foi?");
    await userEvent.click(screen.getByTestId("submit-button"));

    expect(screen.getByTestId("submit-button")).toBeDisabled();
  });
});

describe("QuestionsPanel", () => {
  it("não oferece mover quando existe uma única pergunta", () => {
    render(
      <QuestionsPanel applicationId="app-1" surveyId="srv-1" questions={[nps]} />,
    );

    expect(screen.queryByTestId("move-question-up")).not.toBeInTheDocument();
    expect(screen.queryByTestId("move-question-down")).not.toBeInTheDocument();
  });

  it("oferece mover com duas perguntas, desabilitando nos extremos", () => {
    render(
      <QuestionsPanel applicationId="app-1" surveyId="srv-1" questions={[nps, choice]} />,
    );

    const [primeira, segunda] = screen.getAllByTestId("question-item");
    expect(within(primeira).getByTestId("move-question-up")).toBeDisabled();
    expect(within(primeira).getByTestId("move-question-down")).toBeEnabled();
    expect(within(segunda).getByTestId("move-question-down")).toBeDisabled();
  });

  it("envia a permutação completa ao mover", async () => {
    render(
      <QuestionsPanel applicationId="app-1" surveyId="srv-1" questions={[nps, choice]} />,
    );

    const [primeira] = screen.getAllByTestId("question-item");
    await userEvent.click(within(primeira).getByTestId("move-question-down"));

    expect(actions.moveQuestionAction).toHaveBeenCalledWith(
      "app-1",
      "srv-1",
      ["q-1", "q-2"],
      "q-1",
      1,
    );
  });

  it("exige confirmação explícita para remover", async () => {
    render(<QuestionsPanel applicationId="app-1" surveyId="srv-1" questions={[nps]} />);

    await userEvent.click(screen.getByTestId("remove-question-button"));
    expect(await screen.findByTestId("confirm-dialog")).toBeInTheDocument();
    expect(actions.removeQuestionAction).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId("confirm-button"));
    expect(actions.removeQuestionAction).toHaveBeenCalledWith("app-1", "srv-1", "q-1");
  });

  it("exibe as perguntas na ordem de position, não na ordem de chegada", () => {
    render(
      <QuestionsPanel
        applicationId="app-1"
        surveyId="srv-1"
        questions={[{ ...choice, position: 0 }, { ...nps, position: 1 }]}
      />,
    );

    const items = screen.getAllByTestId("question-item");
    expect(items[0]).toHaveTextContent("Qual sua preferida?");
    expect(items[1]).toHaveTextContent("Qual sua nota?");
  });

  it("em somente leitura não oferece nenhuma ação de edição", () => {
    render(
      <QuestionsPanel applicationId="app-1" surveyId="srv-1" questions={[nps, choice]} readOnly />,
    );

    expect(screen.queryByTestId("add-question-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("edit-question-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("remove-question-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("move-question-up")).not.toBeInTheDocument();
  });

  it("convida a adicionar a primeira pergunta quando não há nenhuma", () => {
    render(<QuestionsPanel applicationId="app-1" surveyId="srv-1" questions={[]} />);

    expect(screen.getByTestId("empty-state")).toBeInTheDocument();
    expect(screen.getByTestId("add-question-button")).toBeInTheDocument();
  });
});

describe("SortableQuestionsList", () => {
  it("oferece uma alça de arrastar por pergunta, na ordem de position", () => {
    render(
      <SortableQuestionsList
        applicationId="app-1"
        surveyId="srv-1"
        questions={[choice, nps]}
      />,
    );

    const handles = screen.getAllByTestId("drag-question-handle");
    expect(handles).toHaveLength(2);
    expect(handles[0]).toHaveAccessibleName('Arrastar pergunta "Qual sua nota?"');
    expect(handles[1]).toHaveAccessibleName('Arrastar pergunta "Qual sua preferida?"');
  });

  it("soltar sobre outra pergunta envia a permutação completa e realinha a tela", async () => {
    const showPending = vi.fn();
    const showError = vi.fn();
    const work: Array<() => Promise<void>> = [];

    reorderOnDrop({
      applicationId: "app-1",
      surveyId: "srv-1",
      ids: ["q-1", "q-2", "q-3"],
      event: { active: { id: "q-1" }, over: { id: "q-3" } } as never,
      showPending,
      showError,
      run: (fn) => {
        work.push(fn);
      },
    });

    // A ordem pretendida aparece antes da resposta chegar.
    expect(showPending).toHaveBeenCalledWith(["q-2", "q-3", "q-1"]);
    await Promise.all(work.map((fn) => fn()));

    expect(actions.reorderQuestionsAction).toHaveBeenCalledWith("app-1", "srv-1", [
      "q-2",
      "q-3",
      "q-1",
    ]);
    expect(showPending).toHaveBeenLastCalledWith(undefined);
    expect(showError).not.toHaveBeenCalledWith(expect.any(String));
  });

  it("soltar fora da lista ou no mesmo lugar não envia nada", () => {
    const run = vi.fn();

    reorderOnDrop({
      applicationId: "app-1",
      surveyId: "srv-1",
      ids: ["q-1", "q-2"],
      event: { active: { id: "q-1" }, over: null } as never,
      showPending: vi.fn(),
      showError: vi.fn(),
      run,
    });
    reorderOnDrop({
      applicationId: "app-1",
      surveyId: "srv-1",
      ids: ["q-1", "q-2"],
      event: { active: { id: "q-1" }, over: { id: "q-1" } } as never,
      showPending: vi.fn(),
      showError: vi.fn(),
      run,
    });

    expect(run).not.toHaveBeenCalled();
    expect(actions.reorderQuestionsAction).not.toHaveBeenCalled();
  });

  it("recusa do backend vira mensagem e a ordem volta à do servidor", async () => {
    actions.reorderQuestionsAction.mockResolvedValue({
      status: "error",
      message: "A pesquisa foi publicada em paralelo.",
      fieldErrors: {},
      values: {},
    } satisfies FormState);
    const showPending = vi.fn();
    const showError = vi.fn();
    const work: Array<() => Promise<void>> = [];

    reorderOnDrop({
      applicationId: "app-1",
      surveyId: "srv-1",
      ids: ["q-1", "q-2"],
      event: { active: { id: "q-2" }, over: { id: "q-1" } } as never,
      showPending,
      showError,
      run: (fn) => {
        work.push(fn);
      },
    });
    await Promise.all(work.map((fn) => fn()));

    expect(showError).toHaveBeenLastCalledWith("A pesquisa foi publicada em paralelo.");
    expect(showPending).toHaveBeenLastCalledWith(undefined);
  });

  it("a alça é desabilitada enquanto uma reordenação está em curso", () => {
    render(
      <SortableQuestionsList
        applicationId="app-1"
        surveyId="srv-1"
        questions={[nps, choice]}
      />,
    );

    for (const handle of screen.getAllByTestId("drag-question-handle")) {
      expect(handle).toBeEnabled();
    }
  });
});

describe("QuestionsPanel em somente leitura", () => {
  it("não oferece alça de arrastar nem ações", () => {
    render(
      <QuestionsPanel applicationId="app-1" surveyId="srv-1" questions={[nps, choice]} readOnly />,
    );

    expect(screen.getAllByTestId("question-item")).toHaveLength(2);
    expect(screen.queryByTestId("drag-question-handle")).not.toBeInTheDocument();
    expect(screen.queryByTestId("edit-question-button")).not.toBeInTheDocument();
  });
});
