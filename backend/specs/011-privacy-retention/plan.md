# Implementation Plan: Privacidade e retenção — descarte, exclusão do titular e auditoria

**Branch**: `011-privacy-retention` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-privacy-retention/spec.md`

## Summary

Módulo novo, `modules/privacy`, com quatro casos de uso e um job:

1. **`ApplyRetentionUseCase`** (D-02, D-03): para cada `RetentionPolicy` configurada, lotes de
   respostas vencidas, cada lote numa transação que congela o agregado
   (`RetentionSnapshots.of`) e apaga as respostas; depois os textos vencidos (`update … set
   text_value = null`); registra `RetentionRun` só se algo saiu. Disparado por `RetentionJob`.
2. **`GetRetentionPreviewUseCase`** (D-05): contagens na próxima execução (`RetentionSchedule`,
   mesma expressão cron do job) e uma semana depois.
3. **`DeleteRespondentUseCase`** (D-06): `ErasureTarget` exige exatamente uma identidade; contagem,
   `delete from respondents` com cascata no banco e `DeletionAudit` na mesma transação.
4. **`ListDeletionAuditsUseCase`**: paginado, `performed_at desc`.
5. **Cascatas no schema** (D-07): a migration troca as FKs sem cascata pelas com `on delete
   cascade`, achando a restrição antiga pelo catálogo do Postgres.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Data JPA, Scheduling — `CronExpression`),
springdoc-openapi, Lombok. **Nenhuma dependência nova.**

**Storage**: `V20260912160000__privacy_retention.sql`: cascatas; `deletion_audits`,
`aggregate_snapshots`, `aggregate_snapshot_counts`, `retention_runs`; índice
`idx_survey_answers_answered_at` e `idx_survey_displays_respondent`. A mesma migration acrescenta as
colunas do aviso de texto livre da pesquisa (`009`).

**Testing**: domínio (`RetentionPolicyTest`, `RetentionSnapshotsTest`, `ErasureTargetTest`); casos
de uso sobre fakes (`InMemoryRetentionStore`, `InMemoryAggregateSnapshotRepository`,
`InMemoryRetentionRunRepository`, `InMemoryRespondentErasureRepository`,
`InMemoryDeletionAuditRepository`, `InMemoryPrivacyApplicationGateway`,
`InMemoryRetentionSchedule`, `DirectTransactor`); E2E `PrivacyE2ETest`, `RetentionE2ETest`,
`CascadeDeletionE2ETest`, `PrivacyOpenApiTest`.

**Target Platform / Project Type**: Linux server, JVM UTC · web service

**Performance Goals**: sem SLO. Descarte por lote limitado (1.000), uma transação por lote; busca
de vencidas por `answered_at` indexado; exclusão do titular com índice na FK `respondent_id` para a
cascata não varrer a tabela.

**Constraints**: nenhum agregado perdido nem contado duas vezes numa queda; identidade do titular
nunca em log nem em registro.

**Scale/Scope**: 3 rotas administrativas, 4 casos de uso, 1 job, 4 tabelas novas, 12 FKs
reescritas com cascata.

## Constitution Check

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Núcleo Independente de Framework** | `RetentionPolicy`, `RetentionSnapshots`, `ErasureTarget`, `AggregateSnapshot`, `DeletionAudit`, `RetentionRun` são Java puro. A política vem de `app` pela porta `PrivacyApplicationGateway` (adaptador `PrivacyApplicationGatewayApp`); o agendamento é `RetentionSchedule` (adaptador `CronRetentionSchedule`). | ✅ |
| **II. Domínio Que Se Valida** | `ErasureTarget.of` recusa sem identidade (`respondent.identity_required`) e com as duas (`respondent.identity_ambiguous`); `DeletionAudit` recusa contagem negativa. | ✅ |
| **III. Teste É Contrato** | Lote, idempotência, isolamento entre aplicações e cascata cobertos; E2E prova que resultados e export param de contar o excluído. | ✅ |
| **IV. Consistência da Superfície Pública** | `PrivacySwagger` declara 200/400/403/404 nas leituras e na exclusão; 400 das duas regras de identidade vem de `@AssertTrue` no DTO com mensagem igual à do domínio. | ✅ |
| **V. Performance Por Construção** | Lotes, índices na mesma migration, contagens por consulta agregada. | ✅ |

**Re-avaliação após Phase 1**: o `ApplyRetentionUseCase` usa `Transactor` explicitamente (não
`@Transactional`), porque precisa de uma transação **por lote**, não uma para tudo. Aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/011-privacy-retention/
├── plan.md · research.md · data-model.md · quickstart.md · tasks.md
├── contracts/privacy.md
└── checklists/requirements.md
```

