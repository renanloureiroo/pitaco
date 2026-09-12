import { expect, test, type Page } from "@playwright/test";

import {
  addQuestion,
  createApplication,
  createSurvey,
  publishFirstVersion,
  questionKeysOf,
  seedCollect,
  usingStubApi,
} from "./support/helpers";

test.skip(
  !usingStubApi,
  "A leitura de resultados exige dados que só o SDK cria; com E2E_API=real não há como semeá-los.",
);

const NPS = "De 0 a 10, recomendaria?";
const TEXTO = "O que faltou?";

type Cenario = { applicationId: string; surveyId: string; base: string };

async function pesquisaPublicada(page: Page): Promise<Cenario> {
  const application = await createApplication(page);
  const survey = await createSurvey(page, application.id);
  const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;

  await addQuestion(page, base, NPS, "NPS");
  await addQuestion(page, base, TEXTO, "Texto livre");
  await publishFirstVersion(page, base);

  return { applicationId: application.id, surveyId: survey.id, base };
}

// Três exibições: concluída com 10 e texto, dispensada com 3, abandonada antiga; mais uma
// concluída em 2025 com texto, para o recorte de período ter o que separar.
async function cenario(page: Page, request: Parameters<typeof seedCollect>[0]): Promise<Cenario> {
  const scenario = await pesquisaPublicada(page);
  const [nota, texto] = await questionKeysOf(page, scenario.base);

  await seedCollect(request, {
    applicationId: scenario.applicationId,
    respondents: [{ identityValue: "user-1" }, { identityValue: "user-2" }, { identityValue: "user-3" }],
    displays: [
      {
        respondent: 0,
        surveyId: scenario.surveyId,
        outcome: "COMPLETED",
        openedAt: "2026-09-08T14:22:31Z",
        closedAt: "2026-09-08T14:23:07Z",
        attributes: { plano: "pro" },
        answers: [
          { questionKey: nota, status: "ANSWERED", number: 10, options: [] },
          { questionKey: texto, status: "ANSWERED", text: "Achei confuso o checkout", options: [] },
        ],
      },
      {
        respondent: 1,
        surveyId: scenario.surveyId,
        outcome: "DISMISSED",
        openedAt: "2026-09-07T10:00:00Z",
        closedAt: "2026-09-07T10:00:20Z",
        answers: [{ questionKey: nota, status: "ANSWERED", number: 3, options: [] }],
      },
      { respondent: 2, surveyId: scenario.surveyId, outcome: "STARTED", openedAt: "2026-09-06T08:00:00Z" },
      {
        respondent: 2,
        surveyId: scenario.surveyId,
        outcome: "COMPLETED",
        openedAt: "2025-01-10T08:00:00Z",
        closedAt: "2025-01-10T08:01:00Z",
        answers: [{ questionKey: texto, status: "ANSWERED", text: "Ficou ótimo", options: [] }],
      },
    ],
  });

  return scenario;
}

