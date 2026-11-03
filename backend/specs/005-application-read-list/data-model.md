# Phase 1 — Modelo de dados: Aplicações — listagem e consulta

**Feature**: `005-application-read-list` · **Data**: 2026-09-08

Esta fatia **não introduz entidade, value object nem coluna**. `Application` já existe e já
guarda tudo o que as duas leituras devolvem. O que nasce aqui é caminho de leitura: uma porta
paginada, dois casos de uso, dois formatos de saída e os índices que sustentam a consulta.

---

## Entidade lida: `Application` (existente, inalterada)

`modules/app/domain/entities/Application.java` — nenhuma alteração nesta fatia.

| Campo | Tipo no domínio | Exposto na listagem | Exposto na consulta |
|---|---|---|---|
| `id` | `ApplicationId` | sim | sim |
| `slug` | `Slug` | sim | sim |
| `name` | `Name` | sim | sim |
| `status` | `Status` (`ACTIVE`/`INACTIVE`) | sim | sim |
| `createdAt` | `Instant` | sim | sim |
| `quietPeriodDays` | `Optional<Integer>` | **não** | sim |
| `retentionDays` | `Optional<Integer>` | **não** | sim |
| `openTextRetentionDays` | `Optional<Integer>` | **não** | sim |
| `updatedAt` | `Instant` | **não** | sim |

`effectiveOpenTextRetentionDays()` existe na entidade e **não é exposto** (D-07): a resposta
diz o que foi configurado, não o que foi derivado.

Nenhum segredo de chave de API atravessa este modelo — `Application` não tem acesso a
`ApiKey`, então FR-015 é estrutural, não uma disciplina a manter.

---

## Porta: `ApplicationRepository` (alterada)

`modules/app/application/repositories/ApplicationRepository.java`

**Entra**:

```java
// items vem ordenado por createdAt desc com desempate por id desc, recortado em page*size, e
// total conta o conjunto filtrado inteiro. Página além do fim devolve items vazio, nunca erro.
Page<Application> findPage(Query query);

record Query(Optional<Status> status, int page, int size) implements PageQuery {}
```

**Sai**: `List<Application> findAll()` — sem chamador em produção (D-03). Permanece em
`InMemoryApplicationRepository` como espião de teste, sem `@Override`.

**Permanece**: `create`, `update`, `findBySlug`, `findById`.

### Contrato do `findPage` — o que o fake e o adaptador JPA precisam ambos honrar

1. `status` vazio devolve ativas **e** inativas (FR-003).
2. `total` conta o conjunto filtrado inteiro, não a página (FR-006).
3. Ordenação `createdAt desc`, desempate `id desc` — determinística (FR-007).
4. Página além do fim devolve `items` vazio e o `total` correto, nunca exceção (FR-008).
5. Nenhuma escrita, em nenhum caminho (FR-016).

---

## Caso de uso: `ListApplicationsUseCase` (novo)

`modules/app/application/usecases/ListApplicationsUseCase.java` · `UseCase<Input, Output>`,
sem `@Transactional` (D-10).

```java
record Input(Optional<Status> status, int page, int size)

record Output(List<Item> items, int page, int size, long total, int totalPages)

record Item(String id, String slug, String name, Status status, Instant createdAt)
```

Fluxo: chama `findPage`, mapeia `Page<Application>` → `Page<Item>` com `Page.map`, calcula
`totalPages(size)`, loga o sucesso com filtro e total (nunca o valor de entrada rejeitado).
Não há caminho de recusa: sem aplicação nenhuma, `items` vem vazio com `total` zero.

Molde: `ListApiKeysUseCase`, menos a verificação de existência da aplicação — aqui não há
escopo acima.

## Caso de uso: `GetApplicationUseCase` (novo)

`modules/app/application/usecases/GetApplicationUseCase.java` · `UseCase<Input, Output>`,
sem `@Transactional`.

```java
record Input(String applicationId)

record Output(
    String id, String slug, String name, Status status,
    Optional<Integer> quietPeriodDays,
    Optional<Integer> retentionDays,
    Optional<Integer> openTextRetentionDays,
    Instant createdAt, Instant updatedAt)
```

Fluxo: converte o identificador (`DomainException` → `ApplicationNotFound`, D-11), busca por
`findById`, `orElseThrow(ApplicationNotFound)`, loga o sucesso. Aplicação inativa é encontrada
normalmente (FR-014).

**Erro reusado**: `ApplicationNotFound` / `application.not_found` → 404. Nenhum código de erro
novo nesta fatia (FR-013).

---

## Persistência

### Adaptador `ApplicationRepositoryJpa` (alterado)

`findPage` monta `PageRequest.of(page, size, Sort.by(desc("createdAt"), desc("id")))` e delega:
`findByStatus(status.name(), pageable)` quando há filtro, `findAll(pageable)` quando não há
(D-05). Traduz para `core.pagination.Page` com `getContent()` + `getTotalElements()`. Molde
literal de `ApiKeyRepositoryJpa.findPage`.

`ApplicationJpaRepository` ganha `Page<ApplicationJpaEntity> findByStatus(String status,
Pageable pageable)`. `ApplicationJpaEntity` e `ApplicationJpaMapper` ficam intactos.

`Pageable` e o `Page` do Spring Data não saem de `infra`.

### Migration (nova) — `V2026<...>__index_applications_listing.sql`

```sql
create index idx_applications_created_at
    on applications (created_at desc, id desc);

create index idx_applications_status_created_at
    on applications (status, created_at desc, id desc);
```

Só índice: nenhuma coluna nasce, nenhuma migration existente é tocada. `ddl-auto` segue
`validate` e o schema não muda de forma. Justificativa dos dois índices em D-04.

---

## Borda HTTP

### `ApplicationSummaryResponseDTO` (novo) — linha da listagem

`id`, `slug`, `name`, `status` (`"active"`/`"inactive"`), `createdAt` — todos obrigatórios.

### `ApplicationResponseDTO` (novo) — corpo da consulta

O resumo mais `quietPeriodDays`, `retentionDays`, `openTextRetentionDays` (os três
`nullable = true`, ausentes quando não configurados — D-07) e `updatedAt` (obrigatório).

### `ListApplicationsQueryDTO` (novo) — filtro e recorte

| Campo | Constraint | Padrão |
|---|---|---|
| `status` | `@Pattern("active\|inactive")` | ausente → todas |
| `page` | `@Min(0)` | `0` |
| `size` | `@Min(1)` `@Max(100)` | `20` |

`toInput()` converte para `ListApplicationsUseCase.Input`. Mensagens espelhando as de
`ListApiKeysQueryDTO`. Molde e limites: D-09 e as premissas da spec.

### Presenters (novos)

`ListApplicationsPresenter.present(Output) → PageResponseDTO<ApplicationSummaryResponseDTO>` e
`GetApplicationPresenter.present(Output) → ApplicationResponseDTO`. `final class`, construtor
privado, um `public static present`. Ambos fazem `status.name().toLowerCase(Locale.ROOT)` e
`Optional::orElse(null)`.

### Envelope de página

`infra/http/dtos/PageResponseDTO<T>` — já existe, reusado como está.

---

## Cabeamento

Dois `@Bean` novos em `modules/app/infra/config/UseCasesConfiguration`, ambos recebendo
apenas `ApplicationRepository`.

---

## O que esta fatia não toca

`Application`, `Status`, `Slug`, `Name`, `ApplicationJpaEntity`, `ApplicationJpaMapper`, a
tabela `applications`, `ApplicationNotFound`, `PageResponseDTO`, `core.pagination` e todo o
módulo `survey`.
