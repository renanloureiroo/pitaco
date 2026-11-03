import userEvent from "@testing-library/user-event";
import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { DeletionAudit, RetentionPreview } from "../schemas/privacy";

const actions = vi.hoisted(() => ({
  eraseRespondentAction: vi.fn(async () => ({
    status: "success" as const,
    data: { deleted: true, displaysDeleted: 2, answersDeleted: 5 },
  })),
}));

vi.mock("../actions", () => actions);

const { EraseRespondentForm } = await import("../components/erase-respondent-form");
const { DeletionAuditsList } = await import("../components/deletion-audits-list");
const { RetentionPolicyCard } = await import("../components/retention-policy-card");

beforeEach(() => {
  actions.eraseRespondentAction.mockClear();
});

describe("EraseRespondentForm", () => {
  it("só habilita a exclusão com identificação preenchida", async () => {
    render(<EraseRespondentForm applicationId="app-1" />);

    expect(screen.getByTestId("erase-respondent-button")).toBeDisabled();
    await userEvent.type(screen.getByTestId("erase-identity-input"), "u-1");
    expect(screen.getByTestId("erase-respondent-button")).toBeEnabled();
  });

  it("pede confirmação dizendo que é irreversível, e cancelar não apaga nada", async () => {
    render(<EraseRespondentForm applicationId="app-1" />);
    await userEvent.type(screen.getByTestId("erase-identity-input"), "u-1");

    await userEvent.click(screen.getByTestId("erase-respondent-button"));
    const dialog = await screen.findByTestId("confirm-dialog");
    expect(dialog).toHaveTextContent("irreversível");
    expect(dialog).toHaveTextContent("u-1");

    await userEvent.click(screen.getByTestId("cancel-button"));

    expect(actions.eraseRespondentAction).not.toHaveBeenCalled();
    expect(screen.queryByTestId("erasure-result")).not.toBeInTheDocument();
  });

  it("confirmar envia a identificação e mostra quanto saiu", async () => {
    render(<EraseRespondentForm applicationId="app-1" />);
    await userEvent.type(screen.getByTestId("erase-identity-input"), "u-1");

    await userEvent.click(screen.getByTestId("erase-respondent-button"));
    await userEvent.click(await screen.findByTestId("confirm-button"));

    await waitFor(() => expect(actions.eraseRespondentAction).toHaveBeenCalledTimes(1));
    expect(await screen.findByTestId("erasure-result")).toHaveTextContent(
      "Excluído: 2 exibições e 5 respostas apagadas.",
    );
  });
});

describe("DeletionAuditsList", () => {
  it("mostra quando e quanto saiu de cada exclusão", () => {
    const audits: DeletionAudit[] = [
      { id: "a-1", displaysDeleted: 2, answersDeleted: 5, performedAt: "2026-09-12T15:00:00Z" },
    ];

    render(<DeletionAuditsList audits={audits} />);

    expect(screen.getAllByTestId("deletion-audit-row")).toHaveLength(1);
    expect(screen.getByTestId("deletion-audit-displays")).toHaveTextContent("2");
    expect(screen.getByTestId("deletion-audit-answers")).toHaveTextContent("5");
  });

  it("sem exclusões, diz isso", () => {
    render(<DeletionAuditsList audits={[]} />);

    expect(screen.getByTestId("deletion-audits-empty")).toBeInTheDocument();
  });
});

describe("RetentionPolicyCard", () => {
  const configured: RetentionPreview = {
    configured: true,
    answerRetentionDays: 30,
    textRetentionDays: 30,
    nextRunAt: "2026-09-13T03:47:00Z",
    nextRun: { answers: 3, texts: 1 },
    nextWeek: { answers: 3, texts: 1 },
    firstDiscardPending: true,
  };

  it("mostra a política e o aviso antes do descarte, com o caminho para exportar", () => {
    render(<RetentionPolicyCard applicationId="app-1" preview={configured} />);

    expect(screen.getByTestId("retention-policy-description")).toHaveTextContent("30 dias");
    expect(screen.getByTestId("retention-warning")).toHaveTextContent(
      "3 respostas e 1 texto livre serão descartados",
    );
    expect(screen.getByRole("link", { name: "Editar prazos" })).toHaveAttribute("href", "/aplicacoes/app-1");
    expect(screen.getByRole("link", { name: /Exportar/ })).toHaveAttribute(
      "href",
      "/aplicacoes/app-1/pesquisas",
    );
  });

  it("sem política, não avisa nem oferece exportar", () => {
    render(
      <RetentionPolicyCard
        applicationId="app-1"
        preview={{ ...configured, configured: false, nextRunAt: undefined }}
      />,
    );

    expect(screen.queryByTestId("retention-warning")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Exportar/ })).not.toBeInTheDocument();
  });
});
