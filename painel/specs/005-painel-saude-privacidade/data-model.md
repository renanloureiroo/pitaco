# Phase 1 — Modelo de dados do painel

Tipos que atravessam a fronteira da API e viram domínio das features `health` e `privacy`. Todo
tipo nasce de um schema Zod; campo ausente vira `undefined` via `absent()`.

---

## Saúde (`features/health`)

### `SdkVersions`

| Campo | Tipo | Observação |
|---|---|---|
| `recentFrom` | `string` (ISO UTC) | início da janela recente |
| `recentRequests` | `number` | consultas na janela |
| `versions` | `SdkVersionUsage[]` | versão mais nova primeiro |

### `SdkVersionUsage`

| Campo | Tipo | Observação |
|---|---|---|
| `version` | `string` | exibida em fonte mono |
| `requestCount` | `number` | desde a primeira aparição |
| `recentRequestCount` | `number` | na janela recente |
| `recentShare` | `number?` | 0..1; **ausente ⇒ "—"** |
| `firstSeenAt`, `lastSeenAt` | `string` | |
| `stale` | `boolean` | verdadeiro ⇒ "Sumiu do tráfego" (mais de 14 dias sem consulta) |

### `SdkErrorReport`

| Campo | Tipo | Observação |
|---|---|---|
| `id` | `string` | |
| `sdkVersion` | `string?` | ausente ⇒ "versão não informada" |
| `kind` | `SdkErrorKind` | valor desconhecido vira `unknown` |
| `message` | `string` | vazio ⇒ "Sem mensagem." |
| `context` | `Record<string, unknown>` | malformado vira `{}` |
| `occurredAt`, `receivedAt` | `string` | |

| `kind` | Rótulo |
|---|---|
| `render_error` | Renderização |
| `network_error` | Rede |
| `malformed_response` | Resposta malformada |
| `storage_error` | Armazenamento |
| `unknown` | Desconhecido |

### Filtros de erros (URL)

| Param | Query da API | Regra |
|---|---|---|
| `tipo` | `kind` | só os cinco tipos; inválido é ignorado |
| `versao` | `sdkVersion` | aparada, até 40 caracteres |
| `page`, `size` | `page`, `size` | `parsePaginationParams` |

### `SurveyHealth`

| Campo | Tipo | Observação |
|---|---|---|
| `from`, `to` | `string` | janela apurada |
| `displays` | `number` | |
| `suppressions` | `{ total, byReason[{ reason, count }], bySdkVersion[{ sdkVersion?, count }] }` | |
| `suppressionShare` | `number?` | proporção das vezes em que seria exibida |
| `relevant` | `boolean` | o backend decide o limiar |
| `minRequiredVersion` | `string?` | versão mínima que desenharia a pesquisa |
| `eventName` | `string?` | evento do disparo publicado |
| `eventLastSeenAt` | `string?` | ausente com `eventName` presente ⇒ nunca chegou |

| Motivo de supressão | Texto |
|---|---|
| `unknown_question_type` | o SDK do app não conhece um tipo de pergunta usado nela |
| `unsupported_feature` | o SDK do app não suporta um recurso usado nela |
| outro | o SDK do app não sabe renderizá-la |

### `HealthNotice` — tipo interno

Produto de `surveyHealthNotices`: `{ kind: "suppression" | "event_missing", title, message }`.
Testids: `survey-health-suppression`, `survey-health-event-missing`.

---

## Privacidade (`features/privacy`)

### `RetentionPreview`

| Campo | Tipo | Observação |
|---|---|---|
| `configured` | `boolean` | falso ⇒ "nada é descartado automaticamente" |
| `answerRetentionDays` | `number?` | ausente ⇒ sem prazo, nunca zero |
| `textRetentionDays` | `number?` | idem |
| `nextRunAt` | `string?` | ausente com política ⇒ descarte desligado na instância |
| `nextRun` | `{ answers, texts }` | o que sai na próxima execução |
| `nextWeek` | `{ answers, texts }` | o que terá saído até uma semana depois |
| `lastRunAt` | `string?` | ausente ⇒ "Nenhum descarte aconteceu ainda" |
| `firstDiscardPending` | `boolean` | |

### `EraseRespondentInput` (formulário)

| Campo | Regra | Envio |
|---|---|---|
| `identityKind` | `device` ⇒ dispositivo; qualquer outro ⇒ `reference` | escolhe o parâmetro |
| `identity` | aparada, 1 a 200 caracteres | `reference=…` ou `deviceId=…` na query |

### `RespondentErasure`

| Campo | Tipo | Observação |
|---|---|---|
| `deleted` | `boolean` | falso ⇒ "Nada foi apagado" |
| `displaysDeleted`, `answersDeleted` | `number` | |

### `DeletionAudit`

| Campo | Tipo | Observação |
|---|---|---|
| `id` | `string` | |
| `displaysDeleted`, `answersDeleted` | `number` | exibidos |
| `performedBy` | `string?` | não exibido nesta entrega |
| `performedAt` | `string` | exibido no fuso de referência |

**Nenhum** campo carrega a identificação de quem foi excluído — a regra é do contrato, não só da
tela.

---

## Prazos da aplicação (`features/applications`)

### `UpdateApplicationForm`

| Campo | Regra | Envio |
|---|---|---|
| `name` | obrigatório | `name` |
| `quietPeriodDays` | inteiro ≥ 1 ou em branco | número ou **`null`** |
| `retentionDays` | inteiro ≥ 1 ou em branco | número ou **`null`** |
| `openTextRetentionDays` | inteiro ≥ 1 ou em branco | número ou **`null`** |

Texto livre com prazo maior que o geral é recusado pelo backend, não pelo painel.

---

## Nota de retenção (`features/results`)

`Retention` — `{ snapshotApplied, discardedBefore, note }`, presente em `SurveyResults` só quando a
retenção já descartou respostas daquela pesquisa. O texto exibido vem de `retentionNoteText`.
