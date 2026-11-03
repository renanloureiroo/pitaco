# Implementation Plan: Respondente, coleta e respostas — a superfície pública do SDK

**Branch**: `004-response-collection` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-response-collection/spec.md`

## Summary

Abrir a primeira superfície consumida pelo SDK: três operações públicas autenticadas por chave
de aplicação — perguntar se há pesquisa para um respondente agora, abrir a sessão de exibição e
enviar o que foi coletado. O núcleo da entrega é um módulo novo, `modules/collect`, com três
agregados próprios (respondente, sessão, resposta) que **leem** a versão publicada da autoria
por uma porta de saída e nunca a modificam.

A abordagem técnica em uma frase por decisão: a chave resolve a aplicação num interceptor da
borda (`infra/http/security`), que nunca deixa o `applicationId` virar parâmetro de rota; a
elegibilidade aplica as sete camadas na ordem da FR-011 sobre **duas** consultas — candidatos e
conteúdo da escolhida —, sem escrever nada quando não há pesquisa; a amostragem é uma função
determinística do par respondente–pesquisa, sem estado guardado; a sessão é criada com o
identificador gerado no dispositivo como chave primária, o que torna o reenvio idempotente de
graça; e a submissão é **um ato atômico** que carrega respostas e desfecho juntos, validado
inteiro antes de qualquer gravação.

## Technical Context

**Language/Version**: Java 21 (records, sealed interfaces, virtual threads ligadas)

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Data JPA, Validation), Flyway,
springdoc-openapi, Lombok (`@Getter`, `@Slf4j`). **Nenhuma dependência nova** — em particular,
sem Spring Security (ver research.md, D-02).

**Storage**: PostgreSQL. Cinco tabelas novas (`respondents`, `survey_sessions`,
`survey_session_attributes`, `survey_answers`, `survey_answer_options`) e duas alterações
(`api_keys.last_used_at`, índices de elegibilidade). O Redis foi removido do projeto: nenhuma
feature o usou, e cache só entra com problema medido (constituição 1.2.0).

**Testing**: JUnit 5 + AssertJ; fakes in-memory em `testsupport/repositories` para caso de uso;
Testcontainers (Postgres + LGTM) com `@E2E` para a borda.

**Target Platform**: JVM em container Linux, atrás do context-path `/api`.

**Project Type**: web-service (backend único, monolito modular).

**Performance Goals**: sem SLO numérico (constituição, Princípio V). As metas estruturais desta
feature: o caminho "não há pesquisa" custa **uma** consulta indexada e **zero escritas nas
tabelas de coleta** — a única gravação possível nele é o `api_keys.last_used_at` da
autenticação, amortizado em no máximo uma por chave por minuto (D-17), e que não cria linha
nenhuma, então SC-003 continua verificável por contagem; a
entrega de pesquisa custa no máximo três consultas (candidatos, histórico, conteúdo); nenhuma
consulta dentro de laço em nenhum dos três casos de uso.

**Constraints**: nenhum dado pessoal armazenado ou logado (FR-008, FR-039, SC-013); nenhum
`ThreadLocal` para estado de requisição (virtual threads); a decisão de amostragem precisa ser
reprodutível sem consultar o banco (FR-017); toda a versão publicada volta em uma resposta só
(FR-019).

**Scale/Scope**: três endpoints públicos, três casos de uso novos em `collect`, um caso de uso
novo em `app` (autenticação da chave), três agregados, uma migration. Volume esperado dominado
pelo caminho vazio: a maioria absoluta das chamadas de elegibilidade não devolve pesquisa.

## Constitution Check

*GATE: verificado antes da Fase 0 e reavaliado após a Fase 1.*

| Princípio | Como este plano o satisfaz | Situação |
| --- | --- | --- |
| I. Núcleo independente de framework | `modules/collect/domain` e `modules/collect/application` são Java puro; casos de uso são POJOs cabeados por `UseCasesConfiguration` do módulo; cross-module só por porta em `application/gateways` com adaptador em `infra/gateways`; persistência por porta de repositório sobre Spring Data com mapper explícito | ✅ |
| II. Domínio que se valida | `RespondentIdentity`, `SessionId`, `AnswerText`, `AttributeSnapshot` validam no construtor e lançam `DomainException`; `SurveySession` e `Answer` são entidades com `create`/`restore`; erros nomeados em `application/errors` com `code` `<contexto>.<motivo>`; ausência é `Optional` | ✅ |
| III. Teste é contrato | Ordem de trabalho da constituição seguida tarefa a tarefa; fakes in-memory novos (`InMemoryRespondentRepository`, `InMemorySurveySessionRepository`, `InMemoryAnswerRepository`, `InMemoryPublishedSurveyCatalog`); E2E cobrindo caminho feliz, 400, e cada erro anunciado no OpenAPI, com leitura do estado de volta do banco | ✅ |
| IV. Consistência da superfície pública | Erro só pelo `ApiExceptionHandler` (RFC 9457); `submission.rejected` usa a extensão `errors[]` no molde já existente de `SurveyNotPublishable`; rotas sem `/api`; presenter `final class` estático; interface `*Swagger` com um `@ApiResponse` por status; 201 + `Location` na abertura de sessão | ✅ |
| V. Performance por construção | Consulta de candidatos com índice parcial dedicado; histórico do respondente em **uma** consulta para todos os candidatos; conteúdo da versão em uma consulta com `join fetch`; gravação das respostas em `saveAll`; nenhum `findAll` sem filtro na superfície pública | ✅ |

**Desvios que exigem justificativa** (detalhados em [Complexity Tracking](#complexity-tracking)):
promoção de vocabulário da versão publicada para `core/catalog`; duplicação da porta
`ApplicationScopeGateway` em `collect`; autenticação por interceptor próprio em vez de Spring
Security; e migration única do módulo antes dos casos de uso, invertendo os passos 3 e 4 da
ordem de trabalho.

**Reavaliação pós-Fase 1**: os quatro desvios permanecem os mesmos, nenhum novo apareceu, e o
desenho de dados não introduziu tabela, índice ou consulta fora das regras do Princípio V.
Gate aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/004-response-collection/
├── plan.md              # Este arquivo
├── research.md          # Fase 0 — as decisões e o que foi descartado
├── data-model.md        # Fase 1 — agregados, tabelas, índices
├── quickstart.md        # Fase 1 — como validar a feature ponta a ponta
├── contracts/
│   ├── public-collect-api.md   # os três endpoints públicos e o catálogo de erros
│   └── module-ports.md         # portas e gateways de `collect`, e o que muda em `app`/`survey`
├── checklists/
└── tasks.md             # Fase 2 — gerado por /speckit-tasks, não por este comando
```

