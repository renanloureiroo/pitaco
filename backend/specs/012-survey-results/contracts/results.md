# Contrato HTTP — Resultados

**Feature**: `012-survey-results` · Base: `/api` · Controller `SurveyResultsController`, Swagger
`SurveyResultsSwagger` · Prefixo `/applications/{applicationId}/surveys/{surveyId}/results`.
Superfície administrativa (`X-Pitaco-Key` → `403 api_key.forbidden_surface`). Todas as rotas são
`GET` e nenhuma altera estado.

## Recorte comum (`ResultsQueryDTO`; `OpenAnswersQueryDTO` acrescenta busca e página)

| Parâmetro | Regra |
| --- | --- |
| `from`, `to` | ISO 8601 UTC, abertura da exibição, inclusivos; `from` ≤ `to` |
| `attribute` | até 80, não vazio |
| `attributeValue` | até 200; exige `attribute`. Sem ele, o recorte é "sem o atributo" |
| `version` | ≥ 1; ausente consolida |

## Erros comuns

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `request.invalid` | recorte inválido (com `errors`) |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` |
| `404` | `survey.not_found` | pesquisa inexistente, malformada ou de outra aplicação |
| `404` | `application.not_found` | aplicação malformada |

---

## `GET …/results`

### `200 OK` — `SurveyResultsResponseDTO`

```json
{
  "everPublished": true,
  "responseRate": {
    "displayed": 120, "completed": 84, "dismissed": 20, "abandoned": 12, "inProgress": 4,
    "rate": 0.7,
    "definition": "Taxa de resposta = concluídas ÷ exibidas. Exibidas conta toda abertura da pesquisa, inclusive as dispensadas, as abandonadas e as ainda em andamento.",
    "timeline": [ { "day": "2026-09-11", "displayed": 60, "completed": 40 } ]
  },
  "questions": [
    {
      "key": "5f0c…", "statement": "Qual a nota?", "type": "NPS", "position": 1,
      "answered": 84, "skipped": 0, "notApplicable": 0,
      "aggregate": { "kind": "nps", "promoters": 50, "passives": 20, "detractors": 14, "score": 42.857,
                     "distribution": [ { "value": 0, "count": 2, "share": 0.024 } ] },
      "comparability": { "comparable": true, "versions": [1, 2] }
    },
    {
      "key": "9a1d…", "statement": "Qual recurso você mais usa?", "type": "MULTIPLE_CHOICE", "position": 2,
      "answered": 80, "skipped": 4, "notApplicable": 0,
      "aggregate": { "kind": "choice", "options": [ { "value": "busca", "label": "Busca", "count": 52, "share": 0.65 } ] }
    }
  ],
  "sampleSize": 84,
  "smallSample": false,
  "filter": { "attribute": "plano", "attributeValue": "pro", "attributeAbsent": false },
  "attributes": [ { "name": "plano", "values": [ { "value": "free", "count": 70 }, { "value": "pro", "count": 50 } ] } ],
  "nps": { "questionKey": "5f0c…", "respondents": 84, "promoters": 50, "passives": 20, "detractors": 14, "score": 42.857 },
  "retention": { "snapshotApplied": true, "discardedBefore": "2026-03-16T03:47:00Z", "note": "Inclui o agregado das respostas descartadas pela política de retenção. …" }
}
```

| Campo | Regra |
| --- | --- |
| `aggregate` | ausente quando ninguém respondeu; `kind` ∈ `choice`, `numeric`, `nps`, `text` |
| `comparability` | só no consolidado |
| `nps` | só em pesquisa do modelo NPS |
| `retention` | só quando há agregado congelado da pesquisa |
| `everPublished: false` | estado vazio, sem perguntas |

---

## `GET …/results/open-answers`

Recorte comum + `q` (até 200), `page` (≥ 0, padrão 0), `size` (1..100, padrão 20).

### `200 OK` — `PageResponseDTO<OpenAnswerResponseDTO>`

```json
{
  "items": [
    {
      "displayId": "d1f0…", "questionKey": "7b2e…", "statement": "Por que você deu essa nota?",
      "text": "A entrega atrasou dois dias.", "answeredAt": "2026-09-12T14:23:07Z",
      "context": [ { "questionKey": "5f0c…", "statement": "Qual a nota?", "value": "4" } ]
    }
  ],
  "page": 0, "size": 20, "total": 1, "totalPages": 1
}
```

Ordem `answeredAt` desc. Texto vencido pela retenção não aparece.

---

## `GET …/results/export`

Recorte comum. `200 OK` com `text/csv; charset=UTF-8`, em streaming:

```http
Content-Disposition: attachment; filename*=UTF-8''resultados-<surveyId>.csv
X-Pitaco-Content-Warning: may-contain-personal-data
```

O segundo cabeçalho só existe quando alguma versão publicada tem texto livre.

```text
﻿displayId,respondentReference,versionNumber,outcome,openedAt,closedAt,sdkVersion,attribute:plano,Qual a nota? [5f0c…],Por que? [7b2e…]
d1f0…,u-8f1c,2,COMPLETED,2026-09-12T14:22:31Z,2026-09-12T14:23:07Z,1.2.0,pro,4,"A entrega atrasou, de novo."
a7e4…,0d0f8a5e-…,2,ABANDONED,2026-09-12T10:00:00Z,,1.2.0,,,
```

| Célula | Valor |
| --- | --- |
| `outcome` | `COMPLETED`, `DISMISSED`, `ABANDONED`, `IN_PROGRESS` |
| múltipla escolha | valores separados por `;` |
| não aplicável | `n/a` |
| pulada, ausente, texto vencido | vazio |

Uma linha por exibição, ordem `openedAt` asc; sem exibição, só o cabeçalho. Erros de recorte,
superfície e escopo saem como `problem+json` antes de qualquer byte.

---

## Composição da visão geral pelo painel (sem rota própria)

| Indicador do painel | De onde sai |
| --- | --- |
| Exibições, taxa, abandono com variação | `responseRate` de cada pesquisa no período atual e no anterior |
| Tendência diária | `responseRate.timeline` somado por dia |
| Desfechos | `completed`, `dismissed`, `abandoned`, `inProgress` |
| NPS consolidado | `nps` das pesquisas do modelo NPS, somando os grupos |
| Análise por segmento | `attributes` + uma leitura por valor com `attribute`/`attributeValue` |
| Desempenho por pesquisa | `responseRate` e `nps` por pesquisa |
