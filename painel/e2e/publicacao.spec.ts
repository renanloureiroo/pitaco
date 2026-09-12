import { expect, test, type Page } from "@playwright/test";

import {
  addQuestion,
  createApplication,
  createSurvey,
  publishFirstVersion,
  uniqueSuffix,
} from "./support/helpers";

async function montarPesquisaCompleta(page: Page, applicationId: string, surveyId: string) {
  const base = `/aplicacoes/${applicationId}/pesquisas/${surveyId}`;

  await page.goto(base);
  await page.getByTestId("add-question-button").click();
  await page.getByLabel("Enunciado").fill("Qual sua nota?");
  await page.getByTestId("question-type-select").click();
  await page.getByRole("option", { name: "NPS", exact: true }).click();
  await page.getByTestId("submit-button").click();
  await expect(page.getByTestId("question-item")).toHaveCount(1);

  await page.goto(`${base}/disparo`);
  await page.getByTestId("event-name-input").fill("checkout.completed");
  await page.getByTestId("window-start-input").fill("2026-09-09T12:00");
  await page.getByTestId("submit-button").click();
  await expect(page.getByTestId("trigger-panel")).toContainText("checkout.completed");
}

test.describe("US5 — publicar e acompanhar versões", () => {
  test("com pesquisa incompleta, lista os impedimentos e bloqueia a publicação", async ({
    page,
  }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);

    await page.goto(`/aplicacoes/${application.id}/pesquisas/${survey.id}/publicacao`);

    await expect(page.getByTestId("impediment-item")).toHaveCount(2);
    await expect(page.getByTestId("impediments-list")).toContainText(
      "Adicione ao menos uma pergunta",
    );
    await expect(page.getByTestId("impediments-list")).toContainText("Defina o disparo");
    await expect(page.getByTestId("publish-button")).toBeDisabled();
  });

  test("resolvidos os impedimentos, publica a versão 1 com o conteúdo congelado", async ({
    page,
  }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;

    await montarPesquisaCompleta(page, application.id, survey.id);

    await page.goto(`${base}/publicacao`);
    await expect(page.getByTestId("impediment-item")).toHaveCount(0);
    await expect(page.getByTestId("impediments-list")).toContainText("Nada impede a publicação");

    // Na versão 1 não há natureza de mudança a informar.
    await expect(page.getByTestId("change-kind-select")).toHaveCount(0);
    await page.getByTestId("publish-button").click();

    await page.goto(`${base}/versoes`);
    const linha = page.getByTestId("version-row").filter({ hasText: "v1" });
    await expect(linha).toContainText("Publicada");

    await linha.getByRole("link", { name: "v1" }).click();
    await expect(page.getByTestId("version-detail")).toBeVisible();
    await expect(page.getByTestId("version-questions")).toContainText("Qual sua nota?");
    // Conteúdo congelado é somente leitura.
    await expect(page.getByTestId("edit-question-button")).toHaveCount(0);
  });

  test("depois de publicar, a montagem fica somente leitura até abrir um rascunho", async ({
    page,
  }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;

    await montarPesquisaCompleta(page, application.id, survey.id);
    await page.goto(`${base}/publicacao`);
    await page.getByTestId("publish-button").click();

    await page.goto(base);
    await expect(page.getByTestId("add-question-button")).toHaveCount(0);

    await page.goto(`${base}/versoes`);
    await page.getByTestId("open-draft-version-button").click();
    await expect(page.getByTestId("version-row")).toHaveCount(2);

    await page.goto(base);
    await expect(page.getByTestId("add-question-button")).toBeVisible();
  });

  test("descarta o rascunho de versão com confirmação, sobrando a publicada", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;

    await montarPesquisaCompleta(page, application.id, survey.id);
    await page.goto(`${base}/publicacao`);
    await page.getByTestId("publish-button").click();

    await page.goto(`${base}/versoes`);
    await page.getByTestId("open-draft-version-button").click();
    await expect(page.getByTestId("version-row")).toHaveCount(2);

    await page.getByTestId("discard-draft-version-button").click();
    await expect(page.getByTestId("confirm-dialog")).toBeVisible();
    await page.getByTestId("confirm-button").click();

    await expect(page.getByTestId("version-row")).toHaveCount(1);
    await expect(page.getByTestId("version-row")).toContainText("Publicada");
  });

  test("a partir da versão 2, a natureza da mudança é exigida", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;

    await montarPesquisaCompleta(page, application.id, survey.id);
    await page.goto(`${base}/publicacao`);
    await page.getByTestId("publish-button").click();

    await page.goto(`${base}/versoes`);
    await page.getByTestId("open-draft-version-button").click();

    await page.goto(base);
    await page.getByTestId("add-question-button").click();
    await page.getByLabel("Enunciado").fill("O que faltou?");
    await page.getByTestId("submit-button").click();
    await expect(page.getByTestId("question-item")).toHaveCount(2);

    await page.goto(`${base}/publicacao`);
    await expect(page.getByTestId("change-kind-select")).toBeVisible();

    await page.getByTestId("publish-button").click();
    await expect(page.getByTestId("field-error-changeKind")).toBeVisible();

    await page.getByTestId("change-kind-select").click();
    await page.getByRole("option", { name: /semântica/i }).click();
    await expect(page.getByTestId("change-kind-select")).toContainText("Semântica");
    await page.getByTestId("change-summary-input").fill("Trocamos a escala");
    await page.getByTestId("publish-button").click();

    await page.goto(`${base}/versoes`);
    await expect(page.getByTestId("version-row")).toHaveCount(2);
    await expect(page.getByTestId("version-row").first()).toContainText("Semântica");
    await expect(page.getByTestId("comparability-panel")).toContainText("Grupo 2");
  });

  test("trata versão inexistente como tela de não encontrado", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);

    await page.goto(`/aplicacoes/${application.id}/pesquisas/${survey.id}/versoes/99`);

    await expect(page.getByRole("heading", { name: "Esta versão não existe" })).toBeVisible();
  });

  test("avisa quando outra pesquisa no ar disputa o mesmo evento, sem bloquear", async ({ page }) => {
    const application = await createApplication(page);
    const concorrente = await createSurvey(page, application.id, `Concorrente ${uniqueSuffix()}`);
    const baseConcorrente = `/aplicacoes/${application.id}/pesquisas/${concorrente.id}`;
    await addQuestion(page, baseConcorrente, "Qual sua nota?", "NPS");
    await publishFirstVersion(page, baseConcorrente);

    const survey = await createSurvey(page, application.id);
    await montarPesquisaCompleta(page, application.id, survey.id);

    await page.goto(`/aplicacoes/${application.id}/pesquisas/${survey.id}/publicacao`);
    const aviso = page.getByTestId("warning-item").filter({ hasText: "escutam o mesmo evento" });
    await expect(aviso).toBeVisible();
    await expect(aviso.getByTestId("competing-survey")).toContainText(concorrente.name);
    await expect(page.getByTestId("publish-button")).toBeEnabled();
  });

  test("avisa a regra que não alcança ninguém, sem bloquear a publicação", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;
    await montarPesquisaCompleta(page, application.id, survey.id);

    await page.goto(`${base}/disparo`);
    await page.getByLabel("Atributo").fill("plano");
    await page.getByTestId("rule-value-input").fill("pro");
    await page.getByTestId("add-rule-button").click();
    await expect(page.getByTestId("rule-item")).toHaveCount(1);

    await page.goto(`${base}/publicacao`);
    await expect(
      page.getByTestId("warning-item").filter({ hasText: "nunca enviou" }),
    ).toContainText("plano");
    await expect(page.getByTestId("impediment-item")).toHaveCount(0);
    await expect(page.getByTestId("publish-button")).toBeEnabled();
  });
});
