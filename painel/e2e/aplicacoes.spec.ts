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
});
