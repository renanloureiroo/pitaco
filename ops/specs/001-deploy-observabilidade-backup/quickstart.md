# Quickstart — validar `001-deploy-observabilidade-backup`

Como provar que a operação funciona de ponta a ponta. Não traz arquivo de implementação: traz o
que rodar e o que esperar. Variáveis em [`contracts/environment.md`](./contracts/environment.md);
comandos, saídas e códigos em [`contracts/operations.md`](./contracts/operations.md); portões em
[`contracts/ci.md`](./contracts/ci.md).

## Pré-requisitos

- Docker Engine com Compose v2
- `shellcheck` (só para o passo 1)
- Para o passo 6, um Grafana LGTM alcançável pelo nome `lgtm` na rede `edge` — ou use a
  sobreposição local do passo 7, que sobe um

---

## 1. O portão `compose`, na sua máquina

```bash
cp .env.example .env
sed -i -e 's/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=ci/' \
       -e 's#^BACKUP_REMOTE=.*#BACKUP_REMOTE=:local:/backups#' .env
docker compose -f compose.yaml config --quiet
docker compose -f compose.yaml --profile backup config --quiet
docker compose -f compose.yaml -f compose.local.yaml config --quiet
shellcheck ops/backup/*.sh
docker build --tag pitaco-backup:ci ops/backup
```

Todos sem erro. É o mesmo que `.github/workflows/compose.yml` roda.

## 2. Os testes de configuração da API

```bash
cd backend
./mvnw test -Dtest='ContextPathTest,OpenApiConfigTest,SwaggerUiRedirectConfigurationTest,SwaggerUiRedirectValveTest,SwaggerUiRedirectE2ETest,ApiExceptionHandlerTest'
```

Verde. Cobrem: prefixo `/api`; URL anunciada em `https` com `X-Forwarded-Proto: https`;
redirecionamento para a documentação só com ela ligada; `traceId` no corpo de erro. O E2E sobe
Postgres e LGTM por Testcontainers.

## 3. Subir a stack de produção

```bash
docker network create edge          # uma vez por máquina
cp .env.example .env                # POSTGRES_PASSWORD=$(openssl rand -base64 24)
docker compose up -d --build
docker compose ps
```

Esperado:

| Conferência | Esperado |
| --- | --- |
| `docker compose ps` | `postgres` (healthy), `api`, `painel` em `running`; coluna de portas sem mapeamento para o host |
| `docker compose logs api` | migrations aplicadas e partida sem erro de validação de schema |
| saúde da API (abaixo) | `{"status":"UP"}` |
| painel (abaixo) | `200` |

```bash
docker run --rm --network pitaco_internal curlimages/curl -fsS http://api:8080/api/actuator/health
docker run --rm --network pitaco_internal curlimages/curl -fsS -o /dev/null -w '%{http_code}\n' \
  http://painel:3001/aplicacoes
```

Os aliases na rede compartilhada:

```bash
docker network inspect edge --format '{{range .Containers}}{{.Name}} {{end}}'
```

Lista os containers `pitaco-api-1` e `pitaco-painel-1` (o túnel os encontra por `pitaco-api:8080`
e `pitaco-painel:3001`).

## 4. Superfície mínima

```bash
curl_int() { docker run --rm --network pitaco_internal curlimages/curl -s -o /dev/null -w '%{http_code}\n' "$@"; }
curl_int http://api:8080/api/v3/api-docs          # não 200: documentação desligada
curl_int http://api:8080/api/actuator/metrics     # não 200: só health e info
curl_int http://api:8080/api                      # não 302: sem redirecionamento
```

Ligar de propósito: `PITACO_API_DOCS_ENABLED: "true"` no `environment` da `api`,
`docker compose up -d api`, e repetir — `/api/v3/api-docs` passa a `200` e `/api` a `302` com
`Location: /api/swagger-ui.html`. Desligar de novo depois.

## 5. Banco sem core dump

```bash
docker compose exec postgres sh -c 'ulimit -c'
```

Esperado: `0`.

## 6. Observabilidade

1. Provocar um erro e guardar o `traceId`:

   ```bash
   docker run --rm --network pitaco_internal curlimages/curl -s \
     http://api:8080/api/applications/nao-existe
   ```

   Corpo `application/problem+json` com `code` e `traceId`.
2. No Grafana, **Explore → Tempo**: buscar o `traceId` — o trace da requisição aparece.
3. **Explore → Loki**: filtrar pelo mesmo `traceId` — os logs da requisição aparecem.
4. **Explore → Prometheus**: métricas da JVM e de HTTP do serviço `pitaco`.
5. `docker compose logs --tail 5 api`: linhas de acesso no formato
   `[…] "GET /api/applications/nao-existe HTTP/1.1" 404 <bytes> <n>ms`.

## 7. Stack inteira na máquina local

```bash
docker compose down                                   # se o passo 3 estiver de pé
docker compose -f compose.yaml -f compose.local.yaml up -d --build
```

Painel em `http://localhost:3001`, API em `http://localhost:8080/api` (com Swagger em
`/api/swagger-ui.html`), Grafana em `http://localhost:3000`. Os passos 6.2 a 6.4 valem aqui.

## 8. Backup, restore e ensaio — sem object storage

Com dumps num diretório local, o ensaio funciona igual ao real:

```bash
mkdir -p backups
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > backups/pitaco-$(date -u +%Y%m%dT%H%M%SZ).dump
BACKUP_REMOTE=:local:/backups BACKUP_LOCAL_DIR="$PWD/backups" ops/backup/restore-test.sh
```

Esperado: `ok: migrations=<n> ultima=<versão>` e `ok: applications=<n> survey_displays=<n>`;
`docker ps -a | grep pitaco-restore` vazio depois.

Recusa sem confirmação:

```bash
docker compose --profile backup run --rm backup restore.sh ; echo "código=$?"
```

Esperado: `restore.sh substitui o conteúdo de pitaco em postgres. Repita com --yes.` e `código=2`.

Backup sem destino falha alto:

```bash
docker compose --profile backup run --rm -e BACKUP_REMOTE= backup backup.sh ; echo "código=$?"
```

Esperado: `… backup BACKUP_REMOTE ausente: defina o destino no .env` e `código=1`.

## 9. Backup com object storage

Com `BACKUP_REMOTE` e `RCLONE_CONFIG_PITACOBACKUP_*` preenchidos no `.env`:

```bash
docker compose --profile backup run --rm backup backup.sh
docker compose --profile backup up -d                 # liga o laço diário
ops/backup/restore-test.sh .env
```

Esperado: `… backup enviado arquivo=pitaco-<instante>.dump bytes=<n>`; o arquivo no bucket; o
ensaio com as duas linhas `ok:`. Com `BACKUP_KEEP=2` e três execuções, o destino fica com dois
arquivos e o log mostra um `removido`.

## 10. Portões de CI

| Pull request que mexe em | Workflows que rodam |
| --- | --- |
| `backend/**` | `backend` |
| `painel/**` | `painel` |
| `sdk/src/**` | `sdk` e `painel` |
| `sdk/example/**` | `sdk` |
| `compose.yaml`, `compose.local.yaml`, `.env.example` ou `ops/**` | `compose` |
