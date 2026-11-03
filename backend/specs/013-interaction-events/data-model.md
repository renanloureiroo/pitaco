# Data Model: Eventos de interação — catálogo, ingestão e leitura de comportamento

**Feature**: `013-interaction-events` · **Date**: 2026-09-12

---

## 1. Catálogo — `core/catalog`

`InteractionEventType` (`CATALOG_VERSION = 1`), `InteractionField(name, kind, choices?)`,
`InteractionFieldKind` ∈ `COUNT` (≥ 0), `POSITIVE` (≥ 1), `DURATION` (inteiro longo ≥ 0), `CHOICE`
(lista fechada), `QUESTION_KEY` (UUID), `ANSWER_VALUE` (1..120 ou inteiro), `EVENT_NAME` (regra do
disparo), `FLAG` (booleano).

| Tipo | De pergunta | `data` |
| --- | --- | --- |
| `survey_presented` | não | `presentation` (`bottom-sheet`, `modal`, `inline`), `questionCount`, `renderableCount`, `triggerEvent` |
| `question_viewed` | sim | `position`, `visit`, `from` (`start`, `next`, `back`) |
| `answer_selected` | sim | `value` |
| `answer_changed` | sim | `from`, `to` |
| `answer_deselected` | sim | `value` |
| `text_focused` | sim | — |
| `text_edited` | sim | `length` |
| `text_blurred` | sim | `length` |
| `validation_blocked` | sim | `reason` (`required_missing`) |
| `question_skipped` | sim | — |
| `question_not_applicable` | sim | `sourceKey` |
| `navigated_next` | sim | `toKey` |
| `navigated_back` | sim | `toKey` |
| `question_left` | sim | `visit`, `to` (`next`, `back`, `dismiss`, `complete`), `durationMs`, `activeMs`, `answered` |
| `survey_backgrounded` | não | — |
| `survey_foregrounded` | não | `backgroundMs` |
| `survey_dismissed` | não | `via` (`close_button`, `swipe`, `backdrop`, `hardware_back`, `navigation`, `programmatic`), `position`, `answeredCount` |
| `survey_completed` | não | `answeredCount`, `skippedCount`, `notApplicableCount`, `activeMs` |

`carriesAnswerValue()` só para `answer_selected`, `answer_changed`, `answer_deselected`.

## 2. Domínio — `collect/domain/interaction`

- `InteractionDraft`: o evento como chegou, com `catalogVersion`, `type`, `displayId`, `seq`
  (`Long`, para detectar estouro de inteiro), `occurredAt`, `elapsedMs` e `questionKey` opcionais, e
  `data` como mapa.
- `InteractionEvent` (value object; identidade `(displayId, seq)`): `seq ≥ 1`, `catalogVersion ≥ 1`,
  `elapsedMs ≥ 0`, `occurredAt` e `receivedAt` obrigatórios, `questionKey` obrigatória em evento de
  pergunta e descartada nos demais, `data = type.sanitize(data)`. Código `interaction_event.invalid`.
- `DiscardReason`: `DISPLAY_UNAVAILABLE`, `OUTSIDE_WINDOW`, `UNKNOWN_TYPE`, `INVALID_ENVELOPE`,
  `UNKNOWN_QUESTION`, `OVER_LIMIT` (no fio em camelCase no recibo).
- `InteractionIntake.read` → `Readable | Discarded`; `admit(candidatos, seqsGravados, totalGravado,
  teto)` → `Admission(accepted, duplicated, overLimit)`.

## 3. Porta — `collect/application/repositories/InteractionEventRepository`

`lockDisplay(displayId)`, `countByDisplay(displayId)`, `storedSeqs(displayId, seqs)`,
`saveAll(eventos)`.

## 4. Tabela — `V20260912162148__create_survey_display_events.sql`

| Coluna | Tipo | Nota |
| --- | --- | --- |
| `id` | `varchar(64)` PK | |
| `display_id` | `varchar(64)` FK `survey_displays` `on delete cascade` | |
| `seq` | `int` | `uq_survey_display_events_seq unique (display_id, seq)` |
| `catalog_version` | `int` | |
| `type` | `varchar(40)` | sem `check`: o catálogo cresce por versão |
| `question_key` | `varchar(64)` | |
| `occurred_at` | `timestamptz` | relógio do dispositivo, informativo |
| `elapsed_ms` | `bigint` | monotônico desde a apresentação |
| `received_at` | `timestamptz` | relógio do servidor |
| `data` | `jsonb default '{}'` | só campos do catálogo |

Índices: `idx_survey_display_events_type (display_id, type, seq) include (question_key)`,
`idx_survey_display_events_received (received_at)`.

## 5. Leitura — `results`

`SurveyBehaviorReadModel` records: `BehaviorTotals(displayed, instrumented)`,
`QuestionFunnel(key, viewed, revisited, answered, skipped, selected, changed, blockedDisplays,
blocks)`, `QuestionAbandonment(key, abandoned)`, `QuestionActiveTime(key, samples, medianMs, p90Ms)`,
`DismissalCount(via?, count)`.

`SurveyBehaviorOutput(everPublished, displayed, instrumented, questions, dismissals, filter,
definitions)`; `BehaviorMetric` ∈ `INSTRUMENTED`, `VIEWED`, `ANSWERED`, `SKIPPED`, `ABANDONED`,
`ACTIVE_TIME`, `REVISIT_RATE`, `ANSWER_CHANGE_RATE`, `VALIDATION_BLOCKS`, `DISMISSAL_VIA`.

## 6. Configuração

| Chave | Padrão |
| --- | --- |
| `pitaco.collect.interaction-events.max-per-display` | `500` |
| `pitaco.collect.interaction-events.acceptance-window` | `7d` |
