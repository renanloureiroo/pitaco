import { expect, test, type Page } from "@playwright/test";

import { createApplication, createSurvey, seedCollect, usingStubApi } from "./support/helpers";

/** Janela já aberta: é o que coloca a pesquisa no ar e destrava pausar e retomar. */
const JANELA_JA_ABERTA = "2026-01-01T00:00";

async function publicar(page: Page, applicationId: string, surveyId: string) {
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
  await page.getByTestId("window-start-input").fill(JANELA_JA_ABERTA);
  await page.getByTestId("submit-button").click();
  await expect(page.getByTestId("trigger-panel")).toContainText("checkout.completed");

  await page.goto(`${base}/publicacao`);
  await expect(page.getByTestId("publish-button")).toBeEnabled();
  await page.getByTestId("publish-button").click();

  await page.goto(base);
  await expect(page.getByTestId("survey-state")).toHaveText("No ar");

  return base;
}

test.describe("US6 — controlar o que está no ar", () => {
  test("pausa, retoma e encerra, oferecendo só o que a API autoriza", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    const base = await publicar(page, application.id, survey.id);

    // No ar: pausar e encerrar são oferecidos; retomar não.
    await expect(page.getByTestId("pause-survey-button")).toBeVisible();
    await expect(page.getByTestId("end-survey-button")).toBeVisible();
    await expect(page.getByTestId("resume-survey-button")).toHaveCount(0);

    await page.getByTestId("pause-survey-button").click();
    await expect(page.getByTestId("survey-state")).toHaveText("Pausada");
    await expect(page.getByTestId("resume-survey-button")).toBeVisible();
    await expect(page.getByTestId("pause-survey-button")).toHaveCount(0);

    await page.getByTestId("resume-survey-button").click();
    await expect(page.getByTestId("survey-state")).toHaveText("No ar");

    // Encerrar avisa que é irreversível e exige confirmação.
    await page.getByTestId("end-survey-button").click();
    await expect(page.getByTestId("confirm-dialog")).toContainText(/irreversível/i);
    await page.getByTestId("confirm-button").click();

    await expect(page.getByTestId("survey-state")).toHaveText("Encerrada");

    // Encerrada não oferece transição nenhuma.
    await expect(page.getByTestId("pause-survey-button")).toHaveCount(0);
    await expect(page.getByTestId("resume-survey-button")).toHaveCount(0);
    await expect(page.getByTestId("end-survey-button")).toHaveCount(0);

    // E o conteúdo fica somente leitura.
    await page.goto(base);
    await expect(page.getByTestId("add-question-button")).toHaveCount(0);
    await expect(page.getByTestId("edit-question-button")).toHaveCount(0);
    await expect(page.getByTestId("rename-survey-button")).toHaveCount(0);
  });

  test("cancelar a confirmação não encerra a pesquisa", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    await publicar(page, application.id, survey.id);

    await page.getByTestId("end-survey-button").click();
    await page.getByTestId("cancel-button").click();

    await expect(page.getByTestId("survey-state")).toHaveText("No ar");
  });

  test("registra o histórico com o motivo de cada mudança", async ({ page }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    await publicar(page, application.id, survey.id);

    await page.getByTestId("pause-survey-button").click();
    await expect(page.getByTestId("survey-state")).toHaveText("Pausada");

    const history = page.getByTestId("transitions-history");
    await expect(history).toContainText("Publicação");
    await expect(history).toContainText("Janela abriu");
    await expect(history).toContainText("Pausada manualmente");
  });

  test("rascunho não oferece transição nenhuma", async ({ page }) => {
    const application = await createApplication(page);
    await createSurvey(page, application.id);

    await expect(page.getByTestId("survey-state")).toHaveText("Rascunho");
    await expect(page.getByTestId("pause-survey-button")).toHaveCount(0);
    await expect(page.getByTestId("end-survey-button")).toHaveCount(0);
  });

  test("encerra sozinha ao atingir a cota e registra o motivo", async ({ page, request }) => {
    test.skip(!usingStubApi, "A conclusão chega pelo SDK; com E2E_API=real não há como semeá-la.");

    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    const base = await publicar(page, application.id, survey.id);

    await page.goto(`${base}/disparo`);
    await page.getByTestId("quota-input").fill("1");
    await page.getByTestId("save-exposure-button").click();
    await expect(page.getByTestId("quota-progress")).toHaveText("Concluídas: 0 de 1");

    await seedCollect(request, {
      applicationId: application.id,
      respondents: [{}],
      displays: [{ surveyId: survey.id, outcome: "COMPLETED", closedAt: "2026-09-08T10:05:00Z" }],
    });

    await page.goto(base);
    await expect(page.getByTestId("survey-state")).toHaveText("Encerrada");
    await expect(page.getByTestId("transitions-history")).toContainText("Encerrada por cota");
  });
});
