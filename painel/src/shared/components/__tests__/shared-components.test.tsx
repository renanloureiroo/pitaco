import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { ConfirmDialog } from "../confirm-dialog";
import { EmptyState } from "../empty-state";
import { ErrorState } from "../error-state";
import { PageHeader } from "../page-header";
import { Pagination } from "../pagination";

describe("EmptyState", () => {
  it("apresenta a ausência de dado como convite, não como erro", () => {
    render(
      <EmptyState
        title="Nenhuma aplicação ainda"
        description="Cadastre a primeira para começar."
        action={<button type="button">Nova aplicação</button>}
      />,
    );

    expect(screen.getByTestId("empty-state")).toBeInTheDocument();
    expect(screen.getByText("Nenhuma aplicação ainda")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Nova aplicação" })).toBeInTheDocument();
    expect(screen.queryByTestId("error-state")).not.toBeInTheDocument();
  });
});

describe("ErrorState", () => {
  it("aciona o retry do error.tsx ao clicar em tentar novamente", async () => {
    const retry = vi.fn();
    render(<ErrorState description="A API não respondeu." retry={retry} />);

    await userEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));

    expect(retry).toHaveBeenCalledTimes(1);
  });

  it("exibe a descrição da falha recebida", () => {
    render(<ErrorState description="A API não respondeu." retry={vi.fn()} />);

    expect(screen.getByText("A API não respondeu.")).toBeInTheDocument();
  });
});

describe("Pagination", () => {
  const page = { items: [{ id: "a" }, { id: "b" }], page: 1, size: 2, total: 6, totalPages: 3 };

  it("preserva o filtro vigente nos links de navegação", () => {
    render(
      <Pagination pathname="/aplicacoes" searchParams={{ status: "active", page: "1" }} page={page} />,
    );

    expect(screen.getByTestId("pagination-prev")).toHaveAttribute("href", "/aplicacoes?status=active");
    expect(screen.getByTestId("pagination-next")).toHaveAttribute(
      "href",
      "/aplicacoes?status=active&page=2",
    );
  });

  it("informa a faixa exibida e o total do conjunto filtrado", () => {
    render(<Pagination pathname="/aplicacoes" searchParams={{}} page={page} />);

    expect(screen.getByTestId("pagination-info")).toHaveTextContent("3–4 de 6");
    expect(screen.getByTestId("pagination-info")).toHaveTextContent("página 2 de 3");
  });

  it("não oferece navegação além dos limites", () => {
    render(
      <Pagination
        pathname="/aplicacoes"
        searchParams={{}}
        page={{ items: [{ id: "a" }], page: 0, size: 20, total: 1, totalPages: 1 }}
      />,
    );

    expect(screen.getByTestId("pagination-prev")).not.toHaveAttribute("href");
    expect(screen.getByTestId("pagination-next")).not.toHaveAttribute("href");
  });

  it("diz explicitamente quando o filtro não retornou nada", () => {
    render(
      <Pagination
        pathname="/aplicacoes"
        searchParams={{}}
        page={{ items: [], page: 0, size: 20, total: 0, totalPages: 0 }}
      />,
    );

    expect(screen.getByTestId("pagination-info")).toHaveTextContent("Nenhum resultado");
  });
});

describe("ConfirmDialog", () => {
  function setup(onConfirm: () => void) {
    return render(
      <ConfirmDialog
        trigger={<button type="button">Revogar</button>}
        title="Revogar esta chave?"
        description="Aplicações que usam esta chave param de responder."
        confirmLabel="Revogar"
        onConfirm={onConfirm}
      />,
    );
  }

  it("não executa a ação destrutiva sem confirmação explícita", async () => {
    const onConfirm = vi.fn();
    setup(onConfirm);

    await userEvent.click(screen.getByRole("button", { name: "Revogar" }));
    expect(await screen.findByTestId("confirm-dialog")).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId("cancel-button"));
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("executa a ação depois da confirmação", async () => {
    const onConfirm = vi.fn();
    setup(onConfirm);

    await userEvent.click(screen.getByRole("button", { name: "Revogar" }));
    await userEvent.click(await screen.findByTestId("confirm-button"));

    expect(onConfirm).toHaveBeenCalledTimes(1);
  });
});

describe("PageHeader", () => {
  it("monta a trilha com links, deixando a página atual sem link", () => {
    render(
      <PageHeader
        crumbs={[{ label: "Aplicações", href: "/aplicacoes" }, { label: "Acme" }]}
        title="Acme"
      />,
    );

    expect(screen.getByRole("link", { name: "Aplicações" })).toHaveAttribute("href", "/aplicacoes");
    // A página atual aparece na trilha, mas não navega para lugar nenhum.
    const current = screen.getByRole("link", { name: "Acme", current: "page" });
    expect(current).not.toHaveAttribute("href");
    expect(screen.getByRole("heading", { level: 1, name: "Acme" })).toBeInTheDocument();
  });
});
