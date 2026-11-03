# Data Model: Operação — implantação auto-hospedada, observabilidade e backup

**Feature**: `001-deploy-observabilidade-backup` · **Date**: 2026-09-08

Esta feature **não cria nem altera tabela, entidade ou migration**. O "modelo" dela é o estado
operacional: o que persiste entre reconstruções, por onde os containers se enxergam, que variáveis
configuram cada serviço, qual é o formato de um backup e que forma têm os sinais de telemetria.

---

## 1. Serviços da stack

| Serviço | Origem | Redes | Porta interna | Reinício | Depende de |
| --- | --- | --- | --- | --- | --- |
| `postgres` | imagem `postgres:16-alpine` | `internal` | 5432 | `unless-stopped` | — |
| `api` | build de `./backend` | `internal`, `edge` (alias `pitaco-api`) | 8080 | `unless-stopped` | `postgres` saudável |
| `painel` | build da raiz com `painel/Dockerfile` | `internal`, `edge` (alias `pitaco-painel`) | 3001 | `unless-stopped` | `api` iniciada |
| `backup` *(perfil `backup`)* | build de `./ops/backup` | `internal` | — | `unless-stopped` | `postgres` saudável |

Projeto Compose: `name: pitaco`. Em produção, nenhum serviço declara `ports`.

**Saúde do banco**: `pg_isready -U $POSTGRES_USER -d $POSTGRES_DB` a cada 10 s, tempo limite de
5 s, 5 tentativas. É a condição que libera a subida da `api` e do `backup`.

**Limites do banco**: `ulimits.core: 0` — nenhum core dump é gravado na pasta de dados.

### Sobreposição local (`compose.local.yaml`)

| Serviço | O que muda |
| --- | --- |
| `api` | publica `8080:8080`; `PITACO_API_DOCS_ENABLED=true` |
| `painel` | publica `3001:3001` |
| `lgtm` *(novo)* | `grafana/otel-lgtm:0.11.10`, `TZ=UTC`, publica `3000:3000`, rede `edge` |
| rede `edge` | deixa de ser externa: o Compose a cria |

---

## 2. Redes

| Rede | Dona | Quem está nela | Para quê |
| --- | --- | --- | --- |
| `internal` (`pitaco_internal`) | a stack | `postgres`, `api`, `painel`, `backup` | banco alcançável só por aqui; painel → API |
| `edge` | a máquina (`external: true`) | `api`, `painel`, túnel reverso, LGTM | túnel → API/painel; API → LGTM |

A `edge` é criada uma vez por máquina (`docker network create edge`) e sobrevive a `docker compose
down` do Pitaco. A `internal` recebe o prefixo do projeto (`pitaco_internal`), que é o nome usado
para conferências com container descartável.

---

## 3. Volumes

| Volume | Montado em | Conteúdo | Ciclo de vida |
| --- | --- | --- | --- |
| `pg_data` (`pitaco_pg_data`) | `postgres:/var/lib/postgresql/data` | o banco inteiro | sobrevive a `--build` e a recriação do container; só some com `down -v` |

Nenhum outro serviço tem estado persistente. A API e o painel são reconstruíveis a partir do
repositório; o backup trabalha em diretório temporário (`mktemp -d`) apagado ao fim de cada
execução.

Caches de build (BuildKit): `/root/.m2` no build da API e `/root/.npm` no build do painel. Não
são volumes da stack; aceleram a reconstrução no servidor.

---

## 4. Arquivo de ambiente (`.env`)

Copiado de `.env.example` e preenchido só no servidor. Nunca versionado (`.gitignore`) nem copiado
para imagem (`.dockerignore`). Contrato completo em
[`contracts/environment.md`](./contracts/environment.md).

