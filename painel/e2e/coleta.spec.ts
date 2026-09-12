import { expect, test, type Locator, type Page } from "@playwright/test";

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
  "A leitura da coleta exige dados que só o SDK cria; com E2E_API=real não há como semeá-los.",
);

const NPS = "Qual sua nota?";
const TEXTO = "O que faltou?";
const MOTIVO = "Por que essa nota?";

async function preencherPeriodo(from: Locator, to: Locator, start: string, end: string) {
  await expect(async () => {
    await from.fill(start);
    await to.fill(end);
    await expect(from).toHaveValue(start);
    await expect(to).toHaveValue(end);
  }).toPass();
}

type Cenario = {
  applicationId: string;
  surveyId: string;
  base: string;
  respondentIds: string[];
  displayIds: string[];
};

async function cenario(page: Page, request: Parameters<typeof seedCollect>[0]): Promise<Cenario> {
  const application = await createApplication(page);
  const survey = await createSurvey(page, application.id);
  const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;

  await addQuestion(page, base, NPS, "NPS");
  await addQuestion(page, base, MOTIVO, "Texto livre");
  await addQuestion(page, base, TEXTO, "Texto livre");
  await publishFirstVersion(page, base);

  const [nota, motivo, texto] = await questionKeysOf(page, base);

  const seeded = await seedCollect(request, {
    applicationId: application.id,
    respondents: [
      { identityKind: "APP_REFERENCE", identityValue: "user-8821" },
      { identityKind: "DEVICE", identityValue: "device-a91f" },
    ],
    displays: [
      {
        respondent: 0,
        surveyId: survey.id,
        outcome: "COMPLETED",
        sdkVersion: "1.4.0",
        openedAt: "2026-09-08T14:22:31Z",
        closedAt: "2026-09-08T14:23:07Z",
        attributes: { plano: "pro" },
        answers: [
          { questionKey: nota, status: "ANSWERED", number: 9, options: [] },
          { questionKey: motivo, status: "SKIPPED", options: [] },
          { questionKey: texto, status: "EXPIRED", options: [] },
        ],
      },
      {
        respondent: 1,
        surveyId: survey.id,
        outcome: "DISMISSED",
        openedAt: "2026-09-07T10:00:00Z",
        closedAt: "2026-09-07T10:00:20Z",
      },
      {
        respondent: 0,
        surveyId: survey.id,
        outcome: "STARTED",
        openedAt: "2026-09-06T08:00:00Z",
      },
    ],
  });

  return { applicationId: application.id, surveyId: survey.id, base, ...seeded };
}

