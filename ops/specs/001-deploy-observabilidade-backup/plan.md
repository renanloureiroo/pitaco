# Implementation Plan: Operação — implantação auto-hospedada, observabilidade e backup

**Branch**: `001-deploy-observabilidade-backup` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `ops/specs/001-deploy-observabilidade-backup/spec.md`

## Summary

Pôr o Pitaco para rodar num servidor próprio com Docker Compose, sem porta aberta, observável e
com backup restaurável. Nenhuma regra de negócio muda: o que nasce aqui é infraestrutura —
arquivos de composição, três Dockerfiles, scripts de backup, configuração da API e quatro
workflows de CI.

A abordagem técnica cabe em seis movimentos:

1. **Uma composição de produção na raiz** (`compose.yaml`) com `postgres`, `api` e `painel`, duas
   redes — `internal` privada e `edge` externa, compartilhada com o túnel e o LGTM — e nenhuma
   porta publicada (D-01, D-02, D-03). Uma sobreposição (`compose.local.yaml`) publica portas e
   sobe um LGTM próprio para quem quer a stack inteira na máquina de desenvolvimento (D-04).
2. **Duas imagens de aplicação em base glibc**: a da API em dois estágios Maven → JRE, a do painel
   compilando o SDK do repositório e instalando-o como cópia (D-10, D-11, D-12).
3. **Configuração de produção como padrão do `application.yml`**: sem perfil ativo, cabeçalhos
   encaminhados pelo túnel respeitados, documentação desligada, Actuator mínimo (D-05, D-08, D-09).
4. **OpenTelemetry por um único endereço OTLP/HTTP** para traces, logs e métricas, com a ponte
   Logback → OTel carregando o MDC e o access log do Tomcat no stdout (D-06, D-07).
5. **Backup como imagem própria**, `pg_dump` + `rclone` configurado só por ambiente, conferido
   antes do envio, com retenção por contagem, restore com confirmação e ensaio em banco
   descartável; na composição, serviço opcional atrás de perfil (D-14 a D-18).
6. **Um workflow de CI por parte**, filtrado por caminho, mais um para composição e backup (D-19).

## Technical Context

**Language/Version**: YAML (Docker Compose v2, GitHub Actions), Dockerfile (BuildKit, sintaxe
`docker/dockerfile:1`), POSIX `sh` nos scripts que rodam dentro da imagem de backup, `bash` no
ensaio que roda no host. Java 21 e Spring Boot 4.1 no lado da API; Node 24 na imagem do painel.

**Primary Dependencies**: `postgres:16-alpine` (banco e base da imagem de backup),
`maven:3.9-eclipse-temurin-21` e `eclipse-temurin:21-jre` (API), `node:24-bookworm-slim`
(painel), `rclone` (pacote Alpine), `grafana/otel-lgtm` (só na sobreposição local; em produção é
externo). Na API: `spring-boot-starter-opentelemetry` e `opentelemetry-logback-appender-1.0` — já
presentes no `pom.xml`, nenhuma dependência nova de aplicação.

**Storage**: volume Docker `pg_data` montado em `/var/lib/postgresql/data`; dumps em object
storage S3 ou compatível (ou diretório local no ensaio).

**Testing**: portões de CI em `.github/workflows/`; `docker compose config --quiet` com `.env`
fictício; `shellcheck` sobre `ops/backup/*.sh`; build da imagem de backup; ensaio de restore
(`ops/backup/restore-test.sh`). No backend, os testes de configuração (`OpenApiConfigTest`,
`ContextPathTest`, `SwaggerUiRedirectConfigurationTest`, `SwaggerUiRedirectValveTest`,
`SwaggerUiRedirectE2ETest`, `ApiExceptionHandlerTest`) rodam no `./mvnw verify` com
Testcontainers subindo Postgres e LGTM.

**Target Platform**: servidor Linux com Docker Engine e Compose v2; túnel reverso (ex.: Cloudflare
Tunnel) e Grafana LGTM numa rede Docker externa chamada `edge`.

**Project Type**: infraestrutura e operação de um monorepo (API REST + painel web + SDK).

**Performance Goals**: sem SLO numérico (Princípio V). O que se dimensiona é para servidor
pequeno: pool de conexões de 10, heap da JVM em 70% da memória do container com `SerialGC` e pilha
de 512 KB por thread, amostragem de trace 100% porque o volume é baixo.

**Constraints**: nenhuma porta publicada em produção; nenhum segredo versionado nem copiado para
imagem; backup sempre fora da máquina; ensaio de restore nunca escreve no ambiente real; tudo em
UTC.

