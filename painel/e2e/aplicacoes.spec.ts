import { expect, test } from "@playwright/test";

import { createApplication, uniqueSuffix } from "./support/helpers";

test.describe("US1 — ver e criar aplicações", () => {
  test("cadastra uma aplicação e a encontra na listagem", async ({ page }) => {
    const application = await createApplication(page);

    await expect(page.getByTestId("application-detail")).toContainText(application.name);

    await page.goto("/aplicacoes");
    const row = page.getByTestId("application-row").filter({ hasText: application.name });
    await expect(row).toHaveCount(1);
    await expect(row).toContainText(application.slug);
  });

  test("deriva o slug do nome quando ele não é informado", async ({ page }) => {
    const suffix = uniqueSuffix();

    await page.goto("/aplicacoes/nova");
    await page.getByLabel("Nome").fill(`Derivada ${suffix}`);
    await page.getByTestId("submit-button").click();

    await page.waitForURL(/\/aplicacoes\/[^/]+$/);
    await expect(page.getByTestId("application-detail")).toContainText(`derivada-${suffix}`);
  });

  test("exibe prazo não configurado como texto, nunca como zero", async ({ page }) => {
    await createApplication(page);

    for (const testId of ["quiet-period", "retention", "open-text-retention"]) {
      await expect(page.getByTestId(testId)).toContainText("não configurado");
    }
  });

  test("recusa slug inválido apontando o campo e preservando o que foi digitado", async ({
    page,
  }) => {
    await page.goto("/aplicacoes/nova");
    await page.getByLabel("Nome").fill("Acme App");
    await page.getByLabel("Slug").fill("Acme App");
    await page.getByTestId("submit-button").click();

    await expect(page.getByTestId("field-error-slug")).toBeVisible();
    await expect(page.getByLabel("Nome")).toHaveValue("Acme App");
    await expect(page.getByLabel("Slug")).toHaveValue("Acme App");
    await expect(page).toHaveURL(/\/aplicacoes\/nova$/);
  });

  test("recusa slug já em uso sem perder o formulário", async ({ page }) => {
    const application = await createApplication(page);

    await page.goto("/aplicacoes/nova");
    await page.getByLabel("Nome").fill("Outra");
    await page.getByLabel("Slug").fill(application.slug);
    await page.getByTestId("submit-button").click();

    await expect(page.getByTestId("form-error")).toContainText(application.slug);
    await expect(page.getByLabel("Nome")).toHaveValue("Outra");
  });

  test("mantém o filtro na URL ao voltar do detalhe", async ({ page }) => {
    const application = await createApplication(page);

    await page.goto("/aplicacoes?status=active");
    const row = page.getByTestId("application-row").filter({ hasText: application.name });
    await row.getByRole("link", { name: application.name }).click();

    await page.waitForURL(/\/aplicacoes\/[^/]+$/);
    await page.goBack();

    await expect(page).toHaveURL(/\/aplicacoes\?status=active$/);
    await expect(
      page.getByTestId("application-row").filter({ hasText: application.name }),
    ).toHaveCount(1);
  });

  test("mostra estado vazio, e não erro, quando o filtro não encontra nada", async ({ page }) => {
    await page.goto("/aplicacoes?status=inactive");

    await expect(page.getByTestId("empty-state")).toBeVisible();
    await expect(page.getByTestId("error-state")).toHaveCount(0);
  });

  test("trata identificador inexistente como tela de não encontrado", async ({ page }) => {
    await page.goto("/aplicacoes/nao-existe");

    await expect(page.getByRole("heading", { name: "Esta aplicação não existe" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Voltar para a listagem" })).toBeVisible();
  });

  test("edita nome e prazos pelo detalhe, e prazo em branco é removido", async ({ page }) => {
    const application = await createApplication(page);

    await page.getByTestId("edit-application-button").click();
    await page.getByLabel("Nome").fill(`${application.name} v2`);
    await page.getByLabel("Período de descanso (dias)").fill("7");
    await page.getByLabel("Retenção (dias)").fill("90");
    await page.getByTestId("edit-application-submit").click();

    await expect(page.getByTestId("edit-application-form")).toHaveCount(0);
    await expect(page.getByTestId("application-detail")).toContainText(`${application.name} v2`);
    await expect(page.getByTestId("quiet-period")).toContainText("7 dias");
    await expect(page.getByTestId("retention")).toContainText("90 dias");
    await expect(page.getByTestId("application-detail")).toContainText(application.slug);

    await page.getByTestId("edit-application-button").click();
    await page.getByLabel("Período de descanso (dias)").fill("");
    await page.getByTestId("edit-application-submit").click();

    await expect(page.getByTestId("edit-application-form")).toHaveCount(0);
    await expect(page.getByTestId("quiet-period")).toContainText("não configurado");
    await expect(page.getByTestId("retention")).toContainText("90 dias");
  });

  test("recusa retenção de texto livre acima da geral sem fechar o diálogo", async ({ page }) => {
    await createApplication(page);

    await page.getByTestId("edit-application-button").click();
    await page.getByLabel("Retenção (dias)").fill("30");
    await page.getByLabel("Retenção de texto livre (dias)").fill("90");
    await page.getByTestId("edit-application-submit").click();

    await expect(page.getByTestId("form-error")).toContainText(/texto livre/i);
    await expect(page.getByLabel("Retenção de texto livre (dias)")).toHaveValue("90");
  });

  test("desativa com confirmação e reativa; o estado aparece no detalhe e na lista", async ({
    page,
  }) => {
    const application = await createApplication(page);

    await page.getByTestId("deactivate-application-button").click();
    await expect(page.getByTestId("confirm-dialog")).toBeVisible();
    await page.getByTestId("confirm-button").click();

    await expect(page.getByTestId("application-detail")).toContainText("Inativa");
    await expect(page.getByTestId("deactivate-application-button")).toHaveCount(0);

    await page.goto("/aplicacoes?status=inactive");
    await expect(
      page.getByTestId("application-row").filter({ hasText: application.name }),
    ).toContainText("Inativa");

    await page.goto(`/aplicacoes/${application.id}`);
    await page.getByTestId("activate-application-button").click();

    await expect(page.getByTestId("application-detail")).toContainText("Ativa");
    await expect(page.getByTestId("deactivate-application-button")).toBeVisible();
  });

  test("cancelar a confirmação mantém a aplicação ativa", async ({ page }) => {
    await createApplication(page);

    await page.getByTestId("deactivate-application-button").click();
    await page.getByTestId("cancel-button").click();

    await expect(page.getByTestId("application-detail")).toContainText("Ativa");
  });
});
