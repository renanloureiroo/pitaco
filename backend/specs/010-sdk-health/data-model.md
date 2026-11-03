# Data Model: Saúde do SDK e proteção da superfície pública

**Feature**: `010-sdk-health` · **Date**: 2026-09-12

---

## 1. Catálogo de capacidades — `core/catalog`

| Tipo | Forma |
| --- | --- |
| `SdkVersion(major, minor, patch, Optional<preRelease>)` | semver sem metadado de build; até 40 caracteres; `BASELINE = 1.0.0`; `parse` (leniente), `of` (estrito, `sdk_version.invalid`), `isAtLeast`, `highest` |
| `SdkFeature` | `CONDITIONAL_DISPLAY`, `SCALE_LABELS`, `FREE_TEXT_NOTICE` (da `009`); `INTERACTION_EVENTS` acrescentado pela `013` |
| `SdkCapabilities` | `minimumFor(QuestionType)`, `minimumFor(SdkFeature)`, `minimumFor(tipos, recursos)` — hoje tudo `1.0.0` |

## 2. Uso de versão — `collect/application/repositories/SdkVersionUsageRepository`

| Record | Campos |
| --- | --- |
| `UsageDelta` | `applicationId`, `version`, `day`, `requests`, `firstSeenAt`, `lastSeenAt` |
| `SdkVersionUsage` | `version`, `requestCount`, `recentRequestCount`, `firstSeenAt`, `lastSeenAt` |

Métodos: `increment(UsageDelta)`, `findByApplication(applicationId, recentFrom)`,
`purgeDailyBefore(day)`.

`sdk_version_usage`: `id` PK, `application_id` FK, `version varchar(40)`, `request_count bigint`,
`first_seen_at`, `last_seen_at`; `uq_sdk_version_usage_version (application_id, version)`.

`sdk_version_daily_usage`: PK `(application_id, version, day)`, `request_count bigint`; índices
`idx_sdk_version_daily_usage_recent (application_id, day)` e `idx_sdk_version_daily_usage_day
(day)`.

## 3. Registro de erro — `collect/domain/health`

`SdkErrorReport` (identidade `SdkErrorReportId`)

| Campo | Regra |
| --- | --- |
| `applicationId` | obrigatório |
| `sdkVersion` | `Optional<SdkVersion>` |
| `kind` | `SdkErrorKind`: `RENDER_ERROR`, `NETWORK_ERROR`, `MALFORMED_RESPONSE`, `STORAGE_ERROR`, `UNKNOWN`; no fio em minúsculas |
| `message` | controles → espaço, mascarada, até 500 (`MAX_MESSAGE_LENGTH`) |
| `context` | `SdkErrorContext(values, discarded)` |
| `occurredAt` | do dispositivo; no futuro além de `CLOCK_TOLERANCE` (5 min) vira `receivedAt` |
| `receivedAt` | relógio do servidor |

`SdkErrorContext`: `MAX_KEYS = 20`, `MAX_NESTED_KEYS = 10`, `MAX_STRING_LENGTH = 200`,
`MAX_TOTAL_SIZE = 2000`; chaves `^[A-Za-z][A-Za-z0-9_.-]{0,39}$` sem palavra de dado pessoal.

`sdk_error_reports`: `message varchar(500)`, `context jsonb default '{}'`, `kind` com `check`;
índices `idx_sdk_error_reports_listing (application_id, received_at desc, id desc)`,
`idx_sdk_error_reports_kind`, `idx_sdk_error_reports_version`, `idx_sdk_error_reports_received`.

## 4. Supressão — `collect/domain/health`

`SuppressionEvent` (identidade `SuppressionEventId`): `applicationId`, `surveyId`, `versionId`,
`respondentId?`, `dedupKey?` (`SuppressionDedupKey`, SHA-256 hex), `sdkVersion?`, `reason`
(`UNKNOWN_QUESTION_TYPE`, `UNSUPPORTED_FEATURE`), `minRequiredVersion`, `occurredAt`. Código de
invariante `suppression.invalid`.

`SuppressionShare(displays, suppressions)`: `share()` = suppressions ÷ (displays + suppressions),
ausente quando ambos zero; `isRelevant(limiar, mínimo)`.

`suppression_events`: FKs para aplicação, pesquisa e versão (`on delete cascade` nas duas últimas),
`respondent_id` com cascata; `check` do motivo; índices `idx_suppression_events_survey (survey_id,
occurred_at)`, parcial `idx_suppression_events_dedup (version_id, dedup_key, occurred_at desc)` e
parcial `idx_suppression_events_respondent`.

## 5. Saída da saúde — `SurveyHealthOutput`

`from`, `to`, `displays`, `suppressions{total, byReason[{reason, count}], bySdkVersion[{sdkVersion,
count}]}`, `suppressionShare?`, `relevant`, `minRequiredVersion?`, `eventName?`,
`eventLastSeenAt?`.

## 6. Configuração — `application.yml`

| Chave | Padrão |
| --- | --- |
| `pitaco.health.sdk-usage.flush-interval` | `1m` |
| `pitaco.health.sdk-usage.recent-window` | `14d` |
| `pitaco.health.sdk-usage.stale-after` | `14d` |
| `pitaco.health.sdk-usage.daily-retention` | `90d` |
| `pitaco.health.suppressions.dedup-window` | `24h` |
| `pitaco.health.suppressions.default-window` | `30d` |
| `pitaco.health.suppressions.relevant-share` / `relevant-minimum` | `0.05` / `5` |
| `pitaco.health.sdk-errors.retention` / `purge-cron` | `90d` / `0 17 3 * * *` (UTC) |
| `pitaco.collect.rate-limit.per-key` | `1200` por `1m` |
| `pitaco.collect.rate-limit.per-origin` | `120` por `1m` |
| `pitaco.collect.rate-limit.sdk-errors` | `30` por `1m` |
| `pitaco.collect.rate-limit.origin.client-ip-header` | `CF-Connecting-IP` |
| `pitaco.collect.rate-limit.origin.trusted-proxies` | `127.0.0.0/8`, `::1/128`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `fc00::/7` |

## 7. Limitador — `infra/http/ratelimit`

`FixedWindowRateLimiter.Decision(allowed, retryAfterSeconds)`; assunto = endereço normalizado
(origem) ou `apiKeyId` (chave). `RateLimitExceeded` → `rate_limit.exceeded`,
`ErrorType.RATE_LIMITED` → `429`.