**Scale/Scope**: 2 arquivos de composição, 3 Dockerfiles, 3 scripts de backup, 1 modelo de
ambiente, 2 `.dockerignore`, 4 workflows, ajustes de configuração na API (`application.yml`,
`logback-spring.xml`, 1 classe de observabilidade, 4 classes do redirecionamento para a
documentação), runbook e guia de publicação.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

A operação não tem constituição própria. Vale a do backend
(`backend/.specify/memory/constitution.md`), que é quem roda dentro da stack; abaixo, os
princípios que se aplicam à operação e como esta feature os satisfaz.

| Princípio | Como esta feature o satisfaz | Veredito |
| --- | --- | --- |
| **I. Núcleo Independente de Framework** | Nada de Docker, OTLP ou túnel chega a `core` nem a `modules/*/domain`. A ponte de log (`OpenTelemetryLogbackConfig`) e o redirecionamento para a documentação (`SwaggerUiRedirect*`) moram em `infra`; o resto é configuração externa (`application.yml`, variáveis de ambiente). | ✅ |
| **II. Domínio Que Se Valida** | Não se aplica: nenhuma entidade, value object ou erro de domínio nasce ou muda. | — |
| **III. Teste É Contrato** | O comportamento de configuração que a API ganha é coberto no `./mvnw verify`: documentação desligada por padrão e redirecionamento só com ela ligada (`SwaggerUiRedirectConfigurationTest`, `SwaggerUiRedirectValveTest`, `SwaggerUiRedirectE2ETest`), URL externa a partir dos cabeçalhos do túnel (`OpenApiConfigTest`), prefixo `/api` (`ContextPathTest`), `traceId` no erro (`ApiExceptionHandlerTest`). Os E2E sobem Postgres **e** LGTM por Testcontainers. Na infraestrutura, o equivalente do teste é o portão `compose` (composição válida, `shellcheck`, build da imagem) e o ensaio de restore, que prova o backup restaurando-o. | ✅ |
| **IV. Consistência da Superfície Pública** | Rotas continuam sob o `context-path` `/api`; com o túnel terminando o TLS, `forward-headers-strategy: framework` mantém corretos o `servers` do OpenAPI e o `Location` do `201`; o `traceId` do corpo de erro passa a apontar para um trace que de fato existe no Grafana. Documentação desligada não muda o contrato: com a variável ligada, o OpenAPI é o mesmo e completo. | ✅ |
| **V. Performance Por Construção** | Sem SLO, sem cache. Pool pequeno e ergonomia da JVM declarados e comentados; amostragem 100% justificada pelo volume e com o gatilho de revisão escrito no `application.yml`. | ✅ |
| **Padrões Técnicos — Stack fixa** | "OpenTelemetry sobre stack Grafana LGTM" passa a valer também em produção, não só nos testes. | ✅ |
| **Padrões Técnicos — Migrations** | Rodam na subida da API; `ddl-auto: validate` impede a partida com schema divergente; nenhuma migration é revertida — voltar de versão é restaurar backup. | ✅ |
| **Padrões Técnicos — Tempo** | `TZ`/`PGTZ` em UTC no banco, `-Duser.timezone=UTC` na imagem da API, `hibernate.jdbc.time_zone: UTC`, nomes de dump e linhas de log de backup com instante UTC. | ✅ |
| **Padrões Técnicos — Log** | Log de acesso e log da aplicação no stdout, sem arquivo em disco; o log da aplicação segue também para o Loki com `traceId`/`spanId`. Nenhuma credencial de banco ou de object storage é registrada pelos scripts. | ✅ |
| **Padrões Técnicos — Configuração** | Cada ajuste não óbvio de `compose.yaml` e `application.yml` é comentado com o porquê. Segredo nunca versionado: `.env` no `.gitignore`, só `.env.example` no repositório; `.dockerignore` tira `.env*` e `application-local.yml` do contexto de build. | ✅ |

**Gate de Phase 0**: aprovado. A única linha sem veredito (Princípio II) não se aplica a
infraestrutura.

### Re-avaliação após Phase 1 (design)

Reavaliado contra `data-model.md` e `contracts/`. Dois pontos ganharam escrutínio:

- **O redirecionamento para a documentação vive na engine do Tomcat**, fora do `DispatcherServlet`,
  e por isso não passa pelo `ApiExceptionHandler` nem pelos interceptors. Não fere o Princípio IV:
  ele só existe com a documentação ligada, responde a `GET`/`HEAD` em três caminhos fixos e não é
  rota da API — é entrada de ferramenta, como a própria UI.
