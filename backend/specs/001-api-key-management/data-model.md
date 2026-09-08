# Phase 1 — Modelo de dados: Chaves de API

**Feature**: `001-api-key-management` · **Data**: 2026-09-08

Deriva das Key Entities da [spec](./spec.md) e das decisões de [research.md](./research.md).

---

## Domínio

### `ApiKey` — entidade (`modules/app/domain/entities/ApiKey.java`)

Estende `Entity<ApiKeyId>`. Identidade, não conteúdo.

| Campo | Tipo | Nulo? | Observação |
| --- | --- | --- | --- |
| `id` | `ApiKeyId` | não | herdado de `Entity` |
| `applicationId` | `ApplicationId` | não | imutável por toda a vida da chave (spec, Key Entities) |
| `label` | `ApiKeyLabel` | não | FR-004 |
| `secret` | `ApiKeySecret` | não | prefixo público + hash; **nunca** o segredo em claro |
| `createdAt` | `Instant` | não | UTC, FR-009 |
| `revokedAt` | `Instant` | sim | ausente ⇒ válida; presente ⇒ revogada (FR-011) |

**Estado derivado, não campo**: não existe enum de status. `isRevoked()` é
`revokedAt != null`; o acessor de leitura é `Optional<Instant> revokedAt()`.

**Construção**
- `static Issued issue(ApplicationId applicationId, ApiKeyLabel label)` — a chave nasce agora:
  gera o segredo, fixa `createdAt = Instant.now()`, deixa `revokedAt` ausente. Devolve
  `record Issued(ApiKey apiKey, String plainSecret)` — o segredo em claro sai **fora** da
  entidade e nunca volta a ela (D-04, FR-006/FR-007).
- `static ApiKey restore(ApiKeyId, ApplicationId, ApiKeyLabel, ApiKeySecret, Instant createdAt, Instant revokedAt)` — volta do banco. É o que o mapper chama.

**Transição**

```
válida  ──revoke()──▶  revogada  ──revoke()──▶  DomainException(CONFLICT, api_key.already_revoked)
```

- `void revoke()` — fixa `revokedAt = Instant.now()` se ausente; se já presente, lança
  `DomainException(ErrorType.CONFLICT, "api_key.already_revoked", …)` sem tocar no estado
  (FR-012, FR-013). Não existe reativação: nenhum método volta `revokedAt` a ausente (FR-014).
- `revoke()` é a única autoridade sobre a transição (D-05).

**Invariantes**
- `applicationId` não nulo — `DomainException(VALIDATION, "api_key.application_required")`.
- `revokedAt`, quando presente, não é anterior a `createdAt` — validado em `restore`,
  `DomainException(VALIDATION, "api_key.revoked_before_created")`.
- Não existe setter de `label`, `secret`, `applicationId` ou `createdAt`: renomear e rotacionar
  estão fora de escopo, e a trilha de FR-020 depende de eles não mudarem.

---

### `ApiKeyId` — identificador (`modules/app/domain/entities/ApiKeyId.java`)

Estende `Id`, no mesmo molde de `ApplicationId`: `generate()` e `of(String)`, valor opaco para
quem consome.

`of(String)` **valida o formato UUID** e lança
`DomainException(VALIDATION, "api_key.id_invalid", "Identificador de chave inválido")` quando não
bate (D-06). A checagem em si vem de `Id.isUuid(String)`, predicado `protected` novo no core; o
`code` e a mensagem são de cada subclasse, porque `<contexto>.<motivo>` é contrato do agregado.

`ApplicationId` recebe o mesmo tratamento nesta feature — `application.id_invalid` —, o que a
torna a primeira a alterar código já escrito. Nenhum dos dois `code` chega ao cliente: o caso de
uso captura a `DomainException` e traduz para `ApplicationNotFound` / `ApiKeyNotFound`.

---

### `ApiKeyLabel` — value object (`modules/app/domain/valueobjects/ApiKeyLabel.java`)

`record ApiKeyLabel(String value)`, no molde de `Name`.