test.describe("US1 — ver as exibições de uma pesquisa", () => {
  test("a aba Exibições lista da mais recente para a mais antiga, com o fuso declarado", async ({
    page,
    request,
  }) => {
    const { base } = await cenario(page, request);

    await page.goto(base);
    await page.getByRole("link", { name: "Exibições" }).click();
    await page.waitForURL(/\/exibicoes$/);

    await expect(page.getByTestId("display-row")).toHaveCount(3);
    await expect(page.getByTestId("timezone-note")).toContainText("Brasília");

    const linhas = page.getByTestId("display-row");
    await expect(linhas.first()).toContainText("08/09/2026");
    await expect(linhas.last()).toContainText("06/09/2026");

    await expect(linhas.last()).toContainText("ainda aberta");
    await expect(linhas.last()).toContainText("não informada");
  });

  test("filtra por desfecho, e recusa período invertido sem navegar", async ({ page, request }) => {
    const { base } = await cenario(page, request);

    await page.goto(`${base}/exibicoes`);

    await page.getByTestId("filter-outcome").click();
    await page.getByRole("option", { name: "Concluída", exact: true }).click();
    await page.getByTestId("apply-filters").click();

    await expect(page).toHaveURL(/desfecho=COMPLETED/);
    await expect(page.getByTestId("display-row")).toHaveCount(1);
    await expect(page.getByTestId("display-outcome-badge")).toContainText("Concluída");

    await page.reload();
    await expect(page.getByTestId("display-row")).toHaveCount(1);

    const antes = page.url();
    await preencherPeriodo(
      page.getByTestId("filter-from"),
      page.getByTestId("filter-to"),
      "2026-09-10T10:00",
      "2026-09-08T10:00",
    );
    await page.getByTestId("apply-filters").click();

    await expect(page.getByTestId("field-error-periodo")).toBeVisible();
    expect(page.url()).toBe(antes);
    await expect(page.getByTestId("display-row")).toHaveCount(1);
  });

  test("recorte sem resultado tem vazio próprio, e limpar filtros devolve a listagem", async ({
    page,
    request,
  }) => {
    const { base } = await cenario(page, request);

    await page.goto(`${base}/exibicoes?de=2020-01-01T00:00&ate=2020-01-02T00:00`);

    await expect(page.getByTestId("empty-state")).toContainText("Nenhuma exibição neste recorte");
    await expect(page.getByTestId("display-row")).toHaveCount(0);

    await page.getByRole("link", { name: "Limpar filtros" }).click();

    await expect(page.getByTestId("display-row")).toHaveCount(3);
  });

  test("pesquisa nunca publicada diz que falta publicar, e não que falta esperar", async ({
    page,
  }) => {
    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);

    await page.goto(`/aplicacoes/${application.id}/pesquisas/${survey.id}/exibicoes`);

    await expect(page.getByTestId("empty-state")).toContainText(
      "Esta pesquisa ainda não foi publicada",
    );
    await expect(page.getByRole("link", { name: "Ir para a publicação" })).toBeVisible();
  });
});

test.describe("US2 — ler o que foi respondido em uma exibição", () => {
  test("o detalhe mostra respostas com enunciado, pulada e expirada distintas, e atributos", async ({
    page,
    request,
  }) => {
    const { base } = await cenario(page, request);

    await page.goto(`${base}/exibicoes?desfecho=COMPLETED`);
    await page.getByTestId("display-row").getByRole("link", { name: "v1" }).click();
    await page.waitForURL(/\/exibicoes\/[^/]+$/);

    await expect(page.getByTestId("display-detail")).toBeVisible();
    await expect(page.getByTestId("answer-item")).toHaveCount(3);

    await expect(page.getByTestId("display-answers")).toContainText(NPS);
    await expect(page.getByTestId("answer-value").first()).toContainText("9");

    await expect(page.getByTestId("answer-skipped")).toContainText("Pulada");
    await expect(page.getByTestId("answer-expired")).toContainText("Texto expirado");
    await expect(page.getByTestId("answer-expired")).toContainText("retenção");

    await expect(page.getByTestId("display-attributes")).toContainText("Atributos desta exibição");
    await expect(page.getByTestId("attribute-item")).toHaveCount(1);
    await expect(page.getByTestId("display-attributes")).toContainText("pro");
  });

  test("exibição dispensada diz por que não há resposta, em vez de mostrar lista vazia", async ({
    page,
    request,
  }) => {
    const { base } = await cenario(page, request);

    await page.goto(`${base}/exibicoes?desfecho=DISMISSED`);
    await page.getByTestId("display-row").getByRole("link", { name: "v1" }).click();

    await expect(page.getByTestId("answers-empty")).toContainText("dispensou");
    await expect(page.getByTestId("answer-item")).toHaveCount(0);
    await expect(page.getByTestId("attributes-empty")).toBeVisible();
  });

  test("identificador de exibição desconhecido leva ao não encontrado, com volta", async ({
    page,
    request,
  }) => {
    const { applicationId } = await cenario(page, request);

    await page.goto(`/aplicacoes/${applicationId}/exibicoes/nao-existe`);

    await expect(page.getByText("Esta exibição não existe")).toBeVisible();
    await expect(page.getByRole("link", { name: "Voltar para as pesquisas" })).toBeVisible();
  });
});

