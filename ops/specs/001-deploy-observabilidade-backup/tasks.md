---
description: "Task list — 001-deploy-observabilidade-backup"
---

# Tasks: Operação — implantação auto-hospedada, observabilidade e backup

**Input**: documentos de desenho em `ops/specs/001-deploy-observabilidade-backup/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/environment.md`, `contracts/operations.md`, `contracts/ci.md`

**Tests**: incluídos. O comportamento que a API ganha nasce com teste no backend (Princípio III da constituição do backend); na infraestrutura, a verificação vem antes do arquivo que ela valida — a validação da composição antes dos serviços, o ensaio de restore antes de ligar o backup em produção.

**Organization**: agrupadas por história de usuário, para que cada uma seja implementável e verificável isoladamente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável (arquivo distinto, sem dependência pendente)
- **[Story]**: US1, US2, US3, US4, US5
- Todo caminho é relativo à raiz do repositório

## Path Conventions

Configuração da API em `backend/src/main/resources/`; classes da API em `backend/src/main/java/com/renanloureiroo/pitaco/infra/` (abreviado `…/infra/`); testes em `backend/src/test/java/com/renanloureiroo/pitaco/` (abreviado `…/test/`).

---

## Phase 1: Setup (Infraestrutura compartilhada)

**Purpose**: o que impede segredo de entrar no repositório ou numa imagem, antes de qualquer arquivo que o use.

- [X] T001 Criar `.env.example` na raiz com `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (vazio, com a dica `openssl rand -base64 24`), o bloco de backup (`BACKUP_REMOTE`, `BACKUP_KEEP=14`, `BACKUP_INTERVAL=86400`, `RCLONE_CONFIG_PITACOBACKUP_*`) e `PITACO_API_DOCS_ENABLED` comentada
- [X] T002 [P] Acrescentar `.env` ao `.gitignore` da raiz
- [X] T003 [P] Criar `.dockerignore` na raiz excluindo `node_modules`, `.next`, `target`, `.git`, `.specify`, `specs`, `docs`, `ops`, `*.md`, `.env*` (exceto `.env.example`), testes e artefatos de teste do painel e `sdk/example/`
- [X] T004 [P] Excluir `application-local.yml`, `specs`, `docs` e `*.md` do contexto de build em `backend/.dockerignore`
- [X] T005 [P] Excluir testes (inclusive `vitest.setup.ts`), `.env*` e artefatos de teste do contexto de build em `painel/.dockerignore`

---

## Phase 2: Foundational (Pré-requisitos bloqueantes)

**Purpose**: o esqueleto da composição — projeto, redes e volume — sobre o qual as histórias acrescentam serviços.

**⚠️ CRITICAL**: nenhuma história pode começar antes desta etapa terminar.

- [X] T006 Criar `compose.yaml` na raiz com `name: pitaco`, o volume `pg_data`, a rede `internal` e a rede `edge` declarada `external: true`, com o comentário de que ela é criada uma vez por máquina (D-02, D-03)
- [X] T007 Confirmar que o esqueleto valida com `docker compose -f compose.yaml config --quiet` usando um `.env` copiado de `.env.example`

**Checkpoint**: composição válida, ainda sem serviço.

---

## Phase 3: User Story 1 - Subir a stack de produção num servidor próprio (Priority: P1) 🎯 MVP

**Goal**: banco, API e painel no ar, sem porta publicada, alcançáveis pelo túnel na rede compartilhada.

**Independent Test**: `quickstart.md` passo 3 — saúde `UP`, painel `200`, nenhuma porta publicada.

### Tests for User Story 1 ⚠️

- [X] T008 [P] [US1] Escrever `…/test/infra/http/config/OpenApiConfigTest.java` com o caso que pede `/v3/api-docs` com `X-Forwarded-Proto: https` e `X-Forwarded-Host` e confere `servers` em `https` (falha antes de T012)
- [X] T009 [P] [US1] Escrever `…/test/infra/http/config/ContextPathTest.java` provando que as rotas e o ferramental respondem sob `/api` e nada fora dele

### Implementation for User Story 1

- [X] T010 [US1] Criar `backend/Dockerfile` em dois estágios: `maven:3.9-eclipse-temurin-21` com `dependency:go-offline` antes de copiar `src` e cache montado em `/root/.m2`, `package -DskipTests`; final `eclipse-temurin:21-jre` (glibc, D-10) com usuário de sistema `app`, `EXPOSE 8080` e `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -Duser.timezone=UTC"` (D-11)
- [X] T011 [P] [US1] Criar `painel/Dockerfile` em `node:24-bookworm-slim`: copiar `sdk/`, `npm ci && npm run build`; em `painel/`, `npm ci --install-links`, `npm run build`; estágio final com `.next/standalone`, `.next/static` e `public`, usuário `app`, `PORT=3001`, `HOSTNAME=0.0.0.0`, `NEXT_TELEMETRY_DISABLED=1` (D-10, D-12)
- [X] T012 [US1] Em `backend/src/main/resources/application.yml`: `server.servlet.context-path: /api`, `server.forward-headers-strategy: framework` com o comentário do porquê (D-05), `spring.docker.compose.enabled: false`, `spring.datasource.hikari.maximum-pool-size: 10`, `ddl-auto: validate` e `hibernate.jdbc.time_zone: UTC`
- [X] T013 [US1] Acrescentar o serviço `postgres` em `compose.yaml`: `postgres:16-alpine`, `restart: unless-stopped`, variáveis do `.env`, `TZ`/`PGTZ` em UTC, volume `pg_data`, healthcheck `pg_isready` (10 s / 5 s / 5), só na rede `internal`
- [X] T014 [US1] Acrescentar `ulimits.core: 0` ao serviço `postgres` em `compose.yaml`, com o comentário do incidente de disco (D-13)
- [X] T015 [US1] Acrescentar o serviço `api` em `compose.yaml`: build de `./backend`, `SPRING_DATASOURCE_*` pela rede `internal`, sem `SPRING_PROFILES_ACTIVE`, `depends_on` com `condition: service_healthy`, redes `internal` e `edge` com alias `pitaco-api`
- [X] T016 [US1] Acrescentar o serviço `painel` em `compose.yaml`: build com contexto na raiz e `dockerfile: painel/Dockerfile`, `PITACO_API_URL=http://api:8080/api`, `PORT=3001`, `depends_on: api`, redes `internal` e `edge` com alias `pitaco-painel`
- [X] T017 [US1] Criar `compose.local.yaml` na raiz: portas `8080` e `3001`, `PITACO_API_DOCS_ENABLED=true` na `api`, serviço `lgtm` (`grafana/otel-lgtm` com versão fixa, porta `3000`, rede `edge`) e `edge` com `external: false` (D-04); documentar o uso no cabeçalho de `compose.yaml`
- [X] T018 [US1] Rodar `quickstart.md` passos 3 e 7 e T008/T009 verdes

