# Phase 1 — Modelo de dados: listagem e consulta de chaves de API

**Feature**: `002-api-key-read-list` · **Data**: 2026-09-08

Deriva das Key Entities da [spec](./spec.md) e das decisões de [research.md](./research.md).
A entidade `ApiKey` já está descrita em
[001-api-key-management/data-model.md](../001-api-key-management/data-model.md); aqui só o que
muda ou nasce.

---

## Domínio

### `ApiKey` — alteração mínima (`modules/app/domain/entities/ApiKey.java`)

Nenhum campo novo, nenhum construtor novo, nenhuma invariante nova. Ganha um acessor:

```java
public ApiKeyStatus status()
```

Derivado: `revokedAt == null ? ACTIVE : REVOKED`. `isRevoked()` continua existindo e passa a ser
a mesma pergunta com resposta booleana — as duas ficam, porque `isRevoked()` é o que o `revoke()`
usa internamente e `status()` é o que a leitura apresenta.

### `ApiKeyStatus` — enum de estado (`modules/app/domain/entities/ApiKeyStatus.java`)

```
ACTIVE    chave válida — revokedAt ausente
REVOKED   chave revogada — revokedAt presente
```

Java puro, sem dependência. **Não é persistido** (D-04): não existe coluna `status` em
`api_keys`, e o enum nunca é gravado nem lido do banco. Ele existe para dar nome ao estado que
`revoked_at` já expressa, de modo que se possa filtrar e apresentar por ele.

Fica em `domain/entities` acompanhando o precedente de `Status` (o estado de `Application`).

| Valor | `revokedAt` | Valor no JSON |
| --- | --- | --- |
| `ACTIVE` | ausente | `"active"` |
| `REVOKED` | presente | `"revoked"` |

A conversão para minúsculas acontece no presenter (D-10); o domínio não conhece o formato do JSON.

---

## Porta de repositório

### `ApiKeyRepository` — alteração (`modules/app/application/repositories/ApiKeyRepository.java`)

Um método novo, e dois `record` aninhados que o descrevem:

```java
Page<ApiKey> findPage(Query query);

record Query(ApplicationId applicationId, Optional<ApiKeyStatus> status, int page, int size)
    implements PageQuery {}
```

`Page<T>` e `PageQuery` vêm de `core/pagination` e são compartilhados por toda consulta paginada
(D-03). `Page<T>` traz `items` e `total`, com `map` para traduzir os itens e `totalPages(size)`
para a contagem; `PageQuery` traz `page()`, `size()` e o `offset()` derivado, e é a interface que
a `Query` de cada porta implementa para acrescentar os próprios filtros.

**Contrato que toda implementação deve honrar** — é o que o fake e o adaptador JPA precisam
provar igualmente:

| Regra | Detalhe |
| --- | --- |
| Recorte | somente chaves cujo `applicationId` bate. Nunca de outra aplicação (FR-002) |
| Filtro | `status` ausente ⇒ todas; `ACTIVE` ⇒ `revokedAt` ausente; `REVOKED` ⇒ presente (D-01) |
| Ordenação | `createdAt` desc, desempate por `id` desc — determinística (D-05) |
| Recorte de página | `items` tem no máximo `size` elementos, começando em `page * size` |
| Total | `total` conta **todas** as que atendem a recorte e filtro, não só as da página |
| Página além do fim | `items` vazia, `total` correto — nunca exceção |
| Leitura pura | nenhuma escrita, nenhum efeito colateral (FR-018) |

Os três métodos existentes (`create`, `findByIdAndApplicationId`, `revoke`) não mudam.
`findByIdAndApplicationId` já é exatamente o que a consulta individual precisa (D-08) — a fatia
não acrescenta método para ela.

---

## Casos de uso

### `ListApiKeysUseCase` (`modules/app/application/usecases/`)

`UseCase<Input, Output>`.

