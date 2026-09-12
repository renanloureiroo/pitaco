import { expect, test, type Page } from "@playwright/test";

import {
  createApplication,
  createSurvey,
  seedObservedAttributes,
  seedObservedEvents,
  usingStubApi,
} from "./support/helpers";

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

  test("sem evento observado, explica e aceita o nome digitado", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);

    await page.goto(`/aplicacoes/${application.id}/pesquisas/${survey.id}/disparo`);
    await expect(page.getByTestId("observed-events-empty")).toBeVisible();
    await expect(page.getByTestId("observed-event-suggestion")).toHaveCount(0);

    await definirDisparo(page, "primeiro.evento");
  });

  test("oferece os eventos já vistos como sugestão e mantém a digitação livre", async ({
    page,
    request,
  }) => {
    test.skip(!usingStubApi, "Só o SDK alimenta o catálogo; com E2E_API=real não há como semeá-lo.");

    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    await seedObservedEvents(request, {
      applicationId: application.id,
      events: [
        { name: "app.opened", lastSeenAt: "2026-09-10T10:00:00Z" },
        { name: "checkout.completed", lastSeenAt: "2026-09-11T10:00:00Z" },
      ],
    });

    await page.goto(`/aplicacoes/${application.id}/pesquisas/${survey.id}/disparo`);

    // Do visto mais recentemente para o mais antigo.
    const suggestions = page.getByTestId("observed-event-suggestion");
    await expect(suggestions).toHaveCount(2);
    await expect(suggestions.nth(0)).toHaveText("checkout.completed");
    await expect(suggestions.nth(1)).toHaveText("app.opened");

    // Escolher uma sugestão preenche o campo; o envio usa esse valor.
    await suggestions.nth(1).click();
    await expect(page.getByTestId("event-name-input")).toHaveValue("app.opened");
    await page.getByTestId("window-start-input").fill("2026-09-09T12:00");
    await page.getByTestId("submit-button").click();
    await expect(page.getByTestId("trigger-event")).toHaveText("app.opened");

    // A digitação continua livre: um evento que ninguém disparou ainda é aceito.
    await definirDisparo(page, "evento.novo");
    await expect(page.getByTestId("trigger-event")).toHaveText("evento.novo");
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

  test("salva prioridade, cota e isenção do descanso, e explica o desempate", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);

    await page.goto(`/aplicacoes/${application.id}/pesquisas/${survey.id}/disparo`);
    await expect(page.getByTestId("tiebreak-rule")).toContainText("vence a de maior prioridade");
    await expect(page.getByTestId("quota-progress")).toHaveText("Sem cota");

    await page.getByTestId("priority-input").fill("7");
    await page.getByTestId("quota-input").fill("50");
    await page.getByTestId("ignore-quiet-period-checkbox").click();
    await expect(page.getByTestId("quiet-period-exemption-notice")).toContainText(
      "gasta a paciência",
    );
    await page.getByTestId("save-exposure-button").click();

    await expect(page.getByTestId("exposure-priority")).toHaveText("7");
    await expect(page.getByTestId("quota-progress")).toHaveText("Concluídas: 0 de 50");

    await page.reload();
    await expect(page.getByTestId("priority-input")).toHaveValue("7");
    await expect(page.getByTestId("quota-input")).toHaveValue("50");
    await expect(page.getByTestId("ignore-quiet-period-checkbox")).toBeChecked();

    // Limpar a cota é removê-la.
    await page.getByTestId("quota-input").fill("");
    await page.getByTestId("save-exposure-button").click();
    await expect(page.getByTestId("quota-progress")).toHaveText("Sem cota");
  });

  test("sugere atributos e valores já observados ao montar a regra", async ({ page, request }) => {
    test.skip(!usingStubApi, "Só o SDK alimenta o catálogo; com E2E_API=real não há como semeá-lo.");

    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    await seedObservedAttributes(request, {
      applicationId: application.id,
      attributes: [
        { name: "plano", values: ["free", "pro"] },
        { name: "versao", values: ["2.1"] },
      ],
    });

    await page.goto(`/aplicacoes/${application.id}/pesquisas/${survey.id}/disparo`);
    await definirDisparo(page, "checkout.completed");

    await expect(page.getByTestId("observed-attribute-suggestion")).toHaveCount(2);
    await page.getByTestId("observed-attribute-suggestion").filter({ hasText: "plano" }).click();
    await expect(page.getByLabel("Atributo")).toHaveValue("plano");

    await expect(page.getByTestId("observed-value-suggestion")).toHaveCount(2);
    await page.getByTestId("observed-value-suggestion").filter({ hasText: "pro" }).click();
    await expect(page.getByTestId("rule-value-input")).toHaveValue("pro");

    await page.getByTestId("add-rule-button").click();
    await expect(page.getByTestId("rule-item")).toContainText('plano é igual a "pro"');
  });
});
