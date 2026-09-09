# Contrato HTTP — Coleta: exibições, respostas e respondentes

**Feature**: `006-collection-read-list` · Base: `/api` (do `context-path`; a rota declarada no
controller **não** o repete) · Tags OpenAPI: `Exibições`, `Respondentes` (novas)

As quatro rotas ficam sob `/applications/**` e por isso já são superfície administrativa: o
`AdminSurfaceInterceptor` recusa com `403 api_key.forbidden_surface` toda requisição que apresente
`X-Pitaco-Key` (D-01). Toda resposta de erro é RFC 9457 (`application/problem+json`), montada só
pelo `ApiExceptionHandler`, com `code` e `traceId`.

Todas são `GET`. Nenhuma altera estado, em nenhum caminho — inclusive nos de recusa (FR-027).

---

## `GET /applications/{applicationId}/surveys/{surveyId}/displays`

Listar as exibições de uma pesquisa (US1). Controller `SurveyDisplayController`.

**Parâmetros de rota**: `applicationId`, `surveyId`.

**Parâmetros de consulta**

| Nome | Tipo | Obrigatório | Padrão | Regra |
| --- | --- | --- | --- | --- |
| `versionId` | string | não | ausente = todas | identificador de versão |
| `outcome` | string | não | ausente = todos | `STARTED`, `COMPLETED` ou `DISMISSED` (D-11) |
| `openedFrom` | date-time | não | ausente = sem limite inferior | ISO-8601 UTC, **inclusive** |
| `openedTo` | date-time | não | ausente = sem limite superior | ISO-8601 UTC, **inclusive** |
| `page` | integer | não | `0` | `>= 0` |
| `size` | integer | não | `20` | `1..100` |

`openedFrom` posterior a `openedTo` é 400 (D-12). `ABANDONED` não é valor aceito, porque nunca é
gravado.

### `200 OK` — `PageResponseDTO<DisplaySummaryResponseDTO>`

```json
{
  "items": [
    {
      "id": "d1f0a3c8-77b2-4e19-9a5c-0b3e6f8d2a41",
      "versionId": "8c2b5e14-3a97-4d60-b1f8-5e7c9a0d4b62",
      "versionNumber": 3,
      "comparabilityGroup": 2,
      "outcome": "COMPLETED",
      "sdkVersion": "1.4.0",
      "openedAt": "2026-09-08T14:22:31Z",
      "closedAt": "2026-09-08T14:23:07Z"
    },
    {
      "id": "a7e4b210-9c31-4f88-8d02-6b1a5c7e3f94",
      "versionId": "8c2b5e14-3a97-4d60-b1f8-5e7c9a0d4b62",
      "versionNumber": 3,
      "comparabilityGroup": 2,
      "outcome": "STARTED",
      "sdkVersion": null,
      "openedAt": "2026-09-08T14:19:02Z",
      "closedAt": null
    }
  ],
  "page": 0,
  "size": 20,
  "total": 2,
  "totalPages": 1
}
```

`closedAt` é `null` na exibição ainda aberta e presente na fechada — presente se e somente se o
desfecho é final (FR-003). `sdkVersion` é `null` quando não informado (FR-013). Ordenação:
`openedAt` desc, desempate por `id` desc.

### Erros

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `validation.failed` | paginação fora de `0`/`1..100`, `outcome` desconhecido, instante malformado, `openedFrom` > `openedTo` |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` presente |
| `404` | `survey.not_found` | pesquisa inexistente **ou** de outra aplicação (FR-010, FR-034) |

Pesquisa que existe e nunca foi exibida devolve `200` com `items: []` e `total: 0` — não `404`.

---

## `GET /applications/{applicationId}/displays/{displayId}`

Consultar uma exibição com suas respostas (US2). Controller `DisplayController`.

Rota plana sob a aplicação, não aninhada na pesquisa: o identificador de exibição é chave
primária global e a exibição já determina sua pesquisa (D-09).

### `200 OK` — `DisplayDetailResponseDTO`

```json
{
  "id": "d1f0a3c8-77b2-4e19-9a5c-0b3e6f8d2a41",
  "respondentId": "b5c9e740-1a83-4f2d-9e06-7c4b1a8f3d25",
  "surveyId": "6e1d9b30-4c72-4a85-b3f1-9d0a2c8e5f47",
  "versionId": "8c2b5e14-3a97-4d60-b1f8-5e7c9a0d4b62",
  "versionNumber": 3,
  "comparabilityGroup": 2,
  "outcome": "COMPLETED",
  "sdkVersion": "1.4.0",
  "openedAt": "2026-09-08T14:22:31Z",
  "closedAt": "2026-09-08T14:23:07Z",
  "attributes": {
    "plan": "pro",
    "locale": "pt-BR"
  },
  "answers": [
    {
      "questionKey": "nps",
      "status": "ANSWERED",
      "number": 9,
      "text": null,
      "options": []
    },
    {
      "questionKey": "favorite_feature",
      "status": "ANSWERED",
      "number": null,
      "text": null,
      "options": ["reports", "alerts"]
    },
    {
      "questionKey": "what_could_improve",
      "status": "EXPIRED",
      "number": null,
      "text": null,
      "options": []
    },
    {
      "questionKey": "contact_ok",
      "status": "SKIPPED",
      "number": null,
      "text": null,
      "options": []
    }
  ]
}
```

**As três situações de resposta são distinguíveis** (FR-016, FR-028):

| `status` | Significado | Valor |
| --- | --- | --- |
| `ANSWERED` | respondida | um dos três campos preenchido conforme o tipo da pergunta |
| `SKIPPED` | a pessoa pulou | nenhum campo preenchido |
| `EXPIRED` | respondeu em texto livre, e o prazo de retenção de texto da aplicação já venceu | nenhum campo preenchido |

`answers` vem na ordem das perguntas da versão exibida (FR-015). Exibição ainda aberta devolve
`answers: []` e `closedAt: null` — não erro (FR-017). Exibição dispensada devolve o desfecho e
nenhuma resposta.

### Erros

| Status | `code` | Quando |
| --- | --- | --- |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` presente |
| `404` | `display.not_found` | exibição inexistente **ou** de outra aplicação (FR-018, FR-034) |

