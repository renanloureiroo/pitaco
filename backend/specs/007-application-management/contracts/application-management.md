# Contrato HTTP — Aplicação: cadastro, edição e ativação

**Feature**: `007-application-management` · Base: `/api` (do `context-path`; a rota no controller
**não** o repete) · Tag OpenAPI: `Aplicações` · Controller `ApplicationController`, documentação em
`ApplicationControllerSwagger`.

Todas as rotas ficam sob `/applications/**`: o `AdminSurfaceInterceptor` recusa com
`403 api_key.forbidden_surface` qualquer requisição que apresente `X-Pitaco-Key`. Toda resposta de
erro é RFC 9457 (`application/problem+json`) com `code` e `traceId`, gerada só pelo
`ApiExceptionHandler`. Validação de entrada responde `400 request.invalid` com o mapa `errors`
(campo → mensagem); corpo ilegível responde `400 request.invalid` sem `errors`.

---

## `POST /applications`

Cadastrar uma aplicação (US1).

### Requisição — `CreateApplicationRequestDTO`

```json
{
  "name": "Acme App",
  "slug": "acme-app",
  "quietPeriodDays": 15,
  "retentionDays": 180,
  "openTextRetentionDays": 30
}
```

| Campo | Obrigatório | Regra |
| --- | --- | --- |
| `name` | sim | não vazio, até 120 |
| `slug` | não | até 50, `^[a-z0-9]+(-[a-z0-9]+)*$`; omitido, derivado do nome |
| `quietPeriodDays` | não | ≥ 1 |
| `retentionDays` | não | ≥ 1 |
| `openTextRetentionDays` | não | ≥ 1 e nunca maior que `retentionDays` |

### `201 Created` — `CreateApplicationResponseDTO`

Header `Location: /api/applications/{id}`.

```json
{ "id": "3f9a1c72-5d84-4a1e-9b0f-2c6e8d5a7b31", "slug": "acme-app" }
```

### Erros

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `request.invalid` | constraint violada ou JSON malformado |
| `400` | `application.slug_invalid` | nome que não produz slug derivável |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` presente (aplicado pelo interceptor; não declarado no Swagger desta rota) |
| `409` | `application.already_exists_with_same_slug` | slug já usado |
| `422` | `application.open_text_retention_invalid` | texto livre maior que o geral |

---

## `PATCH /applications/{applicationId}`

Editar nome e prazos (US2). Campo omitido não muda; prazo enviado como `null` é removido; o slug
não entra.

### Requisição — `UpdateApplicationRequestDTO` (lida por `UpdateApplicationRequestDeserializer`)

```json
{ "name": "Acme", "quietPeriodDays": null, "retentionDays": 90, "openTextRetentionDays": 60 }
```

| Campo | `null` | Regra do valor |
| --- | --- | --- |
| `name` | corpo malformado (`400`) | texto não vazio, até 120 |
| `quietPeriodDays` | remove | inteiro ≥ 1 |
| `retentionDays` | remove | inteiro ≥ 1 |
| `openTextRetentionDays` | volta a seguir o prazo geral | inteiro ≥ 1, nunca maior que o geral |

Corpo `{}` é aceito e não muda nada. Corpo que não é objeto JSON, ou prazo que não é inteiro, é
`400`.

### `200 OK` — `ApplicationResponseDTO`

```json
{
  "id": "3f9a1c72-5d84-4a1e-9b0f-2c6e8d5a7b31",
  "slug": "acme-app",
  "name": "Acme",
  "status": "active",
  "retentionDays": 90,
  "openTextRetentionDays": 60,
  "createdAt": "2026-09-05T14:32:10Z",
  "updatedAt": "2026-09-05T16:05:44Z"
}
```

Prazo não configurado **sai do JSON** (`@JsonInclude(NON_NULL)`), em vez de virar `null`.

### Erros

| Status | `code` | Quando |
| --- | --- | --- |
| `400` | `request.invalid` | constraint, `name: null`, tipo errado, JSON malformado |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` |
| `404` | `application.not_found` | inexistente ou identificador malformado |
| `422` | `application.retention_invalid` / `application.open_text_retention_invalid` | relação entre os prazos violada |

Nenhum caminho de erro grava qualquer campo do pedido.

---

## `POST /applications/{applicationId}/deactivate` · `POST /applications/{applicationId}/activate`

Desligar e religar (US3). Sem corpo. Idempotentes.

### `200 OK` — `ApplicationResponseDTO`

O estado resultante (`"status": "inactive"` ou `"active"`). Repetir a ação responde o mesmo corpo
e não avança `updatedAt`.

### Erros

| Status | `code` | Quando |
| --- | --- | --- |
| `403` | `api_key.forbidden_surface` | header `X-Pitaco-Key` |
| `404` | `application.not_found` | inexistente ou identificador malformado |

### Efeito na superfície pública (aplicação inativa)

| Rota pública | Resposta |
| --- | --- |
| `POST /collect/eligibility` | `200` com `survey` ausente |
| `POST /collect/displays` | `422 application.inactive` |
| `POST /collect/displays/{displayId}/submission` | `422 application.inactive` |
| `POST /collect/suppressions`, `POST /collect/sdk-errors` | `202`, descartado |
| `POST /collect/displays/{displayId}/events` | `202` com tudo em `discarded.displayUnavailable` |

---

## Efeito colateral: catálogo de eventos (US4)

Não há rota nova. `POST /collect/eligibility` de aplicação ativa grava o nome do evento em
`application_events` antes de procurar candidatas, numa transação própria. A leitura do catálogo é
`GET /applications/{applicationId}/events`, contratada na `008-exposure-control`.
