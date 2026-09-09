import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { RespondentDisplay } from "../schemas/display";
import type { Respondent } from "../schemas/respondent";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => "/aplicacoes/app-1/respondentes/rsp-1",
  useSearchParams: () => new URLSearchParams(),
}));

const { RespondentsTable } = await import("../components/respondents/respondents-table");
const { RespondentSummary } = await import("../components/respondents/respondent-summary");
const { DisplaysTable } = await import("../components/displays/displays-table");
const { DisplayFiltersForm } = await import("../components/displays/display-filters");
const { historyEmptyVariant } = await import("../lib/empty-variants");

const respondents: Respondent[] = [
  {
    id: "rsp-1",
    identityKind: "APP_REFERENCE",
    identityValue: "user-8821",
    firstSeenAt: "2026-08-30T09:11:00Z",
    lastSeenAt: "2026-09-08T14:22:31Z",
  },
  {
    id: "rsp-2",
    identityKind: "DEVICE",
    identityValue: "device-a91f",
    firstSeenAt: "2026-09-01T09:11:00Z",
    lastSeenAt: "2026-09-07T10:00:00Z",
  },
];

const display: RespondentDisplay = {
  id: "dsp-1",
  surveyId: "srv-9",
  versionId: "ver-1",
  versionNumber: 3,
  comparabilityGroup: 2,
  outcome: "COMPLETED",
  sdkVersion: "1.4.0",
  openedAt: "2026-09-08T14:22:31Z",
  closedAt: "2026-09-08T14:23:07Z",
};

describe("RespondentsTable", () => {
  it("rotula a forma de identificação em português, nunca o código cru (FR-020)", () => {
    render(<RespondentsTable applicationId="app-1" respondents={respondents} />);

    const table = screen.getByTestId("respondents-table");

    expect(table).toHaveTextContent("Referência da aplicação");
    expect(table).toHaveTextContent("Dispositivo");
    expect(table).not.toHaveTextContent("APP_REFERENCE");
    expect(table).not.toHaveTextContent("DEVICE");
  });

  it("exibe o valor da identificação como veio — ele é opaco para o Pitaco", () => {
    render(<RespondentsTable applicationId="app-1" respondents={respondents} />);

    const [first] = screen.getAllByTestId("respondent-identity-value");

    expect(first).toHaveTextContent("user-8821");
  });

  it("cada linha liga ao histórico do respondente", () => {
    render(<RespondentsTable applicationId="app-1" respondents={respondents} />);

    expect(screen.getByRole("link", { name: "Referência da aplicação" })).toHaveAttribute(
      "href",
      "/aplicacoes/app-1/respondentes/rsp-1",
    );
    expect(screen.getAllByTestId("respondent-row")).toHaveLength(2);
  });

  it("mostra os dois instantes de contato, formatados no fuso de referência", () => {
    render(<RespondentsTable applicationId="app-1" respondents={respondents} />);

    const [row] = screen.getAllByTestId("respondent-row");

    expect(row).toHaveTextContent("30/08/2026");
    expect(row).toHaveTextContent("08/09/2026");
  });
});

describe("RespondentSummary", () => {
  it("sem a leitura do respondente, identifica pelo identificador e aponta a listagem", () => {
    render(<RespondentSummary applicationId="app-1" respondentId="rsp-1" />);

    const summary = screen.getByTestId("respondent-summary");

    expect(summary).toHaveTextContent("rsp-1");
    expect(screen.getByRole("link", { name: "lista de respondentes" })).toHaveAttribute(
      "href",
      "/aplicacoes/app-1/respondentes",
    );
  });

  it("com o respondente conhecido, mostra a identificação e os contatos", () => {
    render(
      <RespondentSummary
        applicationId="app-1"
        respondentId="rsp-1"
        respondent={respondents[0]}
      />,
    );

    const summary = screen.getByTestId("respondent-summary");

    expect(summary).toHaveTextContent("Referência da aplicação");
    expect(summary).toHaveTextContent("user-8821");
    expect(summary).not.toHaveTextContent("APP_REFERENCE");
  });
});

describe("histórico do respondente", () => {
  it("mostra a coluna de pesquisa, que na listagem por pesquisa não existe (R8)", () => {
    render(<DisplaysTable applicationId="app-1" displays={[display]} showSurvey />);

    expect(screen.getByTestId("display-survey-cell")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "srv-9" })).toHaveAttribute(
      "href",
      "/aplicacoes/app-1/pesquisas/srv-9/exibicoes",
    );
  });

  it("não oferece o filtro de versão, e continua oferecendo desfecho e período (FR-022)", () => {
    render(<DisplayFiltersForm filters={{}} />);

    expect(screen.queryByTestId("filter-version")).not.toBeInTheDocument();
    expect(screen.getByTestId("filter-outcome")).toBeInTheDocument();
    expect(screen.getByTestId("filter-from")).toBeInTheDocument();
    expect(screen.getByTestId("filter-to")).toBeInTheDocument();
  });

  it("distingue os dois vazios da tela: nunca recebeu exibição e nenhuma no recorte", () => {
    expect(historyEmptyVariant({})).toBe("no_displays");
    expect(historyEmptyVariant({ outcome: "COMPLETED" })).toBe("no_matches");
    expect(historyEmptyVariant({ from: "2026-09-08T00:00" })).toBe("no_matches");
  });
});
