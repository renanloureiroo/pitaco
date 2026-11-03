---
description: "Task list — 009-advanced-authoring"
---

# Tasks: Autoria avançada — modelos, condição, duplicação e publicação consciente

**Input**: documentos de desenho em `/specs/009-advanced-authoring/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/advanced-authoring.md`

**Tests**: obrigatórios (Princípio III); o teste vem antes da implementação em cada história.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável · **[Story]**: US1 a US6 · caminhos relativos à raiz do backend
- `…/survey/` = `src/main/java/com/renanloureiroo/pitaco/modules/survey/`; `…/collect/` idem para `collect`
- testes em `src/test/java/com/renanloureiroo/pitaco/`

---

## Phase 1: Setup

- [X] T001 Criar `src/main/resources/db/migration/V20260912130759__advanced_authoring.sql`: `surveys.template_kind` com `check`; `questions.range_min_label`/`range_max_label`; colunas `condition_*` com os dois `check` e a FK composta deferível; tabela `question_condition_values`
- [X] T002 Confirmar a validação do schema com `./mvnw test -Dtest=PitacoApplicationTests`

---

## Phase 2: Foundational

- [X] T003 [P] Acrescentar `CONDITIONAL_DISPLAY`, `SCALE_LABELS` e `FREE_TEXT_NOTICE` a `src/main/java/com/renanloureiroo/pitaco/core/catalog/SdkFeature.java` e a versão mínima de cada um em `SdkCapabilities.java`, com `src/test/java/com/renanloureiroo/pitaco/core/catalog/SdkCapabilitiesTest.java`
- [X] T004 Mapear modelo, rótulos e condição em `…/survey/infra/database/jpa/entities/SurveyJpaEntity.java`, `QuestionJpaEntity.java` e nos mappers `…/survey/infra/database/jpa/mappers/SurveyJpaMapper.java`, `QuestionJpaMapper.java`

---

## Phase 3: User Story 1 — Começar de um modelo (P1) 🎯 MVP

### Testes ⚠️

- [X] T005 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/templates/TemplateQuestionsTest.java` e `…/survey/domain/valueobjects/ScaleLabelsTest.java`
- [X] T006 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/CreateSurveyFromTemplateUseCaseTest.java`
- [X] T007 [P] [US1] Acrescentar a `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/dtos/AdvancedAuthoringRequestDTOsTest.java` os casos de modelo e de rótulo

### Implementação

- [X] T008 [US1] Criar `…/survey/domain/valueobjects/SurveyTemplate.java`, `ScaleLabels.java` e `…/survey/domain/templates/TemplateQuestions.java` (D-01)
- [X] T009 [US1] Acrescentar `template` a `…/survey/domain/entities/Survey.java` e `labels` a `Question.java`
- [X] T010 [US1] Aceitar `template` em `…/survey/infra/http/dtos/CreateSurveyRequestDTO.java` e aplicar `TemplateQuestions` em `…/survey/application/usecases/CreateSurveyUseCase.java`; expor `templateKind` em `SurveyResponseDTO.java` e rótulos em `ScaleRangeDTO.java`
- [X] T011 [US1] Cobrir em `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/AdvancedAuthoringE2ETest.java` "Criar pelo modelo de NPS devolve o modelo e grava a pergunta com escala e rótulos"

---

## Phase 4: User Story 2 — Pergunta condicional (P1)

### Testes ⚠️

