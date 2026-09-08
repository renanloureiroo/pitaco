import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { Button } from "@/components/ui/button";

describe("Button", () => {
  it("expõe o rótulo como botão acessível", () => {
    render(<Button>Salvar pesquisa</Button>);

    expect(screen.getByRole("button", { name: "Salvar pesquisa" })).toBeInTheDocument();
  });

  it("dispara o handler ao ser clicado", async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Salvar pesquisa</Button>);

    await userEvent.click(screen.getByRole("button", { name: "Salvar pesquisa" }));

    expect(onClick).toHaveBeenCalledOnce();
  });

  it("não dispara o handler quando desabilitado", async () => {
    const onClick = vi.fn();
    render(
      <Button disabled onClick={onClick}>
        Salvar pesquisa
      </Button>,
    );

    await userEvent.click(screen.getByRole("button", { name: "Salvar pesquisa" }));

    expect(onClick).not.toHaveBeenCalled();
  });
});
