---
description: "Task list — 011-privacy-retention"
---

# Tasks: Privacidade e retenção — descarte, exclusão do titular e auditoria

**Input**: documentos de desenho em `/specs/011-privacy-retention/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/privacy.md`

**Tests**: obrigatórios (Princípio III); o teste vem antes da implementação em cada história.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável · **[Story]**: US1 a US4 · caminhos relativos à raiz do backend
- `…/privacy/` = `src/main/java/com/renanloureiroo/pitaco/modules/privacy/`
- testes em `src/test/java/com/renanloureiroo/pitaco/`

---

## Phase 1: Setup

- [X] T001 Criar `src/main/resources/db/migration/V20260912160000__privacy_retention.sql`: recriação das FKs com cascata pelo catálogo, `idx_survey_displays_respondent`, `deletion_audits`, `aggregate_snapshots`, `aggregate_snapshot_counts`, `retention_runs`, `idx_survey_answers_answered_at` (e as colunas do aviso de texto livre usadas pela `009`)
- [X] T002 Acrescentar `pitaco.privacy.retention.*` a `src/main/resources/application.yml` e criar `…/privacy/infra/config/PrivacyProperties.java`
- [X] T003 Escrever `src/test/java/com/renanloureiroo/pitaco/modules/privacy/infra/database/CascadeDeletionE2ETest.java` (respondente, pesquisa e aplicação levam tudo que pende deles)

---

## Phase 2: Foundational

- [X] T004 [P] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/privacy/domain/retention/RetentionPolicyTest.java`
- [X] T005 [P] Criar `…/privacy/domain/retention/RetentionPolicy.java` (D-01)
- [X] T006 Criar a porta `…/privacy/application/gateways/PrivacyApplicationGateway.java`, o adaptador `…/privacy/infra/gateways/PrivacyApplicationGatewayApp.java` com `…/privacy/infra/database/jpa/repositories/PrivacyApplicationJpaRepository.java`, e o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/gateways/InMemoryPrivacyApplicationGateway.java`
- [X] T007 Criar `…/privacy/application/services/PrivacyScope.java` (aplicação inexistente ou malformada → `application.not_found`)

---

## Phase 3: User Story 1 — Cumprir a política de retenção (P1) 🎯 MVP

### Testes ⚠️

- [X] T008 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/privacy/domain/retention/RetentionSnapshotsTest.java`
- [X] T009 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/privacy/application/usecases/ApplyRetentionUseCaseTest.java` (sem política nada; lotes com congelamento e recentes poupados; texto apaga só o texto; rodar de novo nada; isolamento entre aplicações)

### Implementação

- [X] T010 [US1] Criar `…/privacy/domain/retention/ExpiringAnswer.java`, `SnapshotCount.java`, `RetentionSnapshots.java` e `…/privacy/domain/entities/AggregateSnapshot.java`, `AggregateSnapshotId.java`, `RetentionRun.java`, `RetentionRunId.java` (D-03)
- [X] T011 [US1] Criar as portas `…/privacy/application/repositories/RetentionStore.java`, `AggregateSnapshotRepository.java`, `RetentionRunRepository.java` e os fakes `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryRetentionStore.java`, `InMemoryAggregateSnapshotRepository.java`, `InMemoryRetentionRunRepository.java`
- [X] T012 [US1] Criar `…/privacy/infra/database/jpa/repositories/RetentionJpaRepository.java` (vencidas por `answered_at`, delete, `update … set text_value = null` em lote, contagens) e `RetentionStoreJpa.java`; `…/privacy/infra/database/jpa/entities/AggregateSnapshotJpaEntity.java`, `RetentionRunJpaEntity.java`, `…/mappers/PrivacyJpaMapper.java`, `AggregateSnapshotJpaRepository.java`, `AggregateSnapshotRepositoryJpa.java`, `RetentionRunJpaRepository.java`, `RetentionRunRepositoryJpa.java`
- [X] T013 [US1] Criar `…/privacy/application/usecases/ApplyRetentionUseCase.java` com uma transação por lote via `Transactor` (D-02, D-04) e `…/privacy/application/outputs/RetentionRunOutput.java`
- [X] T014 [US1] Criar `…/privacy/infra/retention/RetentionJob.java` com `@ConditionalOnProperty` e `@Scheduled(zone = "UTC")` (D-08); `@Bean`s em `…/privacy/infra/config/UseCasesConfiguration.java`
- [X] T015 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/privacy/infra/http/controllers/RetentionE2ETest.java` (apaga e registra; resultado mostra o agregado histórico; com período não soma; texto some das respostas abertas; idempotência; aplicação sem prazo intacta)

---

## Phase 4: User Story 2 — Prévia do descarte (P2)

### Testes ⚠️

- [X] T016 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/privacy/application/usecases/GetRetentionPreviewUseCaseTest.java` sobre `src/test/java/com/renanloureiroo/pitaco/testsupport/gateways/InMemoryRetentionSchedule.java`

