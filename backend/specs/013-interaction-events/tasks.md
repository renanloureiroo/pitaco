---
description: "Task list — 013-interaction-events"
---

# Tasks: Eventos de interação — catálogo, ingestão e leitura de comportamento

**Input**: documentos de desenho em `/specs/013-interaction-events/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/interaction-events.md`, `contracts/behavior-read.md`

**Tests**: obrigatórios (Princípio III); o teste vem antes da implementação em cada história.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável · **[Story]**: US1 a US4 · caminhos relativos à raiz do backend
- `…/collect/`, `…/results/`, `…/privacy/` = `src/main/java/com/renanloureiroo/pitaco/modules/<módulo>/`
- testes em `src/test/java/com/renanloureiroo/pitaco/`

---

## Phase 1: Setup

- [X] T001 Criar `src/main/resources/db/migration/V20260912162148__create_survey_display_events.sql` com `uq_survey_display_events_seq`, cascata da exibição e os dois índices
- [X] T002 Acrescentar `pitaco.collect.interaction-events.max-per-display` e `acceptance-window` a `src/main/resources/application.yml` e criar `…/collect/infra/config/InteractionEventsProperties.java`

---

## Phase 2: Foundational — o catálogo

- [X] T003 [P] Escrever `src/test/java/com/renanloureiroo/pitaco/core/catalog/InteractionEventTypeTest.java` e `InteractionFieldTest.java`
- [X] T004 Criar `src/main/java/com/renanloureiroo/pitaco/core/catalog/InteractionFieldKind.java`, `InteractionField.java` e `InteractionEventType.java` (D-01)
- [X] T005 Acrescentar `INTERACTION_EVENTS` a `src/main/java/com/renanloureiroo/pitaco/core/catalog/SdkFeature.java` e a `SdkCapabilities.java` (1.0.0), sem incluí-lo em `…/collect/application/services/RequiredSdk.java` nem em `…/survey/domain/entities/SurveyVersion.java` (D-08)

---

## Phase 3: User Story 1 — Receber os eventos (P1) 🎯 MVP

### Testes ⚠️

- [X] T006 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/domain/interaction/InteractionEventTest.java` e `InteractionIntakeTest.java`
- [X] T007 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/RecordInteractionEventsUseCaseTest.java` sobre `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryInteractionEventRepository.java`
- [X] T008 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/dtos/InteractionEventsRequestDTOTest.java`

### Implementação

- [X] T009 [US1] Criar `…/collect/domain/interaction/InteractionDraft.java`, `InteractionEvent.java`, `DiscardReason.java` e `InteractionIntake.java` (D-02, D-03)
- [X] T010 [US1] Criar a porta `…/collect/application/repositories/InteractionEventRepository.java`
- [X] T011 [US1] Criar `…/collect/infra/database/jpa/entities/SurveyDisplayEventJpaEntity.java`, `…/repositories/SurveyDisplayEventJpaRepository.java` (trava consultiva, `jsonb_to_recordset … on conflict do nothing`) e `InteractionEventRepositoryJpa.java`
- [X] T012 [US1] Criar `…/collect/application/usecases/RecordInteractionEventsUseCase.java` com `@Transactional` do projeto; `@Bean` em `…/collect/infra/config/UseCasesConfiguration.java` com as propriedades
- [X] T013 [US1] Criar `…/collect/infra/http/dtos/InteractionEventsRequestDTO.java`, `InteractionEventsReceiptDTO.java`, `…/presenters/InteractionEventsReceiptPresenter.java`, `…/controllers/CollectInteractionSwagger.java` (202/400/401/429) e `CollectInteractionController.java`
- [X] T014 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/CollectInteractionEventsE2ETest.java` (linhas lidas do banco; reenvio; outra aplicação; tipo desconhecido e catálogo mais novo; só `length`; escolha em texto livre; pergunta de fora; teto de 500; janela de 7 dias; 400; 401; exibição apagada leva os eventos)

---

## Phase 4: User Story 2 — Catálogo no OpenAPI (P1)

- [X] T015 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/config/InteractionCatalogSchemasTest.java`
- [X] T016 [US2] Criar `…/collect/infra/http/config/InteractionCatalogSchemas.java` e `InteractionCatalogOpenApiConfiguration.java` (D-04)
- [X] T017 [US2] Estender `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/CollectOpenApiTest.java`: o catálogo é publicado a partir do `core` e o lote aponta para ele

---

## Phase 5: User Story 3 — Leitura de comportamento (P1)

### Testes ⚠️

- [X] T018 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/domain/behavior/BehaviorMetricTest.java`
- [X] T019 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/application/usecases/GetSurveyBehaviorUseCaseTest.java` sobre `src/test/java/com/renanloureiroo/pitaco/testsupport/readmodels/InMemorySurveyBehaviorReadModel.java`

### Implementação

- [X] T020 [US3] Criar `…/results/domain/behavior/BehaviorMetric.java` (D-06)
- [X] T021 [US3] Criar a porta `…/results/application/readmodels/SurveyBehaviorReadModel.java` e `…/results/infra/database/jpa/BehaviorSql.java`, `SurveyBehaviorJpaRepository.java`, `SurveyBehaviorReadModelJpa.java` (D-05)
- [X] T022 [US3] Criar `…/results/application/outputs/SurveyBehaviorOutput.java` e `…/results/application/usecases/GetSurveyBehaviorUseCase.java`; `@Bean` em `…/results/infra/config/UseCasesConfiguration.java`
- [X] T023 [US3] Criar `…/results/infra/http/dtos/SurveyBehaviorResponseDTO.java`, `…/presenters/SurveyBehaviorPresenter.java`, `toBehaviorInput` em `ResultsQueryDTO.java` e a rota `behavior` em `SurveyResultsSwagger.java`/`SurveyResultsController.java`
- [X] T024 [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/results/infra/http/controllers/SurveyBehaviorE2ETest.java` e estender `ResultsOpenApiTest.java` com a rota `behavior`

---

## Phase 6: User Story 4 — Eventos vencem com as respostas (P2)

- [X] T025 [P] [US4] Acrescentar a `src/test/java/com/renanloureiroo/pitaco/modules/privacy/application/usecases/ApplyRetentionUseCaseTest.java` os casos de eventos (vencem em lotes, recentes ficam; prazo só de texto não leva evento) e a `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryRetentionStore.java` o descarte de eventos
- [X] T026 [US4] Acrescentar `deleteInteractionEvents` a `…/privacy/application/repositories/RetentionStore.java`, `…/privacy/infra/database/jpa/repositories/RetentionJpaRepository.java` e `RetentionStoreJpa.java`; `discardEvents` em `…/privacy/application/usecases/ApplyRetentionUseCase.java` (D-07)
- [X] T027 [US4] Estender `src/test/java/com/renanloureiroo/pitaco/modules/privacy/infra/database/CascadeDeletionE2ETest.java` com os eventos

---

## Phase 7: Polish

- [X] T028 [P] Atualizar `docs/backend/contrato-sdk.md` (seção 6, catálogo versão 1, leitura de comportamento) e `docs/backend/proxy.md` (rota de eventos a repassar)
- [X] T029 [P] Conferir que o log do recibo traz só contagens e motivos
- [X] T030 Rodar `./mvnw verify` verde
