# Contrato consumido — API administrativa de resultados

As quatro rotas do módulo `results` do backend que esta feature consome. Todas são `GET` sob
`/applications/{applicationId}/surveys/{surveyId}/results`, superfície administrativa: o painel
não envia chave de aplicação e chama sempre do servidor. Envelope de página e recusa RFC 9457
seguem o [contrato de 001](../../001-painel-operacao-pesquisas/contracts/backend-api.md).

Notação: `{a}` = `applicationId`, `{s}` = `surveyId`.

---

## Recorte comum

Aceito pelas quatro rotas, todos opcionais:

| Param | Tipo | Regra |
|---|---|---|
| `from` | instante ISO UTC | limite inferior da abertura da exibição, inclusive |
| `to` | instante ISO UTC | limite superior, inclusive |
| `attribute` | texto | nome do atributo |
| `attributeValue` | texto | valor exigido; ausente com `attribute` presente ⇒ exibições **sem** o atributo |
| `version` | inteiro ≥ 1 | restringe a uma versão pelo número; ausente consolida todas |

---

## 1. Resultado

```
GET /applications/{a}/surveys/{s}/results
```

**Sucesso `200`** — `SurveyResults`:

```jsonc
{
  "everPublished": true,
  "responseRate": {
    "displayed": 4, "completed": 2, "dismissed": 1, "abandoned": 1, "inProgress": 0,
    "rate": 0.5,                                   // ausente sem exibição
    "definition": "concluídas ÷ exibidas …",
    "timeline": [{ "day": "2026-09-08", "displayed": 1, "completed": 1 }]
  },
  "questions": [{
    "key": "3f9a…", "statement": "De 0 a 10, recomendaria?", "type": "nps", "position": 1,
    "answered": 2, "skipped": 0, "notApplicable": 0,
    "aggregate": { "kind": "nps", "promoters": 1, "passives": 0, "detractors": 1, "score": 0,
                   "distribution": [{ "value": 3, "count": 1, "share": 0.5 }] },
    "comparability": { "comparable": true, "versions": [1] }   // só no consolidado
  }],
  "sampleSize": 2, "smallSample": true,
  "filter": { "from": null, "to": null, "attribute": null, "attributeValue": null,
              "attributeAbsent": false, "version": null },
  "attributes": [{ "name": "plano", "values": [{ "value": "pro", "count": 1 }] }],
  "nps": { "questionKey": "3f9a…", "respondents": 2, "promoters": 1, "passives": 0,
           "detractors": 1, "score": 0 },
  "retention": { "snapshotApplied": true, "discardedBefore": "2026-08-01T12:00:00Z", "note": "…" }
}
```

**Recusas**: `404` para aplicação ou pesquisa inexistente → `notFound()`; `400` para recorte
malformado (não deve acontecer: a URL é saneada antes).

---

## 2. Respostas abertas

```
GET /applications/{a}/surveys/{s}/results/open-answers?…recorte&q&page&size
```

**Sucesso `200`** — `PageResponse<OpenAnswer>`:

```jsonc
{
  "items": [{
    "displayId": "d1f0…", "questionKey": "7b21…", "statement": "O que faltou?",
    "text": "Achei confuso o checkout", "answeredAt": "2026-09-08T14:23:07Z",
    "context": [{ "questionKey": "3f9a…", "statement": "De 0 a 10, recomendaria?", "value": "10" }]
  }],
  "page": 0, "size": 20, "total": 2, "totalPages": 1
}
```

`q` filtra pelo termo sem diferenciar maiúsculas de minúsculas.

---

## 3. Comportamento

```
GET /applications/{a}/surveys/{s}/results/behavior?…recorte
```

**Sucesso `200`** — `SurveyBehavior`:

```jsonc
{
  "everPublished": true, "displayed": 40, "instrumented": 32,
  "questions": [{
    "key": "3f9a…", "statement": "…", "type": "single_choice", "position": 1,
    "viewed": 32, "answered": 28, "skipped": 2, "abandoned": 2,
    "activeTime": { "samples": 30, "medianMs": 4200, "p90Ms": 11800 },
    "revisited": 3, "revisitRate": 0.09,
    "selected": 28, "changed": 4, "answerChangeRate": 0.14,
    "validationBlocks": 1, "validationBlockedDisplays": 1
  }],
  "dismissals": { "total": 5, "byVia": [{ "via": "swipe", "count": 3, "share": 0.6 }], "unspecified": 1 },
  "filter": { "attributeAbsent": false },
  "definitions": [{ "metric": "answerChangeRate", "definition": "…" }]
}
```

Nomes anteriores aceitos pelo painel: `changeRate` (no lugar de `answerChangeRate`) e
`validationFailures` (no lugar de `validationBlocks`).

---

## 4. Export

```
GET /applications/{a}/surveys/{s}/results/export?…recorte
```

**Sucesso `200`** — `text/csv`, com `Content-Disposition` dando o nome
(`resultados-{surveyId}.csv`), BOM UTF-8 e cabeçalho começando por `displayId,respondentReference`.

O painel **não** chama esta rota do navegador. Ver o Route Handler em
[ui-routes.md](./ui-routes.md#route-handler-de-export).

---

## Leituras auxiliares, de outras features

| Consulta | Para quê | Tela |
|---|---|---|
| `GET …/surveys/{s}/versions/comparability` | versões do seletor "Visão" | Resultados, Comportamento |
| `GET …/surveys/{s}/health` | avisos de supressão e de evento nunca visto ([005](../../005-painel-saude-privacidade/contracts/backend-api.md)) | Resultados |

---

## Leituras por tela

| Tela | Leituras | Paralelas? | Falha derruba a tela? |
|---|---|---|---|
| Resultados | resultado, respostas abertas, comparabilidade, saúde, comportamento | sim | sim, exceto saúde |
| Comportamento | comportamento, resultado (catálogo de atributos), comparabilidade | sim | sim, exceto comparabilidade |
