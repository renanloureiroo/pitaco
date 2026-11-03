# Contrato HTTP — Saúde do SDK

**Feature**: `010-sdk-health` · Base: `/api`. Erros RFC 9457 com `code` e `traceId`.

---

## Superfície pública (`X-Pitaco-Key` obrigatório)

Cabeçalho recomendado `X-Pitaco-Sdk-Version` (semver, até 40; fora do formato é ignorado).

### `POST /collect/suppressions` — `CollectHealthController.suppress`

```json
{
  "surveyId": "…", "versionId": "…",
  "reason": "unknown_question_type",
  "questionTypes": ["matrix"], "features": [],
  "respondentReference": "u-8f1c",
  "deviceId": "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11"
}
```

| Campo | Regra |
| --- | --- |
| `surveyId`, `versionId` | obrigatórios, até 64 |
| `reason` | `unknown_question_type` ou `unsupported_feature` |
| `questionTypes`, `features` | até 20 itens de até 40 caracteres; só diagnóstico, não gravados |
| `respondentReference` | até 200; não gravada |
| `deviceId` | UUID; não gravado |

`202` sem corpo, gravando ou descartando. `400 request.invalid`, `401 api_key.missing|invalid`,
`429 rate_limit.exceeded`.

### `POST /collect/sdk-errors` — `CollectHealthController.report`

```json
{ "kind": "render_error", "message": "Tipo de pergunta sem renderizador: matrix",
  "context": { "questionType": "matrix", "stage": "render" }, "occurredAt": "2026-09-12T13:45:00Z" }
```

| Campo | Regra no envio | O que é guardado |
| --- | --- | --- |
| `kind` | até 40 | fora da lista vira `unknown` |
| `message` | até 4.000 | mascarada, até 500 |
| `context` | até 50 chaves | até 20 chaves, ~2 KB, sem dado pessoal |
| `occurredAt` | ISO 8601 | futuro ou ausente vira o recebimento |

`202` sem corpo (inclusive aplicação inativa, descartada). Balde próprio de 30/min por chave.
`400`, `401`, `429`.

---

## Superfície administrativa (`X-Pitaco-Key` → `403 api_key.forbidden_surface`)

### `GET /applications/{applicationId}/sdk-versions` — `SdkHealthController.versions`

```json
{
  "recentFrom": "2026-08-29",
  "recentRequests": 1840,
  "versions": [
    { "version": "1.2.0", "requestCount": 5120, "recentRequestCount": 1700, "recentShare": 0.924,
      "firstSeenAt": "2026-08-01T10:00:00Z", "lastSeenAt": "2026-09-12T13:45:00Z", "stale": false },
    { "version": "1.0.0", "requestCount": 900, "recentRequestCount": 0, "recentShare": 0.0,
      "firstSeenAt": "2026-07-01T10:00:00Z", "lastSeenAt": "2026-08-20T09:00:00Z", "stale": true }
  ]
}
```

Da versão mais nova para a mais antiga. `recentShare` ausente quando `recentRequests` é zero.
Erros: `403`, `404 application.not_found`.

### `GET /applications/{applicationId}/sdk-errors` — `SdkHealthController.errors`

| Parâmetro | Regra |
| --- | --- |
| `kind` | `render_error`, `network_error`, `malformed_response`, `storage_error`, `unknown` |
| `sdkVersion` | até 40, versão exata |
| `page` / `size` | `0` / `20`, `size` 1..100 |

`200` com `PageResponseDTO<SdkErrorReportResponseDTO>`; item: `id`, `sdkVersion`, `kind`, `message`,
`context`, `occurredAt`, `receivedAt`. Ordem `receivedAt` desc. Erros: `400`, `403`, `404`.

### `GET /applications/{applicationId}/surveys/{surveyId}/health` — `SdkHealthController.surveyHealth`

Parâmetros `from`, `to` (ISO 8601 UTC, inclusivos; sem eles, últimos 30 dias).

```json
{
  "from": "2026-08-13T00:00:00Z", "to": "2026-09-12T23:59:59Z",
  "displays": 95,
  "suppressions": {
    "total": 12,
    "byReason": [ { "reason": "unknown_question_type", "count": 12 }, { "reason": "unsupported_feature", "count": 0 } ],
    "bySdkVersion": [ { "sdkVersion": "0.9.0", "count": 12 } ]
  },
  "suppressionShare": 0.112,
  "relevant": true,
  "minRequiredVersion": "1.0.0",
  "eventName": "checkout.completed",
  "eventLastSeenAt": "2026-09-12T13:45:00Z"
}
```

`eventLastSeenAt` ausente quando o evento nunca chegou. Erros: `400` (período invertido), `403`,
`404 survey.not_found`.
