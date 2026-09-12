import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { FormState } from "@/shared/lib";

import { TransitionsHistory } from "../components/lifecycle/transitions-history";
import type { SurveyStateTransition } from "../schemas/transition";

const actions = vi.hoisted(() => ({
  pauseSurveyAction: vi.fn(),
  resumeSurveyAction: vi.fn(),
  endSurveyAction: vi.fn(),
}));
vi.mock("../actions", () => actions);

const { TransitionActions } = await import("../components/lifecycle/transition-actions");

function transition(
  reason: SurveyStateTransition["reason"],
  from: SurveyStateTransition["from"] = "active",
  to: SurveyStateTransition["to"] = "paused",
): SurveyStateTransition {
  return { from, to, reason, occurredAt: "2026-09-09T12:00:00Z" };
}

beforeEach(() => {
  for (const action of Object.values(actions)) {
    action.mockReset();
    action.mockResolvedValue({ status: "success", data: undefined } satisfies FormState);
  }
});

describe("TransitionActions", () => {
  it("não oferece ação nenhuma na encerrada", () => {
    render(
      <TransitionActions
        applicationId="app-1"
        surveyId="srv-1"
        currentState="ended"
      />,
    );

    expect(screen.queryByTestId("pause-survey-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("resume-survey-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("end-survey-button")).not.toBeInTheDocument();
  });

  it("no ar oferece pausar e encerrar, e não retomar", () => {
    render(
      <TransitionActions
        applicationId="app-1"
        surveyId="srv-1"
        currentState="active"
      />,
    );

    expect(screen.getByTestId("pause-survey-button")).toBeInTheDocument();
    expect(screen.getByTestId("end-survey-button")).toBeInTheDocument();
    expect(screen.queryByTestId("resume-survey-button")).not.toBeInTheDocument();
  });

  it("pausada oferece retomar e encerrar, e não pausar de novo", () => {
    render(
      <TransitionActions
        applicationId="app-1"
        surveyId="srv-1"
        currentState="paused"
      />,
    );

    expect(screen.getByTestId("resume-survey-button")).toBeInTheDocument();
    expect(screen.getByTestId("end-survey-button")).toBeInTheDocument();
    expect(screen.queryByTestId("pause-survey-button")).not.toBeInTheDocument();
  });

  it("rascunho não oferece transição nenhuma", () => {
    render(
      <TransitionActions
        applicationId="app-1"
        surveyId="srv-1"
        currentState="draft"
      />,
    );

    expect(screen.queryByTestId("pause-survey-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("end-survey-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("resume-survey-button")).not.toBeInTheDocument();
  });

  it("pausa e retoma sem confirmação — são ações reversíveis", async () => {
    const { rerender } = render(
      <TransitionActions
        applicationId="app-1"
        surveyId="srv-1"
        currentState="active"
      />,
    );

    await userEvent.click(screen.getByTestId("pause-survey-button"));
    expect(actions.pauseSurveyAction).toHaveBeenCalledWith("app-1", "srv-1");

    rerender(
      <TransitionActions
        applicationId="app-1"
        surveyId="srv-1"
        currentState="paused"
      />,
    );

    await userEvent.click(screen.getByTestId("resume-survey-button"));
    expect(actions.resumeSurveyAction).toHaveBeenCalledWith("app-1", "srv-1");
  });

  it("encerrar exige confirmação explícita e avisa que é irreversível", async () => {
    render(
      <TransitionActions
        applicationId="app-1"
        surveyId="srv-1"
        currentState="active"
      />,
    );

    await userEvent.click(screen.getByTestId("end-survey-button"));
    const dialog = await screen.findByTestId("confirm-dialog");
    expect(dialog).toHaveTextContent(/irreversível/i);
    expect(actions.endSurveyAction).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId("cancel-button"));
    expect(actions.endSurveyAction).not.toHaveBeenCalled();
  });

  it("encerra depois de confirmar", async () => {
    render(
      <TransitionActions
        applicationId="app-1"
        surveyId="srv-1"
        currentState="active"
      />,
    );

    await userEvent.click(screen.getByTestId("end-survey-button"));
    await userEvent.click(await screen.findByTestId("confirm-button"));

    expect(actions.endSurveyAction).toHaveBeenCalledWith("app-1", "srv-1");
  });

  it("exibe a recusa por estado alterado por terceiros", async () => {
    actions.pauseSurveyAction.mockResolvedValue({
      status: "error",
      message: "A pesquisa já foi encerrada por outra pessoa.",
      fieldErrors: {},
      values: {},
    } satisfies FormState);

    render(
      <TransitionActions
        applicationId="app-1"
        surveyId="srv-1"
        currentState="active"
      />,
    );

    await userEvent.click(screen.getByTestId("pause-survey-button"));

    expect(await screen.findByTestId("form-error")).toHaveTextContent(
      "A pesquisa já foi encerrada por outra pessoa.",
    );
  });
});

describe("TransitionsHistory", () => {
  it("registra o que já aconteceu, com o motivo de cada mudança", () => {
    render(
      <TransitionsHistory
        transitions={[
          transition("publication", "draft", "scheduled"),
          transition("manual_pause", "active", "paused"),
        ]}
      />,
    );

    const history = screen.getByTestId("transitions-history");
    expect(history).toHaveTextContent("Publicação");
    expect(history).toHaveTextContent("Rascunho → Agendada");
    expect(history).toHaveTextContent("Pausada manualmente");
  });

  it("não renderiza nada quando não há histórico", () => {
    render(<TransitionsHistory transitions={[]} />);

    expect(screen.queryByTestId("transitions-history")).not.toBeInTheDocument();
  });
});
