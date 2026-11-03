import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { FormState } from "@/shared/lib";

const actions = vi.hoisted(() => ({
  updateExposureAction: vi.fn(),
  addSegmentationRuleAction: vi.fn(),
}));
vi.mock("../actions", () => actions);

const { ExposurePanel } = await import("../components/trigger/exposure-panel");
const { WarningsList } = await import("../components/publication/warnings-list");
const { RuleForm } = await import("../components/trigger/rule-form");

beforeEach(() => {
  for (const action of Object.values(actions)) {
    action.mockReset();
    action.mockResolvedValue({ status: "success", data: undefined } satisfies FormState);
  }
});

describe("ExposurePanel", () => {
  it("mostra o progresso rumo à cota e a regra de desempate", () => {
    render(
      <ExposurePanel
        applicationId="app-1"
        surveyId="srv-1"
        priority={5}
        responseQuota={10}
        ignoresQuietPeriod={false}
        quietPeriodDays={7}
        quotaProgress={{ responseQuota: 10, completedResponses: 3 }}
      />,
    );

    expect(screen.getByTestId("quota-progress")).toHaveTextContent("Concluídas: 3 de 10");
    expect(screen.getByTestId("exposure-priority")).toHaveTextContent("5");
    expect(screen.getByTestId("tiebreak-rule")).toHaveTextContent(
      "vence a de maior prioridade; em empate, a publicada há mais tempo",
    );
    expect(screen.getByTestId("quiet-period-policy")).toHaveTextContent(
      "descansa 7 dias entre pesquisas diferentes",
    );
  });

  it("diz que não há cota nem intervalo em vez de mostrar zero", () => {
    render(
      <ExposurePanel applicationId="app-1" surveyId="srv-1" priority={0} ignoresQuietPeriod={false} />,
    );

    expect(screen.getByTestId("quota-progress")).toHaveTextContent("Sem cota");
    expect(screen.getByTestId("quiet-period-policy")).toHaveTextContent("Sem intervalo de descanso");
  });

  it("ao ligar a isenção, explica o que ela significa", async () => {
    render(
      <ExposurePanel applicationId="app-1" surveyId="srv-1" priority={0} ignoresQuietPeriod={false} />,
    );

    expect(screen.getByTestId("quiet-period-exemption-notice")).not.toHaveTextContent(
      "gasta a paciência",
    );
    await userEvent.click(screen.getByTestId("ignore-quiet-period-checkbox"));
    expect(screen.getByTestId("quiet-period-exemption-notice")).toHaveTextContent(
      "gasta a paciência",
    );
  });

  it("em somente leitura não oferece o formulário", () => {
    render(
      <ExposurePanel
        applicationId="app-1"
        surveyId="srv-1"
        priority={0}
        ignoresQuietPeriod={false}
        readOnly
      />,
    );

    expect(screen.queryByTestId("exposure-form")).not.toBeInTheDocument();
  });
});

describe("WarningsList", () => {
  it("lista as pesquisas concorrentes com a prioridade e a regra de desempate", () => {
    render(
      <WarningsList
        applicationId="app-1"
        warnings={[
          {
            code: "trigger.competing_surveys",
            competingSurveys: [{ surveyId: "srv-2", name: "CSAT do suporte", priority: 5 }],
          },
        ]}
      />,
    );

    expect(screen.getByTestId("warning-item")).toHaveTextContent("escutam o mesmo evento");
    expect(screen.getByTestId("competing-survey")).toHaveTextContent("CSAT do suporte");
    expect(screen.getByTestId("competing-survey")).toHaveTextContent("prioridade 5");
    expect(screen.getByRole("link", { name: "CSAT do suporte" })).toHaveAttribute(
      "href",
      "/aplicacoes/app-1/pesquisas/srv-2/disparo",
    );
  });

  it("explica a regra que não alcança ninguém", () => {
    render(
      <WarningsList
        applicationId="app-1"
        warnings={[
          {
            code: "segmentation.no_known_match",
            ruleId: "r-1",
            attribute: "plano",
            competingSurveys: [],
          },
        ]}
      />,
    );

    expect(screen.getByTestId("warning-item")).toHaveTextContent(
      'regra sobre "plano" exige um atributo ou valor que o app nunca enviou',
    );
  });

  it("sem aviso, não renderiza nada", () => {
    const { container } = render(<WarningsList applicationId="app-1" warnings={[]} />);

    expect(container).toBeEmptyDOMElement();
  });
});

describe("RuleForm — sugestões do catálogo", () => {
  it("preenche atributo e valor a partir do que o app já enviou", async () => {
    render(
      <RuleForm
        applicationId="app-1"
        surveyId="srv-1"
        observedAttributes={[
          { name: "plano", values: ["free", "pro"] },
          { name: "versao", values: ["2.1"] },
        ]}
      />,
    );

    expect(screen.queryByTestId("observed-value-suggestion")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: "plano" }));
    expect(screen.getByLabelText("Atributo")).toHaveValue("plano");

    await userEvent.click(screen.getByRole("button", { name: "pro" }));
    expect(screen.getByTestId("rule-value-input")).toHaveValue("pro");
  });

  it("sem catálogo, explica e continua aceitando digitação", async () => {
    render(<RuleForm applicationId="app-1" surveyId="srv-1" />);

    expect(screen.getByTestId("observed-attributes-empty")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Atributo"), "cidade");
    expect(screen.getByLabelText("Atributo")).toHaveValue("cidade");
  });
});