- [X] T012 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/valueobjects/DisplayConditionTest.java`
- [X] T013 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersionConditionsTest.java` (origem antes, própria origem, valores fora, remover/alterar origem, reordenar, cópia mantém a condição, rótulo só com faixa)
- [X] T014 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/domain/collection/SubmissionValidationNotApplicableTest.java`

### Implementação

- [X] T015 [US2] Criar `…/survey/domain/valueobjects/ConditionOperator.java`, `DisplayCondition.java` e `ConditionRejected.java` (D-02)
- [X] T016 [US2] Criar `…/survey/domain/entities/QuestionConditions.java` e chamá-la nas escritas de `SurveyVersion.java` (D-03)
- [X] T017 [US2] Criar `…/survey/infra/http/dtos/ConditionDTO.java`, aceitar `condition` em `AddQuestionRequestDTO.java` e `UpdateQuestionRequestDTO.java`, devolver em `QuestionResponseDTO.java` via `…/survey/application/outputs/ConditionOutput.java` e `…/survey/infra/http/presenters/QuestionPresenter.java`; tradução em `…/survey/application/services/QuestionDrafts.java`
- [X] T018 [US2] Declarar o 422 de condição em `…/survey/infra/http/controllers/QuestionControllerSwagger.java`
- [X] T019 [US2] Entregar `condition`, rótulos e o recurso exigido em `…/collect/application/gateways/PublishedSurveyCatalog.java`, `…/collect/infra/gateways/PublishedSurveyCatalogSurvey.java`, `…/collect/application/services/RequiredSdk.java` e `…/collect/infra/http/dtos/EligibilityResponseDTO.java`
- [X] T020 [US2] Acrescentar `NOT_APPLICABLE` a `…/collect/domain/entities/AnswerStatus.java` e a regra em `…/collect/domain/collection/SubmissionValidation.java` (`answer.not_applicable_unconditional`), aceitando no `…/collect/infra/http/dtos/AnswerDTO.java`
- [X] T021 [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/ConditionalCollectE2ETest.java` e os casos de condição em `AdvancedAuthoringE2ETest.java` (válida gravada; opção inexistente 422 sem gravar; remover origem 422)

---

## Phase 5: User Story 3 — Duplicar, renomear e descartar (P2)

### Testes ⚠️

- [X] T022 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersionDuplicateTest.java` e `…/survey/domain/valueobjects/SurveyNameCopyTest.java`
- [X] T023 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/DuplicateSurveyUseCaseTest.java` e `DiscardSurveyUseCaseTest.java`
- [X] T024 [P] [US3] Acrescentar a `UpdateSurveyUseCaseTest.java` "Renomear não muda o ciclo de vida nem o estado derivado"

### Implementação

- [X] T025 [US3] Criar `Survey.duplicateInto`, `SurveyVersion.duplicateFor` e `SurveyName.copy` (D-05)
- [X] T026 [US3] Criar `…/survey/application/usecases/DuplicateSurveyUseCase.java` e `…/survey/infra/http/dtos/DuplicateSurveyRequestDTO.java`; rota `duplicate` em `SurveyControllerSwagger.java` e `SurveyController.java` com `Location` da aplicação de destino
- [X] T027 [US3] Garantir em `…/survey/application/usecases/DiscardSurveyUseCase.java` a recusa `survey.published_cannot_be_discarded` (`PublishedSurveyCannotBeDiscarded`), inclusive com rascunho de versão aberto
- [X] T028 [US3] Permitir renomear em qualquer estado em `UpdateSurveyUseCase.java`
- [X] T029 [US3] Cobrir em `AdvancedAuthoringE2ETest.java` "Duplicar para outra aplicação cria um rascunho novo, com chaves novas e sem histórico"

---

## Phase 6: User Story 4 — Aviso de texto livre (P2)

### Testes ⚠️

