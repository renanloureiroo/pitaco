import { randomUUID } from "node:crypto";
import { expect, type Page } from "@playwright/test";

/**
 * Isolamento por dado, não por ordem (R9 e Princípio V).
 *
 * Cada teste cria a **sua** aplicação com um nome único e trabalha só dentro dela. Como toda
 * leitura da API é escopada pela aplicação, dois testes em paralelo nunca se enxergam — e não
 * é preciso reset global, que quebraria `fullyParallel`.
 *
 * Tudo aqui passa pela interface, e não por chamada direta ao simulador, para que a mesma
 * suíte rode contra o backend real com `E2E_API=real`.
 */

export function uniqueSuffix(): string {
  return randomUUID().slice(0, 8);
}

export type CreatedApplication = { id: string; name: string; slug: string };

export async function createApplication(
  page: Page,
  overrides: { name?: string; slug?: string } = {},
): Promise<CreatedApplication> {
  const suffix = uniqueSuffix();
  const name = overrides.name ?? `E2E ${suffix}`;
  const slug = overrides.slug ?? `e2e-${suffix}`;

  await page.goto("/aplicacoes/nova");
  await page.getByLabel("Nome").fill(name);
  await page.getByLabel("Slug").fill(slug);
  await page.getByTestId("submit-button").click();

  await page.waitForURL(/\/aplicacoes\/[^/]+$/);
  await expect(page.getByTestId("application-detail")).toBeVisible();

  const id = new URL(page.url()).pathname.split("/").pop();
  if (id === undefined || id === "") {
    throw new Error(`Não foi possível extrair o identificador da aplicação de ${page.url()}`);
  }

  return { id, name, slug };
}

export type CreatedSurvey = { id: string; name: string; applicationId: string };

export async function createSurvey(
  page: Page,
  applicationId: string,
  name = `Pesquisa ${uniqueSuffix()}`,
): Promise<CreatedSurvey> {
  await page.goto(`/aplicacoes/${applicationId}/pesquisas/nova`);
  await page.getByLabel("Nome").fill(name);
  await page.getByTestId("submit-button").click();

  await page.waitForURL(new RegExp(`/aplicacoes/${applicationId}/pesquisas/[^/]+$`));
  await expect(page.getByTestId("survey-header")).toBeVisible();

  const id = new URL(page.url()).pathname.split("/").pop();
  if (id === undefined || id === "") {
    throw new Error(`Não foi possível extrair o identificador da pesquisa de ${page.url()}`);
  }

  return { id, name, applicationId };
}
