# Contrato consumido — API administrativa de autoria

As rotas do backend que esta feature passa a usar, ou passa a usar com campos novos, como o
painel as enxerga. Base, superfície administrativa, envelope de página, recusa RFC 9457 e
cabeçalhos seguem o [contrato de 001](../../001-painel-operacao-pesquisas/contracts/backend-api.md):
nenhuma chave de aplicação é enviada, e toda chamada parte do servidor do painel.

Notação: `{a}` = `applicationId`, `{s}` = `surveyId`.

---

## Pesquisas

| Método e caminho | Uso no painel | Entrada usada | Saída usada |
|---|---|---|---|
| `POST /applications/{a}/surveys` | criar, em branco ou por modelo | `{ name, template? }`, `template` em `nps \| csat \| ces` | `201` `Survey` com `templateKind` |
| `POST /applications/{a}/surveys/{s}/duplicate` | duplicar | `{ targetApplicationId, name? }` | `201` `Survey` (com o `applicationId` de destino) |
| `PATCH /applications/{a}/surveys/{s}` | exposição | `{ priority, responseQuota \| null, ignoresQuietPeriod }` | `200` `Survey` |
| `PATCH /applications/{a}/surveys/{s}` | aviso de texto livre | `{ freeTextNoticeEnabled, freeTextNoticeText \| null }` | `200` `Survey` |
| `GET /applications/{a}/surveys/{s}` | montagem, disparo, cabeçalho | — | `200` `SurveyDetail` com `templateKind`, `priority`, `responseQuota`, `ignoresQuietPeriod`, `freeTextNotice`, `content.questions[].condition`, `range.minLabel/maxLabel` |
| `GET /applications/{a}/surveys/{s}/quota-progress` | progresso da cota | — | `200` `{ responseQuota?, completedResponses }` |

**Funções**: `createSurvey`, `duplicateSurvey`, `updateSurveyExposure`, `updateFreeTextNotice`,
`getSurvey`, `getQuotaProgress` (em `features/surveys/api/surveys.ts`).

**PATCH parcial**: campo ausente não muda; `null` remove. O painel envia `null` de propósito na
cota e no texto do aviso quando o campo fica em branco.

**Recusas tratadas**:

| Situação | Status | Tratamento |
|---|---|---|
| modelo desconhecido, nome inválido | `400` com `errors` | erro no campo, valores preservados |
| destino da duplicação inexistente ou inativo | `400`/`404`/`409` | mensagem no diálogo, sem navegar |
| prioridade ou cota fora da faixa | `400` | erro no campo (o painel já recusa antes) |

---

## Perguntas — condição e rótulos

| Método e caminho | Entrada acrescentada |
|---|---|
| `POST /applications/{a}/surveys/{s}/questions` | `range.minLabel?`, `range.maxLabel?`, `condition?` |
| `PUT /applications/{a}/surveys/{s}/questions/{q}` | idem |
| `PUT /applications/{a}/surveys/{s}/questions/order` | inalterado: `{ questionIds }` com a permutação completa — usado pelas setas e pelo arrastar |

```jsonc
// condição enviada
{ "sourceKey": "3f9a…", "operator": "between", "values": [], "min": 0, "max": 6 }
{ "sourceKey": "3f9a…", "operator": "equals", "values": ["nao"] }
```

**Recusa de condição**: `400` com `field` em `condition.sourceKey`, `condition.operator`,
`condition.values` ou `condition` (ex.: faixa fora da escala da origem, origem posterior, valor
que não é opção). O painel mostra a mensagem no campo de mesmo nome.

---

## Publicação

| Método e caminho | Uso no painel | Saída usada |
|---|---|---|
| `GET /applications/{a}/surveys/{s}/publication-warnings` | avisos na publicação e no disparo | `200` `{ warnings: PublicationWarning[] }` |
| `POST /applications/{a}/surveys/{s}/publication` | publicar (de 001) | `201` `SurveyVersion`; `422 survey_version.cosmetic_refused` quando a mudança declarada cosmética alterou a estrutura das perguntas |

`PublicationWarning`: `{ code, ruleId?, attribute?, competingSurveys[{ surveyId, name, priority }],
minRequiredVersion?, unsupportedShare? }`. Lista vazia ⇒ nenhum aviso; nenhum aviso bloqueia.

**Função**: `getPublicationWarnings` (em `features/surveys/api/publication.ts`).

---

## Catálogo observado

| Método e caminho | Uso no painel | Entrada | Saída usada |
|---|---|---|---|
| `GET /applications/{a}/events` | sugestões de evento | `page=0`, `size=100` | `PageResponse<{ name, firstSeenAt, lastSeenAt }>` |
| `GET /applications/{a}/attributes` | sugestões de atributo e valor | `page=0`, `size=100` | `PageResponse<{ name, firstSeenAt, lastSeenAt, values[{ value, lastSeenAt }] }>` |

**Funções**: `listObservedEvents`, `listObservedAttributes` (em `features/applications/api/index.ts`).

---

## Leituras auxiliares já existentes

| Consulta | Para quê | Onde |
|---|---|---|
| `GET /applications?status=active&page=0&size=100` | destinos da duplicação | `SurveyHeader` |
| `GET /applications/{a}` | política de descanso da aplicação (`quietPeriodDays`) | tela de disparo |

---

## Leituras por tela

| Tela | Leituras | Paralelas? |
|---|---|---|
| Montagem (`…/pesquisas/{s}`) | `getSurvey` (deduplicada com o cabeçalho) | — |
| Cabeçalho da pesquisa | `getSurvey`, `getTransitions`, `listApplications(active)` | sim |
| Disparo (`…/disparo`) | `getSurvey`, `listObservedEvents`, `listObservedAttributes`, `getApplication`, `getQuotaProgress`, `getPublicationWarnings` | sim |
| Publicação (`…/publicacao`) | `getSurvey`, `getPublicationImpediments`, `getPublicationWarnings` | sim |

---

## Revalidação após mutação

| Ação | O que refazer |
|---|---|
| criar por modelo | `redirect` para a montagem |
| duplicar | `revalidatePath` da listagem de pesquisas do destino + `redirect` para a montagem da cópia |
| salvar exposição, salvar aviso | `refresh()` |
| adicionar/editar pergunta com condição, reordenar por arrastar | `refresh()` |
| abrir rascunho de versão | `revalidatePath` do segmento da pesquisa + `redirect` para a montagem |
