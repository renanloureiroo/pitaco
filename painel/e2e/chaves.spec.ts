import { expect, test } from "@playwright/test";

import { createApplication } from "./support/helpers";

test.describe("US2 — emitir e revogar chaves de acesso", () => {
  test("emite uma chave, vê o segredo uma única vez e o perde ao fechar (SC-004)", async ({
    page,
  }) => {
    const application = await createApplication(page);

    await page.goto(`/aplicacoes/${application.id}/chaves`);
    await expect(page.getByTestId("empty-state")).toBeVisible();

    await page.getByTestId("issue-key-button").first().click();
    await page.getByLabel("Rótulo").fill("Produção");
    await page.getByTestId("submit-button").click();

    const dialog = page.getByTestId("secret-dialog");
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText(/única vez/i);

    const secret = (await page.getByTestId("secret-value").textContent())?.trim();
    expect(secret).toBeTruthy();

    await page.getByTestId("close-secret-dialog").click();
    await expect(page.getByTestId("secret-value")).toHaveCount(0);

    // Nem na tabela, nem em lugar nenhum do documento — inclusive depois de recarregar.
    await expect(page.locator("body")).not.toContainText(secret as string);
    await page.reload();
    await expect(page.getByTestId("api-key-row")).toHaveCount(1);
    await expect(page.getByTestId("secret-value")).toHaveCount(0);
    expect(await page.content()).not.toContain(secret as string);
  });

  test("revoga com confirmação e a situação muda", async ({ page }) => {
    const application = await createApplication(page);

    await page.goto(`/aplicacoes/${application.id}/chaves`);
    await page.getByTestId("issue-key-button").first().click();
    await page.getByLabel("Rótulo").fill("Temporária");
    await page.getByTestId("submit-button").click();
    await page.getByTestId("close-secret-dialog").click();

    const row = page.getByTestId("api-key-row").filter({ hasText: "Temporária" });
    await expect(row).toContainText("Ativa");

    await row.getByTestId("revoke-key-button").click();
    await expect(page.getByTestId("confirm-dialog")).toBeVisible();
    await page.getByTestId("confirm-button").click();

    await expect(row).toContainText("Revogada");
    // A ação some da linha: revogar o já revogado não é uma ação oferecida (FR-015).
    await expect(row.getByTestId("revoke-key-button")).toHaveCount(0);
  });

  test("cancelar a confirmação não revoga", async ({ page }) => {
    const application = await createApplication(page);

    await page.goto(`/aplicacoes/${application.id}/chaves`);
    await page.getByTestId("issue-key-button").first().click();
    await page.getByLabel("Rótulo").fill("Preservada");
    await page.getByTestId("submit-button").click();
    await page.getByTestId("close-secret-dialog").click();

    const row = page.getByTestId("api-key-row").filter({ hasText: "Preservada" });
    await row.getByTestId("revoke-key-button").click();
    await page.getByTestId("cancel-button").click();

    await expect(row).toContainText("Ativa");
  });

  test("recusa emissão sem rótulo", async ({ page }) => {
    const application = await createApplication(page);

    await page.goto(`/aplicacoes/${application.id}/chaves`);
    await page.getByTestId("issue-key-button").first().click();
    await page.getByTestId("submit-button").click();

    // O campo é obrigatório: o diálogo continua aberto e nenhuma chave foi criada.
    await expect(page.getByTestId("issue-key-form")).toBeVisible();
    await expect(page.getByTestId("secret-dialog")).toHaveCount(0);
  });
});