test.describe("Navegação fechada (SC-008, FR-029)", () => {
  test("da exibição se volta à pesquisa, e do histórico se chega à pesquisa e ao detalhe", async ({
    page,
    request,
  }) => {
    const { base, surveyId } = await cenario(page, request);

    await page.goto(`${base}/exibicoes?desfecho=COMPLETED`);
    await page.getByTestId("display-row").getByRole("link", { name: "v1" }).click();

    await page.getByTestId("display-survey-link").click();
    await page.waitForURL(new RegExp(`/pesquisas/${surveyId}/exibicoes$`));
    await expect(page.getByTestId("displays-table")).toBeVisible();

    await page.getByTestId("display-row").first().getByRole("link", { name: "v1" }).click();
    await page.getByTestId("display-respondent-link").click();
    await page.waitForURL(/\/respondentes\/[^/]+$/);

    await page.getByTestId("display-survey-cell").first().getByRole("link").click();
    await page.waitForURL(new RegExp(`/pesquisas/${surveyId}/exibicoes$`));
    await expect(page.getByTestId("displays-table")).toBeVisible();
  });
});

test.describe("US3 — respondentes e histórico", () => {
  test("da exibição se chega ao respondente, e o histórico mostra a pesquisa de cada exibição", async ({
    page,
    request,
  }) => {
    const { base } = await cenario(page, request);

    await page.goto(`${base}/exibicoes?desfecho=COMPLETED`);
    await page.getByTestId("display-row").getByRole("link", { name: "v1" }).click();
    await page.getByTestId("display-respondent-link").click();
    await page.waitForURL(/\/respondentes\/[^/]+$/);

    await expect(page.getByTestId("respondent-summary")).toBeVisible();
    await expect(page.getByTestId("display-row")).toHaveCount(2);
    await expect(page.getByTestId("display-survey-cell").first()).toBeVisible();

    await expect(page.getByTestId("filter-version")).toHaveCount(0);
    await expect(page.getByTestId("filter-outcome")).toBeVisible();
  });

  test("a aba Respondentes lista quem foi visto, em português e sem código cru", async ({
    page,
    request,
  }) => {
    const { applicationId } = await cenario(page, request);

    await page.goto(`/aplicacoes/${applicationId}`);
    await page.getByRole("link", { name: "Respondentes" }).click();
    await page.waitForURL(/\/respondentes$/);

    await expect(page.getByTestId("respondent-row")).toHaveCount(2);
    await expect(page.getByTestId("respondents-table")).toContainText("Referência da aplicação");
    await expect(page.getByTestId("respondents-table")).toContainText("Dispositivo");
    await expect(page.getByTestId("respondents-table")).not.toContainText("APP_REFERENCE");

    await page.getByTestId("respondent-row").first().getByRole("link").click();
    await page.waitForURL(/\/respondentes\/[^/]+$/);
    await expect(page.getByTestId("respondent-displays")).toBeVisible();
  });

  test("aplicação sem contato mostra convite, nunca erro", async ({ page }) => {
    const application = await createApplication(page);

    await page.goto(`/aplicacoes/${application.id}/respondentes`);

    await expect(page.getByTestId("empty-state")).toContainText(
      "Esta aplicação ainda não recebeu contato",
    );
    await expect(page.getByTestId("error-state")).toHaveCount(0);
  });

  test("o histórico distingue 'nenhuma exibição ainda' de 'nenhuma no recorte'", async ({
    page,
    request,
  }) => {
    const { applicationId, respondentIds } = await cenario(page, request);
    const historico = `/aplicacoes/${applicationId}/respondentes/${respondentIds[1]}`;

    await page.goto(`${historico}?desfecho=STARTED`);
    await expect(page.getByTestId("empty-state")).toContainText("Nenhuma exibição neste recorte");

    await page.getByRole("link", { name: "Limpar filtros" }).click();
    await expect(page.getByTestId("display-row")).toHaveCount(1);
  });
});
