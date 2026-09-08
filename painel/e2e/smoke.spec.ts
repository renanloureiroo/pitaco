import { expect, test } from "@playwright/test";

test("a home carrega e renderiza seu título principal", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
});
