# Contrato HTTP — Ingestão de eventos de interação

**Feature**: `013-interaction-events` · Base: `/api` · Controller `CollectInteractionController`,
Swagger `CollectInteractionSwagger`. Superfície pública: `X-Pitaco-Key` obrigatório; limite por
origem e por chave (`010`). O contrato completo do SDK está em `docs/backend/contrato-sdk.md`
(seção 6 e "Catálogo de eventos de interação, versão 1").

## `POST /collect/displays/{displayId}/events`

Enviar só depois de a abertura da exibição ter recebido `201` ou `200`.

### Requisição — `InteractionEventsRequestDTO`

```json
{
  "events": [
    {
      "catalogVersion": 1,
      "type": "question_left",
      "displayId": "3b1f0a2c-6c9a-4a1e-9d0b-2c1f7a3e5d90",
      "seq": 7,
      "occurredAt": "2026-09-12T13:45:03.120Z",
      "elapsedMs": 8120,
      "questionKey": "5f0c2b1e-9a4d-4c3e-8b7a-1d2e3f4a5b6c",
      "data": { "visit": 1, "to": "next", "durationMs": 4200, "activeMs": 4200, "answered": true }
    }
  ]
}
```

| Regra | Resultado |
| --- | --- |
| `events` vazio, ausente ou > 100 | `400 request.invalid` |
| `seq` como texto, `occurredAt` fora de ISO 8601, JSON malformado | `400 request.invalid` |
| tipo fora do catálogo | descartado, `unknownType` |
| envelope incompleto, `seq < 1`, `elapsedMs < 0`, `displayId` divergente, evento de pergunta sem `questionKey`, escolha em texto livre | descartado, `invalidEnvelope` |
| pergunta fora da versão exibida | descartado, `unknownQuestion` |
| acima do teto (500 por exibição, contando o gravado) | descartado, `overLimit` |
| depois da janela (7 dias da abertura, relógio do servidor) | lote inteiro `outsideWindow` |
| exibição inexistente, de outra aplicação ou de aplicação inativa | lote inteiro `displayUnavailable` |
| campo extra (envelope ou `data`) | ignorado |
| campo do `data` fora da forma | removido; o evento fica |
| `displayId` ausente no evento | vale o do caminho |

### `202 Accepted` — `InteractionEventsReceiptDTO`

```json
{
  "accepted": 12,
  "duplicated": 0,
  "discarded": {
    "displayUnavailable": 0, "outsideWindow": 0, "unknownType": 1,
    "invalidEnvelope": 0, "unknownQuestion": 0, "overLimit": 0
  }
}
```

### Erros

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `request.invalid` | ver tabela acima |
| `401` | `api_key.missing` / `api_key.invalid` | sem chave / chave desconhecida ou revogada |
| `429` | `rate_limit.exceeded` | limite por origem ou por chave, com `Retry-After` |

### Idempotência

`(displayId, seq)` é único. Reenviar o mesmo lote não grava nada e volta em `duplicated`. Dois
eventos com o mesmo `seq` no lote: o primeiro vence. Lotes simultâneos da mesma exibição são
serializados na contagem do teto.

## OpenAPI

`GET /api/v3/api-docs` publica, gerados de `InteractionEventType`:

- `components.schemas.InteractionEventType` — `enum` com os 18 nomes e `x-pitaco-catalog-version: 1`;
- `components.schemas.InteractionEvent` — `oneOf` das variantes `<Tipo>Event`, discriminado por `type`;
- `components.schemas.<Tipo>Data` — `additionalProperties: false`.

Os itens de `events` apontam para `#/components/schemas/InteractionEvent`.
