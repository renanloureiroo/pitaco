# Data Model: Privacidade e retenção — descarte, exclusão do titular e auditoria

**Feature**: `011-privacy-retention` · **Date**: 2026-09-12

---

## 1. RetentionPolicy — `privacy/domain/retention`

`record RetentionPolicy(ApplicationId applicationId, Optional<Integer> answerDays,
Optional<Integer> textDays)`; `of(...)` aplica o prazo efetivo de texto. `isConfigured()`,
`answersBefore(referência)`, `textsBefore(referência)`.

## 2. ExpiringAnswer e RetentionSnapshots — `privacy/domain/retention`

`ExpiringAnswer`: `answerId`, `displayId`, `surveyId`, `versionId`, `key`, `status`, `number?`,
`options`. `RetentionSnapshots.of(lote, discardedBefore, computedAt)` devolve um
`AggregateSnapshot` por versão; `SnapshotCount(key, dimension, value, count)` com `Dimension` ∈
`STATUS`, `OPTION`, `NUMBER`.

## 3. Entidades — `privacy/domain/entities`

| Entidade | Campos |
| --- | --- |
| `AggregateSnapshot` | `surveyId`, `versionId`, `discardedBefore`, `respondingDisplays`, `counts`, `computedAt` |
| `RetentionRun` | `applicationId`, `answersDeleted`, `textsCleared`, `ranAt` |
| `DeletionAudit` | `applicationId`, `displaysDeleted`, `answersDeleted` (≥ 0, `deletion_audit.count_invalid`), `performedBy` (sempre nulo), `performedAt` |

## 4. ErasureTarget — `privacy/domain/valueobjects`

`record ErasureTarget(Kind kind, String value)`, `Kind` ∈ `APP_REFERENCE`, `DEVICE`; valor até 200
sem espaço nas pontas.

| `code` | Quando |
| --- | --- |
| `respondent.identity_required` | nenhuma das duas |
| `respondent.identity_ambiguous` | as duas |
| `respondent.identity_invalid` | valor inválido |

## 5. Portas — `privacy/application`

| Porta | Métodos |
| --- | --- |
| `PrivacyApplicationGateway` | `policyOf(applicationId)`, `configuredPolicies()` |
| `RetentionSchedule` | `nextRunAfter(instante)` |
| `RetentionStore` | `expiringAnswers`, `deleteAnswers`, `clearTexts`, `deleteInteractionEvents` (da `013`), `countAnswersBefore`, `countTextsBefore` |
| `AggregateSnapshotRepository` | `create` |
| `RetentionRunRepository` | `create`, `lastRunAt` |
| `RespondentErasureRepository` | `find(applicationId, target)` (com contagens), `erase(respondentId)` |
| `DeletionAuditRepository` | `create`, `findPage(Query)` |

## 6. Tabelas — `V20260912160000__privacy_retention.sql`

`deletion_audits`: `id`, `application_id` FK cascade, `displays_deleted`, `answers_deleted`,
`performed_by varchar(120)`, `performed_at`; índice `idx_deletion_audits_listing (application_id,
performed_at desc, id desc)`.

`aggregate_snapshots`: `id`, `survey_id` FK cascade, `version_id` FK cascade, `reason` (`check in
('RETENTION')`), `discarded_before`, `responding_displays`, `computed_at`; índice
`idx_aggregate_snapshots_survey (survey_id, version_id)`.

`aggregate_snapshot_counts`: PK `(snapshot_id, question_key, dimension, value)`, `count bigint`,
`check` da dimensão.

`retention_runs`: `id`, `application_id` FK cascade, `answers_deleted`, `texts_cleared`, `ran_at`;
índice `idx_retention_runs_application (application_id, ran_at desc)`.

Índices em tabelas de coleta: `idx_survey_answers_answered_at`, `idx_survey_displays_respondent`.

FKs recriadas com `on delete cascade`: `respondents.application_id`, `surveys.application_id`,
`api_keys.application_id`, `survey_displays.application_id|respondent_id|survey_id`,
`application_events.application_id`, `application_attributes.application_id`,
`sdk_version_usage.application_id`, `sdk_version_daily_usage.application_id`,
`suppression_events.application_id`, `sdk_error_reports.application_id`.

## 7. Configuração

| Chave | Padrão |
| --- | --- |
| `pitaco.privacy.retention.enabled` | `true` |
| `pitaco.privacy.retention.cron` | `0 47 3 * * *` (UTC) |
| `pitaco.privacy.retention.batch-size` | `1000` |
