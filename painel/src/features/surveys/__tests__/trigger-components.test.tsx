import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { FormState } from "@/shared/lib";

import type { Trigger } from "../schemas/trigger";

const actions = vi.hoisted(() => ({
  defineTriggerAction: vi.fn(),
  addSegmentationRuleAction: vi.fn(),
  removeSegmentationRuleAction: vi.fn(),
}));
vi.mock("../actions", () => actions);

const { TriggerPanel } = await import("../components/trigger/trigger-panel");
const { RuleForm } = await import("../components/trigger/rule-form");

const trigger: Trigger = {
  eventName: "checkout.completed",
  windowStart: "2026-09-09T15:00:00Z",
  samplingRate: 0.25,
  rules: [
    { id: "rule-1", attribute: "plano", operation: "equals", value: "pro" },
    { id: "rule-2", attribute: "cupom", operation: "present" },
  ],
};

beforeEach(() => {
  for (const action of Object.values(actions)) {
    action.mockReset();
    action.mockResolvedValue({ status: "success", data: undefined } satisfies FormState);
  }
});

describe("TriggerPanel", () => {
  it("exibe disparo e regras juntos (FR-028)", () => {
    render(<TriggerPanel applicationId="app-1" surveyId="srv-1" trigger={trigger} />);

    const panel = screen.getByTestId("trigger-panel");
    expect(panel).toHaveTextContent("checkout.completed");
    expect(panel).toHaveTextContent("25%");
    expect(screen.getAllByTestId("rule-item")).toHaveLength(2);
  });

  it("descreve a regra com e sem valor de comparação", () => {
    render(<TriggerPanel applicationId="app-1" surveyId="srv-1" trigger={trigger} />);

    const [comValor, semValor] = screen.getAllByTestId("rule-item");
    expect(comValor).toHaveTextContent('plano é igual a "pro"');
    expect(semValor).toHaveTextContent("cupom está presente");
    expect(semValor).not.toHaveTextContent('""');
  });

  it("diz explicitamente que o disparo não está configurado", () => {
    render(<TriggerPanel applicationId="app-1" surveyId="srv-1" />);

    expect(screen.getByTestId("trigger-panel")).toHaveTextContent("não configurado");
    expect(screen.getByTestId("trigger-form")).toBeInTheDocument();
  });

  it("mostra janela aberta quando não há fim", () => {
    render(<TriggerPanel applicationId="app-1" surveyId="srv-1" trigger={trigger} />);

    expect(screen.getByTestId("trigger-panel")).toHaveTextContent("janela aberta");
  });

  it("exibe a taxa zero em vez de escondê-la como ausência", () => {
    render(
      <TriggerPanel
        applicationId="app-1"
        surveyId="srv-1"
        trigger={{ ...trigger, samplingRate: 0 }}
      />,
    );

    expect(screen.getByTestId("trigger-panel")).toHaveTextContent("0%");
  });

  it("em somente leitura não oferece redefinir nem mexer nas regras", () => {
    render(<TriggerPanel applicationId="app-1" surveyId="srv-1" trigger={trigger} readOnly />);

    expect(screen.queryByTestId("trigger-form")).not.toBeInTheDocument();
    expect(screen.queryByTestId("rule-form")).not.toBeInTheDocument();
    expect(screen.queryByTestId("add-rule-button")).not.toBeInTheDocument();
    expect(screen.queryByTestId("remove-rule-button")).not.toBeInTheDocument();
  });

  it("exige confirmação explícita para remover uma regra", async () => {
    render(<TriggerPanel applicationId="app-1" surveyId="srv-1" trigger={trigger} />);

    await userEvent.click(screen.getAllByTestId("remove-rule-button")[0]);
    expect(await screen.findByTestId("confirm-dialog")).toBeInTheDocument();
    expect(actions.removeSegmentationRuleAction).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId("confirm-button"));
    expect(actions.removeSegmentationRuleAction).toHaveBeenCalledWith("app-1", "srv-1", "rule-1");
  });
});

describe("RuleForm", () => {
  it("mostra o campo de valor só nas operações que o exigem", async () => {
    render(<RuleForm applicationId="app-1" surveyId="srv-1" />);

    // equals é o padrão: o campo está presente.
    expect(screen.getByTestId("rule-value-input")).toBeInTheDocument();

    await userEvent.click(screen.getByTestId("rule-operation-select"));
    await userEvent.click(await screen.findByRole("option", { name: "está presente" }));
    expect(screen.queryByTestId("rule-value-input")).not.toBeInTheDocument();

    await userEvent.click(screen.getByTestId("rule-operation-select"));
    await userEvent.click(await screen.findByRole("option", { name: "é diferente de" }));
    expect(await screen.findByTestId("rule-value-input")).toBeInTheDocument();
  });

  it("preserva a operação escolhida quando o envio é recusado", async () => {
    actions.addSegmentationRuleAction.mockResolvedValue({
      status: "error",
      message: "Regra já existe.",
      fieldErrors: {},
      values: { attribute: "cupom", operation: "present" },
    } satisfies FormState);

    render(<RuleForm applicationId="app-1" surveyId="srv-1" />);

    await userEvent.click(screen.getByTestId("rule-operation-select"));
    await userEvent.click(await screen.findByRole("option", { name: "está presente" }));
    await userEvent.type(screen.getByLabelText("Atributo"), "cupom");
    await userEvent.click(screen.getByTestId("add-rule-button"));

    expect(await screen.findByTestId("form-error")).toHaveTextContent("Regra já existe.");
    expect(screen.getByTestId("rule-operation-select")).toHaveTextContent("está presente");
    expect(screen.queryByTestId("rule-value-input")).not.toBeInTheDocument();
  });
});
