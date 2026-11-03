---
description: "Task list — 008-exposure-control"
---

# Tasks: Controle de exposição — prioridade, cota, descanso e catálogo observado

**Input**: documentos de desenho em `/specs/008-exposure-control/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/exposure.md`, `contracts/observed-catalog.md`

**Tests**: obrigatórios (Princípio III); dentro de cada história, o teste vem antes da implementação.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável · **[Story]**: US1 a US5 · caminhos relativos à raiz do backend
- `…/survey/` = `src/main/java/com/renanloureiroo/pitaco/modules/survey/`
- `…/collect/` = `src/main/java/com/renanloureiroo/pitaco/modules/collect/`
- testes em `src/test/java/com/renanloureiroo/pitaco/`

---

## Phase 1: Setup

- [X] T001 Criar `src/main/resources/db/migration/V20260912123029__exposure_control.sql`: colunas `priority`, `response_quota`, `ignores_quiet_period` com `check`; índice parcial `idx_survey_displays_completed`; tabelas `application_attributes` e `application_attribute_values` com chaves únicas e índice de listagem
- [X] T002 Confirmar a validação do schema com `./mvnw test -Dtest=PitacoApplicationTests`

---

## Phase 2: Foundational

- [X] T003 [P] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/valueobjects/ExposureTest.java` (padrão, cada alteração mexe só no próprio campo, faixa e cota inválidas)
- [X] T004 [P] Criar `…/survey/domain/valueobjects/Exposure.java` (D-01)
- [X] T005 Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyExposureTest.java` (nasce com a padrão, redefinir não muda ciclo de vida, cota encerra no ar ou pausada, rascunho e encerrada ficam)
- [X] T006 Acrescentar `exposure`, `redefineExposure` e `endByQuota` a `…/survey/domain/entities/Survey.java`, e `QUOTA_REACHED` a `…/survey/domain/entities/TransitionReason.java`
- [X] T007 Mapear as colunas novas em `…/survey/infra/database/jpa/entities/SurveyJpaEntity.java` e `…/survey/infra/database/jpa/mappers/SurveyJpaMapper.java`; acrescentar `lockByIdAndApplicationId` e `endIfLive` a `…/survey/application/repositories/SurveyRepository.java`, `…/survey/infra/database/jpa/repositories/SurveyJpaRepository.java` e `SurveyRepositoryJpa.java`, com o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemorySurveyRepository.java`

**Checkpoint**: exposição persistida e ciclo de vida sabendo encerrar por cota.

---

## Phase 3: User Story 1 — Desempatar pelo que importa (P1) 🎯 MVP

### Testes de US1 ⚠️

- [X] T008 [P] [US1] Acrescentar a `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/UpdateSurveyUseCaseTest.java` os casos de prioridade, cota e isenção (define de uma vez com a pesquisa no ar; nulo remove a cota; faixa inválida não muda nada)
- [X] T009 [P] [US1] Acrescentar a `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/FindEligibleSurveyUseCaseTest.java` o caso "Prioridade maior vence, mesmo publicada depois"
- [X] T010 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/SurveyExposureE2ETest.java` (define e lê do banco, padrão, nulo remove, corpo vazio, 400 sem mudança, 404)

### Implementação de US1

- [X] T011 [US1] Estender `…/survey/infra/http/dtos/UpdateSurveyRequestDTO.java`, `UpdateSurveyRequestDeserializer.java` e `SurveyResponseDTO.java` com `priority`, `responseQuota` (`Patch`) e `ignoresQuietPeriod`
- [X] T012 [US1] Aplicar a exposição em `…/survey/application/usecases/UpdateSurveyUseCase.java` com `SurveyScope.requireLocked`
- [X] T013 [US1] Acrescentar `priority`, `publishedAt` e `ignoresQuietPeriod` a `SurveyCandidate` em `…/collect/application/gateways/PublishedSurveyCatalog.java`, lidos por `…/collect/infra/gateways/PublishedSurveyCatalogSurvey.java` e `PublishedSurveyJpaRepository.java`, e ao fake `src/test/java/com/renanloureiroo/pitaco/testsupport/gateways/InMemoryPublishedSurveyCatalog.java`
- [X] T014 [US1] Implementar `tiebreak()` em `…/collect/application/usecases/FindEligibleSurveyUseCase.java` (D-02)

---

## Phase 4: User Story 2 — Encerrar ao atingir a cota (P1)

### Testes de US2 ⚠️

- [X] T015 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/EndSurveyByQuotaUseCaseTest.java` (no ar e pausada encerram com motivo próprio; já encerrada não registra; janela fechada não registra; fora do escopo é ignorada)
- [X] T016 [P] [US2] Acrescentar a `UpdateSurveyUseCaseTest.java` os casos de cota reduzida (encerra na hora; acima do total só grava; rascunho não encerra; editar outro campo não reconfere)
- [X] T017 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/ExposureControlE2ETest.java` com as corridas reais (duas conclusões simultâneas; pausa + conclusão) e a sessão aberta concluindo depois da cota

### Implementação de US2

