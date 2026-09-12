import { expect, test, type Page } from "@playwright/test";

import {
  createApplication,
  createSurvey,
  publishFirstVersion,
  questionKeysOf,
  seedCollect,
  uniqueSuffix,
  usingStubApi,
} from "./support/helpers";

async function selecionarTipo(page: Page, rotulo: string) {
  await page.getByTestId("question-type-select").click();
  await page.getByRole("option", { name: rotulo, exact: true }).click();
}

async function adicionarPergunta(page: Page, enunciado: string, tipo: string, opcoes: string[] = []) {
  await page.getByTestId("add-question-button").click();
  await page.getByLabel("Enunciado").fill(enunciado);
  await selecionarTipo(page, tipo);

  for (const [index, opcao] of opcoes.entries()) {
    if (index > 0) {
      await page.getByTestId("add-option-button").click();
    }
    await page.getByLabel(`Rótulo da opção ${index + 1}`).fill(opcao);
  }

  await page.getByTestId("submit-button").click();
  await expect(page.getByTestId("question-item").filter({ hasText: enunciado })).toHaveCount(1);
}

test.describe("Autoria avançada", () => {
  test("cria a pesquisa a partir do modelo de NPS, já com a pergunta do formato", async ({ page }) => {
    const application = await createApplication(page);

    await page.goto(`/aplicacoes/${application.id}/pesquisas/nova`);
    await page.getByLabel("Nome").fill(`NPS ${uniqueSuffix()}`);
    await page.getByTestId("template-option-nps").click();
    await page.getByTestId("submit-button").click();

    await page.waitForURL(new RegExp(`/aplicacoes/${application.id}/pesquisas/[^/]+$`));
    const pergunta = page.getByTestId("question-item");
    await expect(pergunta).toHaveCount(1);
    await expect(pergunta).toContainText("0 a 10");
    await expect(pergunta).toContainText("Nada provável … Extremamente provável");
  });

  test("duplica a pesquisa para outra aplicação, como rascunho com as mesmas perguntas", async ({ page }) => {
    const origem = await createApplication(page);
    const destino = await createApplication(page);
    const survey = await createSurvey(page, origem.id);
    await adicionarPergunta(page, "Qual sua nota?", "NPS");

    await page.getByTestId("duplicate-survey-button").click();
    await page.getByTestId("duplicate-target-select").click();
    await page.getByRole("option", { name: destino.name, exact: true }).click();
    await page.getByTestId("duplicate-survey-submit").click();

    await page.waitForURL(new RegExp(`/aplicacoes/${destino.id}/pesquisas/[^/]+$`));
    await expect(page.getByTestId("survey-header")).toContainText(`Cópia de ${survey.name}`);
    await expect(page.getByTestId("survey-state")).toHaveText("Rascunho");
    await expect(page.getByTestId("question-item").filter({ hasText: "Qual sua nota?" })).toHaveCount(1);
  });

  test("monta uma condição e a lista mostra o resumo", async ({ page }) => {
    const application = await createApplication(page);
    await createSurvey(page, application.id);
    await adicionarPergunta(page, "Gostou?", "Escolha única", ["Sim", "Não"]);

    await page.getByTestId("add-question-button").click();
    await page.getByLabel("Enunciado").fill("O que faltou?");
    await page.getByTestId("condition-toggle").click();
    await page.getByTestId("condition-value-select").click();
    await page.getByRole("option", { name: "Não", exact: true }).click();
    await page.getByTestId("submit-button").click();

    await expect(
      page
        .getByTestId("question-item")
        .filter({ hasText: "O que faltou?" })
        .getByTestId("question-condition"),
    ).toHaveText('Exibida se P1 for "Não"');
  });

  test("condição impossível é recusada, com o erro junto do campo", async ({ page }) => {
    const application = await createApplication(page);
    await createSurvey(page, application.id);
    await adicionarPergunta(page, "Qual sua nota?", "NPS");

    await page.getByTestId("add-question-button").click();
    await page.getByLabel("Enunciado").fill("Por quê?");
    await page.getByTestId("condition-toggle").click();
    await page.getByTestId("condition-operator-select").click();
    await page.getByRole("option", { name: "estiver na faixa" }).click();
    await page.getByTestId("condition-min").fill("0");
    await page.getByTestId("condition-max").fill("11");
    await page.getByTestId("submit-button").click();

    await expect(page.getByTestId("field-error-condition.values")).toBeVisible();
    await expect(page.getByTestId("question-item")).toHaveCount(1);
  });

  test("o resultado consolidado avisa quando a pergunta mudou entre as versões", async ({ page, request }) => {
    test.skip(!usingStubApi, "A leitura de resultados exige dados que só o SDK cria.");

    const application = await createApplication(page);
    const survey = await createSurvey(page, application.id);
    const base = `/aplicacoes/${application.id}/pesquisas/${survey.id}`;
    await adicionarPergunta(page, "Recomendaria?", "Escolha única", ["Sim", "Não"]);
    await publishFirstVersion(page, base);

    await page.getByTestId("open-draft-version-button").click();
    await page.goto(base);
    await page
      .getByTestId("question-item")
      .filter({ hasText: "Recomendaria?" })
      .getByTestId("edit-question-button")
      .click();
    await page.getByLabel("Rótulo da opção 2").fill("Talvez");
    await page.getByLabel("Valor da opção 2").fill("Talvez");
    await page.getByTestId("submit-button").click();
    await expect(page.getByTestId("question-item").filter({ hasText: "Talvez" })).toHaveCount(1);

    await page.goto(`${base}/publicacao`);
    await page.getByTestId("change-kind-select").click();
    await page.getByRole("option", { name: /semântica/i }).click();
    await page.getByTestId("publish-button").click();
    await page.goto(`${base}/versoes`);
    await expect(page.getByTestId("version-row")).toHaveCount(2);

    const [chave] = await questionKeysOf(page, base);
    await seedCollect(request, {
      applicationId: application.id,
      respondents: [{ identityValue: "u-1" }, { identityValue: "u-2" }],
      displays: [
        {
          respondent: 0,
          surveyId: survey.id,
          versionNumber: 1,
          comparabilityGroup: 1,
          outcome: "COMPLETED",
          answers: [{ questionKey: chave, status: "ANSWERED", options: ["Não"] }],
        },
        {
          respondent: 1,
          surveyId: survey.id,
          versionNumber: 2,
          comparabilityGroup: 2,
          outcome: "COMPLETED",
          answers: [{ questionKey: chave, status: "ANSWERED", options: ["Talvez"] }],
        },
      ],
    });

    await page.goto(`${base}/resultados`);
    await expect(page.getByTestId("comparability-warning")).toContainText("versões 1 e 2");
  });
});
