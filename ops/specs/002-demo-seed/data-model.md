# Data Model: Demo completa com dados fictícios

**Feature**: `002-demo-seed` · **Date**: 2026-10-01

Esta feature **não cria nem altera tabela**. Ela preenche as 26 tabelas de negócio que as
migrations do backend já criam, só para as aplicações da demo. O que está descrito aqui é o
recorte que a demo ocupa, como ele é reconhecido, o que a limpeza apaga e o que o deslocamento de
datas move.

---

## 1. Como uma linha é reconhecida como da demo

A âncora é `applications.slug like 'demo-completo%'`. Toda outra tabela é da demo quando aponta
para uma dessas aplicações — diretamente por `application_id`, ou por intermédio de pesquisa,
versão, exibição, pergunta ou atributo.

| Aplicação | Slug | Estado | Retenção (respostas / texto livre) |
| --- | --- | --- | --- |
| Loja Aurora | `demo-completo` | `ACTIVE` | 365 dias / 180 dias; descanso de 14 dias |
| Aurora Entregas | `demo-completo-entregas` | `ACTIVE` | sem prazo |
| Aurora Legado (desativado) | `demo-completo-legado` | `INACTIVE` | sem prazo |

O seed do app de exemplo usa a aplicação de slug `demo`, que não casa com o prefixo.

---

## 2. Tabelas preenchidas

| Tabela | Linhas | Pertence à demo por |
| --- | --- | --- |
| `applications` | 3 | slug |
| `api_keys` | 5 | `application_id` |
| `surveys` | 9 | `application_id` |
| `survey_versions` | 10 | `survey_id` |
| `questions` | 28 | `version_id` |
| `question_options` | 49 | `question_id` |
| `question_condition_values` | 0 | `question_id` |
| `segmentation_rules` | 3 | `version_id` |
| `survey_state_transitions` | 12 | `survey_id` |
| `aggregate_snapshots`, `aggregate_snapshot_counts` | 0 | `survey_id` / `snapshot_id` |
| `respondents` | 4.047 | `application_id` |
| `survey_displays` | 4.047 | `application_id` |
| `survey_display_attributes` | 19.973 | `display_id` |
| `survey_answers` | 7.902 | `display_id` |
| `survey_answer_options` | 2.969 | `answer_id` |
| `survey_display_events` | 39.805 | `display_id` |
| `application_events` | 8 | `application_id` |
| `application_attributes` | 10 | `application_id` |
| `application_attribute_values` | 28 | `attribute_id` |
| `sdk_version_usage` | 6 | `application_id` |
| `sdk_version_daily_usage` | 96 | `application_id` |
| `suppression_events` | 45 | `application_id` |
| `sdk_error_reports` | 68 | `application_id` |
| `deletion_audits` | 3 | `application_id` |
| `retention_runs` | 0 | `application_id` |

Tabelas vazias no dump continuam nele (com `COPY` vazio) e no deslocamento: a demo cobre o schema
inteiro, e uma linha que o painel crie nelas — uma exclusão, um congelamento de agregado — é da
demo e é limpa na substituição.

---

## 3. Limpeza (`--substituir`, e antes de toda carga)

Ordem de remoção, com as restrições de integridade ativas. O que não aparece é removido em cascata
pelo schema:

| # | `delete from` | Leva junto em cascata |
| --- | --- | --- |
| 1 | `survey_displays` | respostas, opções de resposta, atributos e eventos da exibição |
| 2 | `respondents` | supressões ligadas ao respondente |
| 3 | `suppression_events` | — |
| 4 | `sdk_error_reports` | — |
| 5 | `sdk_version_daily_usage` | — |
| 6 | `sdk_version_usage` | — |
| 7 | `surveys` | versões, perguntas, opções, condições, regras de segmentação, transições, agregados congelados |
| 8 | `application_attributes` | valores observados |
| 9 | `application_events` | — |
| 10 | `deletion_audits` | — |
| 11 | `retention_runs` | — |
| 12 | `api_keys` | — |
| 13 | `applications` | — |

Todas filtradas por `application_id in (select id from applications where slug like
'demo-completo%')`.

---

## 4. Deslocamento de datas (`seed/datas.sql`)

```text
delta = (now() − interval '1 hour') − max(survey_displays.opened_at das aplicações da demo)
```

Calculado uma vez, numa tabela temporária `on commit drop`, e somado a:

| Tabela | Colunas deslocadas |
| --- | --- |
| `applications` | `created_at`, `updated_at` |
| `api_keys` | `created_at`, `revoked_at`, `last_used_at` |
| `surveys` | `created_at` |
| `survey_versions` | `published_at`, `trigger_window_start`, `trigger_window_end` |
| `survey_state_transitions` | `occurred_at` |
| `respondents` | `first_seen_at`, `last_seen_at` |
| `survey_displays` | `opened_at`, `closed_at` |
| `survey_answers` | `answered_at` |
| `survey_display_events` | `occurred_at`, `received_at` |
| `application_events` | `first_seen_at`, `last_seen_at` |
| `application_attributes` | `first_seen_at`, `last_seen_at` |
| `application_attribute_values` | `last_seen_at` |
| `sdk_version_usage` | `first_seen_at`, `last_seen_at` |
| `suppression_events` | `occurred_at` |
| `sdk_error_reports` | `occurred_at`, `received_at` |
| `deletion_audits` | `performed_at` |
| `retention_runs` | `ran_at` |
| `aggregate_snapshots` | `discarded_before`, `computed_at` |
| `sdk_version_daily_usage` | `day` + parte inteira de dias de `delta` (troca do bloco inteiro) |

Valores nulos continuam nulos. Distâncias entre instantes são preservadas, exceto em
`sdk_version_daily_usage`, que anda em dias inteiros.

---

## 5. Formato do dump

| Atributo | Valor |
| --- | --- |
| Arquivo | `seed/demo-completo.sql.gz` (gzip, ~2,6 MB) |
| Origem | `pg_dump` 16 em texto, só dados |
| Conteúdo | cabeçalho com `SET`s de sessão e `search_path` vazio; um `COPY public.<tabela> (...) FROM stdin;` por tabela, linhas separadas por tabulação, `\N` para nulo; marcadores `\restrict`/`\unrestrict` |
| Período coberto | 88 dias de exibições (antes do deslocamento) |
| Compatibilidade | schema das migrations atuais; `psql` 16 recente (que entende `\restrict`) |