- `strip()` no compact constructor — espaço nas pontas é ruído de digitação (edge case da spec).
- Vazio ou em branco ⇒ `DomainException(VALIDATION, "api_key.label_invalid", "Rótulo é obrigatório")`.
- Acima de **80** caracteres após o `strip()` ⇒ mesmo `code`,
  `"Rótulo não pode passar de 80 caracteres"` (FR-004).
- Não é único dentro da aplicação — é descrição humana, não identidade (Assumptions).

---

### `ApiKeySecret` — value object (`modules/app/domain/valueobjects/ApiKeySecret.java`)

`record ApiKeySecret(String prefix, String hash)` — o que a entidade guarda. Nenhum dos dois é
sensível: o prefixo é público por construção, o hash é irreversível.

- `static Generated generate()` — 32 bytes de `SecureRandom`, Base64 URL-safe sem padding;
  monta o segredo em claro `pit_<prefix8>_<random>`, o prefixo público `pit_<prefix8>` e o hash.
  Devolve `record Generated(ApiKeySecret secret, String plainText)` (D-02).
- `static String hashOf(String plainText)` — SHA-256 em hexadecimal minúsculo, 64 caracteres
  (D-03). É o que a verificação futura vai reusar.
- `toString()` devolve só o prefixo — nem o hash aparece em log por acidente.

---

## Persistência

### Tabela `api_keys` — migration `V20260908120000__create_api_keys.sql`

| Coluna | Tipo | Constraint |
| --- | --- | --- |
| `id` | `varchar(64)` | `primary key` |
| `application_id` | `varchar(64)` | `not null`, `references applications(id)` |
| `label` | `varchar(80)` | `not null` |
| `prefix` | `varchar(16)` | `not null` |
| `secret_hash` | `varchar(64)` | `not null`, `unique` |
| `created_at` | `timestamptz` | `not null` |
| `revoked_at` | `timestamptz` | nulo permitido |

Índices: `idx_api_keys_application_id (application_id)` — filtro do `DELETE` e junção — e
`idx_api_keys_prefix (prefix)`, caminho de busca da verificação futura. A unicidade de
`secret_hash` já cria o índice correspondente (D-08).

`prefix` é **dica pública, não identificador**: repetição é aceitável e não tem caminho de erro.
Quem identifica é o `id`; quem garante que dois segredos não se confundem é o `unique` no hash.

Não há coluna para o segredo em claro. Não há coluna de status: `revoked_at` **é** o status.

### `ApiKeyJpaEntity` + `ApiKeyJpaMapper`

Molde de `ApplicationJpaEntity`: `@Entity`, `@Table(name = "api_keys")`, Lombok
`@Getter/@Setter/@NoArgsConstructor(PROTECTED)/@AllArgsConstructor`. `application_id` é
`String`, não `@ManyToOne` — o agregado é a chave, e a aplicação já é carregada pelo próprio
caso de uso quando precisa.

O mapper chama `ApiKey.restore(...)`, nunca `issue(...)`.

---

## Portas

### `ApiKeyRepository` (`modules/app/application/repositories/ApiKeyRepository.java`)

```java
ApiKey create(ApiKey apiKey);
Optional<ApiKey> findByIdAndApplicationId(ApiKeyId id, ApplicationId applicationId);
boolean revoke(ApiKey apiKey);   // update condicional: false = alguém revogou antes (D-05)
```

O `revoke` do adaptador JPA é `@Modifying @Query` com
`where id = :id and revoked_at is null`, devolvendo linhas afetadas > 0. Nenhum outro método
entra nesta fatia: listar, consultar e rotacionar estão fora de escopo.

O fake `InMemoryApiKeyRepository` em `testsupport/repositories` implementa a mesma semântica,
inclusive o `false` do `revoke` sobre chave já revogada.

---

## Erros

