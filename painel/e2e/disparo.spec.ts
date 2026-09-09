import { expect, test, type Page } from "@playwright/test";

import { createApplication, createSurvey } from "./support/helpers";

async function definirDisparo(page: Page, evento: string, inicio = "2026-09-09T12:00") {
  await page.getByTestId("event-name-input").fill(evento);
  await page.getByTestId("window-start-input").fill(inicio);
  await page.getByTestId("submit-button").click();
  await expect(page.getByTestId("trigger-panel")).toContainText(evento);
}

test.describe("US4 — configurar o disparo e as regras de segmentação", () => {
  test("parte de 'não configurado' e define o disparo", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);

    await page.goto(`/aplicacoes/${application.id}/pesquisas/${survey.id}/disparo`);
    await expect(page.getByTestId("trigger-panel")).toContainText("não configurado");

    await definirDisparo(page, "checkout.completed");
  });

  test("redefinir substitui em vez de duplicar (FR-025)", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);

    await page.goto(`/aplicacoes/${application.id}/pesquisas/${survey.id}/disparo`);
    await definirDisparo(page, "checkout.completed");
    await definirDisparo(page, "app_open");

    const panel = page.getByTestId("trigger-panel");
    await expect(panel).toContainText("app_open");
    await expect(panel).not.toContainText("checkout.completed");
  });

  test("recusa nome de evento fora do padrão sem apagar o formulário", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);

    await page.goto(`/aplicacoes/${application.id}/pesquisas/${survey.id}/disparo`);
    await page.getByTestId("event-name-input").fill("Checkout Completed");
    await page.getByTestId("window-start-input").fill("2026-09-09T12:00");
    await page.getByTestId("submit-button").click();

    await expect(page.getByTestId("field-error-eventName")).toBeVisible();
    await expect(page.getByTestId("event-name-input")).toHaveValue("Checkout Completed");
  });

  test("adiciona regra com e sem valor, remove uma e persiste a outra", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);

    await page.goto(`/aplicacoes/${application.id}/pesquisas/${survey.id}/disparo`);
    await definirDisparo(page, "checkout.completed");

    // Regra `equals`: o campo de valor existe e é exigido.
    await expect(page.getByTestId("rule-value-input")).toBeVisible();
    await page.getByLabel("Atributo").fill("plano");
    await page.getByTestId("rule-value-input").fill("pro");
    await page.getByTestId("add-rule-button").click();
    await expect(page.getByTestId("rule-item")).toHaveCount(1);

    // Regra `present`: o campo de valor nem é renderizado.
    await page.getByLabel("Atributo").fill("cupom");
    await page.getByTestId("rule-operation-select").click();
    await page.getByRole("option", { name: "está presente", exact: true }).click();
    await expect(page.getByTestId("rule-value-input")).toHaveCount(0);
    await page.getByTestId("add-rule-button").click();
    await expect(page.getByTestId("rule-item")).toHaveCount(2);

    // Remover uma preserva a outra.
    await page
      .getByTestId("rule-item")
      .filter({ hasText: "plano" })
      .getByTestId("remove-rule-button")
      .click();
    await page.getByTestId("confirm-button").click();
    await expect(page.getByTestId("rule-item")).toHaveCount(1);

    await page.reload();
    await expect(page.getByTestId("rule-item")).toHaveCount(1);
    await expect(page.getByTestId("rule-item")).toContainText("cupom está presente");
    await expect(page.getByTestId("trigger-panel")).toContainText("checkout.completed");
  });
});
