---
description: "Task list — 010-sdk-health"
---

# Tasks: Saúde do SDK e proteção da superfície pública

**Input**: documentos de desenho em `/specs/010-sdk-health/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/sdk-health.md`, `contracts/public-surface-protection.md`

**Tests**: obrigatórios (Princípio III); o teste vem antes da implementação em cada história.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável · **[Story]**: US1 a US6 · caminhos relativos à raiz do backend
- `…/collect/` = `src/main/java/com/renanloureiroo/pitaco/modules/collect/`; `…/infra/` = `src/main/java/com/renanloureiroo/pitaco/infra/`
- testes em `src/test/java/com/renanloureiroo/pitaco/`

---

## Phase 1: Setup

- [X] T001 Criar `src/main/resources/db/migration/V20260912140112__sdk_health.sql` com `sdk_version_usage`, `sdk_version_daily_usage`, `suppression_events`, `sdk_error_reports`, os `check` e os índices de listagem, deduplicação e expurgo
- [X] T002 Acrescentar `pitaco.health.*` e `pitaco.collect.rate-limit.*` a `src/main/resources/application.yml`, documentando cada valor; criar `…/collect/infra/config/HealthProperties.java`
- [X] T003 Ligar o agendamento em `…/infra/scheduling/SchedulingConfiguration.java`

---

## Phase 2: Foundational

- [X] T004 [P] Escrever `src/test/java/com/renanloureiroo/pitaco/core/catalog/SdkVersionTest.java` e `SdkCapabilitiesTest.java`
- [X] T005 [P] Criar `src/main/java/com/renanloureiroo/pitaco/core/catalog/SdkVersion.java`, `SdkFeature.java` e `SdkCapabilities.java` (D-07)
- [X] T006 Criar `…/collect/application/services/RequiredSdk.java` sobre o conteúdo entregue
- [X] T007 [P] Acrescentar `ErrorType.RATE_LIMITED` e `src/main/java/com/renanloureiroo/pitaco/core/error/RateLimitedException.java`; mapear `429` em `…/infra/http/error/ErrorTypeHttpStatus.java` e o `Retry-After` em `ApiExceptionHandler.java`, com `src/test/java/com/renanloureiroo/pitaco/infra/http/error/ApiExceptionHandlerTest.java`

---

## Phase 3: User Story 1 — Versões em uso (P1) 🎯 MVP

### Testes ⚠️

- [X] T008 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/health/BufferedSdkUsageRecorderTest.java` (acumula e desce por aplicação/versão/dia; falha volta e desce na seguinte; desiste depois do limite; concorrência sem perda)
- [X] T009 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/ListSdkVersionsUseCaseTest.java`
- [X] T010 [P] [US1] Acrescentar a `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/FindEligibleSurveyUseCaseTest.java` "Versão fora do formato é ignorada, e a consulta segue" e "Aplicação inativa não registra versão"

### Implementação

