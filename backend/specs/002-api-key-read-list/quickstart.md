# Quickstart — validando a leitura das chaves de API

**Feature**: `002-api-key-read-list`

Como provar, de ponta a ponta, que a fatia funciona. Detalhes de tipos estão em
[data-model.md](./data-model.md); o contrato HTTP, em
[contracts/api-keys-read.openapi.yaml](./contracts/api-keys-read.openapi.yaml); o porquê de cada
escolha, em [research.md](./research.md).

## Pré-requisitos

- Java 21 e Docker de pé (Testcontainers e o `docker-compose` do `spring-boot-docker-compose`).
- Nada mais: Postgres, Redis e a stack LGTM sobem junto com a aplicação.

## Portão automatizado

```bash
./mvnw verify
```

Verde, sem teste ignorado. É o portão obrigatório da constituição — nada desta fatia conta como
pronto antes dele.

Durante o desenvolvimento, os recortes úteis:

```bash
./mvnw test -Dtest=ApiKeyTest                                    # status derivado, JUnit puro
./mvnw test -Dtest='ListApiKeysUseCaseTest,GetApiKeyUseCaseTest' # sobre o fake in-memory
./mvnw test -Dtest=ListApiKeysQueryDTOTest                       # constraints e padrões
./mvnw test -Dtest='ListApiKeysE2ETest,GetApiKeyE2ETest'         # precisa de Docker
```

## Validação manual

```bash
./mvnw spring-boot:run
```

API em `http://localhost:8080/api` · Swagger em `http://localhost:8080/api/swagger-ui.html`.

### 1. Uma aplicação com três chaves, uma delas revogada

```bash
APP=$(curl -sS -X POST http://localhost:8080/api/applications \
  -H 'Content-Type: application/json' \
  -d '{"name":"Acme App","slug":"acme-app"}' | jq -r .id)

for LABEL in "app iOS" "site de marketing" "homologação"; do
  curl -sS -X POST "http://localhost:8080/api/applications/$APP/api-keys" \
    -H 'Content-Type: application/json' \
    -d "{\"label\":\"$LABEL\"}" | jq -r .id
done
```

Guarde o identificador da terceira e revogue-a:

```bash
KEY=<id da terceira chave>
curl -sS -o /dev/null -w '%{http_code}\n' -X DELETE \
  "http://localhost:8080/api/applications/$APP/api-keys/$KEY"   # 204
```

### 2. Listagem — o caminho feliz

```bash
curl -sS "http://localhost:8080/api/applications/$APP/api-keys" | jq
```

Esperado: `200`, `total: 3`, `page: 0`, `size: 20`, `totalPages: 1`, três itens da mais recente
para a mais antiga, um deles com `"status": "revoked"` e `revokedAt` preenchido.

**A conferência que importa** — nenhum segredo em lugar nenhum:

```bash
curl -sS "http://localhost:8080/api/applications/$APP/api-keys" \
  | jq -e 'tostring | test("secret|hash") | not' && echo "sem segredo na resposta ✓"
```

### 3. Filtro por estado (D-01)

```bash
curl -sS "http://localhost:8080/api/applications/$APP/api-keys?status=active"  | jq '.total'  # 2
curl -sS "http://localhost:8080/api/applications/$APP/api-keys?status=revoked" | jq '.total'  # 1
```

Sem filtro devolve as três — revogada aparece por padrão.

### 4. Paginação e travessia sem repetir nem omitir (SC-003)

```bash
P0=$(curl -sS "http://localhost:8080/api/applications/$APP/api-keys?page=0&size=2" | jq -r '.items[].id')
P1=$(curl -sS "http://localhost:8080/api/applications/$APP/api-keys?page=1&size=2" | jq -r '.items[].id')

echo "$P0
$P1" | sort | uniq -d   # vazio: nenhum id em duas páginas
echo "$P0
$P1" | sort -u | wc -l  # 3: todas as chaves foram alcançadas
```

Página além do fim devolve lista vazia com o total correto, não erro:

```bash
curl -sS "http://localhost:8080/api/applications/$APP/api-keys?page=99" | jq '.items, .total'
# []
# 3
```

### 5. Consulta individual

```bash
curl -sS "http://localhost:8080/api/applications/$APP/api-keys/$KEY" | jq
```

Esperado: `200`, `"status": "revoked"`, `revokedAt` presente, sem campo de segredo — os mesmos
campos de um item da lista (FR-015).

### 6. Isolamento entre aplicações (SC-004)

```bash
OUTRA=$(curl -sS -X POST http://localhost:8080/api/applications \
  -H 'Content-Type: application/json' \
  -d '{"name":"Outra","slug":"outra"}' | jq -r .id)

curl -sS "http://localhost:8080/api/applications/$OUTRA/api-keys" | jq '.total'   # 0

curl -sS -o /dev/null -w '%{http_code}\n' \
  "http://localhost:8080/api/applications/$OUTRA/api-keys/$KEY"                   # 404
```

A chave existe — só não sob essa aplicação. A resposta não diz isso (D-08).

### 7. Os caminhos de recusa

```bash
# aplicação inexistente → 404 application.not_found
curl -sS "http://localhost:8080/api/applications/00000000-0000-0000-0000-000000000000/api-keys" | jq '.code'

# identificador de aplicação malformado → 404 application.not_found, indistinguível
curl -sS "http://localhost:8080/api/applications/nao-e-uuid/api-keys" | jq '.code'

# chave inexistente → 404 api_key.not_found
curl -sS "http://localhost:8080/api/applications/$APP/api-keys/00000000-0000-0000-0000-000000000000" | jq '.code'

# tamanho de página acima do teto → 400 request.invalid
curl -sS "http://localhost:8080/api/applications/$APP/api-keys?size=500" | jq '.code, .errors'

# página negativa → 400 request.invalid
curl -sS "http://localhost:8080/api/applications/$APP/api-keys?page=-1" | jq '.errors'

# estado inexistente → 400 request.invalid
curl -sS "http://localhost:8080/api/applications/$APP/api-keys?status=suspensa" | jq '.errors'
```

Todas em `application/problem+json`, com `code` e `traceId`. Nenhuma repete o valor rejeitado.

### 8. Aplicação inativa continua legível (FR-011)

Desative a aplicação pelo caminho que o módulo oferecer e repita o passo 2: a listagem responde
`200` com as mesmas chaves. Emitir uma chave nova, no mesmo estado, segue respondendo `422`
`application.inactive` — é a diferença entre ler e criar (D-07).

### 9. Nada mudou (FR-018, SC-006)

Repita o passo 2 depois de todos os anteriores, inclusive das falhas: `total` continua `3` e o
`revokedAt` da chave revogada é o mesmo instante da primeira revogação. Leitura não escreve.
