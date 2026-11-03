# Implementation Plan: Eventos de interação — catálogo, ingestão e leitura de comportamento

**Branch**: `013-interaction-events` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-interaction-events/spec.md`

## Summary

1. **Catálogo em `core/catalog`** (D-01): `InteractionEventType` (18 tipos, `CATALOG_VERSION = 1`,
   `fromWire`, `sanitize`), `InteractionField` e `InteractionFieldKind` — usado pela ingestão
   (`collect`) e pela leitura (`results`).
2. **Ingestão evento a evento** (D-02, D-03): `InteractionIntake.read` devolve `Readable` ou
   `Discarded(motivo)`; `InteractionIntake.admit` deduplica por `seq` e aplica o teto.
   `RecordInteractionEventsUseCase` (`@Transactional`) trava a exibição, conta e grava num `insert`
   nativo com `on conflict do nothing`.
3. **OpenAPI gerado do enum** (D-04): `InteractionCatalogSchemas` + um `OpenApiCustomizer`.
4. **Leitura de comportamento** (D-05, D-06): `GetSurveyBehaviorUseCase` sobre o read model
   `SurveyBehaviorReadModel` (SQL em `BehaviorSql`), definições em `BehaviorMetric`.
5. **Retenção** (D-07): `RetentionStore.deleteInteractionEvents` em lotes no `ApplyRetentionUseCase`.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1, springdoc-openapi 3.1 (OpenAPI 3.1, `types`), Lombok.
**Nenhuma dependência nova.**

**Storage**: `V20260912162148__create_survey_display_events.sql` — `survey_display_events` com
`unique (display_id, seq)`, FK `on delete cascade` para a exibição, `data jsonb`, índices
`idx_survey_display_events_type (display_id, type, seq) include (question_key)` e
`idx_survey_display_events_received (received_at)`.

**Testing**: catálogo (`InteractionEventTypeTest`, `InteractionFieldTest`); domínio
(`InteractionEventTest`, `InteractionIntakeTest`); caso de uso sobre
`InMemoryInteractionEventRepository` (`RecordInteractionEventsUseCaseTest`); DTO
(`InteractionEventsRequestDTOTest`); OpenAPI (`InteractionCatalogSchemasTest`,
`CollectOpenApiTest`, `ResultsOpenApiTest`); leitura (`BehaviorMetricTest`,
`GetSurveyBehaviorUseCaseTest` sobre `InMemorySurveyBehaviorReadModel`); E2E
(`CollectInteractionEventsE2ETest`, `SurveyBehaviorE2ETest`); retenção e cascata
(`ApplyRetentionUseCaseTest`, `CascadeDeletionE2ETest`).

**Target Platform / Project Type**: Linux server, JVM UTC · web service

**Performance Goals**: sem SLO. Um lote = uma trava consultiva por exibição, uma contagem, uma
consulta de `seq` existentes e um `insert` em lote. A leitura usa um número fixo de consultas
agregadas, com mediana e p90 por `percentile_cont` no banco.

**Constraints**: teto exato sob concorrência; nenhum texto do respondente gravado; nenhum `400` por
evento; `occurredAt` do dispositivo nunca decide nada.

**Scale/Scope**: 1 rota pública, 1 rota administrativa (`…/results/behavior`), 2 casos de uso, 1
tabela, 18 tipos de evento, 10 métricas definidas.

## Constitution Check

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Núcleo Independente de Framework** | Catálogo e intake são Java puro; o esquema OpenAPI é gerado em `collect/infra/http/config`; a leitura é porta `SurveyBehaviorReadModel` com SQL só em `results/infra`. Catálogo em `core` porque dois módulos o usam. | ✅ |
| **II. Domínio Que Se Valida** | `InteractionEvent` valida o envelope e fecha o `data` no construtor (`interaction_event.invalid`); o intake converte a recusa em motivo de descarte. | ✅ |
| **III. Teste É Contrato** | Cada regra de descarte, teto, janela e privacidade tem teste de domínio e E2E com as linhas lidas do banco; o OpenAPI é testado contra o enum. | ✅ |
| **IV. Consistência da Superfície Pública** | `CollectInteractionSwagger` declara 202/400/401/429; `SurveyResultsSwagger` declara 200/400/403/404 para `behavior`; o recibo e as definições são DTOs próprios. | ✅ |
| **V. Performance Por Construção** | Índice cobrindo `(display_id, type, seq)` com `question_key` incluído; insert em lote; nenhuma consulta por evento. | ✅ |

**Re-avaliação após Phase 1**: o `@Transactional` no caso de uso é obrigatório — contagem do teto e
gravação precisam do mesmo instante do banco, sob a trava da exibição. Aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/013-interaction-events/
├── plan.md · research.md · data-model.md · quickstart.md · tasks.md
├── contracts/interaction-events.md
├── contracts/behavior-read.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
src/main/java/com/renanloureiroo/pitaco/
├── core/catalog/InteractionEventType.java, InteractionField.java, InteractionFieldKind.java,
│                SdkFeature.java (+ INTERACTION_EVENTS), SdkCapabilities.java
└── modules/
    ├── collect/
    │   ├── domain/interaction/InteractionDraft.java, InteractionEvent.java, DiscardReason.java,
    │   │                      InteractionIntake.java
    │   ├── application/repositories/InteractionEventRepository.java
    │   ├── application/usecases/RecordInteractionEventsUseCase.java
    │   └── infra/
    │       ├── config/InteractionEventsProperties.java, UseCasesConfiguration.java
    │       ├── database/jpa/entities/SurveyDisplayEventJpaEntity.java
    │       ├── database/jpa/repositories/SurveyDisplayEventJpaRepository.java,
    │       │                             InteractionEventRepositoryJpa.java
    │       └── http/
    │           ├── config/InteractionCatalogSchemas.java,
    │           │          InteractionCatalogOpenApiConfiguration.java
    │           ├── controllers/CollectInteractionController.java, CollectInteractionSwagger.java
    │           ├── dtos/InteractionEventsRequestDTO.java, InteractionEventsReceiptDTO.java
    │           └── presenters/InteractionEventsReceiptPresenter.java
    ├── results/
    │   ├── domain/behavior/BehaviorMetric.java
    │   ├── application/readmodels/SurveyBehaviorReadModel.java
    │   ├── application/outputs/SurveyBehaviorOutput.java
    │   ├── application/usecases/GetSurveyBehaviorUseCase.java
    │   └── infra/
    │       ├── database/jpa/BehaviorSql.java, SurveyBehaviorJpaRepository.java,
    │       │                SurveyBehaviorReadModelJpa.java
    │       └── http/
    │           ├── controllers/SurveyResultsController.java, SurveyResultsSwagger.java (+ behavior)
    │           ├── dtos/SurveyBehaviorResponseDTO.java, ResultsQueryDTO.java (toBehaviorInput)
    │           └── presenters/SurveyBehaviorPresenter.java
    └── privacy/
        ├── application/repositories/RetentionStore.java      (+ deleteInteractionEvents)
        ├── application/usecases/ApplyRetentionUseCase.java   (+ discardEvents)
        └── infra/database/jpa/repositories/RetentionJpaRepository.java, RetentionStoreJpa.java

src/main/resources/db/migration/V20260912162148__create_survey_display_events.sql
src/main/resources/application.yml       (pitaco.collect.interaction-events.*)
docs/backend/contrato-sdk.md             (seção 6, catálogo, leitura de comportamento)
docs/backend/proxy.md                    (rota de eventos a repassar)
```

**Structure Decision**: ingestão em `collect` (é coleta), leitura em `results` (é leitura
agregada), catálogo em `core` (os dois usam), retenção em `privacy` (é o mesmo job).

## Complexity Tracking

| Item | Por que existe | Alternativa descartada |
| --- | --- | --- |
| Trava consultiva por exibição (`pg_advisory_xact_lock`) | Teto exato com lotes simultâneos da mesma exibição (FR-012). | Contar sem trava — dois lotes de 300 passariam do teto de 500. |
| Esquema OpenAPI gerado por `OpenApiCustomizer` | Fonte única para servidor e teste de contrato do SDK (D-04). | Anotar 18 DTOs à mão — divergência garantida. |