- [X] T018 [US2] Criar `…/survey/application/services/QuotaClosure.java` (update condicional + transição única, D-05)
- [X] T019 [US2] Criar `…/survey/application/usecases/EndSurveyByQuotaUseCase.java` com `@Transactional` do projeto
- [X] T020 [US2] Criar a porta `…/survey/application/gateways/CompletedResponsesGateway.java`, o adaptador `…/survey/infra/gateways/CompletedResponsesGatewayCollect.java` com `SurveyCompletedResponsesJpaRepository.java`, e o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/gateways/InMemoryCompletedResponsesGateway.java`
- [X] T021 [US2] Encerrar por cota reduzida em `UpdateSurveyUseCase.java` (D-06)
- [X] T022 [US2] Criar a porta `…/collect/application/gateways/SurveyQuotaGateway.java`, o adaptador `…/collect/infra/gateways/SurveyQuotaGatewaySurvey.java` (chama `EndSurveyByQuotaUseCase`), `findResponseQuota`/`lockResponseQuota` em `…/collect/infra/gateways/SurveyScopeJpaRepository.java`, e o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/gateways/InMemorySurveyQuotaGateway.java`
- [X] T023 [US2] Acrescentar `countCompleted` a `…/collect/application/repositories/SurveyDisplayRepository.java`, `…/collect/infra/database/jpa/repositories/SurveyDisplayJpaRepository.java`, `SurveyDisplayRepositoryJpa.java` e `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemorySurveyDisplayRepository.java`
- [X] T024 [US2] Implementar `closeOnQuota` em `…/collect/application/usecases/SubmitSurveyDisplayUseCase.java` (D-04) e registrar os `@Bean` em `…/survey/infra/config/UseCasesConfiguration.java` e `…/collect/infra/config/UseCasesConfiguration.java`

---

## Phase 5: User Story 3 — Respeitar o descanso (P2)

### Testes de US3 ⚠️

- [X] T025 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/domain/eligibility/QuietPeriodTest.java`
- [X] T026 [P] [US3] Acrescentar a `FindEligibleSurveyUseCaseTest.java` o caso "Pesquisa isenta ignora o descanso; a outra continua barrada"

### Implementação de US3

- [X] T027 [US3] Criar `…/collect/domain/eligibility/QuietPeriod.java`
- [X] T028 [US3] Acrescentar `quietPeriodDaysOf` a `…/collect/application/gateways/ApplicationScopeGateway.java`, `…/collect/infra/gateways/ApplicationScopeGatewayApp.java` e `src/test/java/com/renanloureiroo/pitaco/testsupport/gateways/InMemoryCollectApplicationScopeGateway.java`; e `lastOpenedAt` a `SurveyDisplayRepository.java` e implementações
- [X] T029 [US3] Implementar `isResting` em `FindEligibleSurveyUseCase.java`, entre histórico e segmentação (D-03)

---

## Phase 6: User Story 4 — Progresso da cota e histórico (P2)

### Testes de US4 ⚠️

- [X] T030 [P] [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/GetQuotaProgressUseCaseTest.java`

### Implementação de US4

- [X] T031 [US4] Criar `…/collect/application/usecases/GetQuotaProgressUseCase.java` (D-07)
- [X] T032 [US4] Criar `…/collect/infra/http/dtos/QuotaProgressResponseDTO.java`, `…/collect/infra/http/presenters/QuotaProgressPresenter.java`, `…/collect/infra/http/controllers/QuotaProgressSwagger.java` (200/403/404) e `QuotaProgressController.java`; `@Bean` no `UseCasesConfiguration` de `collect`
- [X] T033 [US4] Conferir em `ExposureControlE2ETest.java` o progresso com e sem cota, 404 e 403, e a transição `quota_reached` em `GET …/transitions`

---

## Phase 7: User Story 5 — Catálogo observado (P3)

### Testes de US5 ⚠️

- [X] T034 [P] [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/ListObservedEventsUseCaseTest.java` e `ListObservedAttributesUseCaseTest.java`
- [X] T035 [P] [US5] Acrescentar a `FindEligibleSurveyUseCaseTest.java` os casos "Os atributos enviados alimentam o catálogo, cada catálogo na sua transação" e "Catálogo de atributos fora do ar não impede a entrega"

### Implementação de US5

- [X] T036 [US5] Criar `…/collect/domain/entities/ObservedAttribute.java` e `ObservedAttributeId.java`
- [X] T037 [US5] Criar a porta `…/collect/application/repositories/ObservedAttributeRepository.java` e o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryObservedAttributeRepository.java`
- [X] T038 [US5] Criar `…/collect/infra/database/jpa/entities/ApplicationAttributeJpaEntity.java`, `ApplicationAttributeValueJpaEntity.java`, `…/repositories/ApplicationAttributeJpaRepository.java`, `ApplicationAttributeValueJpaRepository.java` e `ObservedAttributeRepositoryJpa.java` (D-08)
- [X] T039 [US5] Gravar os atributos em `FindEligibleSurveyUseCase.observe` numa transação separada da de eventos
- [X] T040 [US5] Criar `…/collect/application/outputs/ObservedEventOutput.java`, `ObservedAttributeOutput.java`, `…/collect/application/usecases/ListObservedEventsUseCase.java` e `ListObservedAttributesUseCase.java` (D-09)
- [X] T041 [US5] Criar `…/collect/infra/http/dtos/ListObservedEventsQueryDTO.java`, `ObservedEventResponseDTO.java`, `ListObservedAttributesQueryDTO.java`, `ObservedAttributeResponseDTO.java`; presenters `ObservedEventPresenter.java`, `ObservedAttributePresenter.java`; `ObservedEventSwagger.java`, `ObservedEventController.java`, `ObservedAttributeSwagger.java`, `ObservedAttributeController.java`
- [X] T042 [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/ListObservedEventsE2ETest.java` e `ListObservedAttributesE2ETest.java` (ordem, isolamento por aplicação, vazio, paginação, 400, 403, 404, amortização, limite de valores, nenhum caminho altera o catálogo)

---

## Phase 8: Polish

- [X] T043 [P] Conferir que nenhum log traz nome ou valor de atributo, nem nome de evento
- [X] T044 Rodar `./mvnw verify` verde