| Classe | Onde | Tipo | `code` | Status |
| --- | --- | --- | --- | --- |
| `ApplicationNotFound` | `modules/app/application/errors` | `NOT_FOUND` | `application.not_found` | 404 |
| `ApplicationIsInactive` | `modules/app/application/errors` | `BUSINESS_RULE` | `application.inactive` | 422 |
| `ApiKeyNotFound` | `modules/app/application/errors` | `NOT_FOUND` | `api_key.not_found` | 404 |
| — (`DomainException`) | `ApiKey.revoke()` | `CONFLICT` | `api_key.already_revoked` | 409 |
| — (`DomainException`) | `ApiKeyLabel` | `VALIDATION` | `api_key.label_invalid` | 400 |

Códigos de domínio que **não chegam ao cliente** — ou são capturados e traduzidos pelo caso de
uso, ou só disparam sobre dado já corrompido no banco:

| Onde | Tipo | `code` | Quando |
| --- | --- | --- | --- |
| `ApplicationId.of` | `VALIDATION` | `application.id_invalid` | texto fora do formato UUID; o caso de uso traduz para `ApplicationNotFound` (D-06) |
| `ApiKeyId.of` | `VALIDATION` | `api_key.id_invalid` | idem, traduzido para `ApiKeyNotFound` (D-06) |
| `ApiKey` (construtor) | `VALIDATION` | `api_key.application_required` | `applicationId` nulo — defeito de programação, não entrada de usuário |
| `ApiKey.restore` | `VALIDATION` | `api_key.revoked_before_created` | `revokedAt` anterior a `createdAt`; só alcançável por linha inconsistente no banco |

`ApplicationNotFound` carrega o identificador tentado; `ApiKeyNotFound` idem. Nenhum deles
carrega segredo, e nenhuma mensagem repete valor de entrada do usuário.

Códigos novos, todos estáveis e distintos por motivo (FR-018, SC-004). Nenhum `ErrorType` novo
é necessário: os cinco caminhos de recusa cabem nos que já existem.

---

## Casos de uso

### `IssueApiKeyUseCase` — `UseCase<Input, Output>`

`Input(String applicationId, String label)` · `Output(String id, String applicationId, String label, String prefix, String plainSecret, Instant createdAt)`

1. `ApplicationId.of(input.applicationId())` dentro de `try/catch (DomainException)` ⇒
   `ApplicationNotFound` quando o formato não é UUID (D-06).
2. `applicationRepository.findById(...)` vazio ⇒ `ApplicationNotFound` (FR-002).
3. `!application.isActive()` ⇒ `ApplicationIsInactive` (FR-003).
4. `ApiKey.issue(applicationId, ApiKeyLabel.of(label))`.
5. `apiKeyRepository.create(issued.apiKey())`.
6. `log.info("Chave de API emitida [{}] application={} prefix={}", …)` — id, aplicação e prefixo;
   **nunca** o rótulo (entrada do usuário) nem o segredo.
7. Devolve o `Output` com `plainSecret` — única vez que ele existe fora da resposta (FR-006).

Escrita única: sem `Transactor`.

### `RevokeApiKeyUseCase` — `UseCaseWithoutOutput<Input>`

`Input(String applicationId, String apiKeyId)`

1. `ApplicationId.of(...)` e `ApiKeyId.of(...)` dentro de `try/catch (DomainException)` ⇒
   `ApiKeyNotFound` quando o formato não é UUID (D-06).
2. `findByIdAndApplicationId(...)` vazio ⇒ `ApiKeyNotFound` — cobre tanto a chave inexistente
   (FR-015) quanto a chave de outra aplicação (edge case da spec).
3. `apiKey.revoke()` — lança `api_key.already_revoked` no caso comum (FR-013).
4. `apiKeyRepository.revoke(apiKey)`; se `false`, relê e chama `revoke()` na instância relida,
   que lança o mesmo erro — é a corrida de duas exclusões simultâneas (D-05, FR-012).
5. `log.info("Chave de API revogada [{}] application={}", …)`.

Nenhum dos dois casos toca em outro agregado: FR-016 e FR-017 saem de graça, porque em todo
caminho de falha nada foi escrito.
