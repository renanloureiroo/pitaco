# Contrato — superfície pública do SDK

Prefixo de rota: `/collect` (o `/api` vem do `context-path`, nunca da rota).
Autenticação: header `X-Pitaco-Key` com a chave exatamente como emitida (`pit_<prefixo>_<segredo>`).
A aplicação é derivada **da chave** — nenhuma operação pública aceita `applicationId` como
parâmetro (FR-002).

Toda resposta de erro é RFC 9457 (`application/problem+json`) com `code` e `traceId`, gerada
exclusivamente pelo `ApiExceptionHandler`.

---

## 1. `POST /collect/eligibility` — há pesquisa para este respondente agora?

**Request**

```json
{
  "event": "checkout_concluido",
  "respondent": { "reference": "u-8f1c", "deviceId": "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11" },
  "attributes": { "plano": "premium", "pais": "BR" }
}
```

| Campo | Obrigatório | Regra |
| --- | --- | --- |
| `event` | sim | ≤ 80 caracteres; comparação exata com o evento da versão publicada |
| `respondent.reference` | não | referência opaca do app hospedeiro; ≤ 200 caracteres; prevalece quando presente |
| `respondent.deviceId` | não | UUID gerado pelo SDK; obrigatório na ausência de `reference` |
| `attributes` | não | até 50 pares; nome ≤ 80, valor ≤ 200 |

**200 — com pesquisa**

```json
{
  "survey": {
    "surveyId": "…",
    "versionId": "…",
    "versionNumber": 3,
    "questions": [
      {
        "key": "…",
        "position": 1,
        "statement": "O que achou do checkout?",
        "type": "SINGLE_CHOICE",
        "required": true,
        "options": [{ "value": "bom", "label": "Bom", "position": 1 }],
        "range": null
      },
      {
        "key": "…",
        "position": 2,
        "statement": "De 0 a 10, quanto recomendaria?",
        "type": "NPS",
        "required": false,
        "options": [],
        "range": { "min": 0, "max": 10 }
      }
    ]
  }
}
```

A versão vai **inteira em uma resposta só** (FR-019): o SDK renderiza sem segunda chamada.
Conteúdo de versão em rascunho nunca aparece aqui (FR-021, SC-004).

**200 — sem pesquisa** (o caso mais frequente; não é erro, e nada é gravado — FR-020, SC-003)

```json
{ "survey": null }
```

**Erros**

| Status | `code` | Quando |
| --- | --- | --- |
| 400 | `request.invalid` | corpo malformado ou constraint de DTO violada |
| 401 | `api_key.missing` | header ausente |
| 401 | `api_key.invalid` | chave desconhecida ou revogada — a mesma resposta para os dois, sem revelar nada (US1.6) |

**Nunca é erro**: aplicação inativa, pesquisa pausada, fora da janela, evento sem pesquisa,
regra não satisfeita, não sorteado, já resolvida. Todos devolvem `{"survey": null}`.

**Desempate** quando mais de uma pesquisa sobrevive (FR-018): vence a de `published_at` mais
antigo; empate desfeito pelo `surveyId` em ordem lexicográfica. A mesma consulta repetida devolve
sempre a mesma pesquisa.

---

## 2. `POST /collect/displays` — abrir a exibição

A abertura é **ato explícito do SDK** (FR-024): pesquisa descartada por incompatibilidade de
renderização não vira exibição e não estraga a taxa de resposta.

**Request**

```json
{
  "displayId": "3b1f0a2c-6c9a-4a1e-9d0b-2c1f7a3e5d90",
  "surveyId": "…",
  "versionId": "…",
  "respondent": { "reference": "u-8f1c", "deviceId": "…" },
  "attributes": { "plano": "premium" },
  "sdkVersion": "1.4.2"
}
```

`displayId` é gerado no dispositivo e é a chave de idempotência (FR-023).

**201 Created** + `Location: /api/collect/displays/{displayId}`

```json
{ "displayId": "…", "surveyId": "…", "versionId": "…", "outcome": "STARTED", "openedAt": "2026-09-08T18:22:31Z" }
```

**200 OK** — mesmo corpo, quando a mesma abertura chega de novo com pesquisa e versão idênticas
(reenvio da fila local; nenhuma segunda exibição é criada).

**Erros**

