import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { DisplaySummary } from "../schemas/display";

const navigation = vi.hoisted(() => ({
  push: vi.fn(),
  pathname: "/aplicacoes/app-1/pesquisas/srv-1/exibicoes",
  searchParams: new URLSearchParams(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: navigation.push }),
  usePathname: () => navigation.pathname,
  useSearchParams: () => navigation.searchParams,
}));

const { DisplaysTable } = await import("../components/displays/displays-table");
const { DisplayFiltersForm } = await import("../components/displays/display-filters");
const { displaysEmptyVariant, historyEmptyVariant } = await import("../lib/empty-variants");

const display: DisplaySummary = {
  id: "dsp-1",
  versionId: "ver-1",
  versionNumber: 3,
  comparabilityGroup: 2,
  outcome: "COMPLETED",
  sdkVersion: "1.4.0",
  openedAt: "2026-09-08T14:22:31Z",
  closedAt: "2026-09-08T14:23:07Z",
};

const open: DisplaySummary = {
  ...display,
  id: "dsp-2",
  outcome: "STARTED",
  sdkVersion: undefined,
  closedAt: undefined,
};

beforeEach(() => {
  navigation.push.mockReset();
  navigation.searchParams = new URLSearchParams();
});

describe("os três vazios da listagem por pesquisa (R6, FR-025)", () => {
  it("distingue nunca publicada, nenhuma exibição e nenhuma no recorte", () => {
    expect(displaysEmptyVariant({ filters: {} })).toBe("never_published");
    expect(displaysEmptyVariant({ publishedVersionNumber: 1, filters: {} })).toBe("no_displays");
    expect(
      displaysEmptyVariant({ publishedVersionNumber: 1, filters: { outcome: "COMPLETED" } }),
    ).toBe("no_matches");
  });

  it("nunca publicada vence o recorte — filtrar o que não existe não muda o que fazer", () => {
    expect(displaysEmptyVariant({ filters: { versionNumber: 2 } })).toBe("never_published");
  });

  it("cada condição leva a uma variante diferente, sem duas colapsarem numa só", () => {
    const variantes = [
      displaysEmptyVariant({ filters: {} }),
      displaysEmptyVariant({ publishedVersionNumber: 1, filters: {} }),
      displaysEmptyVariant({ publishedVersionNumber: 1, filters: { from: "2026-09-08T00:00" } }),
    ];

    expect(new Set(variantes).size).toBe(3);
  });

  it("no histórico do respondente há só dois vazios: lá não existe 'nunca publicada'", () => {
    expect(historyEmptyVariant({})).toBe("no_displays");
    expect(historyEmptyVariant({ outcome: "DISMISSED" })).toBe("no_matches");
  });
});

describe("DisplaysTable", () => {
  it("mostra versão, grupo, desfecho e instantes de cada exibição", () => {
    render(<DisplaysTable applicationId="app-1" displays={[display]} />);

    const row = screen.getByTestId("display-row");

    expect(row).toHaveTextContent("v3");
    expect(row).toHaveTextContent("2");
    expect(screen.getByTestId("display-outcome-badge")).toHaveTextContent("Concluída");
    expect(row).toHaveTextContent("1.4.0");
    expect(row).toHaveTextContent("08/09/2026");
  });

  it("exibição ainda aberta diz 'ainda aberta', nunca data vazia nem zero (FR-008)", () => {
    render(<DisplaysTable applicationId="app-1" displays={[open]} />);

    const row = screen.getByTestId("display-row");

    expect(row).toHaveTextContent("ainda aberta");
    expect(row).not.toHaveTextContent("Invalid Date");
    expect(row).not.toHaveTextContent("01/01/1970");
  });

  it("versão do SDK ausente tem texto próprio, distinto do fechamento ausente", () => {
    render(<DisplaysTable applicationId="app-1" displays={[open]} />);

    const row = screen.getByTestId("display-row");

    expect(row).toHaveTextContent("não informada");
    expect(row).toHaveTextContent("ainda aberta");
  });

  it("cada linha liga ao detalhe da exibição pela rota plana da aplicação", () => {
    render(<DisplaysTable applicationId="app-1" displays={[display]} />);

    expect(screen.getByRole("link", { name: "v3" })).toHaveAttribute(
      "href",
      "/aplicacoes/app-1/exibicoes/dsp-1",
    );
  });

  it("esconde a coluna de pesquisa quando a pesquisa é o contexto, e a mostra quando varia", () => {
    const { rerender } = render(<DisplaysTable applicationId="app-1" displays={[display]} />);

    expect(screen.queryByTestId("display-survey-cell")).not.toBeInTheDocument();

    rerender(
      <DisplaysTable
        applicationId="app-1"
        displays={[{ ...display, surveyId: "srv-9" }]}
        showSurvey
        surveyNames={{ "srv-9": "NPS pós-checkout" }}
      />,
    );

    expect(screen.getByTestId("display-survey-cell")).toHaveTextContent("NPS pós-checkout");
  });

  it("não reordena o que a API já ordenou — reordenar quebraria a paginação", () => {
    const older: DisplaySummary = { ...display, id: "dsp-0", openedAt: "2026-09-01T10:00:00Z" };

    render(<DisplaysTable applicationId="app-1" displays={[older, display]} />);

    const [first, second] = screen.getAllByTestId("display-row");

    expect(first).toHaveTextContent("01/09/2026");
    expect(second).toHaveTextContent("08/09/2026");
  });
});

