# Contrato consumido — API administrativa de saúde e privacidade

Rotas do backend que esta feature consome, todas sob `/applications/{applicationId}/**`,
superfície administrativa: sem chave de aplicação, sempre a partir do servidor do painel.
Envelope de página e recusa RFC 9457 seguem o
[contrato de 001](../../001-painel-operacao-pesquisas/contracts/backend-api.md).

Notação: `{a}` = `applicationId`, `{s}` = `surveyId`.

---

## Saúde

| Método e caminho | Uso no painel | Entrada | Saída usada |
|---|---|---|---|
| `GET /applications/{a}/sdk-versions` | tabela de versões | — | `200` `{ recentFrom, recentRequests, versions[{ version, requestCount, recentRequestCount, recentShare?, firstSeenAt, lastSeenAt, stale }] }` |
| `GET /applications/{a}/sdk-errors` | lista de erros | `page`, `size`, `kind?`, `sdkVersion?` | `200` `PageResponse<{ id, sdkVersion?, kind, message, context, occurredAt, receivedAt }>` |
| `GET /applications/{a}/surveys/{s}/health` | avisos nos resultados | — | `200` `{ from, to, displays, suppressions{ total, byReason[], bySdkVersion[] }, suppressionShare?, relevant, minRequiredVersion?, eventName?, eventLastSeenAt? }` |

**Funções**: `getSdkVersions`, `listSdkErrors`, `getSurveyHealth` (em `features/health/api/health.ts`).

`kind` e `sdkVersion` só vão na query quando presentes.

---

## Privacidade

| Método e caminho | Uso no painel | Entrada | Saída usada |
|---|---|---|---|
| `GET /applications/{a}/retention-preview` | cartão da política | — | `200` `RetentionPreview` |
| `DELETE /applications/{a}/respondents` | exclusão | query `reference=…` **ou** `deviceId=…` | `200` `{ deleted, displaysDeleted, answersDeleted }` |
| `GET /applications/{a}/deletion-audits` | registro de exclusões | `page`, `size` | `200` `PageResponse<{ id, displaysDeleted, answersDeleted, performedBy?, performedAt }>` |

**Funções**: `getRetentionPreview`, `eraseRespondent`, `listDeletionAudits` (em
`features/privacy/api/privacy.ts`).

**Exclusão**: irreversível. Pedido para identificação inexistente devolve `deleted: false`, não
`404`, e não cria registro. Recusa de forma (`400`) volta ao formulário.

```jsonc
// GET /applications/{a}/retention-preview
{
  "configured": true,
  "answerRetentionDays": 30,
  "textRetentionDays": null,
  "nextRunAt": "2026-09-13T03:00:00Z",
  "nextRun": { "answers": 1, "texts": 1 },
  "nextWeek": { "answers": 1, "texts": 1 },
  "lastRunAt": null,
  "firstDiscardPending": true
}
```

---

## Prazos da aplicação

| Método e caminho | Uso no painel | Entrada | Saída |
|---|---|---|---|
| `PATCH /applications/{a}` | diálogo de edição | `{ name, quietPeriodDays \| null, retentionDays \| null, openTextRetentionDays \| null }` | `200` `Application` · `400`/`422` com motivo |

**Função**: `updateApplication` (em `features/applications/api/index.ts`). O PATCH distingue
ausente (não mexe) de `null` (remove); o painel envia `null` de propósito para o que ficou em
branco.

---

## Nota de retenção

Vem dentro do resultado de `GET …/surveys/{s}/results` (ver
[contrato de 004](../../004-painel-resultados-comportamento/contracts/backend-api.md)):
`retention { snapshotApplied, discardedBefore, note }`.

---

## Leituras por tela

| Tela | Leituras | Paralelas? |
|---|---|---|
| Saúde | `getSdkVersions`, `listSdkErrors` | sim |
| Privacidade | `getRetentionPreview`, `listDeletionAudits` | sim |
| Resultados (acréscimo) | `getSurveyHealth`, em paralelo com as leituras de 004 | sim |

## Revalidação após mutação

| Ação | O que refazer |
|---|---|
| excluir respondente | `refresh()` — o registro novo aparece na mesma tela |
| editar a aplicação | `refresh()` — o diálogo fecha em sucesso |
