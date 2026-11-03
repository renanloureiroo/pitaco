---
description: "Task list — 012-survey-results"
---

# Tasks: Resultados de pesquisa — agregados, recortes, respostas abertas e export

**Input**: documentos de desenho em `/specs/012-survey-results/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/results.md`

**Tests**: obrigatórios (Princípio III); o teste vem antes da implementação em cada história.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável · **[Story]**: US1 a US7 · caminhos relativos à raiz do backend
- `…/results/` = `src/main/java/com/renanloureiroo/pitaco/modules/results/`
- testes em `src/test/java/com/renanloureiroo/pitaco/`

---

## Phase 1: Setup

- [X] T001 Acrescentar `pitaco.results.small-sample-threshold` a `src/main/resources/application.yml` e criar `…/results/infra/config/ResultsProperties.java`
- [X] T002 Confirmar que nenhuma migration é necessária: as consultas usam os índices de `survey_displays`, `survey_answers` e `aggregate_snapshots` já existentes

---

## Phase 2: Foundational

- [X] T003 [P] Criar a porta `…/results/application/gateways/SurveyScopeGateway.java`, o adaptador `…/results/infra/gateways/SurveyScopeGatewayJpa.java` com `ResultsScopeJpaRepository.java`, e o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/gateways/InMemoryResultsSurveyScopeGateway.java` (D-02)
- [X] T004 [P] Criar `…/results/application/errors/SurveyNotFound.java` e `…/results/application/services/ResultsScope.java`
- [X] T005 Criar a porta `…/results/application/readmodels/SurveyResultsReadModel.java` e o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/readmodels/InMemorySurveyResultsReadModel.java` (D-01)
- [X] T006 Criar `…/results/infra/database/jpa/ResultsSql.java`, `SurveyResultsJpaRepository.java` e `SurveyResultsReadModelJpa.java`
- [X] T007 [P] Criar `…/results/application/usecases/ResultsSelection.java`, `…/results/application/outputs/ResultsFilterOutput.java` e `…/results/infra/http/dtos/ResultsQueryDTO.java` com os três `@AssertTrue` (D-03), testados em `src/test/java/com/renanloureiroo/pitaco/modules/results/infra/http/dtos/ResultsQueryDTOTest.java`

---

## Phase 3: User Story 1 — Resultado por pergunta (P1) 🎯 MVP

### Testes ⚠️

- [X] T008 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/domain/aggregation/QuestionAggregationTest.java`
- [X] T009 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/application/usecases/GetSurveyResultsUseCaseTest.java` e `GetSurveyResultsAdvancedAuthoringTest.java` (não aplicável fora do denominador; NPS do modelo no topo)

### Implementação

- [X] T010 [US1] Criar `…/results/domain/aggregation/QuestionAggregate.java`, `QuestionAggregation.java` e `NpsGroups.java` (D-04)
- [X] T011 [US1] Criar `…/results/application/outputs/QuestionResultOutput.java`, `NpsSummaryOutput.java`, `SurveyResultsOutput.java`, `AttributeCatalogOutput.java`
- [X] T012 [US1] Criar `…/results/application/usecases/GetSurveyResultsUseCase.java`
- [X] T013 [US1] Criar `…/results/infra/http/dtos/SurveyResultsResponseDTO.java`, `…/results/infra/http/presenters/SurveyResultsPresenter.java`, `…/results/infra/http/controllers/SurveyResultsSwagger.java` e `SurveyResultsController.java` (rota `GET …/results`); `@Bean` em `…/results/infra/config/UseCasesConfiguration.java`

---

## Phase 4: User Story 2 — Taxa, desfechos e tendência (P1)

- [X] T014 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/domain/ResponseRateTest.java`
- [X] T015 [US2] Criar `…/results/domain/ResponseRate.java`, `DisplayResolution.java` e `…/results/application/outputs/ResponseRateOutput.java` (D-05)
- [X] T016 [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/infra/http/controllers/SurveyResultsE2ETest.java` com `ResultsE2ESupport.java` (números conferem com o semeado; sem resposta sem agregado; publicada sem exibição; nunca publicada; nenhuma leitura altera o estado)