```java
record Input(String applicationId, Optional<ApiKeyStatus> status, int page, int size) {}

record Output(List<Item> items, int page, int size, long total, int totalPages) {}

record Item(
    String id,
    String applicationId,
    String label,
    String prefix,
    ApiKeyStatus status,
    Instant createdAt,
    Optional<Instant> revokedAt) {}
```

Fluxo:

1. `applicationIdOf(input.applicationId())` — mesma tradução de formato inválido em
   `ApplicationNotFound` já usada por `IssueApiKeyUseCase` (D-08).
2. `applicationRepository.findById(...)` → ausente ⇒ `ApplicationNotFound` (D-07, FR-010).
   O estado ativo/inativo **não é consultado**: inatividade não impede ler (FR-011).
3. `apiKeyRepository.findPage(new Query(...))`.
4. `totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size)` — `size` nunca é 0
   porque o DTO garante `@Min(1)`, mas o caso de uso não depende dessa garantia da borda.
5. Log de sucesso com aplicação, filtro e quantidade — nunca com rótulo, prefixo ou segredo.

**Não lança** por lista vazia (FR-012). **Não escreve** em nenhum caminho.

### `GetApiKeyUseCase` (`modules/app/application/usecases/`)

`UseCase<Input, Output>`, com `Output` de mesmos campos do `Item` acima.

```java
record Input(String applicationId, String apiKeyId) {}
```

Fluxo:

1. Traduz `applicationId` — formato inválido ⇒ `ApplicationNotFound`.
2. Aplicação inexistente ⇒ `ApplicationNotFound` (FR de US2, cenário 5).
3. Traduz `apiKeyId` — formato inválido ⇒ `ApiKeyNotFound` (D-08).
4. `findByIdAndApplicationId` ausente ⇒ `ApiKeyNotFound` — cobre também a chave que existe em
   outra aplicação, sem distinguir (FR-016).
5. Chave revogada é encontrada normalmente, com `status = REVOKED` (FR-017).

Nenhum erro novo: `ApplicationNotFound` (`application.not_found`) e `ApiKeyNotFound`
(`api_key.not_found`) já existem em `application/errors`, com os mesmos `code`.

---

## Persistência

### Sem mudança de schema

Nenhuma tabela, nenhuma coluna, nenhum dado migrado. A tabela `api_keys` de 001 já tem tudo que
a leitura precisa.

### `V20260908160000__index_api_keys_listing.sql`

```sql
create index idx_api_keys_application_id_created_at
    on api_keys (application_id, created_at desc, id desc);

drop index idx_api_keys_application_id;
```

O composto atende a ordenação e o recorte da listagem sem `sort` em memória, e cobre tudo que o
índice removido cobria — `application_id` é sua coluna líder (D-05). `idx_api_keys_prefix`
permanece: serve à verificação de chave que virá, não a esta fatia.

### `ApiKeyJpaRepository` — três métodos derivados

```java
Page<ApiKeyJpaEntity> findByApplicationId(String applicationId, Pageable pageable);
Page<ApiKeyJpaEntity> findByApplicationIdAndRevokedAtIsNull(String applicationId, Pageable pageable);
Page<ApiKeyJpaEntity> findByApplicationIdAndRevokedAtIsNotNull(String applicationId, Pageable pageable);
```

Derivados, sem JPQL: um predicado nulo opcional em JPQL sairia mais obscuro que três assinaturas
explícitas. O `Page` do Spring Data já traz itens e total.

### `ApiKeyRepositoryJpa` — adaptador

Monta `PageRequest.of(page, size, Sort.by(desc("createdAt"), desc("id")))`, escolhe o método pelo
filtro e mapeia com `ApiKeyJpaMapper::toDomain`. **É a única classe da fatia que importa
`org.springframework.data.domain`** (D-03, Princípio I).

### `InMemoryApiKeyRepository` — fake

Implementa `findPage` com o mesmo contrato: filtra, ordena por `createdAt` desc com desempate por
`id` desc, recorta com `skip`/`limit`, conta o conjunto filtrado inteiro. Devolve cópias, como já
faz nos demais métodos. É o fake que prova que a ordenação determinística é contrato da porta, e
não detalhe do Postgres.