| Status | `code` | Quando |
| --- | --- | --- |
| 400 | `request.invalid` | corpo malformado, `displayId` que não é UUID, respondente sem nenhuma identificação |
| 401 | `api_key.missing` / `api_key.invalid` | autenticação |
| 404 | `survey_version.not_found` | versão inexistente, em rascunho, ou de outra aplicação — os três indistinguíveis (SC-012) |
| 409 | `display.identifier_conflict` | `displayId` já usado para outra pesquisa ou outra versão |
| 422 | `application.inactive` | a aplicação da chave está inativa (D-19) |

---

## 3. `POST /collect/displays/{displayId}/submission` — respostas e desfecho

Um ato só, atômico: as respostas e o desfecho chegam juntos, o envio é validado **inteiro** antes
de qualquer gravação, e uma recusa deixa zero linhas (FR-034, SC-008).

**Request**

```json
{
  "outcome": "COMPLETED",
  "answers": [
    { "questionKey": "…", "status": "ANSWERED", "value": "bom" },
    { "questionKey": "…", "status": "ANSWERED", "value": 9 },
    { "questionKey": "…", "status": "ANSWERED", "value": ["a", "b"] },
    { "questionKey": "…", "status": "ANSWERED", "value": "achei o frete caro" },
    { "questionKey": "…", "status": "SKIPPED" }
  ]
}
```

| `type` da pergunta | forma de `value` |
| --- | --- |
| `SINGLE_CHOICE` | string, uma opção declarada |
| `MULTIPLE_CHOICE` | array de strings, não vazio, sem repetição, todas declaradas |
| `RATING`, `SCALE`, `NPS` | inteiro dentro da faixa da pergunta |
| `FREE_TEXT` | string de até 2000 caracteres |
| qualquer, `status = "SKIPPED"` | `value` ausente |

`outcome` é `COMPLETED` ou `DISMISSED`. `COMPLETED` exige resposta `ANSWERED` para **toda**
pergunta obrigatória; `DISMISSED` aceita o parcial e preserva o que já veio (FR-037, SC-011).

**204 No Content** — gravado, ou reconhecido como reenvio idêntico de um envio já gravado
(FR-036, US2.2).

**Erros**

| Status | `code` | Quando |
| --- | --- | --- |
| 400 | `request.invalid` | corpo malformado, `outcome` desconhecido |
| 401 | `api_key.missing` / `api_key.invalid` | autenticação |
| 404 | `display.not_found` | exibição inexistente ou de outra aplicação — indistinguíveis (US2.8) |
| 409 | `display.already_closed` | exibição já fechada e o envio traz desfecho diferente ou resposta nova (US3.2, US3.3) |
| 422 | `submission.rejected` | qualquer problema de conteúdo; ver abaixo |
| 422 | `application.inactive` | aplicação inativa (D-19) |

**Aceito mesmo assim** (FR-038, SC-014): pesquisa pausada, encerrada, republicada ou com a janela
fechada entre a abertura e o envio. A resposta continua apontando a versão exibida.

**Corpo de `submission.rejected`** — todos os problemas de uma vez (FR-035, SC-007):

```json
{
  "type": "about:blank",
  "title": "Unprocessable Content",
  "status": 422,
  "detail": "O envio tem respostas que a versão exibida não aceita",
  "instance": "/api/collect/displays/3b1f…/submission",
  "code": "submission.rejected",
  "traceId": "c21f86e5471ac0e39a4cf1d3ce080c61",
  "errors": [
    { "questionKey": "…", "code": "answer.required_missing" },
    { "questionKey": "…", "code": "answer.option_unknown" },
    { "questionKey": "…", "code": "answer.value_out_of_range" }
  ]
}
```

Códigos possíveis em `errors[].code`: `answer.question_unknown`, `answer.question_duplicated`,
`answer.required_missing`, `answer.value_missing`, `answer.value_type_mismatch`,
`answer.option_unknown`, `answer.options_empty`, `answer.options_duplicated`,
`answer.value_out_of_range`, `answer.text_too_long`.

Nunca há recusa genérica do tipo "respostas inválidas" (SC-007).

---

## 4. Superfície administrativa

As rotas administrativas (`/applications/**`) **recusam** requisição que apresente
`X-Pitaco-Key`, com `403 api_key.forbidden_surface` (FR-003, US1.14). A chave do SDK não vale no
painel, e apresentá-la lá falha barulhento em vez de ser ignorada em silêncio.

---

## 5. Privacidade no contrato

Nenhum campo desta superfície é pessoalmente identificável, e nada do que chega nela vai para
log: nem `respondent.reference`, nem `deviceId`, nem `attributes`, nem o conteúdo das respostas
(FR-008, FR-039, SC-013). O log registra o campo e o motivo, jamais o valor.