**Checkpoint**: o Pitaco no ar pelo túnel. MVP operacional.

---

## Phase 4: User Story 2 - Não perder os dados (Priority: P1)

**Goal**: backup fora da máquina, conferido, com retenção, restore com confirmação e ensaio que prova o restore.

**Independent Test**: `quickstart.md` passos 8 e 9.

### Tests for User Story 2 ⚠️

- [X] T019 [US2] Escrever `ops/backup/restore-test.sh`: build da imagem de `ops/backup`, rede e `postgres:16-alpine` descartáveis nomeados por PID, espera de prontidão por TCP (até 60 s), `restore.sh --yes` contra o banco descartável, conferência de `flyway_schema_history` (presente, ao menos uma migration aplicada, nenhuma com falha), linhas `ok:` com migrations, última versão, `applications` e `survey_displays`, e `trap` removendo banco e rede; aceitar arquivo `.env` como argumento, `BACKUP_LOCAL_DIR` e repassar `RCLONE_CONFIG_*` (D-17)

### Implementation for User Story 2

- [X] T020 [P] [US2] Criar `ops/backup/Dockerfile` sobre `postgres:16-alpine` com `rclone`, `/etc/rclone/rclone.conf` vazio e `RCLONE_CONFIG` apontando para ele, `backup.sh` e `restore.sh` em `/usr/local/bin` e `CMD ["backup.sh", "--loop"]` (D-14)
- [X] T021 [P] [US2] Criar `ops/backup/backup.sh` (`sh`, `set -eu`): `pg_dump --format=custom --no-owner` para `pitaco-<UTC>.dump` em diretório temporário, `pg_restore --list` antes do envio, `rclone copyto`, log `enviado arquivo=… bytes=…`, retenção por `BACKUP_KEEP` só sobre `pitaco-*.dump` com log `removido arquivo=…`, recusa sem `BACKUP_REMOTE`, modo `--loop` executando cada rodada em subprocesso e dormindo `BACKUP_INTERVAL` (D-14, D-15, D-18)
- [X] T022 [P] [US2] Criar `ops/backup/restore.sh` (`sh`, `set -eu`): exigir `--yes` (código 2 com o banco e o servidor que seriam substituídos), aceitar o mais recente do destino, um nome do destino ou um arquivo local, `pg_restore --list` e `pg_restore --clean --if-exists --no-owner --exit-on-error`, log `restaurado arquivo=… banco=…` (D-17, D-18)
- [X] T023 [US2] Acrescentar o serviço `backup` em `compose.yaml` com `profiles: [backup]`, build de `./ops/backup`, `PGHOST=postgres` e `PG*` a partir do `.env`, `BACKUP_*` e `RCLONE_CONFIG_PITACOBACKUP_*` com padrões, `depends_on` do banco saudável, só na rede `internal`, e o comentário sobre o laço de reinício sem destino (D-16)
- [X] T024 [US2] Rodar `restore-test.sh` com `BACKUP_REMOTE=:local:/backups` sobre um dump local e confirmar as duas linhas `ok:` e a remoção do banco e da rede descartáveis

