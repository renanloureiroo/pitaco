# Contrato HTTP — Catálogo observado: eventos e atributos

**Feature**: `008-exposure-control` · Base: `/api` · Superfície administrativa. Nenhuma das duas
leituras altera estado. Paginação no envelope `PageResponseDTO` (`items`, `page`, `size`, `total`,
`totalPages`).

| Parâmetro | Padrão | Regra |
| --- | --- | --- |
| `page` | `0` | `>= 0` |
| `size` | `20` | `1..100` |

---

## `GET /applications/{applicationId}/events`

Controller `ObservedEventController`, Swagger `ObservedEventSwagger`, use case
`ListObservedEventsUseCase`.

### `200 OK` — `PageResponseDTO<ObservedEventResponseDTO>`

```json
{
  "items": [
    { "name": "checkout.completed", "firstSeenAt": "2026-09-11T16:00:00Z", "lastSeenAt": "2026-09-12T13:45:00Z" }
  ],
  "page": 0, "size": 20, "total": 1, "totalPages": 1
}
```

Ordem: `lastSeenAt` desc, desempate por `name` asc. `total` conta o catálogo inteiro da aplicação.

---

## `GET /applications/{applicationId}/attributes`

Controller `ObservedAttributeController`, Swagger `ObservedAttributeSwagger`, use case
`ListObservedAttributesUseCase`.

### `200 OK` — `PageResponseDTO<ObservedAttributeResponseDTO>`

```json
{
  "items": [
    {
      "name": "plano",
      "firstSeenAt": "2026-09-12T12:31:00Z",
      "lastSeenAt": "2026-09-12T13:45:00Z",
      "values": [
        { "value": "free", "lastSeenAt": "2026-09-12T13:40:00Z" },
        { "value": "pro", "lastSeenAt": "2026-09-12T13:45:00Z" }
      ]
    }
  ],
  "page": 0, "size": 20, "total": 1, "totalPages": 1
}
```

Atributos na mesma ordem dos eventos; valores em ordem alfabética, no máximo 200 por atributo.

---

## Erros (as duas rotas)

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `request.invalid` | `page` < 0, `size` fora de `1..100` |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` |
| `404` | `application.not_found` | aplicação inexistente ou identificador malformado |

Aplicação inativa responde `200` normalmente.

---

## Escrita (efeito colateral de `POST /collect/eligibility`)

- Eventos: `007-application-management`.
- Atributos: cada par do objeto `attributes` da consulta vira nome e valor no catálogo, em
  transação própria, amortizado em 5 minutos, com limite de 200 valores distintos por atributo.
  Falha vira aviso no log, nunca erro ao SDK.
