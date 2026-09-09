import { expect, test } from "@playwright/test";

test("a raiz do painel leva à listagem de aplicações", async ({ page }) => {
  await page.goto("/");

  await expect(page).toHaveURL(/\/aplicacoes$/);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
});
