import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { freeTextNoticeFormSchema } from "../schemas/notice";
import type { FreeTextNotice } from "../schemas/survey";

vi.mock("../actions", () => ({
  updateFreeTextNoticeAction: vi.fn(async () => ({ status: "success", data: undefined })),
}));

const { FreeTextNoticePanel } = await import("../components/trigger/free-text-notice-panel");

const DEFAULT = "Evite escrever dados pessoais, como nome, telefone ou e-mail.";
const notice: FreeTextNotice = { enabled: true, text: DEFAULT, defaultText: DEFAULT };

describe("formulário do aviso de texto livre", () => {
  it("texto em branco vira ausente, que a API envia como volta ao padrão", () => {
    expect(freeTextNoticeFormSchema.parse({ enabled: "true", customText: "   " })).toEqual({
      enabled: true,
      customText: undefined,
    });
  });

  it("desligar chega como false e o texto é aparado", () => {
    expect(freeTextNoticeFormSchema.parse({ enabled: "false", customText: " Sem telefone " })).toEqual({
      enabled: false,
      customText: "Sem telefone",
    });
  });

  it("recusa texto acima de 200 caracteres", () => {
    const result = freeTextNoticeFormSchema.safeParse({ enabled: "true", customText: "a".repeat(201) });

    expect(result.success).toBe(false);
  });
});

describe("FreeTextNoticePanel", () => {
  it("mostra o texto exibido e oferece o padrão como sugestão no campo", () => {
    render(<FreeTextNoticePanel applicationId="app-1" surveyId="srv-1" notice={notice} hasFreeText />);

    expect(screen.getByTestId("free-text-notice-current")).toHaveTextContent(DEFAULT);
    expect(screen.getByTestId("free-text-notice-input")).toHaveAttribute("placeholder", DEFAULT);
    expect(screen.queryByTestId("free-text-notice-unused")).not.toBeInTheDocument();
  });

  it("sem pergunta de texto livre, explica que o aviso ainda não aparece", () => {
    render(
      <FreeTextNoticePanel applicationId="app-1" surveyId="srv-1" notice={notice} hasFreeText={false} />,
    );

    expect(screen.getByTestId("free-text-notice-unused")).toBeInTheDocument();
  });

  it("desmarcar muda o valor enviado para false", async () => {
    const { container } = render(
      <FreeTextNoticePanel applicationId="app-1" surveyId="srv-1" notice={notice} hasFreeText />,
    );

    await userEvent.click(screen.getByTestId("free-text-notice-checkbox"));

    expect(container.querySelector('input[name="enabled"]')).toHaveValue("false");
  });

  it("desligado, diz que os campos aparecem sem aviso; encerrada, não oferece edição", () => {
    render(
      <FreeTextNoticePanel
        applicationId="app-1"
        surveyId="srv-1"
        notice={{ ...notice, enabled: false }}
        hasFreeText
        readOnly
      />,
    );

    expect(screen.getByTestId("free-text-notice-current")).toHaveTextContent("Desligado");
    expect(screen.queryByTestId("free-text-notice-form")).not.toBeInTheDocument();
  });
});
