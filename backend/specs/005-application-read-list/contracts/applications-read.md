# Contrato HTTP — Aplicações: listagem e consulta

**Feature**: `005-application-read-list` · Base: `/api` (do `context-path`; a rota declarada no
controller **não** o repete) · Tag OpenAPI: `Aplicações` (já existente)

Ambas as rotas ficam sob `/applications/**` e por isso já são superfície administrativa: o
`AdminSurfaceInterceptor` recusa toda requisição que apresente o header de chave de aplicação
(D-08). Toda resposta de erro é RFC 9457 (`application/problem+json`), montada só pelo
`ApiExceptionHandler`, com `code` e `traceId`.

---

## `GET /applications` — listar aplicações

**Parâmetros de consulta**

| Nome | Tipo | Obrigatório | Padrão | Regra |
|---|---|---|---|---|
| `status` | string | não | ausente = todas | `active` ou `inactive` |
| `page` | integer | não | `0` | `>= 0` |
| `size` | integer | não | `20` | `1..100` |

### `200 OK` — `PageResponseDTO<ApplicationSummaryResponseDTO>`

```json
{
  "items": [
    {
      "id": "3f9a1c72-5d84-4a1e-9b0f-2c6e8d5a7b31",
      "slug": "acme-app",
      "name": "Acme App",
      "status": "active",
      "createdAt": "2026-09-08T14:32:10Z"
    }
  ],
  "page": 0,
  "size": 20,
  "total": 42,
  "totalPages": 3
}
```

- `items` vazio com `total: 0` quando nada atende ao filtro — nunca um erro (FR-008).
- Ordem: `createdAt` decrescente, desempate por `id` decrescente (FR-007).
- Sem `status`, o resultado traz ativas e inativas (FR-003).
- `total` conta o conjunto filtrado inteiro, não a página (FR-006).

### `400 Bad Request` — `ApiValidationErrorResponse`

`page` negativa, `size` fora de `1..100`, `status` fora de `active|inactive`. O corpo aponta o
campo e o motivo; nenhum resultado parcial acompanha a recusa (FR-009).

### `403 Forbidden` — `ApiErrorResponse` · `api_key.forbidden_surface`

Requisição apresentando o header de chave de aplicação. A chave do SDK não vale no painel.

---

## `GET /applications/{applicationId}` — consultar uma aplicação

Fecha o contrato que o `Location` do `201` da criação já prometia.

**Parâmetro de rota**: `applicationId` — identificador opaco devolvido na criação.

### `200 OK` — `ApplicationResponseDTO`

```json
{
  "id": "3f9a1c72-5d84-4a1e-9b0f-2c6e8d5a7b31",
  "slug": "acme-app",
  "name": "Acme App",
  "status": "active",
  "quietPeriodDays": 15,
  "retentionDays": 180,
  "openTextRetentionDays": 30,
  "createdAt": "2026-09-08T14:32:10Z",
  "updatedAt": "2026-09-08T16:05:44Z"
}
```

- Prazo não configurado é **campo ausente**, nunca `0` (FR-012). Uma aplicação sem política
  nenhuma responde sem as três chaves de prazo.
- Aplicação inativa é encontrada, com `"status": "inactive"` (FR-014).
- O prazo de texto livre é o **configurado**, não o herdado do prazo geral (D-07).

### `404 Not Found` — `ApiErrorResponse` · `application.not_found`

Identificador inexistente **ou** malformado — os dois recebem a mesma resposta, para não
entregar um oráculo de formato a quem sonda a API (FR-013, edge case da spec).

### `403 Forbidden` — `ApiErrorResponse` · `api_key.forbidden_surface`

Mesma regra da listagem.

---

## Schemas

### `ApplicationSummaryResponseDTO`

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `id` | string | sim | Identificador da aplicação |
| `slug` | string | sim | Identificador legível, público e imutável |
| `name` | string | sim | Nome de exibição |
| `status` | string | sim | `active` ou `inactive` |
| `createdAt` | date-time | sim | Instante da criação, em UTC |

### `ApplicationResponseDTO`

Os cinco campos acima, mais:

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `quietPeriodDays` | integer | não | Intervalo de descanso em dias; ausente quando não configurado |
| `retentionDays` | integer | não | Prazo de retenção em dias; ausente quando não configurado |
| `openTextRetentionDays` | integer | não | Prazo de retenção de texto livre em dias; ausente quando não configurado |
| `updatedAt` | date-time | sim | Instante da última alteração, em UTC |

### `PageResponseDTO<T>` — existente, reusado

`items`, `page`, `size`, `total`, `totalPages`.

---

## Invariantes de contrato desta fatia

1. **Nenhuma escrita.** Reler o conjunto antes e depois de qualquer chamada, inclusive das que
   falham, devolve o mesmo estado (FR-016).
2. **Nenhum segredo de chave** aparece em qualquer corpo, mensagem ou log (FR-015).
3. **Nenhum código de erro novo.** `application.not_found` e `api_key.forbidden_surface` já
   existem e são reusados (FR-013, FR-019).
4. **Toda recusa tem `code` estável**; o cliente nunca precisa ler a mensagem (FR-017).
5. Cada status acima é declarado no `ApplicationControllerSwagger` com o schema correspondente.
