# Quickstart — validar `005-application-read-list`

Como provar que a fatia funciona de ponta a ponta. Não traz código de implementação: traz o
que rodar e o que esperar.

## Pré-requisitos

- Java 21 e o wrapper Maven do repositório
- Docker de pé (Testcontainers sobe Postgres e a stack LGTM nos testes E2E)

---

## 1. Suíte completa — o portão

```bash
./mvnw verify
```

Verde, sem teste ignorado. É o portão da constituição antes de qualquer PR.

Sem Docker, dá para rodar tudo menos o que sobe contexto:

```bash
./mvnw test -Dtest='!*E2ETest,!ContextPathTest,!OpenApiConfigTest,!PitacoApplicationTests'
```

## 2. Classes desta fatia, isoladas

```bash
./mvnw test -Dtest='ListApplicationsUseCaseTest,GetApplicationUseCaseTest,ListApplicationsQueryDTOTest'
./mvnw test -Dtest='ListApplicationsE2ETest,GetApplicationE2ETest'
```

---

## 3. Exercitar à mão

```bash
./mvnw spring-boot:run
```

API em `http://localhost:8080/api` · Swagger em `http://localhost:8080/api/swagger-ui.html`.

### Semear e listar

```bash
# cria duas aplicações
curl -s -X POST http://localhost:8080/api/applications \
  -H 'Content-Type: application/json' \
  -d '{"name":"Acme App","retentionDays":180,"openTextRetentionDays":30}'

curl -s -X POST http://localhost:8080/api/applications \
  -H 'Content-Type: application/json' -d '{"name":"Outra App"}'

# lista: as duas, mais recente primeiro
curl -s http://localhost:8080/api/applications | jq
```

Esperado: `total: 2`, `page: 0`, `size: 20`, `totalPages: 1`, e `items[0]` sendo a **Outra
App** — a mais recente vem primeiro.

### Consultar, seguindo o `Location`

```bash
LOCATION=$(curl -s -i -X POST http://localhost:8080/api/applications \
  -H 'Content-Type: application/json' -d '{"name":"Terceira App","quietPeriodDays":15}' \
  | tr -d '\r' | awk '/^Location:/ {print $2}')

curl -s "$LOCATION" | jq
```

Esperado: `200` com `quietPeriodDays: 15`, `updatedAt` presente, e **sem** as chaves
`retentionDays` e `openTextRetentionDays` — prazo não configurado é campo ausente, nunca zero.
Este é o passo que prova a dívida paga: antes desta fatia o `Location` da criação apontava para
uma rota inexistente.

### Filtro, paginação e recusas

```bash
curl -s 'http://localhost:8080/api/applications?status=active' | jq '.total'
curl -s 'http://localhost:8080/api/applications?page=99'      | jq '.items, .total'
curl -s 'http://localhost:8080/api/applications?size=0'       | jq
curl -s 'http://localhost:8080/api/applications?status=zumbi' | jq
curl -s 'http://localhost:8080/api/applications/nao-existe'   | jq
curl -s 'http://localhost:8080/api/applications/nao-existe' -H 'X-Pitaco-Key: qualquer' | jq
```

Esperado, em ordem:

| Chamada | Resultado |
|---|---|
| `status=active` | total só das ativas |
| `page=99` | `items: []` com o `total` correto — não é erro |
| `size=0` | `400`, `application/problem+json`, apontando o campo |
| `status=zumbi` | `400`, apontando os valores aceitos |
| id inexistente | `404` com `"code": "application.not_found"` |
| id inexistente + header de chave | `403` com `"code": "api_key.forbidden_surface"` — a superfície vence antes do 404 |

Todo corpo de erro traz `code` e `traceId`.

---

## 4. Conferir os índices

```bash
docker exec -it $(docker ps -qf name=postgres) \
  psql -U pitaco -d pitaco -c '\di idx_applications*'
```

Esperado: `idx_applications_created_at` e `idx_applications_status_created_at`.

---

## Sinais de que a fatia está pronta

- [ ] `./mvnw verify` verde
- [ ] Listagem paginada, ordenada da mais recente para a mais antiga, com ativas e inativas
- [ ] Filtro por estado funcionando; valor desconhecido vira `400`, não `500`
- [ ] Página além do fim devolve vazio com o total correto, nunca erro
- [ ] `Location` da criação abre a consulta e devolve a aplicação
- [ ] Prazo não configurado ausente do JSON, nunca `0`
- [ ] Aplicação inativa aparece na listagem e é encontrada na consulta
- [ ] `404` com `application.not_found` para id inexistente **e** malformado
- [ ] `403` com `api_key.forbidden_surface` nas duas rotas com header de chave
- [ ] Swagger declarando todos os status de cada rota, com o schema de erro certo
- [ ] O conjunto de aplicações não muda depois de nenhuma leitura, nem das que falham
- [ ] Os dois índices existem no banco

Detalhe de campos e status em [`contracts/applications-read.md`](contracts/applications-read.md);
formato das portas e dos casos de uso em [`data-model.md`](data-model.md).
