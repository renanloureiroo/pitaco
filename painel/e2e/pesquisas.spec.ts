import { expect, test, type Page } from "@playwright/test";

import { createApplication, createSurvey } from "./support/helpers";

async function selecionarTipo(page: Page, rotulo: string) {
  await page.getByTestId("question-type-select").click();
  await page.getByRole("option", { name: rotulo, exact: true }).click();
}

async function adicionarPergunta(
  page: Page,
  enunciado: string,
  tipo: string,
  opcoes: string[] = [],
) {
  await page.getByTestId("add-question-button").click();
  await page.getByLabel("Enunciado").fill(enunciado);
  await selecionarTipo(page, tipo);

  for (const [index, opcao] of opcoes.entries()) {
    if (index > 0) {
      await page.getByTestId("add-option-button").click();
    }
    await page.getByLabel(`Rótulo da opção ${index + 1}`).fill(opcao);
  }

  await page.getByTestId("submit-button").click();
  await expect(page.getByTestId("question-item").filter({ hasText: enunciado })).toHaveCount(1);
}

test.describe("US3 — criar e montar o rascunho de uma pesquisa", () => {
  test("cria uma pesquisa em rascunho, sem perguntas", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);

    await expect(page.getByTestId("survey-header")).toContainText(survey.name);
    await expect(page.getByTestId("survey-state")).toHaveText("Rascunho");
    await expect(page.getByTestId("empty-state")).toBeVisible();

    await page.goto(`/aplicacoes/${application.id}/pesquisas`);
    await expect(page.getByTestId("survey-row").filter({ hasText: survey.name })).toHaveCount(1);
  });

  test("recusa escolha única sem opções antes de enviar", async ({ page }) => {
    const application = await createApplication(page);
    await createSurvey(page, application.id);

    await page.getByTestId("add-question-button").click();
    await page.getByLabel("Enunciado").fill("Qual sua preferida?");
    await selecionarTipo(page, "Escolha única");
    await page.getByTestId("submit-button").click();

    await expect(page.getByTestId("field-error-options")).toBeVisible();
    await expect(page.getByTestId("question-item")).toHaveCount(0);
    // O enunciado digitado continua ali.
    await expect(page.getByLabel("Enunciado")).toHaveValue("Qual sua preferida?");
  });

  test("monta, edita, remove, reordena e persiste a montagem", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);

    await adicionarPergunta(page, "Qual sua preferida?", "Escolha única", ["Café", "Chá"]);
    await adicionarPergunta(page, "Qual sua nota?", "NPS");
    await adicionarPergunta(page, "Quer comentar?", "Texto livre");

    await expect(page.getByTestId("question-item")).toHaveCount(3);

    // Editar reescreve o enunciado sem trocar a pergunta de lugar.
    await page
      .getByTestId("question-item")
      .filter({ hasText: "Quer comentar?" })
      .getByTestId("edit-question-button")
      .click();
    await page.getByLabel("Enunciado").fill("Quer deixar um comentário?");
    await page.getByTestId("submit-button").click();
    await expect(
      page.getByTestId("question-item").filter({ hasText: "Quer deixar um comentário?" }),
    ).toHaveCount(1);

    // Remover exige confirmação explícita.
    await page
      .getByTestId("question-item")
      .filter({ hasText: "Qual sua nota?" })
      .getByTestId("remove-question-button")
      .click();
    await expect(page.getByTestId("confirm-dialog")).toBeVisible();
    await page.getByTestId("confirm-button").click();
    await expect(page.getByTestId("question-item")).toHaveCount(2);

    // Reordenar envia a permutação completa.
    const primeira = page.getByTestId("question-item").first();
    await primeira.getByTestId("move-question-down").click();
    await expect(page.getByTestId("question-item").first()).toContainText(
      "Quer deixar um comentário?",
    );

    await page.reload();
    const itens = page.getByTestId("question-item");
    await expect(itens).toHaveCount(2);
    await expect(itens.nth(0)).toContainText("Quer deixar um comentário?");
    await expect(itens.nth(1)).toContainText("Qual sua preferida?");

    expect(survey.applicationId).toBe(application.id);
  });

  test("não oferece mover quando existe uma única pergunta", async ({ page }) => {
    const application = await createApplication(page);
    await createSurvey(page, application.id);

    await adicionarPergunta(page, "Qual sua nota?", "NPS");

    await expect(page.getByTestId("move-question-up")).toHaveCount(0);
    await expect(page.getByTestId("move-question-down")).toHaveCount(0);
  });

  test("renomeia a pesquisa", async ({ page }) => {
    const application = await createApplication(page);
    await createSurvey(page, application.id);

    await page.getByTestId("rename-survey-button").click();
    await page.getByRole("textbox", { name: "Nome" }).fill("Pesquisa renomeada");
    await page.getByTestId("rename-survey-submit").click();

    await expect(page.getByTestId("survey-header")).toContainText("Pesquisa renomeada");
  });

  test("descarta uma pesquisa nunca publicada", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);

    await page.getByTestId("discard-survey-button").click();
    await page.getByTestId("confirm-button").click();

    await page.waitForURL(new RegExp(`/aplicacoes/${application.id}/pesquisas$`));
    await expect(page.getByTestId("survey-row").filter({ hasText: survey.name })).toHaveCount(0);
  });

  test("trata pesquisa inexistente como tela de não encontrado", async ({ page }) => {
    const application = await createApplication(page);

    await page.goto(`/aplicacoes/${application.id}/pesquisas/nao-existe`);

    await expect(page.getByRole("heading", { name: "Esta pesquisa não existe" })).toBeVisible();
  });
});
