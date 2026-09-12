import { expect, test, type Page } from "@playwright/test";

import {
  addQuestion,
  createApplication,
  createSurvey,
  publishFirstVersion,
  seedObservedEvents,
  seedSdkErrors,
  seedSdkVersions,
  seedSuppressions,
  usingStubApi,
} from "./support/helpers";

test.skip(
  !usingStubApi,
  "Versões, supressões e erros do SDK só existem com o SDK; com E2E_API=real não há como semeá-los.",
);

const EVENTO = "checkout.completed";

async function pesquisaPublicada(page: Page) {
  const application = await createApplication(page);
  const survey = await createSurvey(page, application.id);
  const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;

  await addQuestion(page, base, "O que achou do checkout?", "Texto livre");
  await publishFirstVersion(page, base);

  return { applicationId: application.id, surveyId: survey.id, base };
}

test("a aba Saúde mostra a distribuição de versões e marca a que sumiu do tráfego", async ({
  page,
  request,
}) => {
  const application = await createApplication(page);
  const longAgo = new Date(Date.now() - 40 * 24 * 60 * 60 * 1000).toISOString();

  await seedSdkVersions(request, {
    applicationId: application.id,
    versions: [
      { version: "1.1.0", requestCount: 300, recentRequestCount: 300 },
      { version: "1.0.0", requestCount: 900, recentRequestCount: 100 },
      { version: "0.9.0", requestCount: 5000, recentRequestCount: 0, lastSeenAt: longAgo },
    ],
  });

  await page.goto(`/aplicacoes/${application.id}`);
  await page.getByRole("link", { name: "Saúde" }).click();
  await page.waitForURL(`**/aplicacoes/${application.id}/saude`);

  const rows = page.getByTestId("sdk-version-row");
  await expect(rows).toHaveCount(3);
  await expect(rows.first().getByTestId("sdk-version")).toHaveText("1.1.0");

  const newest = rows.filter({ hasText: "1.1.0" });
  await expect(newest.getByTestId("sdk-version-share")).toHaveText("75%");
  await expect(newest.getByTestId("sdk-version-stale")).toHaveCount(0);

  const gone = rows.filter({ hasText: "0.9.0" });
  await expect(gone.getByTestId("sdk-version-stale")).toBeVisible();
});

test("os erros do SDK são listados, filtrados por tipo e mostram o contexto", async ({
  page,
  request,
}) => {
  const application = await createApplication(page);

  await seedSdkErrors(request, {
    applicationId: application.id,
    errors: [
      {
        kind: "render_error",
        message: "Tipo de pergunta sem renderizador: matrix",
        sdkVersion: "1.0.0",
        context: { questionType: "matrix" },
      },
      { kind: "network_error", message: "Tempo esgotado", sdkVersion: "1.1.0" },
    ],
  });

  await page.goto(`/aplicacoes/${application.id}/saude`);
  await expect(page.getByTestId("sdk-error-item")).toHaveCount(2);

  await page.getByTestId("filter-kind").click();
  await page.getByRole("option", { name: "Renderização" }).click();
  await page.getByTestId("apply-filters").click();

  await expect(page).toHaveURL(/tipo=render_error/);
  const item = page.getByTestId("sdk-error-item");
  await expect(item).toHaveCount(1);
  await expect(item.getByTestId("sdk-error-message")).toContainText("matrix");

  await item.getByTestId("sdk-error-context-toggle").click();
  await expect(item.getByTestId("sdk-error-context")).toContainText("questionType");
});

test("supressão relevante aparece nos resultados com motivo e versão mínima, não como falta de resposta", async ({
  page,
  request,
}) => {
  const scenario = await pesquisaPublicada(page);

  await seedSuppressions(request, {
    applicationId: scenario.applicationId,
    surveyId: scenario.surveyId,
    count: 6,
    reason: "unknown_question_type",
    sdkVersion: "0.9.0",
  });

  await page.goto(`${scenario.base}/resultados`);

  const notice = page.getByTestId("survey-health-suppression");
  await expect(notice).toBeVisible();
  await expect(notice).toContainText("não conhece um tipo de pergunta");
  await expect(notice).toContainText("1.0.0");
  await expect(page.getByTestId("empty-state")).toHaveCount(0);
});

test("evento que nunca chegou é distinguível de supressão e some quando o evento chega", async ({
  page,
  request,
}) => {
  const scenario = await pesquisaPublicada(page);

  await page.goto(`${scenario.base}/resultados`);
  await expect(page.getByTestId("survey-health-event-missing")).toContainText(EVENTO);
  await expect(page.getByTestId("survey-health-suppression")).toHaveCount(0);

  await seedObservedEvents(request, {
    applicationId: scenario.applicationId,
    events: [{ name: EVENTO }],
  });

  await page.reload();
  await expect(page.getByTestId("survey-health-event-missing")).toHaveCount(0);
});

test("publicar com a maioria do tráfego em versão antiga do SDK avisa antes, sem bloquear", async ({
  page,
  request,
}) => {
  const application = await createApplication(page);
  await seedSdkVersions(request, {
    applicationId: application.id,
    versions: [
      { version: "0.9.0", requestCount: 80, recentRequestCount: 80 },
      { version: "1.0.0", requestCount: 20, recentRequestCount: 20 },
    ],
  });

  const survey = await createSurvey(page, application.id);
  const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;
  await addQuestion(page, base, "O que achou?", "Texto livre");

  await page.goto(`${base}/publicacao`);

  const warning = page.getByTestId("warning-item").filter({ hasText: "1.0.0" });
  await expect(warning).toBeVisible();
  await expect(warning).toContainText("80%");
});