### Implementação

- [X] T017 [US2] Criar a porta `…/privacy/application/gateways/RetentionSchedule.java` e o adaptador `…/privacy/infra/gateways/CronRetentionSchedule.java` (D-05)
- [X] T018 [US2] Criar `…/privacy/application/usecases/GetRetentionPreviewUseCase.java` e `…/privacy/application/outputs/RetentionPreviewOutput.java`
- [X] T019 [US2] Criar `…/privacy/infra/http/dtos/RetentionPreviewResponseDTO.java`, a tradução em `…/privacy/infra/http/presenters/PrivacyPresenter.java` e a rota `retention-preview` em `…/privacy/infra/http/controllers/PrivacySwagger.java`/`PrivacyController.java`

---

## Phase 5: User Story 3 — Exclusão do titular (P1)

### Testes ⚠️

- [X] T020 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/privacy/domain/valueobjects/ErasureTargetTest.java`
- [X] T021 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/privacy/application/usecases/DeleteRespondentUseCaseTest.java`

### Implementação

- [X] T022 [US3] Criar `…/privacy/domain/valueobjects/ErasureTarget.java` e `…/privacy/domain/entities/DeletionAudit.java`, `DeletionAuditId.java`
- [X] T023 [US3] Criar as portas `…/privacy/application/repositories/RespondentErasureRepository.java`, `DeletionAuditRepository.java`; os adaptadores `…/privacy/infra/database/jpa/repositories/ErasureJpaRepository.java`, `RespondentErasureRepositoryJpa.java`, `DeletionAuditJpaRepository.java`, `DeletionAuditRepositoryJpa.java`, `…/entities/DeletionAuditJpaEntity.java`; e os fakes `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryRespondentErasureRepository.java`, `InMemoryDeletionAuditRepository.java`
- [X] T024 [US3] Criar `…/privacy/application/usecases/DeleteRespondentUseCase.java` (`@Transactional`, D-06) e `…/privacy/application/outputs/RespondentErasureOutput.java`
- [X] T025 [US3] Criar `…/privacy/infra/http/dtos/EraseRespondentQueryDTO.java` (dois `@AssertTrue`) e `RespondentErasureResponseDTO.java`; rota `DELETE …/respondents` em `PrivacySwagger.java`/`PrivacyController.java`

---

## Phase 6: User Story 4 — Auditoria (P2)

- [X] T026 [P] [US4] Cobrir a listagem em `DeleteRespondentUseCaseTest.java` ("A listagem traz os registros da aplicação, do mais recente para o mais antigo")
- [X] T027 [US4] Criar `…/privacy/application/usecases/ListDeletionAuditsUseCase.java`, `…/privacy/application/outputs/DeletionAuditOutput.java`, `…/privacy/infra/http/dtos/ListDeletionAuditsQueryDTO.java`, `DeletionAuditResponseDTO.java`; rota `deletion-audits`
- [X] T028 [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/privacy/infra/http/controllers/PrivacyE2ETest.java` com o apoio de `PrivacyE2ESupport.java` (exclusão nas duas pesquisas com resultados e export parando de contar; registro sem referência; repetição; dispositivo; 400; 403 e 404 nas três rotas; prévia) e `PrivacyOpenApiTest.java`

---

## Phase 7: Polish

- [X] T029 [P] Conferir que os logs de exclusão e de retenção trazem só aplicação e contagens
- [X] T030 Rodar `./mvnw verify` verde
