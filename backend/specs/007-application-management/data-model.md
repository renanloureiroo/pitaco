# Data Model: Aplicação — cadastro, edição, ativação e eventos observados

**Feature**: `007-application-management` · **Date**: 2026-09-05

---

## 1. Application — `modules/app/domain/entities`

Entidade, identidade `ApplicationId` (`core/identity`, texto opaco, hoje UUID).

| Campo | Tipo | Regra |
| --- | --- | --- |
| `id` | `ApplicationId` | gerado em `create`, restaurado em `restore` |
| `slug` | `Slug` | imutável; `^[a-z0-9]+(-[a-z0-9]+)*$`, até 50 |
| `name` | `Name` | obrigatório, até 120, sem espaço nas pontas (`RequiredText`) |
| `status` | `Status` | `ACTIVE` ou `INACTIVE`; nasce `ACTIVE` |
| `quietPeriodDays` | `Optional<Integer>` | ≥ 1 quando presente |
| `retentionDays` | `Optional<Integer>` | ≥ 1; nunca menor que `openTextRetentionDays` |
| `openTextRetentionDays` | `Optional<Integer>` | ≥ 1; nunca maior que `retentionDays` |
| `createdAt` | `Instant` | nunca muda |
| `updatedAt` | `Instant` | igual a `createdAt` no nascimento; avança só em mudança aceita |

**Derivado**: `effectiveOpenTextRetentionDays()` = `openTextRetentionDays` ou, na falta dele,
`retentionDays`.

**Transições**: `rename`, `defineQuietPeriod`/`removeQuietPeriod`, `defineRetention`/
`removeRetention`, `defineOpenTextRetention`/`resetOpenTextRetention`, `activate`, `deactivate`.
Cada uma chama `touch()` só depois de validar.

**Erros de domínio**

| `code` | `ErrorType` | Quando |
| --- | --- | --- |
| `application.name_invalid` | VALIDATION | nome vazio ou acima de 120 |
| `application.slug_invalid` | VALIDATION | slug fora do formato, acima de 50, ou não derivável do nome |
| `application.quiet_period_invalid` | VALIDATION | descanso < 1 dia |
| `application.retention_invalid` | VALIDATION / BUSINESS_RULE | < 1 dia / menor que o texto livre já definido |
| `application.open_text_retention_invalid` | VALIDATION / BUSINESS_RULE | < 1 dia / maior que o geral |

**Erros de aplicação** (`modules/app/application/errors`)

| Classe | `code` | Status |
| --- | --- | --- |
| `ApplicationAlreadyExistsWithSameSlug` | `application.already_exists_with_same_slug` | 409 |
| `ApplicationNotFound` | `application.not_found` | 404 |
| `ApplicationIsInactive` | `application.inactive` | 422 |

A coleta tem sua própria `ApplicationIsInactive` (`modules/collect/application/errors`) com o mesmo
`code`: o contrato compartilhado é o código, não a classe.

### Tabela `applications` — `V20260905180000__create_applications.sql`

| Coluna | Tipo | Nota |
| --- | --- | --- |
| `id` | `varchar(64)` PK | texto opaco |
| `slug` | `varchar(50)` `not null unique` | unicidade garantida no banco (FR-004) |
| `name` | `varchar(120)` `not null` | |
| `status` | `varchar(20)` `not null` | `ACTIVE`/`INACTIVE` |
| `quiet_period_days` | `integer` | nulo = sem descanso |
| `retention_days` | `integer` | nulo = sem prazo |
| `open_text_retention_days` | `integer` | nulo = segue o geral |
| `created_at`, `updated_at` | `timestamptz` `not null` | UTC |

---

## 2. ApplicationOutput — `modules/app/application/outputs`

`record` compartilhado por consulta, edição, desativação e reativação: `id`, `slug`, `name`,
`status`, `quietPeriodDays`, `retentionDays`, `openTextRetentionDays` (todos `Optional`),
`createdAt`, `updatedAt`. Traduzido por `ApplicationPresenter` para `ApplicationResponseDTO`, com
`@JsonInclude(NON_NULL)` — prazo ausente some do JSON.

---

## 3. Patch&lt;T&gt; — `core/usecase`

`record Patch<T>(boolean present, Optional<T> value)` com `absent()`, `clear()`, `set(value)` e
`apply(define, remove)`. Ausente não chama nada; nulo chama `remove`; valor chama `define`.

---

## 4. ObservedEvent — `modules/collect/domain/entities`

Entidade, identidade `ObservedEventId`.

| Campo | Tipo | Regra |
| --- | --- | --- |
| `applicationId` | `ApplicationId` | obrigatório |
| `name` | `EventName` (`core/catalog`) | mesma regra do evento de disparo |
| `firstSeenAt` | `Instant` | primeira ocorrência |
| `lastSeenAt` | `Instant` | nunca anterior a `firstSeenAt`; `seenAt(now)` só avança |

Código de invariante: `observed_event.invalid`.

### Porta `ObservedEventRepository` — `modules/collect/application/repositories`

| Método | Semântica |
| --- | --- |
| `record(applicationId, name, now)` | cria na primeira vez, avança `lastSeenAt` nas seguintes, podendo amortizar |
| `lastSeenAt(applicationId, name)` | `Optional<Instant>`; usado pela saúde da pesquisa (`010`) |
| `findPage(ListObservedEventsQuery)` | leitura paginada, entregue pela `008` |

### Tabela `application_events` — `V20260911160000__create_application_events.sql`

| Coluna | Tipo | Nota |
| --- | --- | --- |
| `id` | `varchar(64)` PK | |
| `application_id` | `varchar(64)` FK `applications` | cascata acrescentada na `011` |
| `name` | `varchar(80)` | |
| `first_seen_at`, `last_seen_at` | `timestamptz` | |

Restrições e índices:

- `uq_application_events_name unique (application_id, name)` — alvo do upsert.
- `idx_application_events_listing (application_id, last_seen_at desc, name)` — leitura do painel.
