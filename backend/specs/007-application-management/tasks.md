---
description: "Task list — 007-application-management"
---

# Tasks: Aplicação — cadastro, edição, ativação e eventos observados

**Input**: documentos de desenho em `/specs/007-application-management/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/application-management.md`

**Tests**: incluídos e obrigatórios (Princípio III). Em cada história, o teste vem antes da implementação.

**Organization**: agrupadas por história de usuário.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável (arquivo distinto, sem dependência pendente)
- **[Story]**: US1, US2, US3, US4
- Todo caminho é relativo à raiz do backend

## Path Conventions

`src/main/java/com/renanloureiroo/pitaco/` abreviado como `…/pitaco/`; testes em `src/test/java/com/renanloureiroo/pitaco/`.

---

## Phase 1: Setup

- [X] T001 Criar a migration `src/main/resources/db/migration/V20260905180000__create_applications.sql` com `slug unique` e as três colunas de prazo anuláveis
- [X] T002 Confirmar que o schema valida com `./mvnw test -Dtest=PitacoApplicationTests` (`ddl-auto: validate`)

---

## Phase 2: Foundational

- [X] T003 [P] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/domain/valueobjects/SlugTest.java` (formato, limite, derivação com acento e pontuação, texto não derivável) e `NameTest.java`
- [X] T004 [P] Criar `…/pitaco/modules/app/domain/valueobjects/Slug.java` (`of`, `from`) e `Name.java` sobre `…/pitaco/core/text/RequiredText.java`
- [X] T005 Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/domain/entities/ApplicationTest.java` cobrindo nascimento ativo, prazos ≥ 1 dia, relação texto livre × geral, prazo efetivo e edição recusada sem mexer no `updatedAt`
- [X] T006 Criar `…/pitaco/modules/app/domain/entities/Application.java` e `Status.java` com os setters validados e os códigos `application.quiet_period_invalid`, `application.retention_invalid`, `application.open_text_retention_invalid` (D-02)
- [X] T007 [P] Criar os erros `…/pitaco/modules/app/application/errors/ApplicationAlreadyExistsWithSameSlug.java`, `ApplicationNotFound.java` e `ApplicationIsInactive.java`
- [X] T008 [P] Criar a porta `…/pitaco/modules/app/application/repositories/ApplicationRepository.java` e o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryApplicationRepository.java`, com `ApplicationFactory` em `src/test/java/com/renanloureiroo/pitaco/testsupport/factories/ApplicationFactory.java`
- [X] T009 Criar `…/pitaco/modules/app/infra/database/jpa/entities/ApplicationJpaEntity.java`, `…/mappers/ApplicationJpaMapper.java` (chama `restore`), `…/repositories/ApplicationJpaRepository.java` e `ApplicationRepositoryJpa.java`
- [X] T010 [P] Criar `…/pitaco/modules/app/application/outputs/ApplicationOutput.java`, `…/pitaco/modules/app/infra/http/dtos/ApplicationResponseDTO.java` (`@JsonInclude(NON_NULL)`) e `…/pitaco/modules/app/infra/http/presenters/ApplicationPresenter.java`

**Checkpoint**: domínio, porta, fake e persistência prontos.

---

## Phase 3: User Story 1 — Cadastrar uma aplicação (P1) 🎯 MVP

### Testes de US1 ⚠️

- [X] T011 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/application/usecases/CreateApplicationUseCaseTest.java` (cria e persiste, sem políticas, slug informado, slug derivado, conflito de slug)
- [X] T012 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/infra/http/dtos/CreateApplicationRequestDTOTest.java`

### Implementação de US1

- [X] T013 [US1] Criar `…/pitaco/modules/app/application/usecases/CreateApplicationUseCase.java` (D-01)
- [X] T014 [US1] Criar `…/pitaco/modules/app/infra/http/dtos/CreateApplicationRequestDTO.java`, `CreateApplicationResponseDTO.java` e `…/presenters/CreateApplicationPresenter.java`
- [X] T015 [US1] Declarar `create` em `…/pitaco/modules/app/infra/http/controllers/ApplicationControllerSwagger.java` e implementar em `ApplicationController.java`, com `201` e `Location`
- [X] T016 [US1] Registrar o `@Bean` em `…/pitaco/modules/app/infra/config/UseCasesConfiguration.java`
- [X] T017 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/CreateApplicationE2ETest.java` (201 com `Location` e estado no banco, slug derivado, 409 sem segunda aplicação, 422 de prazos, 400 por campo, JSON malformado)

---

## Phase 4: User Story 2 — Editar nome e prazos (P1)

