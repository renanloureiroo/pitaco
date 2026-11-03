# Contrato de CI — portões por parte

**Feature**: `001-deploy-observabilidade-backup` · Fonte: `.github/workflows/*.yml`

Todos os portões: `push` na `main` e `pull_request`, filtrados por `paths`; `permissions:
contents: read`; `runs-on: ubuntu-latest`. O nome do workflow é o nome da parte.

| Workflow | Dispara por | Job(s) | Tempo limite | Cancela anterior | Evidência em falha |
| --- | --- | --- | --- | --- | --- |
| `backend` | `backend/**`, o próprio arquivo | `mvnw verify` | 40 min | sim | `surefire-reports` (`backend/target/surefire-reports`), 7 dias |
| `painel` | `painel/**`, `sdk/src/**`, `sdk/package.json`, o próprio arquivo | `npm run verify` | 30 min | sim | `playwright-test-results` (`painel/test-results`), 7 dias |
| `sdk` | `sdk/**`, o próprio arquivo | `npm run verify` · `expo export (exemplo)` | 30 min cada | sim | log do job |
| `compose` | `compose.yaml`, `compose.local.yaml`, `.env.example`, `ops/**`, o próprio arquivo | `compose e backup` | 15 min | não | log do job |

---

## `backend`

1. `actions/setup-java` — Temurin 21, cache Maven por `backend/pom.xml`.
2. `./mvnw -B -ntp verify` em `backend/`. O runner tem Docker: o Testcontainers sobe Postgres e
   Grafana LGTM; o `spring-boot-docker-compose` fica desligado nos testes.

## `painel`

Com `CI=true` (Playwright faz build de produção, não reaproveita servidor, um worker, duas novas
tentativas; o E2E fala com o simulador de API, sem backend).

1. `actions/setup-node` — versão de `painel/.nvmrc`, cache npm por `painel/package-lock.json` e
   `sdk/package-lock.json`.
2. Em `sdk/`: `npm ci` e `npm run build`.
3. Em `painel/`: `npm ci --install-links` — o mesmo caminho do `painel/Dockerfile`.
4. `npx playwright install --with-deps chromium`.
5. `npm run verify` = lint, tipos, Vitest e Playwright.

## `sdk`

Job `npm run verify` (em `sdk/`):

1. Node de `painel/.nvmrc`; cache por `sdk/package-lock.json` e `sdk/example/package-lock.json`.
2. `npm ci` e `npm ci --prefix example`.
3. `npm run verify` = lint, tipos, Jest (inclui o contrato do catálogo contra a especificação da
   API), build e lint/tipos do app de exemplo.

Job `expo export (exemplo)` (em `sdk/example/`):

1. `npm ci --prefix ..` e `npm ci`.
2. `npx expo export -p ios --output-dir dist-ios` e `npx expo export -p android --output-dir
   dist-android`.

## `compose`

1. `.env` fictício a partir de `.env.example`, com `POSTGRES_PASSWORD=ci` e
   `BACKUP_REMOTE=:local:/backups`; `config --quiet` em três combinações:
   `-f compose.yaml`, `-f compose.yaml --profile backup` e `-f compose.yaml -f compose.local.yaml`.
2. `shellcheck ops/backup/*.sh`.
3. `docker build --tag pitaco-backup:ci ops/backup`.

---

## O que os portões não fazem

- Não constroem as imagens da API nem do painel (são construídas no servidor).
- Não rodam o ensaio de restore (é manual, com o destino real).
- Não publicam nada: nenhum portão tem permissão de escrita.
