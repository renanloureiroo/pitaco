# Implementation Plan: Saúde do SDK e proteção da superfície pública

**Branch**: `010-sdk-health` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-sdk-health/spec.md`

## Summary

Duas metades com o mesmo motivo — o que acontece do outro lado, em apps que o Pitaco não
controla:

**Saúde** (módulo `collect`, subpacote `domain/health`):

1. **Versões por acumulador em memória** (D-01): `BufferedSdkUsageRecorder` implementa a porta
   `SdkUsageRecorder`, acumula por `(aplicação, versão, dia)` e desce em lote agendado para
   `sdk_version_usage` (rollup) e `sdk_version_daily_usage` (janela recente).
2. **Erro do SDK sanitizado no domínio** (D-02, D-03): `SdkErrorReport.create` mascara a mensagem
   (`PersonalDataMask`) e `SdkErrorContext.sanitize` descarta o que parece pessoal.
3. **Supressão fora de `survey_displays`** (D-04, D-05): tabela própria, deduplicada por resumo
   SHA-256 da identidade (`SuppressionDedupKey`) sob trava consultiva.
4. **Saúde por pesquisa** (D-06): `GetSurveyHealthUseCase` junta supressões, exibições, versão
   mínima (`RequiredSdk`) e última ocorrência do evento.
5. **Capacidades em `core/catalog`** (D-07): `SdkVersion`, `SdkFeature`, `SdkCapabilities` com
   `switch` sem `default`.

**Borda** (`infra/http/ratelimit` e `infra/http/security`):

6. **Janela fixa em memória** (D-08) com três baldes e ordem de interceptors explícita.
7. **Origem só de quem é confiável** (D-09): `OriginResolver` + `IpLiteral` + `IpRange`.
8. **Superfícies separadas por prefixo** (D-10).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Data JPA, Validation, Scheduling),
springdoc-openapi, Lombok. **Nenhuma dependência nova** — o limitador é próprio, sem Redis nem
biblioteca de rate limit.

**Storage**: `V20260912140112__sdk_health.sql`: `sdk_version_usage`, `sdk_version_daily_usage`,
`suppression_events`, `sdk_error_reports` (contexto `jsonb`).

**Testing**: domínio (`SdkVersionTest`, `SdkCapabilitiesTest`, `SdkErrorContextTest`,
`SdkErrorReportTest`, `SdkErrorKindTest`, `SuppressionDedupKeyTest`, `SuppressionShareTest`);
casos de uso sobre fakes; `BufferedSdkUsageRecorderTest`; limitador e origem em teste unitário
(`FixedWindowRateLimiterTest`, `OriginResolverTest`, `RateLimitInterceptorChainTest` com
`MockMvc` montado à mão); E2E (`SdkVersionsE2ETest`, `SdkErrorReportE2ETest`, `SuppressionE2ETest`,
`SurveyHealthE2ETest`, `PublicSurfaceRateLimitE2ETest`, `PublicSurfaceSecurityE2ETest`).

**Target Platform / Project Type**: Linux server, uma instância · web service

**Performance Goals**: sem SLO. Nenhuma escrita síncrona por consulta para contar versão;
expurgos e descarga fora do caminho de requisição; um mapa concorrente por balde.

**Constraints**: nenhum dado pessoal em registro gravado; origem nunca escolhida pelo cliente;
`Retry-After` sempre presente no `429`.

**Scale/Scope**: 3 rotas administrativas (`sdk-versions`, `sdk-errors`, `surveys/{id}/health`), 2
públicas (`suppressions`, `sdk-errors`), 6 casos de uso, 2 jobs agendados, 4 tabelas, 1 pacote de
infraestrutura de borda.

## Constitution Check

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Núcleo Independente de Framework** | `domain/health` e `core/catalog` são Java puro. Agendamento (`@Scheduled`) só em `infra/health` (`BufferedSdkUsageRecorder`, `HealthDataPurgeJob`); o caso de uso `PurgeHealthDataUseCase` é POJO. O limitador mora em `infra/http/ratelimit`, porque é decisão de borda. | ✅ |
| **II. Domínio Que Se Valida** | `SdkErrorReport` e `SuppressionEvent` validam no construtor; `SdkVersion.of` recusa com `sdk_version.invalid`, `parse` é leniente de propósito. | ✅ |
| **III. Teste É Contrato** | Cada regra de sanitização tem teste; corrida de supressões simultâneas em E2E; cadeia de interceptors testada com cabeçalhos forjados. | ✅ |
| **IV. Consistência da Superfície Pública** | `429` sai do `ApiExceptionHandler` (`RateLimitedException` → `ErrorType.RATE_LIMITED` com `Retry-After`); Swaggers `CollectHealthSwagger` (202/400/401/429) e `SdkHealthSwagger` (200/400/403/404). | ✅ |
| **V. Performance Por Construção** | Índices de listagem e de expurgo na mesma migration; supressão com índice parcial de deduplicação; nada em laço por item. | ✅ |

**Re-avaliação após Phase 1**: aprovado. O estado em memória do limitador e do acumulador é
aceito porque o processo é único; a perda num reinício é declarada na spec.

## Project Structure

### Documentation (this feature)

```text
specs/010-sdk-health/
├── plan.md · research.md · data-model.md · quickstart.md · tasks.md
├── contracts/sdk-health.md
├── contracts/public-surface-protection.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
src/main/java/com/renanloureiroo/pitaco/
├── core/
│   ├── catalog/SdkVersion.java, SdkFeature.java, SdkCapabilities.java
│   └── error/RateLimitedException.java, ErrorType.java (RATE_LIMITED)
├── infra/
│   ├── http/error/ApiExceptionHandler.java, ErrorTypeHttpStatus.java
│   ├── http/ratelimit/FixedWindowRateLimiter.java, OriginResolver.java, IpLiteral.java,
│   │                  IpRange.java, OriginRateLimitInterceptor.java,
│   │                  ApiKeyRateLimitInterceptor.java, SdkErrorRateLimitInterceptor.java,
│   │                  RateLimitExceeded.java, RateLimitProperties.java,
│   │                  RateLimitConfiguration.java
│   ├── http/security/SecurityWebMvcConfiguration.java, AdminSurfaceInterceptor.java,
│   │                 ApiKeyAuthenticationInterceptor.java, AuthenticatedApplication.java,
│   │                 AuthenticatedApplicationArgumentResolver.java
│   └── scheduling/SchedulingConfiguration.java
└── modules/collect/
    ├── domain/health/PersonalDataMask.java, SdkErrorContext.java, SdkErrorKind.java,
    │                 SdkErrorReport.java, SdkErrorReportId.java, SuppressionDedupKey.java,
    │                 SuppressionEvent.java, SuppressionEventId.java, SuppressionReason.java,
    │                 SuppressionShare.java
    ├── application/
    │   ├── gateways/SdkUsageRecorder.java
    │   ├── repositories/SdkVersionUsageRepository.java, SdkErrorReportRepository.java,
    │   │               SuppressionEventRepository.java
    │   ├── services/RequiredSdk.java
    │   ├── outputs/SdkVersionUsageOutput.java, SdkErrorReportOutput.java, SurveyHealthOutput.java
    │   └── usecases/ListSdkVersionsUseCase.java, ReportSdkErrorUseCase.java,
    │                ListSdkErrorsUseCase.java, RecordSuppressionUseCase.java,
    │                GetSurveyHealthUseCase.java, PurgeHealthDataUseCase.java,
    │                FindEligibleSurveyUseCase.java (recordSdkUsage)
    └── infra/
        ├── config/HealthProperties.java, UseCasesConfiguration.java
        ├── health/BufferedSdkUsageRecorder.java, HealthDataPurgeJob.java
        ├── database/jpa/entities/SdkVersionUsageJpaEntity.java, SdkErrorReportJpaEntity.java,
        │                         SuppressionEventJpaEntity.java
        ├── database/jpa/repositories/SdkVersionUsageJpaRepository.java,
        │       SdkVersionUsageRepositoryJpa.java, SdkErrorReportJpaRepository.java,
        │       SdkErrorReportRepositoryJpa.java, SuppressionEventJpaRepository.java,
        │       SuppressionEventRepositoryJpa.java
        └── http/
            ├── controllers/CollectHealthController.java, CollectHealthSwagger.java,
            │               SdkHealthController.java, SdkHealthSwagger.java, SdkVersionHeader.java
            ├── dtos/SuppressionRequestDTO.java, SdkErrorReportRequestDTO.java,
            │        ListSdkErrorsQueryDTO.java, SurveyHealthQueryDTO.java,
            │        SdkVersionsResponseDTO.java, SdkErrorReportResponseDTO.java,
            │        SurveyHealthResponseDTO.java
            └── presenters/SdkVersionPresenter.java, SdkErrorReportPresenter.java,
                           SurveyHealthPresenter.java

src/main/resources/db/migration/V20260912140112__sdk_health.sql
src/main/resources/application.yml       (pitaco.collect.rate-limit.*, pitaco.health.*)
```

**Structure Decision**: a saúde é coleta vista do outro lado, por isso mora em `collect`. O
limitador e a separação de superfícies são infraestrutura HTTP compartilhada por todos os módulos,
por isso moram em `infra/http`.

## Complexity Tracking

| Item | Por que existe | Alternativa descartada |
| --- | --- | --- |
| Limitador próprio em memória | Um processo só atende a API; a precisão de um token bucket distribuído não compra nada (D-08). | Redis ou biblioteca de rate limit — dependência e serviço novos para um processo. |
| Acumulador de uso em memória | Evita uma escrita por evento do app hospedeiro no caminho mais quente (D-01). | Escrita síncrona por consulta. |