- [X] T030 [P] [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/valueobjects/FreeTextNoticeTest.java`
- [X] T031 [P] [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/UpdateSurveyFreeTextNoticeTest.java` e `…/survey/infra/http/dtos/UpdateSurveyNoticeRequestDTOTest.java`

### Implementação

- [X] T032 [US4] Criar `…/survey/domain/valueobjects/FreeTextNotice.java` e `redefineFreeTextNotice` em `Survey.java` (D-06); colunas mapeadas em `SurveyJpaEntity.java` (criadas em `V20260912160000__privacy_retention.sql`)
- [X] T033 [US4] Aceitar `freeTextNoticeEnabled`/`freeTextNoticeText` em `UpdateSurveyRequestDTO.java` e `UpdateSurveyRequestDeserializer.java`; devolver em `SurveyResponseDTO.java` via `…/survey/infra/http/presenters/SurveyPresenter.java`
- [X] T034 [US4] Entregar `freeTextNotice` ao SDK em `EligibilityResponseDTO.java` e exigir `FREE_TEXT_NOTICE` quando ligado e houver texto livre (`SurveyVersion.requiredSdkVersion`, `RequiredSdk`)
- [X] T035 [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/FreeTextNoticeE2ETest.java`

---

## Phase 7: User Story 5 — Publicar sabendo o que muda (P1)

### Testes ⚠️

- [X] T036 [P] [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/publication/ChangeClassificationConditionTest.java` e estender `ChangeClassificationTest.java`
- [X] T037 [P] [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/ComparabilityGroupTest.java`

### Implementação

- [X] T038 [US5] Acrescentar `RANGE_CHANGED` e `CONDITION_CHANGED` a `…/survey/domain/publication/ChangeClassification.java` (D-07)
- [X] T039 [US5] Devolver `differences` em `…/survey/application/errors/CosmeticDeclarationRefused.java` e verificar antes de publicar em `…/survey/application/usecases/PublishSurveyUseCase.java` (`SurveyVersionHasNoChanges` primeiro)
- [X] T040 [US5] Conferir `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/PublishSurveyE2ETest.java` e `SurveyVersionE2ETest.java` com a recusa cosmética por condição e os grupos de comparabilidade

---

## Phase 8: User Story 6 — Impedimentos e avisos (P2)

### Testes ⚠️

- [X] T041 [P] [US6] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/publication/SegmentationReachTest.java` e `CompatibilityReachTest.java`
- [X] T042 [P] [US6] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/CheckPublicationWarningsUseCaseTest.java` sobre `src/test/java/com/renanloureiroo/pitaco/testsupport/gateways/InMemoryAttributeCatalogGateway.java` e `InMemorySdkTrafficGateway.java`

### Implementação

- [X] T043 [US6] Criar `…/survey/domain/publication/PublicationWarning.java`, `CompetingSurvey.java`, `KnownAttribute.java`, `SegmentationReach.java`, `CompatibilityReach.java`, `SdkTraffic.java` (D-08)
- [X] T044 [US6] Criar as portas `…/survey/application/gateways/AttributeCatalogGateway.java` e `SdkTrafficGateway.java`, e os adaptadores `…/survey/infra/gateways/AttributeCatalogGatewayCollect.java`, `SurveyAttributeCatalogJpaRepository.java`, `SdkTrafficGatewayCollect.java`, `SurveySdkTrafficJpaRepository.java`
- [X] T045 [US6] Acrescentar `findLiveListeningTo` a `…/survey/application/repositories/SurveyRepository.java` e implementações
- [X] T046 [US6] Criar `…/survey/application/usecases/CheckPublicationWarningsUseCase.java`, `…/survey/application/outputs/PublicationWarningOutput.java`, `CompetingSurveyOutput.java`, `…/survey/infra/http/dtos/PublicationWarningDTO.java`, `PublicationWarningsResponseDTO.java`, `CompetingSurveyDTO.java`, `…/survey/infra/http/presenters/PublicationWarningsPresenter.java`; rota `publication-warnings` em `SurveyControllerSwagger.java`/`SurveyController.java`; `@Bean` em `…/survey/infra/config/UseCasesConfiguration.java` com a janela recente
- [X] T047 [US6] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/PublicationWarningsE2ETest.java`

---

## Phase 9: Polish

- [X] T048 [P] Conferir em `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/SurveyOpenApiTest.java` que as rotas novas estão publicadas com seus status
- [X] T049 Rodar `./mvnw verify` verde