**Checkpoint**: backup ligável por perfil e restore provado.

---

## Phase 5: User Story 3 - Ver o que acontece em produção (Priority: P2)

**Goal**: traces, logs e métricas no LGTM por um único endereço, com `traceId` ligando erro, trace e log.

**Independent Test**: `quickstart.md` passo 6.

### Tests for User Story 3 ⚠️

- [X] T025 [P] [US3] Registrar o `LgtmStackContainer` (`grafana/otel-lgtm`) com `@ServiceConnection` ao lado do Postgres em `…/test/infra/TestcontainersConfiguration.java`, para todo E2E exportar contra uma stack real
- [X] T026 [P] [US3] Cobrir em `…/test/infra/http/error/ApiExceptionHandlerTest.java` o `traceId` no corpo de erro quando há span corrente, e sua ausência quando não há

### Implementation for User Story 3

- [X] T027 [US3] Criar `backend/src/main/resources/logback-spring.xml` mantendo o console do Spring Boot e acrescentando o `OpenTelemetryAppender` com `captureMdcAttributes=*`, `captureKeyValuePairAttributes` e `captureLoggerContext` (D-07)
- [X] T028 [US3] Criar `…/infra/observability/OpenTelemetryLogbackConfig.java` instalando o appender no construtor (`OpenTelemetryAppender.install(openTelemetry)`), com o Javadoc explicando por que não num listener tardio
- [X] T029 [US3] Em `backend/src/main/resources/application.yml`: `management.tracing.sampling.probability: 1.0` com o gatilho de revisão comentado, e `server.tomcat.accesslog` ligado em `/dev/stdout`, sem rotação, sem buffer, padrão `%t "%r" %s %b %{ms}Tms` (D-06, D-07)
- [X] T030 [US3] Acrescentar `OTEL_EXPORTER_OTLP_ENDPOINT=http://lgtm:4318` ao serviço `api` em `compose.yaml`, com o comentário dos três sinais, da porta HTTP e da propriedade inexistente `MANAGEMENT_OPENTELEMETRY_OTLP_ENDPOINT` (D-06)
- [X] T031 [US3] Rodar `quickstart.md` passo 6 (ou 7 + 6) e encontrar trace e logs pelo `traceId` de uma resposta de erro

**Checkpoint**: erro em produção rastreável do corpo da resposta até o log.

---

## Phase 6: User Story 4 - Expor só o necessário (Priority: P2)

**Goal**: documentação desligada por padrão e ligável por uma variável; Actuator mínimo.

**Independent Test**: `quickstart.md` passo 4.

