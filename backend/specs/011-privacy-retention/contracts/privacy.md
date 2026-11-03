# Contrato HTTP — Privacidade

**Feature**: `011-privacy-retention` · Base: `/api` · Controller `PrivacyController`, Swagger
`PrivacySwagger`. Superfície administrativa: `X-Pitaco-Key` → `403 api_key.forbidden_surface`. Erros
RFC 9457 com `code` e `traceId`.

---

## `DELETE /applications/{applicationId}/respondents`

Exclusão do titular pela identidade opaca, na query. Exatamente um dos dois:

| Parâmetro | Regra |
| --- | --- |
| `reference` | referência do app, até 200 |
| `deviceId` | identificador do dispositivo, até 200 |

### `200 OK` — `RespondentErasureResponseDTO`

```json
{ "deleted": true, "displaysDeleted": 4, "answersDeleted": 11 }
```

Sem alvo: `{ "deleted": false, "displaysDeleted": 0, "answersDeleted": 0 }`, sem registro.

### Erros

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `request.invalid` | nenhuma identidade, as duas, ou acima de 200 |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` |
| `404` | `application.not_found` | aplicação inexistente ou malformada |

Nenhum caminho de erro apaga ou registra nada.

---

## `GET /applications/{applicationId}/deletion-audits`

`page` (padrão 0, ≥ 0) e `size` (padrão 20, 1..100).

### `200 OK` — `PageResponseDTO<DeletionAuditResponseDTO>`

```json
{
  "items": [ { "id": "d4…", "displaysDeleted": 4, "answersDeleted": 11, "performedAt": "2026-09-12T16:10:00Z" } ],
  "page": 0, "size": 20, "total": 1, "totalPages": 1
}
```

`performedBy` sai ausente enquanto não houver login. Ordem `performedAt` desc. Erros: `400`, `403`,
`404`.

---

## `GET /applications/{applicationId}/retention-preview`

### `200 OK` — `RetentionPreviewResponseDTO`

```json
{
  "configured": true,
  "answerRetentionDays": 180,
  "textRetentionDays": 30,
  "nextRunAt": "2026-09-13T03:47:00Z",
  "nextRun": { "answers": 120, "texts": 340 },
  "nextWeek": { "answers": 410, "texts": 980 },
  "lastRunAt": "2026-09-12T03:47:02Z",
  "firstDiscardPending": false
}
```

`textRetentionDays` é o prazo efetivo. Sem política: `configured: false`, `nextRunAt` ausente,
contagens zero. Descarte desligado: `nextRunAt` ausente, contagens sobre o agora. `texts` conta todos
os textos vencidos, inclusive os que saem junto com a resposta.

Erros: `403`, `404 application.not_found`.

---

## Job de descarte (sem rota)

`RetentionJob`, cron `pitaco.privacy.retention.cron` (padrão `0 47 3 * * *` UTC). Efeitos:

| Prazo | Efeito |
| --- | --- |
| geral | respostas respondidas antes do corte apagadas em lotes, com agregado congelado por versão |
| texto (efetivo) | `text_value` limpo, resposta mantida |

Registro em `retention_runs` só quando algo saiu. Leitura do agregado em
`012-survey-results` (`retention` na resposta dos resultados).