### Source Code (repository root)

```text
src/main/java/com/renanloureiroo/pitaco/
├── core/
│   ├── catalog/                       # NOVO — vocabulário congelado compartilhado
│   │   ├── EventName.java             #   movido de modules/survey/domain/valueobjects
│   │   ├── QuestionKey.java           #   movido
│   │   ├── QuestionType.java          #   movido
│   │   ├── QuestionOption.java        #   movido
│   │   ├── ScaleRange.java            #   movido
│   │   ├── SamplingRate.java          #   movido
│   │   ├── RuleOperation.java         #   movido de modules/survey/domain/entities
│   │   └── SegmentationCriterion.java # NOVO — atributo/operação/valor sem o id da regra
│   └── identity/
│       ├── SurveyId.java              #   movido de modules/survey/domain/entities
│       └── SurveyVersionId.java       #   movido
├── infra/http/security/               # NOVO — a porta de entrada da superfície pública
│   ├── ApiKeyAuthenticationInterceptor.java
│   ├── AdminSurfaceInterceptor.java
│   ├── AuthenticatedApplication.java
│   ├── AuthenticatedApplicationArgumentResolver.java
│   └── SecurityWebMvcConfiguration.java
├── modules/app/
│   ├── application/
│   │   ├── errors/ApiKeyInvalid.java              # NOVO
│   │   ├── repositories/ApiKeyRepository.java     # + findActiveBySecretHash, touch
│   │   └── usecases/AuthenticateApiKeyUseCase.java# NOVO
│   └── infra/database/jpa/…                       # + coluna last_used_at
└── modules/collect/                   # NOVO módulo
    ├── domain/
    │   ├── entities/                  # Respondent, SurveySession, Answer (+ ids e enums)
    │   ├── valueobjects/              # RespondentIdentity, AttributeSnapshot, AnswerValue…
    │   ├── eligibility/               # SamplingDecision, SegmentationEvaluation, ResolvedHistory
    │   └── collection/                # SubmissionValidation, SubmissionProblem
    ├── application/
    │   ├── errors/                    # SessionNotFound, SessionAlreadyClosed, SubmissionRejected…
    │   ├── gateways/                  # PublishedSurveyCatalog, ApplicationScopeGateway
    │   ├── outputs/                   # DeliverableSurveyOutput, SurveySessionOutput…
    │   ├── repositories/              # RespondentRepository, SurveySessionRepository, AnswerRepository
    │   └── usecases/                  # FindEligibleSurvey, OpenSurveySession, SubmitSurveySession
    └── infra/
        ├── config/                    # UseCasesConfiguration, CollectProperties
        ├── database/jpa/              # entidades, mappers, repositórios
        ├── gateways/                  # PublishedSurveyCatalogSurvey, ApplicationScopeGatewayApp
        └── http/                      # controllers, dtos, presenters

src/main/resources/db/migration/
└── V20260908210000__create_collect.sql   # tabelas, índices e last_used_at

src/test/java/com/renanloureiroo/pitaco/
├── modules/collect/…                  # domínio, casos de uso, DTOs, E2E
└── testsupport/
    ├── factories/                     # RespondentFactory, SurveySessionFactory, AnswerFactory
    ├── gateways/                      # InMemoryPublishedSurveyCatalog
    └── repositories/                  # InMemoryRespondentRepository, InMemorySurveySessionRepository,
                                       # InMemoryAnswerRepository
```