---

## Borda HTTP

### `ListApiKeysQueryDTO` — entrada (`infra/http/dtos/`)

| Campo | Tipo | Padrão | Constraint | Mensagem |
| --- | --- | --- | --- | --- |
| `status` | `String` | ausente ⇒ todas | `@Pattern("active\|revoked")` | "Estado deve ser active ou revoked" |
| `page` | `Integer` | `0` | `@Min(0)` | "Página não pode ser negativa" |
| `size` | `Integer` | `20` | `@Min(1)` `@Max(100)` | "Tamanho de página deve estar entre 1 e 100" |

`toInput(String applicationId)` aplica os padrões e converte `status` para
`Optional<ApiKeyStatus>`. O caso de uso nunca recebe o DTO (Princípio IV).

### `ApiKeyResponseDTO` — item, compartilhado (D-10)

| Campo JSON | Tipo | Nulo? | Origem |
| --- | --- | --- | --- |
| `id` | string | não | `ApiKeyId` |
| `applicationId` | string | não | aplicação dona |
| `label` | string | não | rótulo da emissão |
| `prefix` | string | não | prefixo público, não sensível |
| `status` | string | não | `"active"` ou `"revoked"` |
| `createdAt` | date-time | não | UTC |
| `revokedAt` | date-time | **sim** | presente só quando `status = "revoked"` |

**Não existe** campo `secret` nem `secretHash`, nesta nem em nenhuma resposta de leitura (FR-004).

### `PageResponseDTO<T>` — envelope da listagem (`infra/http/dtos/`, compartilhado)

| Campo JSON | Tipo | Observação |
| --- | --- | --- |
| `items` | array de `ApiKeyResponseDTO` | vazio quando não há chave que atenda |
| `page` | integer | base 0, ecoa o pedido |
| `size` | integer | ecoa o pedido, já com o padrão aplicado |
| `total` | integer (int64) | total que atende ao filtro, não o da página |
| `totalPages` | integer | `ceil(total / size)`; `0` quando `total` é `0` |

### Presenters

`ListApiKeysPresenter.present(Output) → PageResponseDTO<ApiKeyResponseDTO>` e
`GetApiKeyPresenter.present(Output) → ApiKeyResponseDTO`, ambos `final class` com construtor
privado e método estático (constituição 1.1.0). São eles que
convertem `ApiKeyStatus` para o texto minúsculo e `Optional<Instant>` para o campo anulável do
JSON. Os DTOs não têm factory estática e não conhecem o caso de uso.

---

## Rastreamento requisito → artefato

| Requisito | Onde é atendido |
| --- | --- |
| FR-001, FR-007, FR-008 | `ListApiKeysUseCase` + `ApiKeyRepository.findPage` + envelope |
| FR-002, FR-016 | recorte por `applicationId` na porta e em `findByIdAndApplicationId` |
| FR-003, FR-015 | `ApiKeyResponseDTO`, um só para os dois endpoints |
| FR-004 | ausência de campo de segredo nos DTOs; `ApiKey` sem getter de texto claro |
| FR-005, FR-006 | `Optional<ApiKeyStatus>` na `Query`; ausente ⇒ todas |
| FR-009 | `Sort.by(desc(createdAt), desc(id))` + índice de D-05 |
| FR-010, FR-011 | passo 2 dos dois casos de uso: existência sim, atividade não |
| FR-012 | `Page` vazia sem exceção, no fake e no adaptador |
| FR-013 | constraints do `ListApiKeysQueryDTO` |
| FR-014, FR-017 | `GetApiKeyUseCase` sobre `findByIdAndApplicationId` |
| FR-018 | nenhum método de escrita é chamado por nenhum dos dois casos de uso |
| FR-019 | `ApplicationNotFound` e `ApiKeyNotFound` reusados, `code` inalterado |
| FR-020 | `limit`/`offset` no banco, ordenação servida pelo índice |
