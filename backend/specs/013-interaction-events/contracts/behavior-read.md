# Contrato HTTP — Leitura de comportamento

**Feature**: `013-interaction-events` · Base: `/api` · `SurveyResultsController.behavior`, Swagger
`SurveyResultsSwagger`. Superfície administrativa.

## `GET /applications/{applicationId}/surveys/{surveyId}/results/behavior`

Mesmos parâmetros de recorte de `GET …/results` (`from`, `to`, `attribute`, `attributeValue`,
`version`), com as mesmas regras de `400`.

### `200 OK` — `SurveyBehaviorResponseDTO`

```json
{
  "everPublished": true,
  "displayed": 120,
  "instrumented": 96,
  "questions": [
    {
      "key": "5f0c…", "statement": "Qual a nota?", "type": "NPS", "position": 1,
      "viewed": 96, "answered": 90, "skipped": 2, "abandoned": 4,
      "activeTime": { "samples": 92, "medianMs": 3100, "p90Ms": 9800 },
      "revisited": 6, "revisitRate": 0.0625,
      "selected": 0, "changed": 0, "answerChangeRate": null,
      "validationBlockedDisplays": 3, "validationBlocks": 5
    }
  ],
  "dismissals": {
    "total": 10,
    "byVia": [
      { "via": "close_button", "count": 6, "share": 0.6 },
      { "via": "swipe", "count": 3, "share": 0.3 },
      { "via": "backdrop", "count": 0, "share": 0.0 },
      { "via": "hardware_back", "count": 0, "share": 0.0 },
      { "via": "navigation", "count": 0, "share": 0.0 },
      { "via": "programmatic", "count": 0, "share": 0.0 }
    ],
    "unspecified": 1
  },
  "filter": { "attributeAbsent": false },
  "definitions": [
    { "metric": "instrumented", "definition": "Exibições instrumentadas = exibições do recorte com ao menos um evento de interação aceito. …" }
  ]
}
```

| Métrica | Definição |
| --- | --- |
| `instrumented` | exibições do recorte com ao menos um evento aceito; base de toda a leitura |
| `viewed` | exibições com `question_viewed` na pergunta |
| `answered` | última `question_left` (maior `seq`) com `answered: true` |
| `skipped` | última `question_left` com `answered: false` e `to` ∈ `next`, `complete` |
| `abandoned` | exibição dispensada, ou sem desfecho depois do prazo de abandono (30 min), cuja última `question_viewed` é esta |
| `activeTime` | soma do `activeMs` das visitas por exibição; mediana e p90 por interpolação linear, arredondados |
| `revisitRate` | exibições com `question_viewed` de `visit ≥ 2` ÷ `viewed`; ausente sem `viewed` |
| `answerChangeRate` | exibições com `answer_changed` ou `answer_deselected` ÷ exibições com `answer_selected`; ausente sem escolha |
| `validationBlocks` | eventos `validation_blocked` e exibições com ao menos um |
| `dismissalVia` | via da última `survey_dismissed` por exibição instrumentada; as seis vias sempre presentes; ausente ou fora do catálogo em `unspecified` |

Pergunta sem evento aparece com zeros. Pesquisa nunca publicada: `everPublished: false`, vazio.

### Erros

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `request.invalid` | recorte inválido |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` |
| `404` | `survey.not_found` | pesquisa inexistente, malformada ou de outra aplicação |