test.describe("US5 — ler o resultado de uma pesquisa", () => {
  test("os números conferem com o semeado, com a definição da taxa junto deles", async ({ page, request }) => {
    const { base } = await cenario(page, request);

    await page.goto(base);
    await page.getByRole("link", { name: "Resultados" }).click();
    await page.waitForURL(/\/resultados$/);

    await expect(page.getByTestId("stat-displayed")).toHaveText("4");
    await expect(page.getByTestId("stat-completed")).toHaveText("2");
    await expect(page.getByTestId("stat-dismissed")).toHaveText("1");
    await expect(page.getByTestId("stat-abandoned")).toHaveText("1");
    await expect(page.getByTestId("response-rate-value")).toHaveText("50%");
    await expect(page.getByTestId("response-rate-definition")).toContainText("concluídas ÷ exibidas");
    await expect(page.getByTestId("small-sample-badge")).toBeVisible();

    const nps = page.getByTestId("question-result").filter({ hasText: NPS });
    await expect(nps.getByTestId("nps-promoters")).toHaveText("1");
    await expect(nps.getByTestId("nps-detractors")).toHaveText("1");
    await expect(nps.getByTestId("nps-score")).toHaveText("0");

    await expect(page.getByTestId("open-answer")).toHaveCount(2);
    await expect(page.getByTestId("open-answer").first()).toContainText("Achei confuso o checkout");
    await page.getByTestId("open-answer-context").first().locator("summary").click();
    await expect(page.getByTestId("context-value").first()).toHaveText("10");
  });

  test("pesquisa nunca publicada e pesquisa sem exibição têm vazios próprios", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;

    await page.goto(`${base}/resultados`);
    await expect(page.getByTestId("empty-state")).toContainText("ainda não foi publicada");
    await expect(page.getByTestId("results-filters")).toHaveCount(0);

    await addQuestion(page, base, NPS, "NPS");
    await publishFirstVersion(page, base);

    await page.goto(`${base}/resultados`);
    await expect(page.getByTestId("empty-state")).toContainText("Nenhuma exibição ainda");
    await expect(page.getByTestId("empty-state")).toContainText("disparo");
  });

  test("o recorte de período muda todos os números ao mesmo tempo e fica visível na URL", async ({
    page,
    request,
  }) => {
    const { base } = await cenario(page, request);

    await page.goto(`${base}/resultados`);
    await page.getByTestId("filter-period").click();
    await page.getByRole("option", { name: "Personalizado" }).click();
    await page.getByTestId("filter-from").fill("2026-09-01T00:00");
    await page.getByTestId("filter-to").fill("2026-09-30T00:00");
    await page.getByTestId("apply-filters").click();

    await expect(page).toHaveURL(/de=2026-09-01T00%3A00/);
    await expect(page.getByTestId("stat-displayed")).toHaveText("3");
    await expect(page.getByTestId("open-answer")).toHaveCount(1);
    await expect(page.getByTestId("active-filter")).toContainText("De 2026-09-01");

    await page.getByTestId("filter-attribute").click();
    await page.getByRole("option", { name: "plano" }).click();
    await page.getByTestId("filter-value").click();
    await page.getByRole("option", { name: "Sem o atributo" }).click();
    await page.getByTestId("apply-filters").click();

    await expect(page).toHaveURL(/ausente=1/);
    await expect(page.getByTestId("stat-displayed")).toHaveText("2");
    await expect(page.getByTestId("open-answers-empty")).toBeVisible();

    await page.getByTestId("clear-filters").click();
    await expect(page.getByTestId("stat-displayed")).toHaveText("4");
  });

  test("a busca encontra o termo nas respostas abertas", async ({ page, request }) => {
    const { base } = await cenario(page, request);

    await page.goto(`${base}/resultados`);
    await page.getByTestId("search-input").fill("ÓTIMO");
    await page.getByTestId("search-button").click();

    await expect(page).toHaveURL(/q=%C3%93TIMO/);
    await expect(page.getByTestId("open-answer")).toHaveCount(1);
    await expect(page.getByTestId("open-answer")).toContainText("Ficou ótimo");
    await expect(page.getByTestId("stat-displayed")).toHaveText("4");

    await page.getByTestId("clear-search").click();
    await expect(page.getByTestId("open-answer")).toHaveCount(2);
  });

  test("exportar lembra do dado pessoal e baixa um CSV com o nome da pesquisa", async ({ page, request }) => {
    const { base, surveyId } = await cenario(page, request);

    await page.goto(`${base}/resultados?periodo=90`);
    await page.getByTestId("export-button").click();
    await expect(page.getByTestId("confirm-dialog")).toContainText("dado pessoal");

    const downloadPromise = page.waitForEvent("download");
    await page.getByTestId("confirm-button").click();
    const download = await downloadPromise;

    expect(download.suggestedFilename()).toBe(`resultados-${surveyId}.csv`);
    const path = await download.path();
    const { readFileSync } = await import("node:fs");
    const content = readFileSync(path, "utf8");
    expect(content.startsWith("﻿")).toBe(true);
    expect(content).toContain("displayId,respondentReference");
    expect(content).toContain("Achei confuso o checkout");
    expect(content).not.toContain("Ficou ótimo");
  });
});