---

## `GET /applications/{applicationId}/respondents`

Listar os respondentes de uma aplicação (US3). Controller `RespondentController`.

**Parâmetros de consulta**

| Nome | Tipo | Obrigatório | Padrão | Regra |
| --- | --- | --- | --- | --- |
| `page` | integer | não | `0` | `>= 0` |
| `size` | integer | não | `20` | `1..100` |

### `200 OK` — `PageResponseDTO<RespondentResponseDTO>`

```json
{
  "items": [
    {
      "id": "b5c9e740-1a83-4f2d-9e06-7c4b1a8f3d25",
      "identityKind": "APP_REFERENCE",
      "identityValue": "user-8821",
      "firstSeenAt": "2026-08-30T09:11:00Z",
      "lastSeenAt": "2026-09-08T14:22:31Z"
    },
    {
      "id": "1c8f2a56-7d40-4b93-a2e5-3f6b9c0d8e17",
      "identityKind": "DEVICE",
      "identityValue": "9f3c1b7a-4e28-4d05-8a61-2b7f0c9e5d34",
      "firstSeenAt": "2026-09-07T18:40:12Z",
      "lastSeenAt": "2026-09-07T18:44:55Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 2,
  "totalPages": 1
}
```

Ordenação: `lastSeenAt` desc, desempate por `id` desc. `identityValue` aparece no corpo e
**nunca em log** (FR-030).

### Erros

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `validation.failed` | paginação fora dos limites |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` presente |
| `404` | `application.not_found` | aplicação inexistente (FR-023) |

---

## `GET /applications/{applicationId}/respondents/{respondentId}/displays`

Listar as exibições de um respondente (US4). Controller `RespondentController`, mesmo prefixo.

**Parâmetros de consulta**: `outcome`, `openedFrom`, `openedTo`, `page`, `size` — idênticos aos da
listagem por pesquisa, sem `versionId` (FR-025).

### `200 OK` — `PageResponseDTO<RespondentDisplayResponseDTO>`

Mesmo corpo de `DisplaySummaryResponseDTO` mais `surveyId` (FR-024):

```json
{
  "items": [
    {
      "id": "d1f0a3c8-77b2-4e19-9a5c-0b3e6f8d2a41",
      "surveyId": "6e1d9b30-4c72-4a85-b3f1-9d0a2c8e5f47",
      "versionId": "8c2b5e14-3a97-4d60-b1f8-5e7c9a0d4b62",
      "versionNumber": 3,
      "comparabilityGroup": 2,
      "outcome": "COMPLETED",
      "sdkVersion": "1.4.0",
      "openedAt": "2026-09-08T14:22:31Z",
      "closedAt": "2026-09-08T14:23:07Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1,
  "totalPages": 1
}
```

### Erros

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `validation.failed` | paginação, `outcome` desconhecido, período invertido |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` presente |
| `404` | `respondent.not_found` | respondente inexistente **ou** de outra aplicação (FR-026, FR-034) |

---

## Invariantes de contrato válidas nas quatro rotas

1. **Nada de segredo**: nenhuma resposta e nenhuma mensagem de erro carrega segredo de chave de
   acesso, em texto claro ou hash (FR-029).
2. **Nada em log**: `identityValue` e conteúdo de resposta nunca vão para log; registra-se campo e
   motivo (FR-030).
3. **Existência alheia não é revelada**: recurso de outra aplicação recusa como inexistente,
   nunca como proibido — `404`, nunca `403` (FR-034).
4. **Página além do fim** é `200` com `items: []` e o `total` correto (FR-008).
5. **Toda recusa tem `code` estável**, distinto por motivo, tratável sem ler a mensagem (FR-031).
6. **Todo status declarado no OpenAPI**, inclusive `400`, `403` e `404`, com o schema de erro
   correspondente, em interface `*Swagger` que o controller implementa.
