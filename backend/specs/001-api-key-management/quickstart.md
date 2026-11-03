# Quickstart — validando as chaves de API

**Feature**: `001-api-key-management`

Como provar, de ponta a ponta, que a fatia funciona. Detalhes de tipos estão em
[data-model.md](./data-model.md); o contrato HTTP, em
[contracts/api-keys.openapi.yaml](./contracts/api-keys.openapi.yaml).

## Pré-requisitos

- Java 21 e Docker de pé (Testcontainers e o `docker-compose` do `spring-boot-docker-compose`).
- Nada mais: Postgres, Redis e a stack LGTM sobem junto com a aplicação.

## Portão automatizado

```bash
./mvnw verify
```

Verde, sem teste ignorado. É o portão obrigatório da constituição — nada mais desta fatia
conta como pronto antes dele.

Durante o desenvolvimento, os recortes úteis:

```bash
./mvnw test -Dtest=ApiKeyTest,ApiKeyLabelTest,ApiKeySecretTest   # domínio, JUnit puro
./mvnw test -Dtest='IssueApiKeyUseCaseTest,RevokeApiKeyUseCaseTest'
./mvnw test -Dtest='*ApiKey*E2ETest'                              # precisa de Docker
```

## Validação manual

```bash
./mvnw spring-boot:run
```

API em `http://localhost:8080/api` · Swagger em `http://localhost:8080/api/swagger-ui.html`.

### 1. Uma aplicação para ser dona da chave

```bash
APP=$(curl -sS -X POST http://localhost:8080/api/applications \
  -H 'Content-Type: application/json' \
  -d '{"name":"Acme App","slug":"acme-app"}' | jq -r .id)
```

### 2. Emitir (User Story 1, caminho feliz)

```bash
curl -sS -i -X POST "http://localhost:8080/api/applications/$APP/api-keys" \
  -H 'Content-Type: application/json' \
  -d '{"label":"app iOS"}'
```

**Esperado**: `201`, header `Location` terminando em `/api-keys/<id>`, corpo com `id`,
`applicationId`, `label`, `prefix` e `secret` — este último no formato `pit_<prefixo>_<aleatório>`,
começando pelo mesmo `prefix` devolvido. Emitir uma segunda vez devolve `id` e `secret`
diferentes, e as duas chaves coexistem.

### 3. O segredo não volta (SC-003)

```bash
docker compose exec -T postgres psql -U pitaco -d pitaco \
  -c 'select id, application_id, label, prefix, secret_hash, created_at, revoked_at from api_keys'
```

**Esperado**: nenhuma coluna com o segredo em claro; `secret_hash` com 64 caracteres
hexadecimais; `revoked_at` nulo. Nenhuma outra rota devolve o segredo — não existe rota de
consulta nesta fatia, por decisão de escopo. O log da aplicação traz o `prefix`, nunca o
segredo nem o rótulo.

### 4. Recusas da emissão

| Requisição | Esperado |
| --- | --- |
| `{"label":""}` ou corpo sem `label` | `400`, `code: request.invalid`, `errors.label` preenchido |
| `label` com 81 caracteres | `400`, `code: request.invalid` |
| `applicationId` inexistente | `404`, `code: application.not_found` |
| `applicationId` em formato inválido (`nao-e-um-id`) | `404`, mesmo `code` — indistinguível de propósito (D-06) |
| aplicação inativa | `422`, `code: application.inactive` |
| corpo `{` malformado | `400`, `application/problem+json` |

Depois de cada uma, o `select count(*) from api_keys` do passo 3 não mudou (FR-017, SC-005).

### 5. Excluir (User Story 2)

```bash
KEY=<id devolvido no passo 2>
curl -sS -i -X DELETE "http://localhost:8080/api/applications/$APP/api-keys/$KEY"
```

**Esperado**: `204`. Repetindo o mesmo `DELETE`: `409`, `code: api_key.already_revoked`, e o
`revoked_at` no banco **continua o mesmo instante da primeira chamada** (FR-012). A outra chave
emitida no passo 2 segue com `revoked_at` nulo (FR-016).

Ainda no banco, a linha revogada preserva `label`, `prefix` e `created_at`, e ganhou
`revoked_at` — é a trilha de SC-007.

### 6. Recusas da exclusão

| Requisição | Esperado |
| --- | --- |
| `apiKeyId` inexistente | `404`, `code: api_key.not_found` |
| `apiKeyId` em formato inválido (`nao-e-um-id`) | `404`, mesmo `code` — indistinguível de propósito |
| chave de outra aplicação | `404`, `code: api_key.not_found` |

## Checagem de conformidade

```bash
grep -rE 'org\.springframework|jakarta\.(persistence|validation)|io\.swagger' \
  src/main/java/com/renanloureiroo/pitaco/core \
  src/main/java/com/renanloureiroo/pitaco/modules/app/domain \
  src/main/java/com/renanloureiroo/pitaco/modules/app/application
```

Sem saída. Framework não entra em `core`, `domain` nem `application`.

```bash
grep -rn 'plainSecret\|plainText' src/main/java/com/renanloureiroo/pitaco/modules/app/infra/database
```

Sem saída: o segredo em claro não passa pela persistência.
