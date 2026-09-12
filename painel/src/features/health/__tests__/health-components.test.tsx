import userEvent from "@testing-library/user-event";
import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { SdkErrorReport, SdkVersionUsage } from "../schemas/health";

const navigation = vi.hoisted(() => ({
  push: vi.fn(),
  pathname: "/aplicacoes/app-1/saude",
  searchParams: new URLSearchParams("page=2"),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: navigation.push }),
  usePathname: () => navigation.pathname,
  useSearchParams: () => navigation.searchParams,
}));

const { SdkVersionsTable } = await import("../components/sdk-versions-table");
const { SdkErrorsList } = await import("../components/sdk-errors-list");
const { SdkErrorFiltersForm } = await import("../components/sdk-error-filters");
const { SurveyHealthNotices } = await import("../components/survey-health-notices");

const versions: SdkVersionUsage[] = [
  {
    version: "1.1.0",
    requestCount: 300,
    recentRequestCount: 300,
    recentShare: 0.75,
    firstSeenAt: "2026-09-01T00:00:00Z",
    lastSeenAt: "2026-09-12T00:00:00Z",
    stale: false,
  },
  {
    version: "0.9.0",
    requestCount: 5000,
    recentRequestCount: 0,
    recentShare: 0,
    firstSeenAt: "2026-01-01T00:00:00Z",
    lastSeenAt: "2026-07-01T00:00:00Z",
    stale: true,
  },
];

const errors: SdkErrorReport[] = [
  {
    id: "e-1",
    sdkVersion: "1.0.0",
    kind: "render_error",
    message: "Tipo sem renderizador: matrix",
    context: { questionType: "matrix", render: { component: "Rating" } },
    occurredAt: "2026-09-12T10:00:00Z",
    receivedAt: "2026-09-12T10:00:01Z",
  },
  {
    id: "e-2",
    kind: "unknown",
    message: "",
    context: {},
    occurredAt: "2026-09-12T11:00:00Z",
    receivedAt: "2026-09-12T11:00:01Z",
  },
];

beforeEach(() => {
  navigation.push.mockReset();
  navigation.searchParams = new URLSearchParams("page=2");
});

describe("versões do SDK", () => {
  it("mostra a proporção recente e marca só a versão que sumiu do tráfego", () => {
    render(<SdkVersionsTable versions={versions} />);

    const rows = screen.getAllByTestId("sdk-version-row");
    expect(rows).toHaveLength(2);
    expect(within(rows[0]!).getByTestId("sdk-version-share")).toHaveTextContent("75%");
    expect(within(rows[0]!).queryByTestId("sdk-version-stale")).toBeNull();
    expect(within(rows[1]!).getByTestId("sdk-version-stale")).toHaveTextContent(
      "Sumiu do tráfego",
    );
  });
});

describe("erros do SDK", () => {
  it("mostra tipo, versão e contexto, e diz quando não há mensagem", () => {
    render(<SdkErrorsList errors={errors} />);

    const [first, second] = screen.getAllByTestId("sdk-error-item");
    expect(within(first!).getByTestId("sdk-error-kind")).toHaveTextContent("Renderização");
    expect(within(first!).getByTestId("sdk-error-context")).toHaveTextContent("questionType");
    expect(within(first!).getByTestId("sdk-error-context")).toHaveTextContent(
      '{"component":"Rating"}',
    );
    expect(within(second!).getByTestId("sdk-error-message")).toHaveTextContent("Sem mensagem.");
    expect(within(second!).getByTestId("sdk-error-version")).toHaveTextContent(
      "versão não informada",
    );
    expect(within(second!).queryByTestId("sdk-error-context")).toBeNull();
  });

  it("filtrar por versão leva o recorte para a URL e volta à primeira página", async () => {
    render(<SdkErrorFiltersForm filters={{}} />);

    await userEvent.type(screen.getByTestId("filter-sdk-version"), "1.0.0");
    await userEvent.click(screen.getByTestId("apply-filters"));

    expect(navigation.push).toHaveBeenCalledWith("/aplicacoes/app-1/saude?versao=1.0.0");
  });

  it("limpar tira os filtros da URL", async () => {
    navigation.searchParams = new URLSearchParams("tipo=unknown&versao=1.0.0");
    render(<SdkErrorFiltersForm filters={{ kind: "unknown", sdkVersion: "1.0.0" }} />);

    await userEvent.click(screen.getByTestId("clear-filters"));

    expect(navigation.push).toHaveBeenCalledWith("/aplicacoes/app-1/saude");
  });
});

describe("avisos de saúde da pesquisa", () => {
  it("cada aviso tem a sua marca, e sem aviso nada é desenhado", () => {
    const { rerender } = render(
      <SurveyHealthNotices
        notices={[
          { kind: "suppression", title: "Suprimida", message: "a partir da versão 1.0.0" },
          { kind: "event_missing", title: "Evento", message: "nunca chegou" },
        ]}
      />,
    );

    expect(screen.getByTestId("survey-health-suppression")).toHaveTextContent("1.0.0");
    expect(screen.getByTestId("survey-health-event-missing")).toHaveTextContent("nunca chegou");

    rerender(<SurveyHealthNotices notices={[]} />);
    expect(screen.queryByTestId("survey-health-notices")).toBeNull();
  });
});
