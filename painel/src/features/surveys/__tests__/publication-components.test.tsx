import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { FormState } from "@/shared/lib";

import { ComparabilityPanel } from "../components/versions/comparability-panel";
import { ImpedimentsList } from "../components/publication/impediments-list";
import { VersionDetail } from "../components/versions/version-detail";
import { VersionsTable } from "../components/versions/versions-table";
import type { SurveyVersion, SurveyVersionDetail } from "../schemas/version";

const actions = vi.hoisted(() => ({
  publishSurveyAction: vi.fn(),
  openDraftVersionAction: vi.fn(),
  discardDraftVersionAction: vi.fn(),
}));
vi.mock("../actions", () => actions);

const { PublishForm } = await import("../components/publication/publish-form");
const { DraftVersionActions } = await import("../components/versions/draft-version-actions");

const published: SurveyVersion = {
  number: 1,
  status: "published",
  publishedAt: "2026-09-09T12:00:00Z",
  comparabilityGroup: 1,
};

beforeEach(() => {
  for (const action of Object.values(actions)) {
    action.mockReset();
    action.mockResolvedValue({ status: "success", data: undefined } satisfies FormState);
  }
});

describe("ImpedimentsList", () => {
  it("diz que nada impede quando a lista está vazia (FR-030)", () => {
    render(<ImpedimentsList applicationId="app-1" surveyId="srv-1" impediments={[]} />);

    expect(screen.getByTestId("impediments-list")).toHaveTextContent("Nada impede a publicação");
    expect(screen.queryByTestId("impediment-item")).not.toBeInTheDocument();
  });

  it("traduz cada impedimento em frase acionável", () => {
    render(
      <ImpedimentsList
        applicationId="app-1"
        surveyId="srv-1"
        impediments={[{ code: "trigger.missing" }, { code: "survey.no_questions" }]}
      />,
    );

    expect(screen.getAllByTestId("impediment-item")).toHaveLength(2);
    expect(screen.getByText(/defina o disparo/i)).toBeInTheDocument();
    expect(screen.getByText(/adicione ao menos uma pergunta/i)).toBeInTheDocument();
  });

  it("liga o impedimento à pergunta quando há questionKey", () => {
    render(
      <ImpedimentsList
        applicationId="app-1"
        surveyId="srv-1"
        impediments={[{ code: "question.options_missing", questionKey: "preferida" }]}
      />,
    );

    expect(screen.getByRole("link", { name: "Ir para a pergunta" })).toHaveAttribute(
      "href",
      "/aplicacoes/app-1/pesquisas/srv-1#preferida",
    );
  });

  it("não oferece link quando o impedimento não aponta pergunta", () => {
    render(
      <ImpedimentsList applicationId="app-1" surveyId="srv-1" impediments={[{ code: "trigger.missing" }]} />,
    );

    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});

describe("PublishForm", () => {
  it("desabilita o botão enquanto houver impedimento", () => {
    render(
      <PublishForm applicationId="app-1" surveyId="srv-1" hasImpediments hasPublishedVersion={false} />,
    );

    expect(screen.getByTestId("publish-button")).toBeDisabled();
  });

  it("libera o botão quando não há impedimento", () => {
    render(
      <PublishForm
        applicationId="app-1"
        surveyId="srv-1"
        hasImpediments={false}
        hasPublishedVersion={false}
      />,
    );

    expect(screen.getByTestId("publish-button")).toBeEnabled();
  });

  it("não pede natureza da mudança na versão 1", () => {
    render(
      <PublishForm
        applicationId="app-1"
        surveyId="srv-1"
        hasImpediments={false}
        hasPublishedVersion={false}
      />,
    );

    expect(screen.queryByTestId("change-kind-select")).not.toBeInTheDocument();
    expect(screen.queryByTestId("change-summary-input")).not.toBeInTheDocument();
  });

  it("pede natureza e resumo da mudança a partir da versão 2", () => {
    render(
      <PublishForm applicationId="app-1" surveyId="srv-1" hasImpediments={false} hasPublishedVersion />,
    );

    expect(screen.getByTestId("change-kind-select")).toBeInTheDocument();
    expect(screen.getByTestId("change-summary-input")).toBeInTheDocument();
  });

  it("clique duplo não cria duas versões", async () => {
    let resolve: (state: FormState) => void = () => {};
    actions.publishSurveyAction.mockImplementation(
      () =>
        new Promise<FormState>((done) => {
          resolve = done;
        }),
    );
    render(
      <PublishForm
        applicationId="app-1"
        surveyId="srv-1"
        hasImpediments={false}
        hasPublishedVersion={false}
      />,
    );

    await userEvent.click(screen.getByTestId("publish-button"));
    expect(screen.getByTestId("publish-button")).toBeDisabled();

    await userEvent.click(screen.getByTestId("publish-button"));
    expect(actions.publishSurveyAction).toHaveBeenCalledTimes(1);

    resolve({ status: "idle" });
  });

  it("exibe a recusa por impedimento devolvida pela action", async () => {
    actions.publishSurveyAction.mockResolvedValue({
      status: "error",
      message: "A pesquisa ainda tem impedimentos.",
      fieldErrors: {},
      values: {},
    } satisfies FormState);

    render(
      <PublishForm
        applicationId="app-1"
        surveyId="srv-1"
        hasImpediments={false}
        hasPublishedVersion={false}
      />,
    );
    await userEvent.click(screen.getByTestId("publish-button"));

    expect(await screen.findByTestId("form-error")).toHaveTextContent(
      "A pesquisa ainda tem impedimentos.",
    );
  });
});

describe("VersionsTable", () => {
  it("ordena da mais recente para a mais antiga", () => {
    render(
      <VersionsTable
        applicationId="app-1"
        surveyId="srv-1"
        versions={[published, { number: 2, status: "draft", comparabilityGroup: 1 }]}
      />,
    );

    const rows = screen.getAllByTestId("version-row");
    expect(rows[0]).toHaveTextContent("v2");
    expect(rows[1]).toHaveTextContent("v1");
  });

  it("mostra ausência de natureza e de publicação como 'não configurado'", () => {
    render(
      <VersionsTable
        applicationId="app-1"
        surveyId="srv-1"
        versions={[{ number: 2, status: "draft", comparabilityGroup: 1 }]}
      />,
    );

    expect(screen.getByTestId("version-row")).toHaveTextContent("não configurado");
  });

  it("só liga à versão publicada — rascunho não tem conteúdo congelado a ver", () => {
    render(
      <VersionsTable
        applicationId="app-1"
        surveyId="srv-1"
        versions={[published, { number: 2, status: "draft", comparabilityGroup: 1 }]}
      />,
    );

    expect(screen.getAllByRole("link")).toHaveLength(1);
    expect(screen.getByRole("link", { name: "v1" })).toHaveAttribute(
      "href",
      "/aplicacoes/app-1/pesquisas/srv-1/versoes/1",
    );
  });
});

describe("ComparabilityPanel", () => {
  it("exibe os grupos e suas versões", () => {
    render(
      <ComparabilityPanel
        comparability={{
          groups: [
            { group: 1, versions: [1, 2] },
            { group: 2, versions: [3] },
          ],
        }}
      />,
    );

    const panel = screen.getByTestId("comparability-panel");
    expect(panel).toHaveTextContent("Grupo 1:");
    expect(panel).toHaveTextContent("v1, v2");
    expect(panel).toHaveTextContent("v3");
  });

  it("diz que não há o que comparar quando não há grupo", () => {
    render(<ComparabilityPanel comparability={{ groups: [] }} />);

    expect(screen.getByTestId("comparability-panel")).toHaveTextContent(/ainda não há versão/i);
  });
});

describe("VersionDetail", () => {
  const detail: SurveyVersionDetail = {
    ...published,
    questions: [
      {
        id: "q-1",
        key: "nota",
        statement: "Qual sua nota?",
        type: "nps",
        position: 0,
        required: true,
      },
    ],
  };

  it("mostra o conteúdo congelado sem nenhuma ação de edição", () => {
    render(<VersionDetail version={detail} />);

    expect(screen.getByTestId("version-questions")).toHaveTextContent("Qual sua nota?");
    expect(screen.queryByTestId("edit-question-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("remove-question-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("add-question-button")).not.toBeInTheDocument();
  });
});

describe("DraftVersionActions", () => {
  it("oferece abrir rascunho quando há publicação e nenhum rascunho aberto", async () => {
    render(
      <DraftVersionActions applicationId="app-1" surveyId="srv-1" hasDraft={false} canOpenDraft />,
    );

    await userEvent.click(screen.getByTestId("open-draft-version-button"));

    expect(actions.openDraftVersionAction).toHaveBeenCalledWith("app-1", "srv-1");
    expect(screen.queryByTestId("discard-draft-version-button")).not.toBeInTheDocument();
  });

  it("descarta o rascunho apenas depois de confirmar", async () => {
    render(
      <DraftVersionActions applicationId="app-1" surveyId="srv-1" hasDraft canOpenDraft={false} />,
    );

    await userEvent.click(screen.getByTestId("discard-draft-version-button"));
    expect(await screen.findByTestId("confirm-dialog")).toBeInTheDocument();
    expect(actions.discardDraftVersionAction).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId("confirm-button"));
    expect(actions.discardDraftVersionAction).toHaveBeenCalledWith("app-1", "srv-1");
  });

  it("não oferece abrir rascunho enquanto não há versão publicada", () => {
    render(
      <DraftVersionActions
        applicationId="app-1"
        surveyId="srv-1"
        hasDraft={false}
        canOpenDraft={false}
      />,
    );

    expect(screen.queryByTestId("open-draft-version-button")).not.toBeInTheDocument();
  });
});