### Tests for User Story 4 ⚠️

- [X] T032 [P] [US4] Escrever `…/test/infra/http/config/SwaggerUiRedirectConfigurationTest.java` num `ApplicationContextRunner` mínimo: sem a propriedade ou com `false`, nenhum `SwaggerUiRedirectCustomizer`; com `true`, a valve instalada na engine com `Location` `/api/swagger-ui.html` e o redirecionamento da raiz de contexto do Tomcat desligado
- [X] T033 [P] [US4] Escrever `…/test/infra/http/config/SwaggerUiRedirectValveTest.java` cobrindo os pontos de entrada (`/`, `/api`, `/api/`), os métodos aceitos (`GET`, `HEAD`) e a recusa dos demais
- [X] T034 [P] [US4] Escrever `…/test/infra/http/config/SwaggerUiRedirectE2ETest.java`: `302` com `Location` relativo nas três entradas, `/api` terminando na UI ao seguir redirecionamentos, demais rotas inalteradas e `POST /api` sem `302`
- [X] T035 [US4] Ligar a documentação em `…/test/testsupport/annotations/E2E.java` (`springdoc.api-docs.enabled=true`, `springdoc.swagger-ui.enabled=true`), com o comentário de que em produção ela nasce desligada

### Implementation for User Story 4

- [X] T036 [US4] Em `backend/src/main/resources/application.yml`: `springdoc.api-docs.enabled` e `springdoc.swagger-ui.enabled` como `${PITACO_API_DOCS_ENABLED:false}`, com o comentário do porquê (D-08), e `management.endpoints.web.exposure.include: health,info` (D-09)
- [X] T037 [P] [US4] Ligar a documentação no perfil local em `backend/src/main/resources/application-local.yml.example`
- [X] T038 [P] [US4] Criar `…/infra/http/config/SwaggerUiRedirectValve.java` (valve na engine, `GET`/`HEAD` nos pontos de entrada, `302` com `Location` relativo)
- [X] T039 [US4] Criar `…/infra/http/config/SwaggerUiRedirectCustomizer.java` (instala a valve e desliga o redirecionamento `/api` → `/api/` do Tomcat, com ordem mais baixa) e `…/infra/http/config/SwaggerUiRedirectConfiguration.java` condicionada a `springdoc.swagger-ui.enabled=true`
- [X] T040 [US4] Deixar comentado `PITACO_API_DOCS_ENABLED` no serviço `api` em `compose.yaml` e no `.env.example`, explicando que ligar expõe o mapa da API pelo túnel
- [X] T041 [US4] Rodar T032–T034 e `quickstart.md` passo 4

**Checkpoint**: nada além de saúde, informações e as rotas da API atrás do túnel por padrão.

---

## Phase 7: User Story 5 - Saber antes do merge que cada parte continua de pé (Priority: P3)

**Goal**: um portão por parte, filtrado por caminho, mais o portão da composição.

**Independent Test**: `quickstart.md` passos 1 e 10.

### Tests for User Story 5 ⚠️

- [X] T042 [US5] Criar `.github/workflows/compose.yml` primeiro, porque ele valida o que as histórias anteriores criaram: `.env` fictício (`POSTGRES_PASSWORD=ci`, `BACKUP_REMOTE=:local:/backups`) e `config --quiet` de `compose.yaml` sozinho, com `--profile backup` e somado a `compose.local.yaml`; `shellcheck ops/backup/*.sh`; `docker build ops/backup`; gatilhos `compose.yaml`, `compose.local.yaml`, `.env.example`, `ops/**` e o próprio arquivo

### Implementation for User Story 5

