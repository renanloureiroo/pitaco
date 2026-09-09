import userEvent from "@testing-library/user-event";
import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { FormState } from "@/shared/lib";

import { ApiKeysTable } from "../components/api-keys-table";
import { RevokeKeyButton } from "../components/revoke-key-button";
import type { ApiKey, IssuedApiKey } from "../schemas/api-key";

const actions = vi.hoisted(() => ({
  issueApiKeyAction: vi.fn(),
  revokeApiKeyAction: vi.fn(),
}));
vi.mock("../actions", () => actions);

const { IssueKeyForm } = await import("../components/issue-key-form");

const active: ApiKey = {
  id: "key-1",
  applicationId: "app-1",
  label: "Produção",
  prefix: "pit_live_abc",
  status: "active",
  createdAt: "2026-09-09T12:00:00Z",
};

const revoked: ApiKey = {
  ...active,
  id: "key-2",
  label: "Antiga",
  status: "revoked",
  revokedAt: "2026-09-10T12:00:00Z",
};

const issued: IssuedApiKey = {
  id: "key-9",
  applicationId: "app-1",
  label: "Produção",
  prefix: "pit_live_xyz",
  createdAt: "2026-09-09T12:00:00Z",
  secret: "pit_live_xyz.o-segredo",
};

beforeEach(() => {
  for (const action of Object.values(actions)) {
    action.mockReset();
  }
});

describe("ApiKeysTable", () => {
  it("mostra rótulo, prefixo e situação — e nada que se pareça com um segredo", () => {
    render(<ApiKeysTable apiKeys={[active, revoked]} />);

    expect(screen.getAllByTestId("api-key-row")).toHaveLength(2);
    expect(screen.getAllByText("pit_live_abc")).toHaveLength(2);
    expect(screen.getByText("Ativa")).toBeInTheDocument();
    expect(screen.getByText("Revogada")).toBeInTheDocument();
    expect(screen.getByTestId("api-keys-table")).not.toHaveTextContent(/segredo/i);
  });

  it("exibe revogação ausente como 'não configurado' na chave ativa", () => {
    render(<ApiKeysTable apiKeys={[active]} />);

    expect(screen.getByTestId("api-key-row")).toHaveTextContent("não configurado");
  });

  it("oferece revogar apenas nas chaves ativas (FR-015)", () => {
    render(
      <ApiKeysTable
        apiKeys={[active, revoked]}
        actionsFor={(apiKey) =>
          apiKey.status === "active" ? (
            <RevokeKeyButton applicationId="app-1" apiKeyId={apiKey.id} label={apiKey.label} />
          ) : null
        }
      />,
    );

    const [linhaAtiva, linhaRevogada] = screen.getAllByTestId("api-key-row");
    expect(within(linhaAtiva).getByTestId("revoke-key-button")).toBeInTheDocument();
    expect(within(linhaRevogada).queryByTestId("revoke-key-button")).not.toBeInTheDocument();
  });
});

describe("RevokeKeyButton", () => {
  it("não revoga sem confirmação explícita", async () => {
    render(<RevokeKeyButton applicationId="app-1" apiKeyId="key-1" label="Produção" />);

    await userEvent.click(screen.getByTestId("revoke-key-button"));
    expect(await screen.findByTestId("confirm-dialog")).toBeInTheDocument();
    expect(actions.revokeApiKeyAction).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId("cancel-button"));
    expect(actions.revokeApiKeyAction).not.toHaveBeenCalled();
  });

  it("revoga depois de confirmar", async () => {
    actions.revokeApiKeyAction.mockResolvedValue({ status: "success", data: undefined });
    render(<RevokeKeyButton applicationId="app-1" apiKeyId="key-1" label="Produção" />);

    await userEvent.click(screen.getByTestId("revoke-key-button"));
    await userEvent.click(await screen.findByTestId("confirm-button"));

    expect(actions.revokeApiKeyAction).toHaveBeenCalledWith("app-1", "key-1");
  });

  it("exibe a recusa de chave já revogada", async () => {
    actions.revokeApiKeyAction.mockResolvedValue({
      status: "error",
      message: "Chave já revogada.",
      fieldErrors: {},
      values: {},
    } satisfies FormState);
    render(<RevokeKeyButton applicationId="app-1" apiKeyId="key-1" label="Produção" />);

    await userEvent.click(screen.getByTestId("revoke-key-button"));
    await userEvent.click(await screen.findByTestId("confirm-button"));

    expect(await screen.findByTestId("form-error")).toHaveTextContent("Chave já revogada.");
  });
});

describe("IssueKeyForm", () => {
  async function emitir() {
    render(<IssueKeyForm applicationId="app-1" />);
    await userEvent.click(screen.getByTestId("issue-key-button"));
    await userEvent.type(await screen.findByLabelText("Rótulo"), "Produção");
    await userEvent.click(screen.getByTestId("submit-button"));
  }

  it("mostra o segredo uma vez, com aviso de que não poderá ser recuperado", async () => {
    actions.issueApiKeyAction.mockResolvedValue({ status: "success", data: issued });

    await emitir();

    expect(await screen.findByTestId("secret-dialog")).toBeInTheDocument();
    expect(screen.getByTestId("secret-value")).toHaveTextContent(issued.secret);
    expect(screen.getByTestId("secret-dialog")).toHaveTextContent(/única vez/i);
  });

  it("remove o segredo do documento ao fechar o diálogo", async () => {
    actions.issueApiKeyAction.mockResolvedValue({ status: "success", data: issued });

    await emitir();
    await userEvent.click(await screen.findByTestId("close-secret-dialog"));

    expect(screen.queryByTestId("secret-value")).not.toBeInTheDocument();
    expect(document.body.textContent ?? "").not.toContain(issued.secret);
  });

  it("copia o segredo para a área de transferência", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal("navigator", { ...navigator, clipboard: { writeText } });
    actions.issueApiKeyAction.mockResolvedValue({ status: "success", data: issued });

    await emitir();
    await userEvent.click(await screen.findByTestId("copy-secret-button"));

    expect(writeText).toHaveBeenCalledWith(issued.secret);
    vi.unstubAllGlobals();
  });

  it("exibe a recusa sem abrir o diálogo de segredo", async () => {
    actions.issueApiKeyAction.mockResolvedValue({
      status: "error",
      message: "Limite de chaves atingido.",
      fieldErrors: {},
      values: { label: "Produção" },
    } satisfies FormState);

    await emitir();

    expect(await screen.findByTestId("form-error")).toHaveTextContent("Limite de chaves atingido.");
    expect(screen.queryByTestId("secret-dialog")).not.toBeInTheDocument();
  });
});
