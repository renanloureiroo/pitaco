# Contrato HTTP — Exposição, cota e histórico

**Feature**: `008-exposure-control` · Base: `/api` · Superfície administrativa (`/applications/**`):
o `AdminSurfaceInterceptor` recusa `X-Pitaco-Key` com `403 api_key.forbidden_surface`. Erros em
RFC 9457 com `code` e `traceId`.

---

## `PATCH /applications/{applicationId}/surveys/{surveyId}` — campos de exposição

Controller `SurveyController.update`, Swagger `SurveyControllerSwagger`. A mesma rota carrega
`name` e os campos do aviso de texto livre (`009-advanced-authoring`); aqui ficam os de exposição.
Lida por `UpdateSurveyRequestDeserializer`: ausente não mexe; `responseQuota: null` remove; os
demais recusam `null`.

```json
{ "priority": 10, "responseQuota": 100, "ignoresQuietPeriod": false }
```

| Campo | Tipo | Regra | `null` |
| --- | --- | --- | --- |
| `priority` | inteiro | `-100..100` | corpo malformado |
| `responseQuota` | inteiro | `>= 1` | remove a cota |
| `ignoresQuietPeriod` | booleano | — | corpo malformado |

Corpo `{}` responde `200` e não muda nada.

### `200 OK` — `SurveyResponseDTO`

```json
{
  "id": "8f1c…",
  "applicationId": "3f9a…",
  "name": "NPS pós-entrega",
  "state": "active",
  "publishedVersionNumber": 2,
  "priority": 10,
  "responseQuota": 100,
  "ignoresQuietPeriod": false,
  "freeTextNotice": { "enabled": true, "text": "Evite escrever dados pessoais, como nome, telefone ou e-mail.", "defaultText": "Evite escrever dados pessoais, como nome, telefone ou e-mail." },
  "createdAt": "2026-09-12T12:30:29Z"
}
```

Se a cota definida nesta chamada já foi alcançada e a pesquisa está publicada e não encerrada, a
resposta já traz `"state": "ended"` e o histórico ganha uma transição `quota_reached`.

### Erros

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `request.invalid` | prioridade fora da faixa, cota < 1, tipo errado, `null` em campo não removível, JSON malformado |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` (aplicado pelo interceptor; não declarado no Swagger da pesquisa) |
| `404` | `survey.not_found` | pesquisa inexistente, malformada ou de outra aplicação |

---

## `GET /applications/{applicationId}/surveys/{surveyId}/quota-progress`

Controller `QuotaProgressController`, Swagger `QuotaProgressSwagger`.

### `200 OK` — `QuotaProgressResponseDTO`

```json
{ "responseQuota": 100, "completedResponses": 42 }
```

`responseQuota` ausente quando não há cota. `completedResponses` conta todas as versões e pode
passar um pouco da cota (sessões já abertas concluem).

| Status | `code` | Quando |
| --- | --- | --- |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` |
| `404` | `application.not_found` / `survey.not_found` | aplicação malformada / pesquisa fora do escopo |

---

## `GET /applications/{applicationId}/surveys/{surveyId}/transitions` — motivo de cota

Rota da `003`. A lista (sem paginação, ordenada por instante) passa a poder conter:

```json
{ "from": "active", "to": "ended", "reason": "quota_reached", "actor": null, "occurredAt": "2026-09-12T15:02:11Z" }
```

`reason` ∈ `publication`, `manual_pause`, `manual_resume`, `manual_end`, `quota_reached`,
`window_opened`, `window_closed`. `actor` é sempre ausente até haver autenticação do painel.

---

## Efeito na superfície pública

| Rota | Mudança |
| --- | --- |
| `POST /collect/eligibility` | desempate por prioridade; descanso aplicado; candidatas isentas furam o descanso |
| `POST /collect/displays/{displayId}/submission` | conclusão que atinge a cota encerra a pesquisa na mesma transação; a resposta continua `204` |
