# Contrato consumido — API administrativa do Pitaco

**Base**: `PITACO_API_URL` (já inclui o `context-path` `/api` do backend, ex.
`http://localhost:8080/api`). Todos os caminhos abaixo são relativos a essa base.

**Superfície**: tudo sob `/applications/**` é superfície administrativa. O `AdminSurfaceInterceptor`
do backend **recusa com 403 `api_key.forbidden_surface`** qualquer requisição que apresente o
header de chave de aplicação. O painel nunca envia esse header; se um 403 desse código aparecer, é
defeito do painel, não algo a pedir ao usuário.

**Erros**: toda recusa vem em RFC 9457 (`application/problem+json`) com `code` estável e
`traceId`. 400 acrescenta `errors: Record<campo, mensagem>`. O mapeamento para o resultado tipado
do painel está em [data-model.md](../data-model.md#apierror--resultado-de-fronteira).

**Cabeçalhos enviados pelo painel**: `Accept: application/json`, e `Content-Type: application/json`
nas escritas. Nada mais.

**Cache**: nenhuma leitura é cacheada. `fetch` do Next 16 já não cacheia por padrão; nenhuma
função de leitura do painel usa `use cache`.

---

## Aplicações

| Método e caminho | Uso no painel | Entrada | Saída |
|---|---|---|---|
| `GET /applications` | listagem em `/aplicacoes` | query `status?`, `page=0`, `size=20` | `200` `PageResponse<ApplicationSummary>` |
| `GET /applications/{applicationId}` | detalhe e breadcrumb | — | `200` `Application` · `404 application.not_found` |
| `POST /applications` | formulário em `/aplicacoes/nova` | `CreateApplicationForm` | `201` `{ id, slug }` + `Location` · `400` validação · `409` slug em conflito |

Ordenação da listagem: `createdAt` decrescente, desempate por `id` decrescente. `404` cobre
identificador inexistente **e** malformado — o painel trata os dois com a mesma tela.

**Funções do painel** (`features/applications/api`): `listApplications`, `getApplication`,
`createApplication`.

---

## Chaves de acesso

| Método e caminho | Uso no painel | Entrada | Saída |
|---|---|---|---|
| `GET /applications/{applicationId}/api-keys` | listagem em `/aplicacoes/{id}/chaves` | query `status?` (`active`/`revoked`), `page`, `size` | `200` `PageResponse<ApiKey>` |
| `GET /applications/{applicationId}/api-keys/{apiKeyId}` | detalhe da chave | — | `200` `ApiKey` · `404` |
| `POST /applications/{applicationId}/api-keys` | emissão | `{ label }` | `201` `IssuedApiKey` **(único lugar com `secret`)** |
| `DELETE /applications/{applicationId}/api-keys/{apiKeyId}` | revogação | — | `204` · `404` · `409` já revogada |

**Funções**: `listApiKeys`, `getApiKey`, `issueApiKey`, `revokeApiKey`.

**Regra crítica**: `IssuedApiKey.secret` só existe na resposta do `POST`. Nenhuma outra operação o
devolve; nenhum tipo de leitura tem o campo. O ciclo de vida dele dentro do painel está em
[data-model.md](../data-model.md#issuedapikey--devolvido-apenas-pela-emissão).

---

## Pesquisas

| Método e caminho | Uso no painel | Entrada | Saída |
|---|---|---|---|
| `POST /applications/{aid}/surveys` | criar em `/pesquisas/nova` | `{ name }` | `201` `Survey` |
| `GET /applications/{aid}/surveys` | listagem | query `page`, `size` | `200` `PageResponse<Survey>` |
| `GET /applications/{aid}/surveys/{sid}` | detalhe/montagem | — | `200` `SurveyDetail` · `404` |
| `PATCH /applications/{aid}/surveys/{sid}` | renomear | `{ name }` | `200` `Survey` |
| `DELETE /applications/{aid}/surveys/{sid}` | descartar rascunho nunca publicado | — | `204` · `409` se já publicada |

**Funções**: `createSurvey`, `listSurveys`, `getSurvey`, `renameSurvey`, `discardSurvey`.

O painel só oferece "descartar" quando `publishedVersionNumber` está ausente (FR-020); o `409` do
backend continua sendo tratado caso o estado mude entre a leitura e a ação.

---

## Perguntas

| Método e caminho | Uso no painel | Entrada | Saída |
|---|---|---|---|
| `POST /applications/{aid}/surveys/{sid}/questions` | adicionar | `AddQuestion` | `201` `Question` |
| `PUT /applications/{aid}/surveys/{sid}/questions/{qid}` | editar | `UpdateQuestion` | `200` `Question` |
| `DELETE /applications/{aid}/surveys/{sid}/questions/{qid}` | remover | — | `204` |
| `PUT /applications/{aid}/surveys/{sid}/questions/order` | reordenar | `{ questionIds: string[] }` | `200` `Question[]` |

`AddQuestion` / `UpdateQuestion`: `{ statement, type, required?, options?, range? }`, com `type` em
`single_choice | multiple_choice | rating | scale | nps | free_text`.

`questionIds` precisa ser a **permutação exata** dos identificadores das perguntas da versão — o
painel monta a lista completa a partir do estado que está exibindo, nunca um subconjunto.

Editar não muda a `key` estável nem a `position`: reescrever é a operação, não substituir.

**Funções**: `addQuestion`, `updateQuestion`, `removeQuestion`, `reorderQuestions`.

---

## Disparo e regras de segmentação

| Método e caminho | Uso no painel | Entrada | Saída |
|---|---|---|---|
| `PUT /applications/{aid}/surveys/{sid}/trigger` | definir/redefinir | `{ eventName, windowStart, windowEnd?, samplingRate? }` | `200` `Trigger` |
| `POST /applications/{aid}/surveys/{sid}/trigger/rules` | adicionar regra | `{ attribute, operation, value? }` | `201` `SegmentationRule` |
| `DELETE /applications/{aid}/surveys/{sid}/trigger/rules/{ruleId}` | remover regra | — | `204` |

`PUT` é idempotente por definição — redefinir substitui, não duplica (FR-025).

`operation` em `equals | not_equals | present | absent`; `value` exigido nas duas primeiras.

**Funções**: `defineTrigger`, `addSegmentationRule`, `removeSegmentationRule`.

---

## Publicação

| Método e caminho | Uso no painel | Entrada | Saída |
|---|---|---|---|
| `GET /applications/{aid}/surveys/{sid}/publication-impediments` | tela de publicação | — | `200` `{ impediments: PublicationImpediment[] }` |
| `POST /applications/{aid}/surveys/{sid}/publication` | publicar | `{ changeKind?, changeSummary? }` | `201` `SurveyVersion` · `409` com impedimentos |

Lista de impedimentos vazia ⇒ publicação liberada. É a mesma lista que a publicação usaria para
recusar, então a tela nunca mostra "pode publicar" e recebe recusa por outro motivo de conteúdo.

`changeKind` é obrigatório a partir da versão 2 e ignorado na versão 1.

**Funções**: `getPublicationImpediments`, `publishSurvey`.

---

## Versões

| Método e caminho | Uso no painel | Entrada | Saída |
|---|---|---|---|
| `GET /applications/{aid}/surveys/{sid}/versions` | listagem em `/versoes` | query `page`, `size` | `200` `PageResponse<SurveyVersion>` |
| `GET /applications/{aid}/surveys/{sid}/versions/{number}` | conteúdo congelado | — | `200` `SurveyVersionDetail` · `404` |
| `POST /applications/{aid}/surveys/{sid}/versions` | abrir nova versão de rascunho | — | `201` `SurveyVersion` |
| `DELETE /applications/{aid}/surveys/{sid}/versions/draft` | descartar rascunho de versão | — | `204` |
| `GET /applications/{aid}/surveys/{sid}/versions/comparability` | comparabilidade | — | `200` `VersionComparability` |

**Funções**: `listVersions`, `getVersion`, `openDraftVersion`, `discardDraftVersion`,
`getVersionComparability`.

---

## Ciclo de vida

| Método e caminho | Uso no painel | Saída |
|---|---|---|
| `GET /applications/{aid}/surveys/{sid}/transitions` | quais ações oferecer + histórico | `200` `SurveyStateTransition[]` |
| `POST /applications/{aid}/surveys/{sid}/pause` | pausar | `200` `Survey` |
| `POST /applications/{aid}/surveys/{sid}/resume` | retomar | `200` `Survey` |
| `POST /applications/{aid}/surveys/{sid}/end` | encerrar (irreversível) | `200` `Survey` |

**Funções**: `getTransitions`, `pauseSurvey`, `resumeSurvey`, `endSurvey`.

O painel oferece **apenas** as transições que esta leitura autoriza — nunca as deriva do estado
(FR-035).

---

## Fora do contrato desta feature

- `/collect/**` — superfície pública de coleta, consumida pelo SDK. Fora de escopo por decisão.
- Leitura de respostas ou resultados agregados — **não existe endpoint**. Nenhuma tela do painel
  exibe resultado nesta entrega.

---

## Revalidação após mutação

| Ação | O que refazer |
|---|---|
| criar aplicação | `redirect` para o detalhe |
| emitir / revogar chave | `refresh()` |
| criar pesquisa | `redirect` para a montagem |
| renomear / descartar pesquisa | `revalidatePath` da listagem de pesquisas |
| adicionar/editar/remover/reordenar pergunta | `refresh()` |
| definir disparo, adicionar/remover regra | `refresh()` |
| publicar, abrir/descartar versão | `revalidatePath` do segmento da pesquisa (afeta cabeçalho, versões e publicação) |
| pausar / retomar / encerrar | `revalidatePath` do segmento da pesquisa |
