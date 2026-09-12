import { expect, test, type Page } from "@playwright/test";

import {
  addQuestion,
  createApplication,
  createSurvey,
  publishFirstVersion,
  questionKeysOf,
  seedCollect,
  seedRetentionSnapshot,
  setApplicationRetention,
  usingStubApi,
} from "./support/helpers";

const DAY = 24 * 60 * 60 * 1000;

async function openPrivacy(page: Page, applicationId: string) {
  await page.goto(`/aplicacoes/${applicationId}`);
  await page.getByRole("link", { name: "Privacidade" }).click();
  await page.waitForURL(`**/aplicacoes/${applicationId}/privacidade`);
}

async function publishedSurvey(page: Page) {
  const application = await createApplication(page);
  const survey = await createSurvey(page, application.id);
  const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;

  await addQuestion(page, base, "O que achou?", "Texto livre");
  await publishFirstVersion(page, base);
  const [key] = await questionKeysOf(page, base);

  return { applicationId: application.id, surveyId: survey.id, base, key: key! };
}

test("excluir respondente pede confirmação: cancelar não apaga; confirmar apaga e registra sem a referência", async ({
  page,
  request,
}) => {
  test.skip(!usingStubApi, "Respondente só nasce pelo SDK; com E2E_API=real não há como semeá-lo.");

  const application = await createApplication(page);
  await seedCollect(request, {
    applicationId: application.id,
    respondents: [{ identityKind: "APP_REFERENCE", identityValue: "u-apagar" }],
  });

  await openPrivacy(page, application.id);
  await expect(page.getByTestId("deletion-audits-empty")).toBeVisible();

  await page.getByTestId("erase-identity-input").fill("u-apagar");
  await page.getByTestId("erase-respondent-button").click();
  await expect(page.getByTestId("confirm-dialog")).toContainText("irreversível");
  await page.getByTestId("cancel-button").click();

  await expect(page.getByTestId("erasure-result")).toHaveCount(0);
  await page.goto(`/aplicacoes/${application.id}/respondentes`);
  await expect(page.getByText("u-apagar")).toBeVisible();

  await openPrivacy(page, application.id);
  await page.getByTestId("erase-identity-input").fill("u-apagar");
  await page.getByTestId("erase-respondent-button").click();
  await page.getByTestId("confirm-button").click();

  await expect(page.getByTestId("erasure-result")).toContainText("Excluído");
  await expect(page.getByTestId("deletion-audit-row")).toHaveCount(1);
  await expect(page.getByTestId("deletion-audits")).not.toContainText("u-apagar");

  await page.goto(`/aplicacoes/${application.id}/respondentes`);
  await expect(page.getByText("u-apagar")).toHaveCount(0);
});

test("pedido para quem não existe diz que nada foi apagado e não cria registro", async ({ page }) => {
  const application = await createApplication(page);

  await openPrivacy(page, application.id);
  await page.getByTestId("erase-identity-input").fill("u-nunca-visto");
  await page.getByTestId("erase-respondent-button").click();
  await page.getByTestId("confirm-button").click();

  await expect(page.getByTestId("erasure-result")).toContainText("Nada foi apagado");
  await expect(page.getByTestId("deletion-audit-row")).toHaveCount(0);
});

test("com prazo configurado, a aba avisa o que vai ser descartado antes do primeiro descarte", async ({
  page,
  request,
}) => {
  test.skip(!usingStubApi, "Respostas antigas só existem semeadas; com E2E_API=real não há como criá-las.");

  const { applicationId, surveyId, key } = await publishedSurvey(page);
  await setApplicationRetention(request, applicationId, { retentionDays: 30 });
  const longAgo = new Date(Date.now() - 45 * DAY).toISOString();
  await seedCollect(request, {
    applicationId,
    respondents: [{ identityValue: "u-antigo" }],
    displays: [
      {
        respondent: 0,
        surveyId,
        outcome: "COMPLETED",
        openedAt: longAgo,
        closedAt: longAgo,
        answers: [{ questionKey: key, status: "ANSWERED", text: "resposta antiga" }],
      },
    ],
  });

  await openPrivacy(page, applicationId);

  await expect(page.getByTestId("retention-policy-description")).toContainText("30 dias");
  await expect(page.getByTestId("retention-warning")).toContainText(
    "1 resposta e 1 texto livre serão descartados",
  );
  await expect(page.getByTestId("retention-last-run")).toContainText("Nenhum descarte");
});

test("o aviso de dado pessoal do texto livre é desligado e personalizado pela tela de disparo", async ({
  page,
}) => {
  const application = await createApplication(page);
  const survey = await createSurvey(page, application.id);
  const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;
  await addQuestion(page, base, "O que achou?", "Texto livre");

  await page.goto(`${base}/disparo`);
  const panel = page.getByTestId("free-text-notice-panel");
  await expect(panel.getByTestId("free-text-notice-current")).toContainText("Evite escrever dados pessoais");
  await expect(panel.getByTestId("free-text-notice-unused")).toHaveCount(0);

  await panel.getByTestId("free-text-notice-input").fill("Não escreva telefone aqui.");
  await panel.getByTestId("save-free-text-notice-button").click();
  await expect(panel.getByTestId("free-text-notice-current")).toContainText("Não escreva telefone aqui.");

  await panel.getByTestId("free-text-notice-checkbox").click();
  await panel.getByTestId("save-free-text-notice-button").click();
  await expect(panel.getByTestId("free-text-notice-current")).toContainText("Desligado");

  await page.reload();
  await expect(page.getByTestId("free-text-notice-checkbox")).toHaveAttribute("data-state", "unchecked");
  await expect(page.getByTestId("free-text-notice-input")).toHaveValue("Não escreva telefone aqui.");
});

test("os resultados dizem quando incluem o agregado descartado pela retenção", async ({ page, request }) => {
  test.skip(!usingStubApi, "O agregado congelado só nasce pelo job de retenção; aqui ele é semeado.");

  const { applicationId, surveyId, base, key } = await publishedSurvey(page);
  const recent = new Date(Date.now() - DAY).toISOString();
  await seedCollect(request, {
    applicationId,
    respondents: [{ identityValue: "u-recente" }],
    displays: [
      {
        respondent: 0,
        surveyId,
        outcome: "COMPLETED",
        openedAt: recent,
        closedAt: recent,
        answers: [{ questionKey: key, status: "ANSWERED", text: "recente" }],
      },
    ],
  });
  await seedRetentionSnapshot(request, { surveyId, discardedBefore: "2026-08-01T12:00:00Z" });

  await page.goto(`${base}/resultados`);
  await expect(page.getByTestId("retention-note")).toContainText("Inclui agregados");

  await page.goto(`${base}/resultados?periodo=7`);
  await expect(page.getByTestId("retention-note")).toContainText("não entram neste recorte");
});