---

## Phase 5: User Story 3 — Recortes (P1)

- [X] T017 [US3] Cobrir em `SurveyResultsE2ETest.java` recorte por período, atributo, ausência de atributo e versão valendo para tudo, e `400` apontando o campo
- [X] T018 [US3] Cobrir em `GetSurveyResultsUseCaseTest.java` "Recorte por atributo restringe os números, e ausência é um recorte próprio"

---

## Phase 6: User Story 4 — Avisos de leitura (P2)

### Testes ⚠️

- [X] T019 [P] [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/domain/comparability/QuestionComparabilityTest.java`
- [X] T020 [P] [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/application/usecases/GetSurveyResultsRetentionTest.java`

### Implementação

- [X] T021 [US4] Criar `…/results/domain/comparability/QuestionShape.java`, `Comparability.java`, `QuestionComparability.java` e aplicar só no consolidado em `GetSurveyResultsUseCase.java` (D-06)
- [X] T022 [US4] Criar `…/results/application/outputs/RetentionOutput.java` e somar o congelado só em recorte de versão
- [X] T023 [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/infra/http/controllers/AdvancedAuthoringResultsE2ETest.java` (só a pergunta que mudou vem marcada; não aplicável à parte e `n/a` no CSV; NPS do modelo)

---

## Phase 7: User Story 5 — Respostas abertas (P2)

- [X] T024 [P] [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/application/usecases/ListOpenAnswersUseCaseTest.java`
- [X] T025 [US5] Criar `…/results/application/usecases/ListOpenAnswersUseCase.java`, `ReadableAnswers.java` e `…/results/application/outputs/OpenAnswerOutput.java` (D-08)
- [X] T026 [US5] Criar `…/results/infra/http/dtos/OpenAnswersQueryDTO.java`, `OpenAnswerResponseDTO.java`, `…/results/infra/http/presenters/OpenAnswerPresenter.java` e a rota `open-answers`
- [X] T027 [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/infra/http/controllers/OpenAnswersE2ETest.java`

---

## Phase 8: User Story 6 — Export CSV (P2)

- [X] T028 [P] [US6] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/infra/http/export/CsvExportWriterTest.java` e `CsvExportWriterNotApplicableTest.java`
- [X] T029 [P] [US6] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/application/usecases/ExportSurveyResultsUseCaseTest.java` (uma linha por exibição; lotes por cursor sem consulta por linha)
- [X] T030 [US6] Criar `…/results/application/outputs/ExportOutput.java` e `…/results/application/usecases/ExportSurveyResultsUseCase.java` (D-07)
- [X] T031 [US6] Criar `…/results/infra/http/export/CsvExportWriter.java` e a rota `export` com `StreamingResponseBody`, `Content-Disposition` e `X-Pitaco-Content-Warning` em `SurveyResultsController.java`/`SurveyResultsSwagger.java`
- [X] T032 [US6] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/infra/http/controllers/ExportResultsE2ETest.java` e `ExportPersonalDataWarningE2ETest.java`

---

## Phase 9: User Story 7 — Alimentar a visão geral (P3)

- [X] T033 [US7] Conferir que `SurveyResultsResponseDTO` traz tudo que a visão geral do painel usa (`responseRate` com `timeline`, `nps`, `attributes`) e que o recorte por período e por atributo funciona para leituras repetidas (D-09); nenhuma rota nova

---

## Phase 10: Polish

- [X] T034 [P] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/infra/http/controllers/ResultsOpenApiTest.java` (cada leitura publica exatamente 200/400/403/404)
- [X] T035 [P] Conferir que os logs trazem só aplicação, pesquisa e contagens
- [X] T036 Rodar `./mvnw verify` verde
