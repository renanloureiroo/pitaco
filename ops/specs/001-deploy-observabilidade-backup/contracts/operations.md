# Contrato operacional — comandos, saídas e códigos de saída

**Feature**: `001-deploy-observabilidade-backup` · Comandos rodam na raiz do repositório, no
servidor, com o `.env` preenchido.

Este documento descreve a interface que quem opera usa: o que se digita, o que volta e como se
distingue sucesso de falha. Mensagens podem ganhar palavras; os prefixos e pares `chave=valor`
abaixo são o que vale para filtrar log.

---

## Stack

| Ação | Comando | Resultado esperado |
| --- | --- | --- |
| Criar a rede compartilhada (uma vez por máquina) | `docker network create edge` | rede `edge` existe |
| Subir/atualizar | `docker compose up -d --build` | `postgres` saudável, `api` e `painel` de pé |
| Subir o backup | `docker compose --profile backup up -d` | serviço `backup` em laço |
| Construir um serviço por vez (pouca memória) | `docker compose build api` · `docker compose build painel` · `docker compose up -d` | mesmo resultado, sem builds em paralelo |
| Estado | `docker compose ps` | nenhum serviço reiniciando em laço; nenhuma porta publicada |
| Logs | `docker compose logs --tail 200 api` | migration com erro aparece aqui, na subida |
| Stack inteira na máquina local | `docker compose -f compose.yaml -f compose.local.yaml up -d --build` | painel em `:3001`, API em `:8080/api`, Grafana em `:3000` |

### Conferir que está no ar (sem porta publicada)

```bash
docker run --rm --network pitaco_internal curlimages/curl -fsS http://api:8080/api/actuator/health
# {"status":"UP"}  (pode trazer mais campos)

docker run --rm --network pitaco_internal curlimages/curl -fsS -o /dev/null -w '%{http_code}\n' \
  http://painel:3001/aplicacoes
# 200
```

### Rotas de ferramenta da API

| Rota | Documentação desligada (padrão) | Documentação ligada |
| --- | --- | --- |
| `GET /api/actuator/health` | `200 {"status":"UP"}` | igual |
| `GET /api/actuator/info` | `200` | igual |
| `GET /api/v3/api-docs` | sem documentação | `200`, OpenAPI com `servers` no esquema/host do túnel |
| `GET /api/swagger-ui.html` | sem documentação | UI |
| `GET` ou `HEAD` em `/`, `/api`, `/api/` | sem redirecionamento | `302`, `Location: /api/swagger-ui.html` |
| `POST /api` | sem redirecionamento | sem redirecionamento |

---

## `backup.sh` (dentro da imagem de backup)

```text
backup.sh            uma execução
backup.sh --loop     uma execução agora e depois a cada BACKUP_INTERVAL
```

Uso pela composição: `docker compose --profile backup run --rm backup backup.sh`.

| Situação | Saída | Código |
| --- | --- | --- |
| Enviado | `<instante> backup enviado arquivo=pitaco-<…>.dump bytes=<n>` (stdout) | `0` |
| Cópia antiga removida pela retenção | `<instante> backup removido arquivo=pitaco-<…>.dump` (uma por cópia) | — |
| `BACKUP_REMOTE` ausente | `<instante> backup BACKUP_REMOTE ausente: defina o destino no .env` (stderr) | `1` |
| Dump, conferência ou envio falhou | mensagem da ferramenta que falhou (stderr); nada é enviado depois da falha | `≠ 0` |
| Argumento desconhecido | `uso: backup.sh [--loop]` (stderr) | `2` |
| No laço, uma execução falhou | `<instante> backup falhou; nova tentativa em <n>s` (stderr); o laço continua | — |

`<instante>` é `yyyy-MM-ddTHH:mm:ssZ`, UTC.

---

## `restore.sh` (dentro da imagem de backup)

```text
restore.sh --yes                       o dump mais recente do BACKUP_REMOTE
restore.sh --yes pitaco-<data>.dump    um dump específico do BACKUP_REMOTE
restore.sh --yes /caminho/local.dump   um arquivo já baixado (precisa existir)
```

Alvo: o banco em `PGHOST`/`PGPORT`/`PGUSER`/`PGPASSWORD`/`PGDATABASE`. Uso pela composição, com
quem escreve parado:

```bash
docker compose stop api painel
docker compose --profile backup run --rm backup restore.sh --yes
docker compose start api painel
```

| Situação | Saída | Código |
| --- | --- | --- |
| Restaurado | `<instante> restore restaurado arquivo=<nome> banco=<PGDATABASE>` | `0` |
| Sem `--yes` | `restore.sh substitui o conteúdo de <PGDATABASE> em <PGHOST>. Repita com --yes.` (stderr) | `2` |
| Sem arquivo local e sem `BACKUP_REMOTE` | `BACKUP_REMOTE ausente: informe um arquivo local ou o destino dos backups` | `1` |
| Destino sem nenhum `pitaco-*.dump` | `nenhum backup em <BACKUP_REMOTE>` | `1` |
| Arquivo ilegível ou erro de restore | mensagem do `pg_restore`; para no primeiro erro | `≠ 0` |

Efeito: objetos existentes são removidos e recriados (`--clean --if-exists`), sem dono
(`--no-owner`). Repetível depois de uma interrupção.

---

## `ops/backup/restore-test.sh` (no host)

```text
ops/backup/restore-test.sh [arquivo.env]
```

`arquivo.env` (o `.env` da raiz, por exemplo) fornece `BACKUP_REMOTE` e `RCLONE_CONFIG_*`; sem ele,
as variáveis vêm do ambiente. Precisa de Docker no host.

Passos: constrói a imagem de `ops/backup`; cria rede `pitaco-restore-test-<pid>` e banco
`pitaco-restore-db-<pid>` (`postgres:16-alpine`); espera prontidão por TCP até 60 s; roda
`restore.sh --yes` contra ele; confere; remove banco e rede em qualquer desfecho.

| Situação | Saída | Código |
| --- | --- | --- |
| Restore conferido | `ok: migrations=<n> ultima=<versão>` e `ok: applications=<n> survey_displays=<n>` | `0` |
| Banco descartável não ficou pronto | `o Postgres descartável não ficou pronto` | `1` |
| Histórico de migrations ausente | `falhou: flyway_schema_history ausente` | `1` |
| Nenhuma migration aplicada, ou alguma com falha | `falhou: migrations aplicadas=<n> com_falha=<n>` | `1` |
| Falha do restore | saída do `restore.sh` (tabela acima) | `≠ 0` |

Sem object storage:

```bash
BACKUP_REMOTE=:local:/backups BACKUP_LOCAL_DIR="$PWD/backups" ops/backup/restore-test.sh
```

---

## Voltar de versão

```bash
git log --oneline -5
git checkout <commit-bom>
docker compose up -d --build
```

Se a versão nova aplicou migration e a anterior não sobe contra o schema novo (`ddl-auto:
validate` recusa a partida), restaurar o backup feito antes da publicação.