- [X] T043 [P] [US5] Criar `.github/workflows/backend.yml`: gatilhos `backend/**`, Temurin 21 com cache Maven, `./mvnw -B -ntp verify` em `backend/`, `surefire-reports` como artefato em falha (7 dias), `concurrency` com cancelamento, 40 min
- [X] T044 [P] [US5] Criar `.github/workflows/painel.yml`: gatilhos `painel/**`, `sdk/src/**`, `sdk/package.json`; Node de `painel/.nvmrc`; build do SDK, `npm ci --install-links`, Chromium do Playwright, `npm run verify` com `CI=true`; `playwright-test-results` em falha (7 dias); `concurrency` com cancelamento, 30 min
- [X] T045 [P] [US5] Criar `.github/workflows/sdk.yml`: gatilhos `sdk/**`; job `npm run verify` (SDK e exemplo) e job `expo export` para iOS e Android em `sdk/example`; Node de `painel/.nvmrc`; `concurrency` com cancelamento, 30 min
- [X] T046 [US5] Declarar `permissions: contents: read` nos quatro workflows em `.github/workflows/`
- [X] T047 [US5] Abrir pull requests de teste tocando uma parte de cada vez e conferir a tabela do `quickstart.md` passo 10

**Checkpoint**: cada parte com portão próprio; a composição e o backup validados no CI.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: documentação operacional e inventário.

- [X] T048 [P] Escrever `ops/RUNBOOK.md`: subir, backup (destino por ambiente, perfil), backup manual, restaurar, testar o restore (com e sem object storage), chave de acesso comprometida, desativar aplicação em incidente, o que olhar na saúde do SDK e nos resultados
- [X] T049 [P] Escrever `ops/DEPLOY.md`: como a publicação funciona, publicar uma versão nova (revisar commits e migrations, backup antes, `git pull --ff-only`, `up -d --build`), construir um serviço por vez, conferir que está no ar, falhas de build, arquivos de core em volume antigo, voltar de versão com restore, primeira instalação e checklist
- [X] T050 [P] Registrar a operação (OpenAPI/Swagger, observabilidade, backup e restore, CI por parte) na seção de operação de `docs/FEATURES.md`
- [X] T051 Rodar `quickstart.md` do passo 1 ao 10 numa máquina limpa

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependência
- **Foundational (Phase 2)**: depende da Phase 1 — bloqueia todas as histórias
- **US1 (Phase 3)**: depende da Phase 2
- **US2 (Phase 4)**: depende do serviço `postgres` de US1 (T013) para o serviço `backup`; os scripts (T019–T022) independem
- **US3 (Phase 5)**: depende do serviço `api` de US1 (T015)
- **US4 (Phase 6)**: depende de `application.yml` de US1 (T012); independente de US2 e US3
- **US5 (Phase 7)**: o portão `compose` (T042) valida o que US1 e US2 criaram; os outros três independem
- **Polish (Phase 8)**: depende de todas as histórias desejadas

### Dentro de cada história

Teste ou verificação primeiro → configuração da API → Dockerfile/script → serviço na composição → validação de ponta a ponta.

### Parallel Opportunities

- Phase 1: T002–T005 em paralelo
- US1: T008 e T009 em paralelo; T010 e T011 em paralelo
- US2: T020, T021 e T022 em paralelo
- US3: T025 e T026 em paralelo
- US4: T032–T034 em paralelo; T037 e T038 em paralelo
- US5: T043–T045 em paralelo
- Polish: T048–T050 em paralelo

---

## Parallel Example: User Story 2

```bash
Task: "Criar ops/backup/Dockerfile sobre postgres:16-alpine com rclone"
Task: "Criar ops/backup/backup.sh com dump, conferência, envio, retenção e laço"
Task: "Criar ops/backup/restore.sh com --yes, origem do dump e pg_restore --clean"
```

---

## Implementation Strategy

### MVP (US1 + US2)

1. Phase 1 → Phase 2
2. Phase 3 (US1) → **parar e validar**: o Pitaco no ar pelo túnel
3. Phase 4 (US2) → **parar e validar**: o ensaio de restore verde antes de confiar dados reais à stack

### Entrega incremental

1. Stack no ar (US1)
2. Backup e ensaio (US2)
3. Observabilidade (US3)
4. Superfície mínima garantida por teste (US4)
5. Portões de CI (US5)

---

## Notes

- Nenhum serviço publica porta em `compose.yaml`; portas só em `compose.local.yaml`
- Nenhum segredo em arquivo versionado nem em imagem: tudo do `.env`
- Todo ajuste não óbvio em `compose.yaml`, Dockerfiles e `application.yml` leva comentário com o porquê
- Instantes sempre em UTC — banco, JVM, nomes de dump e linhas de log de backup
- Commit após cada tarefa ou grupo lógico
