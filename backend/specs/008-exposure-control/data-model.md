# Data Model: Controle de exposição — prioridade, cota, descanso e catálogo observado

**Feature**: `008-exposure-control` · **Date**: 2026-09-12

---

## 1. Exposure — `survey/domain/valueobjects` (novo)

| Campo | Tipo | Regra | Padrão |
| --- | --- | --- | --- |
| `priority` | `int` | `-100..100` (`survey.priority_invalid`) | `0` |
| `responseQuota` | `Optional<Integer>` | `>= 1` (`survey.response_quota_invalid`) | ausente |
| `ignoresQuietPeriod` | `boolean` | — | `false` |

Operações: `standard()`, `withPriority`, `withResponseQuota`, `withoutResponseQuota`,
`ignoringQuietPeriod`. Cada uma muda só o próprio campo.

`Survey` ganha `getExposure()`, `redefineExposure(Exposure)` (exige não nulo) e
`endByQuota()`: devolve `false` em `DRAFT` ou `ENDED`; caso contrário passa o ciclo de vida a
`ENDED` e devolve `true`.

### Colunas em `surveys`

| Coluna | Tipo | Restrição |
| --- | --- | --- |
| `priority` | `int not null default 0` | `ck_surveys_priority` |
| `response_quota` | `int` | `ck_surveys_response_quota` |
| `ignores_quiet_period` | `boolean not null default false` | — |

---

## 2. TransitionReason — `survey/domain/entities` (alterado)

`PUBLICATION`, `MANUAL_PAUSE`, `MANUAL_RESUME`, `MANUAL_END`, **`QUOTA_REACHED`** (gravados) e
`WINDOW_OPENED`, `WINDOW_CLOSED` (derivados na leitura, nunca gravados). `SurveyStateTransition`
carrega `from`, `to`, `reason`, `actor` (`Optional`, sempre vazio) e `occurredAt`.

---

## 3. QuietPeriod — `collect/domain/eligibility` (novo)

`record QuietPeriod(Duration length)`, positivo (`quiet_period.invalid`). `isRestingAt(last, now)`
é `now < last + length`.

---

## 4. Candidata da elegibilidade — `PublishedSurveyCatalog.SurveyCandidate` (alterado)

Ganha `priority` e `ignoresQuietPeriod`, além de `publishedAt` já usado no desempate.

---

## 5. Portas novas e alteradas

| Porta | Módulo | Métodos |
| --- | --- | --- |
| `SurveyQuotaGateway` | `collect` | `responseQuotaOf(SurveyId)`, `lockedResponseQuotaOf(SurveyId)`, `endByQuota(ApplicationId, SurveyId)` |
| `CompletedResponsesGateway` | `survey` | `completedResponsesOf(SurveyId)` |
| `ApplicationScopeGateway` | `collect` | `+ quietPeriodDaysOf(ApplicationId)` |
| `SurveyDisplayRepository` | `collect` | `+ lastOpenedAt(RespondentId)`, `+ countCompleted(SurveyId)` |
| `SurveyRepository` | `survey` | `lockByIdAndApplicationId`, `endIfLive(SurveyId)` |
| `ObservedAttributeRepository` | `collect` | `record(ApplicationId, AttributeSnapshot, Instant)`, `findPage(ListObservedAttributesQuery)` |
| `ObservedEventRepository` | `collect` | `findPage(ListObservedEventsQuery)` passa a ser lido pela borda |

---

## 6. ObservedAttribute — `collect/domain/entities` (novo)

| Campo | Tipo | Nota |
| --- | --- | --- |
| `id` | `ObservedAttributeId` | |
| `applicationId` | `ApplicationId` | |
| `name` | `String` | não vazio |
| `firstSeenAt`, `lastSeenAt` | `Instant` | `lastSeenAt >= firstSeenAt` |
| `values` | `List<Value(value, lastSeenAt)>` | em ordem alfabética na leitura; no máximo `MAX_VALUES_PER_ATTRIBUTE = 200` |

Código de invariante: `observed_attribute.invalid`. Só `restore`: a criação acontece no banco, pelo
upsert.

### Tabelas — `V20260912123029__exposure_control.sql`

`application_attributes`

| Coluna | Tipo |
| --- | --- |
| `id` | `varchar(64)` PK |
| `application_id` | `varchar(64)` FK `applications` |
| `name` | `varchar(80)` |
| `first_seen_at`, `last_seen_at` | `timestamptz` |

- `uq_application_attributes_name unique (application_id, name)`
- `idx_application_attributes_listing (application_id, last_seen_at desc, name)`

`application_attribute_values`

| Coluna | Tipo |
| --- | --- |
| `id` | `varchar(64)` PK |
| `attribute_id` | `varchar(64)` FK `application_attributes` `on delete cascade` |
| `value` | `varchar(200)` |
| `last_seen_at` | `timestamptz` |

- `uq_application_attribute_values_value unique (attribute_id, value)`

### Índice novo em `survey_displays`

`idx_survey_displays_completed on survey_displays (survey_id) where outcome = 'COMPLETED'` — a
contagem que decide o encerramento.

---

## 7. Outputs novos — `collect/application/outputs`

- `ObservedEventOutput(name, firstSeenAt, lastSeenAt)`
- `ObservedAttributeOutput(name, firstSeenAt, lastSeenAt, values[(value, lastSeenAt)])`
- `GetQuotaProgressUseCase.Output(Optional<Integer> responseQuota, long completedResponses)`
