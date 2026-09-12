import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { FormState } from "@/shared/lib";

import { ApplicationDetail } from "../components/application-detail";
import { ApplicationsTable } from "../components/applications-table";
import type { Application } from "../schemas/application";

/**
 * O formulário é exercitado através de um `useActionState` real, com a action substituída:
 * o que se prova aqui é o comportamento observável — recusa exibida por campo, valores
 * preservados e submit bloqueado enquanto pendente.
 */
const actionMock = vi.hoisted(() => vi.fn());
const updateMock = vi.hoisted(() => vi.fn());
const statusMock = vi.hoisted(() => vi.fn());
vi.mock("../actions", () => ({
  createApplicationAction: actionMock,
  updateApplicationAction: updateMock,
  setApplicationStatusAction: statusMock,
}));

const { ApplicationForm } = await import("../components/application-form");
const { EditApplicationDialog } = await import("../components/edit-application-dialog");
const { ApplicationStatusButton } = await import("../components/application-status-button");

const application: Application = {
  id: "app-1",
  slug: "acme",
  name: "Acme",
  status: "active",
  createdAt: "2026-09-01T12:00:00Z",
  updatedAt: "2026-09-02T12:00:00Z",
};

describe("ApplicationsTable", () => {
  it("lista as aplicações com situação e link para o detalhe", () => {
    render(
      <ApplicationsTable
        applications={[application, { ...application, id: "app-2", name: "Beta", status: "inactive" }]}
      />,
    );

    expect(screen.getAllByTestId("application-row")).toHaveLength(2);
    expect(screen.getByRole("link", { name: "Acme" })).toHaveAttribute("href", "/aplicacoes/app-1");
    expect(screen.getByText("Ativa")).toBeInTheDocument();
    expect(screen.getByText("Inativa")).toBeInTheDocument();
  });

  it("renderiza corpo vazio sem quebrar quando não há aplicação", () => {
    render(<ApplicationsTable applications={[]} />);

    expect(screen.queryAllByTestId("application-row")).toHaveLength(0);
  });
});

describe("ApplicationDetail", () => {
  it("exibe prazo ausente como 'não configurado', nunca como zero", () => {
    render(<ApplicationDetail application={application} />);

    for (const testId of ["quiet-period", "retention", "open-text-retention"]) {
      expect(screen.getByTestId(testId)).toHaveTextContent("não configurado");
      expect(screen.getByTestId(testId)).not.toHaveTextContent(/\b0\b/);
    }
  });

  it("exibe o prazo zero vindo do backend como zero, e não como ausência", () => {
    render(<ApplicationDetail application={{ ...application, quietPeriodDays: 0 }} />);

    expect(screen.getByTestId("quiet-period")).toHaveTextContent("0 dias");
    expect(screen.getByTestId("quiet-period")).not.toHaveTextContent("não configurado");
  });

  it("exibe os prazos configurados", () => {
    render(
      <ApplicationDetail
        application={{ ...application, retentionDays: 30, openTextRetentionDays: 1 }}
      />,
    );

    expect(screen.getByTestId("retention")).toHaveTextContent("30 dias");
    expect(screen.getByTestId("open-text-retention")).toHaveTextContent("1 dia");
  });
});

describe("ApplicationForm", () => {
  beforeEach(() => {
    actionMock.mockReset();
  });

  it("mostra a recusa por campo e mantém o que foi digitado", async () => {
    const rejected: FormState = {
      status: "error",
      message: "Requisição inválida.",
      fieldErrors: { slug: "Use apenas letras minúsculas, números e hífens (ex.: minha-app)." },
      values: { name: "Acme App", slug: "Acme App" },
    };
    actionMock.mockResolvedValue(rejected);

    render(<ApplicationForm />);

    await userEvent.type(screen.getByLabelText("Nome"), "Acme App");
    await userEvent.type(screen.getByLabelText("Slug"), "Acme App");
    await userEvent.click(screen.getByTestId("submit-button"));

    expect(await screen.findByTestId("field-error-slug")).toHaveTextContent(/letras minúsculas/i);
    expect(screen.getByTestId("form-error")).toHaveTextContent("Requisição inválida.");
    expect(screen.getByLabelText("Nome")).toHaveValue("Acme App");
    expect(screen.getByLabelText("Slug")).toHaveValue("Acme App");
  });

  it("bloqueia o envio enquanto a action está pendente — clique duplo não cadastra duas vezes", async () => {
    let resolve: (state: FormState) => void = () => {};
    actionMock.mockImplementation(
      () => new Promise<FormState>((done) => {
        resolve = done;
      }),
    );

    render(<ApplicationForm />);
    await userEvent.type(screen.getByLabelText("Nome"), "Acme");
    await userEvent.click(screen.getByTestId("submit-button"));

    expect(screen.getByTestId("submit-button")).toBeDisabled();
    expect(actionMock).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByTestId("submit-button"));
    expect(actionMock).toHaveBeenCalledTimes(1);

    resolve({ status: "idle" });
  });

  it("não mostra erro nenhum antes do primeiro envio", () => {
    render(<ApplicationForm />);

    expect(screen.queryByTestId("form-error")).not.toBeInTheDocument();
    expect(screen.queryByTestId("field-error-slug")).not.toBeInTheDocument();
    expect(screen.getByTestId("submit-button")).toBeEnabled();
  });
});