**Structure Decision**: monolito modular já existente, com um **módulo novo** `modules/collect`
ao lado de `app` e `survey`. Coleta e autoria têm ciclos de vida, superfícies e consumidores
distintos — painel de um lado, SDK do outro —, e a única coisa que compartilham é a versão
publicada, que atravessa por porta. As pastas listadas acima são caminhos reais; nada aqui
inventa camada nova além do `core/catalog` justificado abaixo.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Novo pacote `core/catalog` com sete tipos movidos de `modules/survey` | `collect` valida a resposta contra o mesmo catálogo de tipos, faixas e operações que a autoria congelou. A regra é uma só e precisa ter uma implementação só | Duplicar `QuestionType`/`RuleOperation` em `collect` com um mapper na travessia: um tipo novo acrescentado na autoria e esquecido na coleta passaria a aceitar qualquer valor em silêncio. É exatamente o precedente do `ApplicationId`, que nasceu em `modules/app` e subiu quando `survey` passou a precisar dele |
| Porta `ApplicationScopeGateway` duplicada em `collect` (idêntica à de `survey`) | Dois módulos nunca se conhecem pelo domínio; a porta pertence a quem a declara | Promover a porta para `core`: `core` guarda o que inverte dependência entre módulos, e uma porta de saída de módulo não é isso — seria `collect` e `survey` passando a compartilhar contrato de saída, o oposto do isolamento. O custo é uma interface de um método |
| Migration e adaptadores JPA antes dos casos de uso, invertendo os passos 3 e 4 da ordem de trabalho da constituição | A migration é **uma só** para o módulo inteiro (cinco tabelas mais os índices de elegibilidade), e a fase Foundational precisa fechar antes de qualquer story para que as cinco possam ser desenvolvidas em paralelo. Dentro de cada story a ordem canônica é respeitada: teste, domínio, caso de uso, borda, E2E | Uma migration por story: quatro arquivos que se sobrepõem no mesmo conjunto de tabelas, com a segunda alterando o que a primeira acabou de criar — e migration aplicada não se edita, então o custo apareceria já no primeiro ajuste de índice |
| Autenticação por interceptor próprio em `infra/http/security`, sem Spring Security | A regra inteira é "hash da chave → aplicação"; Spring Security traria filtro, contexto e configuração para expressar isso, além de um `SecurityContextHolder` baseado em `ThreadLocal` que o Princípio V desaconselha sob virtual threads | Adicionar `spring-boot-starter-security`: dependência nova exigiria justificativa própria e traria mais superfície de configuração do que regra implementada |