- **A imagem da API pula os testes no build** (`-DskipTests`). O Princípio III continua satisfeito
  porque o portão `backend` roda `./mvnw verify` antes do merge; repetir a suíte no servidor, sem
  Docker-in-Docker para o Testcontainers, não provaria nada novo.

Veredito final: **aprovado**. Nenhuma entrada em `Complexity Tracking`.

## Project Structure

### Documentation (this feature)

```text
ops/specs/001-deploy-observabilidade-backup/
├── plan.md              # This file
├── research.md          # Phase 0: D-01 a D-19
├── data-model.md        # Phase 1: volumes, redes, variáveis, formato do backup, sinais
├── quickstart.md        # Phase 1
├── contracts/
│   ├── environment.md   # variáveis de ambiente e propriedades por serviço
│   ├── operations.md    # comandos, argumentos, saídas e códigos de saída
│   └── ci.md            # portões: gatilhos, passos e evidência de falha
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2
```

### Source Code (repository root)

```text
compose.yaml                          # produção: postgres, api, painel, backup (perfil)
compose.local.yaml                    # sobreposição local: portas, docs ligadas, LGTM próprio
.env.example                          # modelo do .env (o .env real nunca é versionado)
.gitignore                            # .env
.dockerignore                         # contexto da raiz, usado pela imagem do painel

backend/
├── Dockerfile                        # Maven → JRE, usuário sem privilégio, JVM em UTC
├── .dockerignore                     # tira application-local.yml, specs, docs
├── compose.yaml                      # desenvolvimento do backend (não é produção)
└── src/
    ├── main/
    │   ├── java/com/renanloureiroo/pitaco/infra/
    │   │   ├── observability/
    │   │   │   └── OpenTelemetryLogbackConfig.java
    │   │   └── http/config/
    │   │       ├── OpenApiConfig.java
    │   │       ├── SwaggerUiRedirectConfiguration.java
    │   │       ├── SwaggerUiRedirectCustomizer.java
    │   │       └── SwaggerUiRedirectValve.java
    │   └── resources/
    │       ├── application.yml       # context-path, forward headers, access log, Actuator,
    │       │                         # amostragem, springdoc ligado por PITACO_API_DOCS_ENABLED
    │       ├── application-local.yml.example
    │       └── logback-spring.xml    # console + appender OTel com MDC
    └── test/java/com/renanloureiroo/pitaco/
        ├── infra/
        │   ├── TestcontainersConfiguration.java   # Postgres + LGTM nos E2E
        │   └── http/
        │       ├── config/
        │       │   ├── ContextPathTest.java
        │       │   ├── OpenApiConfigTest.java
        │       │   ├── SwaggerUiRedirectConfigurationTest.java
        │       │   ├── SwaggerUiRedirectE2ETest.java
        │       │   └── SwaggerUiRedirectValveTest.java
        │       └── error/
        │           └── ApiExceptionHandlerTest.java
        └── testsupport/annotations/
            └── E2E.java              # liga a documentação nos testes

painel/
├── Dockerfile                        # SDK compilado + npm ci --install-links + standalone
├── .dockerignore
└── next.config.ts                    # output: "standalone"

ops/
├── RUNBOOK.md
├── DEPLOY.md                         # guia de publicação
└── backup/
    ├── Dockerfile                    # postgres:16-alpine + rclone
    ├── backup.sh                     # dump, conferência, envio, retenção, laço
    ├── restore.sh                    # restore com --yes
    └── restore-test.sh               # ensaio em Postgres descartável

.github/workflows/
├── backend.yml
├── painel.yml
├── sdk.yml
└── compose.yml
```

**Structure Decision**: a operação mora na raiz e em `ops/`, fora das três partes, porque
atravessa as três: a composição constrói a API a partir de `backend/` e o painel a partir da raiz
(precisa de `sdk/` e `painel/` no mesmo contexto). O que é comportamento da API — a ponte de log,
o redirecionamento para a documentação, a leitura dos cabeçalhos do túnel — fica no backend, em
`infra`, coberto pelos testes do backend. O backup ganhou imagem própria em `ops/backup/` em vez
de rodar dentro do container do banco, para ter as ferramentas certas (`rclone`) sem mexer na
imagem oficial do Postgres e para servir também ao ensaio, que roda longe da produção.

## Complexity Tracking

> Preenchido apenas quando o Constitution Check tem violação a justificar.

Sem violação. Nenhum orquestrador, nenhum registro de imagens, nenhum agendador externo: a
composição e um laço `sleep` dentro da imagem de backup bastam para um servidor só. Abstração
nova entra quando houver o segundo servidor.