- [X] T011 [US1] Criar a porta `…/collect/application/gateways/SdkUsageRecorder.java` e o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/gateways/InMemorySdkUsageRecorder.java`
- [X] T012 [US1] Criar `…/collect/application/repositories/SdkVersionUsageRepository.java`, `…/collect/infra/database/jpa/entities/SdkVersionUsageJpaEntity.java`, `…/repositories/SdkVersionUsageJpaRepository.java`, `SdkVersionUsageRepositoryJpa.java` e o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemorySdkVersionUsageRepository.java`
- [X] T013 [US1] Criar `…/collect/infra/health/BufferedSdkUsageRecorder.java` (D-01)
- [X] T014 [US1] Chamar `recordSdkUsage` em `…/collect/application/usecases/FindEligibleSurveyUseCase.java`, lendo `X-Pitaco-Sdk-Version` (`…/collect/infra/http/controllers/SdkVersionHeader.java`) em `CollectEligibilityController.java`
- [X] T015 [US1] Criar `…/collect/application/usecases/ListSdkVersionsUseCase.java`, `…/collect/application/outputs/SdkVersionUsageOutput.java`, `…/collect/infra/http/dtos/SdkVersionsResponseDTO.java`, `…/collect/infra/http/presenters/SdkVersionPresenter.java` e a rota `sdk-versions` em `…/collect/infra/http/controllers/SdkHealthSwagger.java`/`SdkHealthController.java`
- [X] T016 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/SdkVersionsE2ETest.java` com o apoio de `HealthE2ESupport.java`

---

## Phase 4: User Story 2 — Erros do SDK (P1)

### Testes ⚠️

- [X] T017 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/domain/health/SdkErrorContextTest.java`, `SdkErrorReportTest.java` e `SdkErrorKindTest.java`
- [X] T018 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/ReportSdkErrorUseCaseTest.java`, `ListSdkErrorsUseCaseTest.java` e `PurgeHealthDataUseCaseTest.java`

### Implementação

- [X] T019 [US2] Criar `…/collect/domain/health/PersonalDataMask.java`, `SdkErrorContext.java`, `SdkErrorKind.java`, `SdkErrorReport.java`, `SdkErrorReportId.java` (D-03)
- [X] T020 [US2] Criar `…/collect/application/repositories/SdkErrorReportRepository.java`, `…/collect/infra/database/jpa/entities/SdkErrorReportJpaEntity.java`, `…/repositories/SdkErrorReportJpaRepository.java` (insert nativo com `jsonb`), `SdkErrorReportRepositoryJpa.java` e o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemorySdkErrorReportRepository.java`
- [X] T021 [US2] Criar `…/collect/application/usecases/ReportSdkErrorUseCase.java` (`@Transactional`) e `ListSdkErrorsUseCase.java`, com `…/collect/application/outputs/SdkErrorReportOutput.java`
- [X] T022 [US2] Criar `…/collect/infra/http/dtos/SdkErrorReportRequestDTO.java`, `ListSdkErrorsQueryDTO.java`, `SdkErrorReportResponseDTO.java`, `…/presenters/SdkErrorReportPresenter.java`; rota pública em `CollectHealthSwagger.java`/`CollectHealthController.java` e listagem em `SdkHealthSwagger.java`/`SdkHealthController.java`
- [X] T023 [US2] Criar `…/collect/application/usecases/PurgeHealthDataUseCase.java` e `…/collect/infra/health/HealthDataPurgeJob.java`
- [X] T024 [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/SdkErrorReportE2ETest.java` (gravação sanitizada no banco, listagem filtrada e isolada, balde próprio, 401, 400, 403, 404)

---

## Phase 5: User Story 3 — Supressões (P1)

### Testes ⚠️

- [X] T025 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/domain/health/SuppressionDedupKeyTest.java` e `SuppressionShareTest.java`
- [X] T026 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/RecordSuppressionUseCaseTest.java`

### Implementação

- [X] T027 [US3] Criar `…/collect/domain/health/SuppressionEvent.java`, `SuppressionEventId.java`, `SuppressionReason.java`, `SuppressionDedupKey.java`, `SuppressionShare.java` (D-04, D-05)
- [X] T028 [US3] Criar `…/collect/application/repositories/SuppressionEventRepository.java`, `…/collect/infra/database/jpa/entities/SuppressionEventJpaEntity.java`, `…/repositories/SuppressionEventJpaRepository.java`, `SuppressionEventRepositoryJpa.java` e o fake `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemorySuppressionEventRepository.java`
- [X] T029 [US3] Criar `…/collect/application/usecases/RecordSuppressionUseCase.java` e `…/collect/infra/http/dtos/SuppressionRequestDTO.java`; rota `suppressions` em `CollectHealthSwagger.java`/`CollectHealthController.java`
- [X] T030 [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/SuppressionE2ETest.java` (grava sem abrir exibição nem criar respondente; deduplicação; simultâneas; outra aplicação 202 sem gravar; 400; 401)

---

## Phase 6: User Story 4 — Saúde por pesquisa e evento nunca visto (P2)

### Testes ⚠️

- [X] T031 [P] [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/GetSurveyHealthUseCaseTest.java`

### Implementação

- [X] T032 [US4] Acrescentar `countOpenedBetween` a `…/collect/application/repositories/SurveyDisplayRepository.java` e implementações; `currentPublicationOf` a `…/collect/application/gateways/PublishedSurveyCatalog.java`
- [X] T033 [US4] Criar `…/collect/application/usecases/GetSurveyHealthUseCase.java`, `…/collect/application/outputs/SurveyHealthOutput.java`, `…/collect/infra/http/dtos/SurveyHealthQueryDTO.java`, `SurveyHealthResponseDTO.java`, `…/presenters/SurveyHealthPresenter.java`; rota `surveys/{surveyId}/health` em `SdkHealthSwagger.java`/`SdkHealthController.java`; `@Bean`s em `…/collect/infra/config/UseCasesConfiguration.java` com `HealthProperties`
- [X] T034 [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/SurveyHealthE2ETest.java`

---

## Phase 7: User Story 5 — Limite de requisições (P1)

### Testes ⚠️

- [X] T035 [P] [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/infra/http/ratelimit/FixedWindowRateLimiterTest.java` e `OriginResolverTest.java`
- [X] T036 [P] [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/infra/http/ratelimit/RateLimitInterceptorChainTest.java` (quarta chamada 429 com `Retry-After`; chaves isoladas; origem antes da autenticação; forjar `X-Forwarded-For` não foge; admin fora do limite)

### Implementação

- [X] T037 [US5] Criar `…/infra/http/ratelimit/FixedWindowRateLimiter.java` (D-08)
- [X] T038 [US5] Criar `…/infra/http/ratelimit/IpLiteral.java`, `IpRange.java` e `OriginResolver.java` (D-09)
- [X] T039 [US5] Criar `…/infra/http/ratelimit/OriginRateLimitInterceptor.java`, `ApiKeyRateLimitInterceptor.java`, `SdkErrorRateLimitInterceptor.java`, `RateLimitExceeded.java`, `RateLimitProperties.java` e `RateLimitConfiguration.java`
- [X] T040 [US5] Registrar a ordem em `…/infra/http/security/SecurityWebMvcConfiguration.java`
- [X] T041 [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/infra/http/ratelimit/PublicSurfaceRateLimitE2ETest.java`

---

## Phase 8: User Story 6 — Superfícies separadas (P2)

- [X] T042 [P] [US6] Conferir `src/test/java/com/renanloureiroo/pitaco/infra/http/security/PublicSurfaceSecurityE2ETest.java` (401 sem chave, 401 com corpo idêntico para desconhecida e revogada, último uso registrado, 403 no painel)
- [X] T043 [US6] Manter `…/infra/http/security/AdminSurfaceInterceptor.java` e `ApiKeyAuthenticationInterceptor.java` nos prefixos `/applications/**` e `/collect/**` (D-10)
- [X] T044 [US6] Declarar `403` nos Swaggers das leituras administrativas novas (`SdkHealthSwagger.java`)

---

## Phase 9: Polish

- [X] T045 [P] Atualizar `docs/backend/contrato-sdk.md` (seções 4, 5 e "Limites de requisição") e `docs/backend/proxy.md` ("Como o Pitaco decide a origem")
- [X] T046 [P] Conferir que nenhum log traz mensagem, contexto, referência ou dispositivo — só aplicação, tipo, versão e contagens
- [X] T047 Rodar `./mvnw verify` verde
