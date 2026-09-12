import { randomUUID } from "node:crypto";
import { expect, type APIRequestContext, type Page } from "@playwright/test";

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

const STUB_API_URL = `http://localhost:${process.env.STUB_API_PORT ?? "4010"}/api`;

export const usingStubApi = process.env.E2E_API !== "real";

export type SeedAnswer = {
  questionKey: string;
  status: "ANSWERED" | "SKIPPED" | "NOT_APPLICABLE" | "EXPIRED";
  text?: string;
  number?: number;
  options?: string[];
};

export type SeedDisplay = {
  respondent?: number;
  surveyId: string;
  versionNumber?: number;
  comparabilityGroup?: number;
  outcome?: "STARTED" | "COMPLETED" | "DISMISSED";
  sdkVersion?: string;
  openedAt?: string;
  closedAt?: string;
  attributes?: Record<string, string>;
  answers?: SeedAnswer[];
};

export type SeedCollect = {
  applicationId: string;
  respondents?: Array<{
    identityKind?: "APP_REFERENCE" | "DEVICE";
    identityValue?: string;
    firstSeenAt?: string;
    lastSeenAt?: string;
  }>;
  displays?: SeedDisplay[];
};

export async function seedCollect(
  request: APIRequestContext,
  seed: SeedCollect,
): Promise<{ respondentIds: string[]; displayIds: string[] }> {
  const response = await request.post(`${STUB_API_URL}/stub/collect`, { data: seed });

  if (!response.ok()) {
    throw new Error(`A semeadura de coleta falhou: ${response.status()} ${await response.text()}`);
  }

  return response.json() as Promise<{ respondentIds: string[]; displayIds: string[] }>;
}

export type SeedObservedEvents = {
  applicationId: string;
  events: Array<{ name: string; firstSeenAt?: string; lastSeenAt?: string }>;
};

export async function seedObservedEvents(
  request: APIRequestContext,
  seed: SeedObservedEvents,
): Promise<void> {
  const response = await request.post(`${STUB_API_URL}/stub/observed-events`, { data: seed });

  if (!response.ok()) {
    throw new Error(
      `A semeadura de eventos observados falhou: ${response.status()} ${await response.text()}`,
    );
  }
}

export async function questionKeysOf(page: Page, base: string): Promise<string[]> {
  await page.goto(base);
  await expect(page.getByTestId("questions-list")).toBeVisible();

  const keys = await page.getByTestId("question-item").evaluateAll((items) =>
    items.map((item) => item.getAttribute("data-question-key") ?? ""),
  );

  if (keys.some((key) => key === "")) {
    throw new Error("Uma pergunta da montagem não expôs a chave estável.");
  }

  return keys;
}

export async function addQuestion(
  page: Page,
  base: string,
  statement: string,
  typeLabel: string,
): Promise<void> {
  await page.goto(base);
  await page.getByTestId("add-question-button").click();
  await page.getByLabel("Enunciado").fill(statement);
  await page.getByTestId("question-type-select").click();
  await page.getByRole("option", { name: typeLabel, exact: true }).click();
  await page.getByTestId("submit-button").click();
  await expect(page.getByTestId("questions-list")).toContainText(statement);
}

export async function publishFirstVersion(page: Page, base: string): Promise<void> {
  await page.goto(`${base}/disparo`);
  await page.getByTestId("event-name-input").fill("checkout.completed");
  await page.getByTestId("window-start-input").fill("2026-09-09T12:00");
  await page.getByTestId("submit-button").click();
  await expect(page.getByTestId("trigger-panel")).toContainText("checkout.completed");

  await page.goto(`${base}/publicacao`);
  await expect(page.getByTestId("impediment-item")).toHaveCount(0);
  await page.getByTestId("publish-button").click();

  await page.goto(`${base}/versoes`);
  await expect(page.getByTestId("version-row").filter({ hasText: "v1" })).toContainText(
    "Publicada",
  );
}

export type SeedObservedAttributes = {
  applicationId: string;
  attributes: Array<{ name: string; values?: string[]; lastSeenAt?: string }>;
};

export async function seedObservedAttributes(
  request: APIRequestContext,
  seed: SeedObservedAttributes,
): Promise<void> {
  const response = await request.post(`${STUB_API_URL}/stub/observed-attributes`, { data: seed });

  if (!response.ok()) {
    throw new Error(
      `A semeadura de atributos observados falhou: ${response.status()} ${await response.text()}`,
    );
  }
}

async function seed(request: APIRequestContext, path: string, data: unknown, what: string) {
  const response = await request.post(`${STUB_API_URL}${path}`, { data });

  if (!response.ok()) {
    throw new Error(`A semeadura de ${what} falhou: ${response.status()} ${await response.text()}`);
  }
}

export type SeedSdkVersions = {
  applicationId: string;
  versions: Array<{
    version: string;
    requestCount?: number;
    recentRequestCount?: number;
    firstSeenAt?: string;
    lastSeenAt?: string;
  }>;
};

export function seedSdkVersions(request: APIRequestContext, data: SeedSdkVersions) {
  return seed(request, "/stub/sdk-versions", data, "versões do SDK");
}

export type SeedSdkErrors = {
  applicationId: string;
  errors: Array<{
    kind?: string;
    message?: string;
    sdkVersion?: string;
    context?: Record<string, unknown>;
    occurredAt?: string;
  }>;
};

export function seedSdkErrors(request: APIRequestContext, data: SeedSdkErrors) {
  return seed(request, "/stub/sdk-errors", data, "erros do SDK");
}

export type SeedSuppressions = {
  applicationId: string;
  surveyId: string;
  count: number;
  reason?: "unknown_question_type" | "unsupported_feature";
  sdkVersion?: string;
};

export function seedSuppressions(request: APIRequestContext, data: SeedSuppressions) {
  return seed(request, "/stub/suppressions", data, "supressões");
}

export type SeedRetentionSnapshot = { surveyId: string; discardedBefore: string };

/** O agregado congelado pela retenção; no backend real, só o job de retenção o cria. */
export async function seedRetentionSnapshot(
  request: APIRequestContext,
  seed: SeedRetentionSnapshot,
): Promise<void> {
  const response = await request.post(`${STUB_API_URL}/stub/retention-snapshots`, { data: seed });

  if (!response.ok()) {
    throw new Error(`A semeadura de retenção falhou: ${response.status()} ${await response.text()}`);
  }
}

/** Prazos da aplicação direto pelo contrato do PATCH, sem passar pelo diálogo de edição. */
export async function setApplicationRetention(
  request: APIRequestContext,
  applicationId: string,
  retention: { retentionDays?: number | null; openTextRetentionDays?: number | null },
): Promise<void> {
  const response = await request.patch(`${STUB_API_URL}/applications/${applicationId}`, {
    data: retention,
  });

  if (!response.ok()) {
    throw new Error(`A edição de retenção falhou: ${response.status()} ${await response.text()}`);
  }
}