describe("DisplayFiltersForm", () => {
  it("oferece exatamente os três desfechos que o backend aceita (FR-006)", async () => {
    const user = userEvent.setup();
    render(<DisplayFiltersForm filters={{}} versions={[2, 1]} />);

    await user.click(screen.getByTestId("filter-outcome"));

    expect(screen.getByRole("option", { name: "Em andamento" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Concluída" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Dispensada" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /abandon/i })).not.toBeInTheDocument();
  });

  it("recusa início posterior ao fim no campo, sem navegar e sem perder o digitado (FR-007)", async () => {
    const user = userEvent.setup();
    render(<DisplayFiltersForm filters={{}} versions={[1]} />);

    await user.type(screen.getByTestId("filter-from"), "2026-09-10T10:00");
    await user.type(screen.getByTestId("filter-to"), "2026-09-08T10:00");
    await user.click(screen.getByTestId("apply-filters"));

    expect(screen.getByTestId("field-error-periodo")).toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();
    expect(screen.getByTestId("filter-from")).toHaveValue("2026-09-10T10:00");
    expect(screen.getByTestId("filter-to")).toHaveValue("2026-09-08T10:00");
  });

  it("com período coerente, navega e volta para a primeira página", async () => {
    const user = userEvent.setup();
    navigation.searchParams = new URLSearchParams("page=3&desfecho=COMPLETED");
    render(<DisplayFiltersForm filters={{ outcome: "COMPLETED" }} versions={[1]} />);

    await user.type(screen.getByTestId("filter-from"), "2026-09-08T00:00");
    await user.click(screen.getByTestId("apply-filters"));

    const target = String(navigation.push.mock.calls[0][0]);

    expect(target).toContain("de=2026-09-08T00%3A00");
    expect(target).toContain("desfecho=COMPLETED");
    expect(target).not.toContain("page=");
  });

  it("limpar filtros tira todos os recortes da URL, e só eles", async () => {
    const user = userEvent.setup();
    navigation.searchParams = new URLSearchParams("versao=2&desfecho=COMPLETED&de=2026-09-08T00:00&size=50");
    render(<DisplayFiltersForm filters={{ versionNumber: 2, outcome: "COMPLETED" }} versions={[2, 1]} />);

    await user.click(screen.getByTestId("clear-filters"));

    const target = String(navigation.push.mock.calls[0][0]);

    expect(target).toContain("size=50");
    expect(target).not.toContain("versao");
    expect(target).not.toContain("desfecho");
    expect(target).not.toContain("de=");
  });

  it("repovoa o formulário com o recorte vigente — recarregar não perde o filtro", () => {
    render(
      <DisplayFiltersForm
        filters={{ versionNumber: 3, outcome: "DISMISSED", from: "2026-09-08T00:00" }}
        versions={[3, 2, 1]}
      />,
    );

    expect(screen.getByTestId("filter-version")).toHaveTextContent("v3");
    expect(screen.getByTestId("filter-outcome")).toHaveTextContent("Dispensada");
    expect(screen.getByTestId("filter-from")).toHaveValue("2026-09-08T00:00");
  });

  it("sem versões oferecidas, não mostra o seletor de versão (FR-022)", () => {
    render(<DisplayFiltersForm filters={{}} />);

    expect(screen.queryByTestId("filter-version")).not.toBeInTheDocument();
    expect(screen.getByTestId("filter-outcome")).toBeInTheDocument();
  });
});
