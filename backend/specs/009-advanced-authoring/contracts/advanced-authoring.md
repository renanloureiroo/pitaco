# Contrato HTTP — Autoria avançada

**Feature**: `009-advanced-authoring` · Base: `/api` · Superfície administrativa
(`/applications/**`; `X-Pitaco-Key` é recusada com `403 api_key.forbidden_surface` pelo
interceptor, embora os Swaggers de pesquisa não declarem o 403). Erros RFC 9457 com `code`,
`traceId` e, quando o tipo de erro as define, extensões (`field`, `questionKey`, `differences`).

Prefixo abaixo: `/applications/{applicationId}/surveys`.

---

## `POST /applications/{applicationId}/surveys` — com modelo

```json
{ "name": "NPS pós-entrega", "template": "nps" }
```

| Campo | Regra |
| --- | --- |
| `name` | obrigatório, até 120 |
| `template` | opcional: `nps`, `csat`, `ces` |

`201` com `Location` e `SurveyResponseDTO` (`templateKind: "nps"`). Erros: `400 request.invalid`,
`404 application.not_found`, `422 application.inactive`.

---

## Perguntas — condição e rótulos

`POST …/{surveyId}/questions` e `PUT …/{surveyId}/questions/{questionId}` aceitam:

```json
{
  "statement": "Por que você deu essa nota?",
  "type": "free_text",
  "required": true,
  "condition": { "sourceKey": "5f0c2b1e-…", "operator": "between", "min": 0, "max": 6 }
}
```

```json
{ "statement": "Qual a nota?", "type": "nps", "required": true,
  "range": { "min": 0, "max": 10, "minLabel": "Nada provável", "maxLabel": "Extremamente provável" } }
```

| `condition.operator` | Pede | Origem aceita |
| --- | --- | --- |
| `equals`, `not_equals` | `values` com 1 item | escolha (valor ∈ opções) ou numérica (inteiro na escala) |
| `in` | `values` com 1..50 itens distintos | idem |
| `between` | `min` e `max`, sem `values` | só numérica, faixa contida na escala |

`QuestionResponseDTO` devolve `condition` e `range.minLabel`/`range.maxLabel`.

### Erros de condição — `422`

```json
{ "status": 422, "code": "question.condition_value_invalid", "field": "condition.values", "traceId": "…" }
```

| `code` | Quando |
| --- | --- |
| `question.condition_source_invalid` | origem inexistente, posterior, a própria pergunta ou texto livre |
| `question.condition_operator_invalid` | `between` em origem de escolha |
| `question.condition_value_invalid` | cardinalidade errada, valor fora das opções/escala, repetido, vazio ou > 120 |
| `question.condition_source_in_use` | remover ou alterar a origem quebraria a dependente (`questionKey` da dependente) |
| `question.condition_order_invalid` | reordenar poria a condicionada antes da origem (`questionKey`) |
| `question.scale_labels_not_allowed` | rótulo em pergunta sem faixa |

Operador fora da lista ou origem ausente no corpo é `400 request.invalid`.

---

## `POST …/{surveyId}/duplicate`

Corpo opcional (`DuplicateSurveyRequestDTO`):

```json
{ "targetApplicationId": "7c1e…", "name": "NPS pós-entrega (Android)" }
```

`201 Created` com `Location: /api/applications/{destino}/surveys/{novaPesquisa}` e
`SurveyResponseDTO` em rascunho (`state: "draft"`, `draftVersionNumber: 1`).

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `request.invalid` | `name` em branco ou > 120 |
| `404` | `survey.not_found` / `application.not_found` | origem fora do escopo / destino inexistente |
| `422` | `application.inactive` | destino inativo |

---

## `PATCH …/{surveyId}` — nome e aviso de texto livre

```json
{ "name": "NPS pós-entrega v2", "freeTextNoticeEnabled": true, "freeTextNoticeText": "Não escreva telefone nem e-mail aqui." }
```

| Campo | `null` | Regra |
| --- | --- | --- |
| `name` | corpo malformado | 1..120 |
| `freeTextNoticeEnabled` | corpo malformado | booleano |
| `freeTextNoticeText` | volta ao texto padrão | 1..200, não só espaço |

`SurveyResponseDTO.freeTextNotice = { enabled, customText?, text, defaultText }`. Erros: `400`,
`404 survey.not_found`.

---

## `DELETE …/{surveyId}`

`204` para pesquisa nunca publicada (leva versões e conteúdo). `422
survey.published_cannot_be_discarded` para publicada. `404 survey.not_found`.

---

## `POST …/{surveyId}/publication` — classificação

```json
{ "changeKind": "cosmetic", "changeSummary": "Rótulo da escala revisado" }
```

| Campo | Regra |
| --- | --- |
| `changeKind` | `cosmetic` ou `semantic`; obrigatório da segunda versão em diante |
| `changeSummary` | opcional, até 500 |

`201` com `Location` da versão e `SurveyVersionResponseDTO` (`changeKind`, `changeSummary`,
`comparabilityGroup`).

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `request.invalid` / `survey_version.change_kind_required` | formato / classificação ausente na v2+ |
| `404` | `survey.not_found` | fora do escopo |
| `409` | `survey.already_published` | não há rascunho a publicar |
| `422` | `survey.not_publishable` | impedimentos (lista completa) |
| `422` | `survey_version.no_changes` | rascunho idêntico ao publicado |
| `422` | `survey_version.cosmetic_refused` | declaração cosmética com diferença estrutural |

```json
{ "code": "survey_version.cosmetic_refused",
  "differences": [ { "questionKey": "5f0c…", "difference": "condition_changed" } ] }
```

`difference` ∈ `question_added`, `question_removed`, `type_changed`, `options_changed`,
`range_changed`, `condition_changed`.

---

## `GET …/{surveyId}/versions/comparability`

`200` com `{ "groups": [ { "group": 1, "versions": [1, 2] }, { "group": 2, "versions": [3, 4] } ] }`.

## `GET …/{surveyId}/publication-impediments`

`200` com a lista de `{ code, field, questionKey? }`: `survey.no_questions`,
`question.statement_missing`, `question.options_missing`, `trigger.missing`,
`trigger.window_invalid`. `404` fora do escopo.

## `GET …/{surveyId}/publication-warnings`

### `200 OK` — `PublicationWarningsResponseDTO`

```json
{
  "warnings": [
    { "code": "trigger.competing_surveys",
      "competingSurveys": [ { "surveyId": "a1…", "name": "Pesquisa de catálogo", "priority": 0 } ] },
    { "code": "segmentation.no_known_match", "ruleId": "r9…", "attribute": "plano" },
    { "code": "segmentation.contradictory", "attribute": "pais" },
    { "code": "compatibility.unsupported_by_majority", "minRequiredVersion": "1.0.0", "unsupportedShare": 0.62 }
  ]
}
```

Nenhum aviso bloqueia a publicação. `404 survey.not_found` fora do escopo.

---

## Superfície pública (reflexos)

`POST /collect/eligibility` — cada pergunta traz `condition` e `range.minLabel/maxLabel`; a
pesquisa traz `freeTextNotice: { enabled, text }` (texto ausente quando desligado).

`POST /collect/displays/{displayId}/submission` — `status: "NOT_APPLICABLE"` aceito só em pergunta
com condição; fora disso, `422 submission.rejected` com o problema
`answer.not_applicable_unconditional` apontando a pergunta.