describe("EditApplicationDialog", () => {
  beforeEach(() => {
    updateMock.mockReset();
  });

  it("abre já preenchido com os valores atuais, com prazo ausente em branco", async () => {
    render(
      <EditApplicationDialog application={{ ...application, retentionDays: 30 }} />,
    );

    await userEvent.click(screen.getByTestId("edit-application-button"));

    expect(screen.getByLabelText("Nome")).toHaveValue("Acme");
    expect(screen.getByLabelText("Retenção (dias)")).toHaveValue("30");
    expect(screen.getByLabelText("Período de descanso (dias)")).toHaveValue("");
    expect(screen.queryByLabelText("Slug")).not.toBeInTheDocument();
  });

  it("mostra a recusa por campo e mantém o que foi digitado", async () => {
    const rejected: FormState = {
      status: "error",
      message: "Requisição inválida.",
      fieldErrors: { retentionDays: "O prazo precisa ser de pelo menos 1 dia." },
      values: { name: "Acme", retentionDays: "0" },
    };
    updateMock.mockResolvedValue(rejected);

    render(<EditApplicationDialog application={application} />);
    await userEvent.click(screen.getByTestId("edit-application-button"));
    await userEvent.type(screen.getByLabelText("Retenção (dias)"), "0");
    await userEvent.click(screen.getByTestId("edit-application-submit"));

    expect(await screen.findByTestId("field-error-retentionDays")).toHaveTextContent(/1 dia/);
    expect(screen.getByLabelText("Retenção (dias)")).toHaveValue("0");
    expect(screen.getByTestId("edit-application-form")).toBeInTheDocument();
  });

  it("fecha o diálogo quando a action aceita", async () => {
    updateMock.mockResolvedValue({ status: "success", data: undefined });

    render(<EditApplicationDialog application={application} />);
    await userEvent.click(screen.getByTestId("edit-application-button"));
    await userEvent.click(screen.getByTestId("edit-application-submit"));

    await vi.waitFor(() =>
      expect(screen.queryByTestId("edit-application-form")).not.toBeInTheDocument(),
    );
  });
});

describe("ApplicationStatusButton", () => {
  beforeEach(() => {
    statusMock.mockReset();
  });

  it("desativar pede confirmação e só chama a action depois de confirmar", async () => {
    statusMock.mockResolvedValue({ status: "success", data: undefined });

    render(<ApplicationStatusButton application={application} />);
    await userEvent.click(screen.getByTestId("deactivate-application-button"));

    expect(screen.getByTestId("confirm-dialog")).toHaveTextContent(/continuam acessíveis/i);
    expect(statusMock).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId("confirm-button"));

    await vi.waitFor(() => expect(statusMock).toHaveBeenCalledWith("app-1", "deactivate"));
  });

  it("cancelar a confirmação não desativa", async () => {
    render(<ApplicationStatusButton application={application} />);
    await userEvent.click(screen.getByTestId("deactivate-application-button"));
    await userEvent.click(screen.getByTestId("cancel-button"));

    expect(statusMock).not.toHaveBeenCalled();
  });

  it("aplicação inativa oferece reativar, sem confirmação", async () => {
    statusMock.mockResolvedValue({ status: "success", data: undefined });

    render(<ApplicationStatusButton application={{ ...application, status: "inactive" }} />);

    expect(screen.queryByTestId("deactivate-application-button")).not.toBeInTheDocument();
    await userEvent.click(screen.getByTestId("activate-application-button"));

    await vi.waitFor(() => expect(statusMock).toHaveBeenCalledWith("app-1", "activate"));
  });

  it("exibe a recusa da action", async () => {
    statusMock.mockResolvedValue({
      status: "error",
      message: "Aplicação não encontrada.",
      fieldErrors: {},
      values: {},
    });

    render(<ApplicationStatusButton application={{ ...application, status: "inactive" }} />);
    await userEvent.click(screen.getByTestId("activate-application-button"));

    expect(await screen.findByTestId("form-error")).toHaveTextContent("Aplicação não encontrada.");
  });
});