| Variável | Obrigatória | Padrão | Consumida por |
| --- | --- | --- | --- |
| `POSTGRES_DB` | sim | `pitaco` (no modelo) | `postgres`, `api`, `backup` |
| `POSTGRES_USER` | sim | `pitaco` (no modelo) | `postgres`, `api`, `backup` |
| `POSTGRES_PASSWORD` | sim | vazio (gerar com `openssl rand -base64 24`) | `postgres`, `api`, `backup` |
| `BACKUP_REMOTE` | com o perfil `backup` | vazio | `backup`, ensaio de restore |
| `BACKUP_KEEP` | não | `14` | `backup` |
| `BACKUP_INTERVAL` | não | `86400` | `backup` |
| `RCLONE_CONFIG_PITACOBACKUP_TYPE` | com o perfil `backup` | `s3` | `backup`, ensaio |
| `RCLONE_CONFIG_PITACOBACKUP_PROVIDER` | com o perfil `backup` | `Other` | `backup`, ensaio |
| `RCLONE_CONFIG_PITACOBACKUP_ACCESS_KEY_ID` | com o perfil `backup` | vazio | `backup`, ensaio |
| `RCLONE_CONFIG_PITACOBACKUP_SECRET_ACCESS_KEY` | com o perfil `backup` | vazio | `backup`, ensaio |
| `RCLONE_CONFIG_PITACOBACKUP_ENDPOINT` | conforme o provedor | vazio | `backup`, ensaio |
| `RCLONE_CONFIG_PITACOBACKUP_REGION` | conforme o provedor | vazio | `backup`, ensaio |
| `PITACO_API_DOCS_ENABLED` | não | ausente = `false` | `api` (comentada no modelo) |

---

## 5. Backup

### Dump

| Atributo | Valor |
| --- | --- |
| Nome | `pitaco-<yyyyMMdd>T<HHmmss>Z.dump`, instante UTC da execução (ex.: `pitaco-20260912T034700Z.dump`) |
| Formato | custom do PostgreSQL (`pg_dump --format=custom`), comprimido |
| Dono dos objetos | omitido (`--no-owner`) — restaurável com outro usuário |
| Escopo | o banco `PGDATABASE` inteiro: schema, dados e `flyway_schema_history` |
| Conferência | `pg_restore --list` antes do envio e antes de cada restore |

A ordem alfabética dos nomes é a ordem cronológica. É nela que a retenção apaga e que o restore
escolhe "o mais recente".

### Destino

Um remoto do rclone chamado `pitacobackup`, definido só por `RCLONE_CONFIG_PITACOBACKUP_*`,
seguido de bucket e prefixo: `pitacobackup:<bucket>/<prefixo>`. Para ensaio, `:local:/backups`
(remoto local embutido do rclone) com o diretório do host montado em `/backups`.

### Retenção

Depois de cada envio bem-sucedido: lista `pitaco-*.dump` no destino, ordena, apaga os
`total − BACKUP_KEEP` mais antigos. Arquivos com outro nome não entram na conta nem são apagados.

### Ciclo de vida de uma execução

```text
pg_dump ──▶ pg_restore --list ──▶ rclone copyto ──▶ log "enviado" ──▶ retenção ──▶ log "removido" (0..n)
   │               │                    │
   └── falha ──────┴────────────────────┴──▶ sai ≠ 0 (no laço: log "falhou", dorme, tenta de novo)
```

---

## 6. Sinais de telemetria

| Sinal | Origem na API | Caminho OTLP | Destino no LGTM |
| --- | --- | --- | --- |
| Trace | instrumentação do Spring Boot, amostragem 1.0 | `/v1/traces` | Tempo |
| Log | Logback → `OpenTelemetryAppender`, com MDC (`traceId`, `spanId`) e pares chave-valor | `/v1/logs` | Loki |
| Métrica | Micrometer `OtlpMeterRegistry` | `/v1/metrics` | Prometheus |

Base: `OTEL_EXPORTER_OTLP_ENDPOINT` (`http://lgtm:4318`, OTLP sobre HTTP). Nome do serviço:
`spring.application.name` = `pitaco`.

**Log de acesso**: escrito pelo Tomcat direto em `/dev/stdout`, sem passar pelo Logback — fica no
log do container (`docker compose logs api`), não no Loki:

```text
%t "%r" %s %b %{ms}Tms
[03/Nov/2026:14:02:11 +0000] "GET /api/applications?page=0 HTTP/1.1" 200 1532 12ms
```

**Correlação**: o corpo de erro (RFC 9457) traz `traceId` quando o tracing está ligado; o mesmo
valor está no MDC de cada log da requisição e identifica o trace no Tempo.