### Source Code (repository root)

```text
src/main/java/com/renanloureiroo/pitaco/modules/privacy/
├── domain/
│   ├── entities/AggregateSnapshot.java, AggregateSnapshotId.java, DeletionAudit.java,
│   │            DeletionAuditId.java, RetentionRun.java, RetentionRunId.java
│   ├── retention/RetentionPolicy.java, RetentionSnapshots.java, SnapshotCount.java,
│   │             ExpiringAnswer.java
│   └── valueobjects/ErasureTarget.java
├── application/
│   ├── gateways/PrivacyApplicationGateway.java, RetentionSchedule.java
│   ├── outputs/RetentionPreviewOutput.java, RetentionRunOutput.java,
│   │           RespondentErasureOutput.java, DeletionAuditOutput.java
│   ├── repositories/RetentionStore.java, AggregateSnapshotRepository.java,
│   │                RetentionRunRepository.java, RespondentErasureRepository.java,
│   │                DeletionAuditRepository.java
│   ├── services/PrivacyScope.java
│   └── usecases/ApplyRetentionUseCase.java, GetRetentionPreviewUseCase.java,
│                DeleteRespondentUseCase.java, ListDeletionAuditsUseCase.java
└── infra/
    ├── config/PrivacyProperties.java, UseCasesConfiguration.java
    ├── gateways/PrivacyApplicationGatewayApp.java, CronRetentionSchedule.java
    ├── retention/RetentionJob.java
    ├── database/jpa/entities/AggregateSnapshotJpaEntity.java, DeletionAuditJpaEntity.java,
    │                         RetentionRunJpaEntity.java
    ├── database/jpa/mappers/PrivacyJpaMapper.java
    ├── database/jpa/repositories/RetentionJpaRepository.java, RetentionStoreJpa.java,
    │       AggregateSnapshotJpaRepository.java, AggregateSnapshotRepositoryJpa.java,
    │       RetentionRunJpaRepository.java, RetentionRunRepositoryJpa.java,
    │       ErasureJpaRepository.java, RespondentErasureRepositoryJpa.java,
    │       DeletionAuditJpaRepository.java, DeletionAuditRepositoryJpa.java,
    │       PrivacyApplicationJpaRepository.java
    └── http/
        ├── controllers/PrivacyController.java, PrivacySwagger.java
        ├── dtos/EraseRespondentQueryDTO.java, RespondentErasureResponseDTO.java,
        │        ListDeletionAuditsQueryDTO.java, DeletionAuditResponseDTO.java,
        │        RetentionPreviewResponseDTO.java
        └── presenters/PrivacyPresenter.java

src/main/resources/db/migration/V20260912160000__privacy_retention.sql
src/main/resources/application.yml          (pitaco.privacy.retention.*)
```

**Structure Decision**: módulo próprio porque privacidade atravessa todos os outros (aplicação,
coleta, resultados) e não pertence a nenhum. Ele lê a política de `app` por porta e escreve direto
nas tabelas de coleta pelos seus próprios repositórios de infraestrutura — o descarte e a exclusão
são operações de armazenamento, não regras de domínio da coleta.

## Complexity Tracking

| Item | Por que existe | Alternativa descartada |
| --- | --- | --- |
| Bloco `do $$` na migration para recriar FKs | O nome gerado pelo Postgres para as FKs antigas não é contrato; a restrição é achada pelo catálogo (tabela + coluna). | Escrever os nomes à mão — quebraria em qualquer banco cujo nome gerado divergisse. |