### Testes de US2 ⚠️

- [X] T018 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/application/usecases/UpdateApplicationUseCaseTest.java` (ausente não mexe nem no `updatedAt`, nulo remove, geral antes do texto livre, recusa sem gravar, 404 para inexistente e malformado)
- [X] T019 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/infra/http/dtos/UpdateApplicationRequestDTOTest.java` (corpo vazio, `null` remove, `name: null` malformado, tipo errado, constraints)

### Implementação de US2

- [X] T020 [US2] Criar `…/pitaco/core/usecase/Patch.java` (D-03)
- [X] T021 [US2] Criar `…/pitaco/modules/app/application/usecases/UpdateApplicationUseCase.java` aplicando retenção geral antes da de texto livre (D-04)
- [X] T022 [US2] Criar `…/pitaco/modules/app/infra/http/dtos/UpdateApplicationRequestDTO.java` e `UpdateApplicationRequestDeserializer.java`
- [X] T023 [US2] Declarar e implementar `update` (`PATCH /applications/{applicationId}`) em `ApplicationControllerSwagger.java` e `ApplicationController.java`; `@Bean` em `UseCasesConfiguration.java`
- [X] T024 [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/UpdateApplicationE2ETest.java` (altera e persiste, `null` remove e some da resposta, corpo vazio, 400, 422 sem gravar, 404, 403)

---

## Phase 5: User Story 3 — Desligar e religar (P2)

### Testes de US3 ⚠️

- [X] T025 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/application/usecases/ApplicationStatusUseCasesTest.java` (desativa e grava, idempotência sem mexer no `updatedAt`, políticas intactas, reativa, 404)

### Implementação de US3

- [X] T026 [US3] Criar `…/pitaco/modules/app/application/usecases/DeactivateApplicationUseCase.java` e `ActivateApplicationUseCase.java` (D-05)
- [X] T027 [US3] Declarar e implementar `deactivate` e `activate` em `ApplicationControllerSwagger.java` e `ApplicationController.java`; `@Bean` em `UseCasesConfiguration.java`
- [X] T028 [US3] Garantir que `…/pitaco/modules/collect/infra/gateways/ApplicationScopeGatewayApp.java` traduz `Application.isActive()` em `ApplicationScopeState` (D-06)
- [X] T029 [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/ApplicationStatusE2ETest.java` (estado no banco, idempotência, silêncio ao SDK e retorno ao reativar, histórico legível, 404, 403 sem mudança)

---

## Phase 6: User Story 4 — Lembrar os eventos que o app já enviou (P3)

### Testes de US4 ⚠️

- [X] T030 [P] [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/domain/entities/ObservedEventTest.java` (nasce com primeira = última, só avança, restaurar inválido recusa)
- [X] T031 [P] [US4] Acrescentar a `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/FindEligibleSurveyUseCaseTest.java` o grupo "Catálogo de eventos observados" (toda consulta registra, mesmo sem pesquisa; uma linha por evento; inativa ou desconhecida não alimenta)

### Implementação de US4

- [X] T032 [US4] Criar a migration `src/main/resources/db/migration/V20260911160000__create_application_events.sql` com `uq_application_events_name` e `idx_application_events_listing`
- [X] T033 [US4] Criar `…/pitaco/modules/collect/domain/entities/ObservedEvent.java` e `ObservedEventId.java` (D-07)
- [X] T034 [US4] Criar a porta `…/pitaco/modules/collect/application/repositories/ObservedEventRepository.java` e o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryObservedEventRepository.java`
- [X] T035 [US4] Criar `…/pitaco/modules/collect/infra/database/jpa/entities/ApplicationEventJpaEntity.java`, `…/mappers/ObservedEventMapper.java`, `…/repositories/ApplicationEventJpaRepository.java` (upsert amortizado, D-08) e `ObservedEventRepositoryJpa.java` (`REFRESH_INTERVAL` de 5 minutos)
- [X] T036 [US4] Chamar `observe(...)` em `…/pitaco/modules/collect/application/usecases/FindEligibleSurveyUseCase.java`, depois do teste de aplicação ativa, via `Transactor.runInTransaction` dentro de `safely` (D-09)
- [X] T037 [US4] Acrescentar a `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/CollectEligibilityE2ETest.java` os casos de registro no catálogo, uma linha por evento e aplicação inativa sem registro

---

## Phase 7: Polish

- [X] T038 [P] Conferir que os logs de sucesso trazem só identificadores (id e slug) e que falha do catálogo loga aplicação e classe da falha, nunca o nome do evento nem valor rejeitado
- [X] T039 Rodar `./mvnw verify` verde
