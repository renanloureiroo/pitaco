# Quickstart — validar `013-interaction-events`

Contratos em [`contracts/interaction-events.md`](./contracts/interaction-events.md) e
[`contracts/behavior-read.md`](./contracts/behavior-read.md).

## 1. Portão

```bash
./mvnw verify
```

## 2. Só esta fatia

```bash
# catálogo, domínio, DTO e OpenAPI do catálogo (sem Docker)
./mvnw test -Dtest='InteractionEventTypeTest,InteractionFieldTest,InteractionEventTest,InteractionIntakeTest,InteractionEventsRequestDTOTest,InteractionCatalogSchemasTest,BehaviorMetricTest'

# casos de uso
./mvnw test -Dtest='RecordInteractionEventsUseCaseTest,GetSurveyBehaviorUseCaseTest,ApplyRetentionUseCaseTest'

# E2E e OpenAPI publicado
./mvnw test -Dtest='CollectInteractionEventsE2ETest,SurveyBehaviorE2ETest,CollectOpenApiTest,ResultsOpenApiTest,CascadeDeletionE2ETest'
```

## 3. O catálogo publicado

```bash
curl -s http://localhost:8080/api/v3/api-docs \
  | jq '.components.schemas.InteractionEventType | {enum, v: ."x-pitaco-catalog-version"}'
```

Esperado: os 18 nomes e `v: 1`. (Com `PITACO_API_DOCS_ENABLED=true` ou o perfil local.)

## 4. Roteiro manual

Abra uma exibição pela superfície pública (`POST /collect/displays`) e guarde o `displayId`.

| # | Envio para `POST /collect/displays/{displayId}/events` | Esperado |
| --- | --- | --- |
| 1 | `survey_presented` (seq 1), `question_viewed` (seq 2), `question_left` (seq 3) | `202`, `accepted: 3` |
| 2 | o mesmo lote de novo | `202`, `accepted: 0`, `duplicated: 3` |
| 3 | um evento `type: "matrix_opened"` | `discarded.unknownType: 1` |
| 4 | `answer_selected` numa pergunta de texto livre | `discarded.invalidEnvelope: 1` |
| 5 | `text_edited` com `data: {"length": 12, "text": "meu email"}` | aceito; no banco, `data` só com `length` |
| 6 | `{"events": []}` | `400` |
| 7 | `displayId` de outra aplicação | `202`, tudo em `displayUnavailable` |
| 8 | `GET /applications/{app}/surveys/{s}/results/behavior` | funil e tempo ativo da pergunta; `definitions` com as dez métricas |

```sql
select seq, type, question_key, data from survey_display_events
 where display_id = '<displayId>' order by seq;
```

## 5. O que precisa estar verde

- [X] Nenhum `400` por evento; recibo com cada motivo
- [X] Reenvio idempotente; teto exato com lotes simultâneos
- [X] Nenhum texto do respondente gravado
- [X] OpenAPI igual ao enum
- [X] Comportamento conferindo com os eventos enviados, com as definições
- [X] Retenção apaga eventos vencidos; exibição apagada leva os eventos
