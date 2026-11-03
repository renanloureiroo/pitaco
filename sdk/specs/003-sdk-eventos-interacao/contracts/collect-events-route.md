# Contrato — `POST /collect/displays/{displayId}/events`

**Feature**: `003-sdk-eventos-interacao`

A rota publicada pela `backend 013-interaction-events`, do lado de quem envia. Cabeçalhos,
classificação de resposta e regras de fila são os de
`specs/001-sdk-core/contracts/collect-routes.md`.

## Quando sai

| Situação | Envio |
| --- | --- |
| `survey_presented` (abertura) | imediato, depois da abertura |
| durante a resposta | agendado 5 s depois do primeiro evento pendente |
| desfecho (`survey_completed` / `survey_dismissed`) | imediato |
| app vai para o segundo plano | imediato |
| app volta ao primeiro plano / Provider monta | passada forçada da fila |

Nunca antes de a `open_display` da mesma exibição sair da fila.

## Requisição

```http
POST {baseUrl}/collect/displays/3b1f0a2c-6c9a-4a1e-9d0b-2c1f7a3e5d90/events
Content-Type: application/json
X-Pitaco-Key: pk_…
X-Pitaco-Sdk-Version: 1.0.0
```

```json
{
  "events": [
    { "catalogVersion": 1, "type": "survey_presented", "displayId": "3b1f0a2c-…", "seq": 1,
      "occurredAt": "2026-09-13T13:45:00.000Z", "elapsedMs": 0,
      "data": { "presentation": "bottom-sheet", "questionCount": 6, "renderableCount": 6,
                "triggerEvent": "checkout_completed" } },
    { "catalogVersion": 1, "type": "question_viewed", "displayId": "3b1f0a2c-…", "seq": 2,
      "occurredAt": "2026-09-13T13:45:00.000Z", "elapsedMs": 0, "questionKey": "q-nps",
      "data": { "position": 1, "visit": 1, "from": "start" } }
  ]
}
```

| Regra do lado do SDK | Valor |
| --- | --- |
| tamanho do lote | 1 a 100, em ordem de `seq` |
| eventos por exibição na fila | até 500; o excedente não é enfileirado |
| `displayId` do evento | sempre igual ao do caminho |
| repetição | um `seq` nunca é enfileirado duas vezes |

## Resposta

`202` com a contagem:

```json
{
  "accepted": 2,
  "duplicated": 0,
  "discarded": {
    "displayUnavailable": 0, "outsideWindow": 0, "unknownType": 0,
    "invalidEnvelope": 0, "unknownQuestion": 0, "overLimit": 0
  }
}
```

| Resposta | O que o SDK faz |
| --- | --- |
| `202` (qualquer corpo, inclusive ilegível) | remove os `seq` enviados; o restante sai na mesma passada |
| rede, timeout, 5xx | mantém, backoff |
| `429` | pausa a fila inteira até o `Retry-After` |
| `401`/`403` | descarta e desliga as consultas |
| `400` e outros 4xx | descarta e reporta |

O SDK não lê a contagem: ela serve à depuração (o painel do app de exemplo mostra o corpo).
