# Implementation Plan: Coleta — leitura das exibições, respostas e respondentes

**Branch**: `006-collection-read-list` | **Date**: 2026-09-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-collection-read-list/spec.md`

## Summary

Fechar a única lacuna de leitura que resta na API: o módulo `collect` grava exibição, resposta e
respondente e não lê nada de volta. Quatro leituras administrativas — exibições de uma pesquisa,
uma exibição com suas respostas, respondentes de uma aplicação, exibições de um respondente —
todas paginadas, filtráveis e estritamente de leitura.

A abordagem técnica cabe em cinco movimentos, e nenhum deles toca o caminho quente da coleta:

1. **Projeção em vez de entidade nas listagens** (D-02): a página devolve um `record` de
   projeção, porque montar entidades de domínio forçaria uma consulta de atributos por linha.
2. **Duas portas novas de travessia**: `SurveyScopeGateway` para a existência da pesquisa (D-04)
   e um método a mais em `ApplicationScopeGateway` para a retenção de texto livre (D-08).
3. **Supressão de texto livre expirado na leitura** (D-07), com situação de saída `EXPIRED`
   distinta de pulado — não há expurgo no projeto e criar um seria feature própria.
4. **Ordem das respostas vinda da versão exibida** (D-06), por `PublishedSurveyCatalog.contentOf`
   já existente.
5. **Uma migration de índices** (D-10) com os filtros como payload de `include`, sem índice
   adicional numa tabela escrita a cada exibição.

Quatro casos de uso, quatro presenters, três controllers, zero escrita, zero transação.

## Technical Context

**Language/Version**: Java 21 (records, sealed interfaces, virtual threads ligadas)

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Data JPA, Validation), springdoc-openapi,
Lombok (`@Getter`, `@Slf4j` apenas), OpenTelemetry sobre stack Grafana LGTM. **Nenhuma
dependência nova.**

**Storage**: PostgreSQL + Flyway. Tabelas já existentes (`survey_displays`, `survey_answers`,
`survey_answer_options`, `survey_display_attributes`, `respondents`); uma migration nova, só de
índice. `ddl-auto` segue `validate`.

**Testing**: JUnit 5 + AssertJ. Domínio em JUnit puro; casos de uso sobre os fakes já existentes
em `testsupport/repositories` (`InMemorySurveyDisplayRepository`, `InMemoryAnswerRepository`,
`InMemoryRespondentRepository`) e `testsupport/gateways`; E2E com `@E2E` + Testcontainers +
`DatabaseCleaner`.

**Target Platform**: Linux server, JVM com `-Duser.timezone=UTC`

**Project Type**: web service (API REST monolítica modular)

**Performance Goals**: sem SLO numérico (Princípio V). As garantias estruturais desta fatia:
uma consulta por página mais uma de contagem; nenhuma consulta por item devolvido; a consulta
individual custa no máximo quatro consultas fixas (exibição, respostas com opções em `join
fetch`, conteúdo da versão, retenção da aplicação), independentes do volume armazenado.

**Constraints**: nenhuma escrita em nenhum caminho, inclusive de recusa; nenhum segredo de chave
e nenhum valor de identificação de respondente em log; tamanho de página no máximo 100;
`AdminSurfaceInterceptor` recusa `X-Pitaco-Key` nas quatro rotas.

**Scale/Scope**: 4 leituras, 4 casos de uso, 2 portas novas (uma classe, um método), 4 erros
nomeados (2 novos, 2 reaproveitados), 1 migration de índice, 3 controllers, 9 DTOs de resposta,
2 DTOs de consulta. Volume esperado dominado por `survey_displays`, a tabela que cresce com o
tráfego do SDK.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Núcleo Independente de Framework** | Os quatro casos de uso são POJOs sem anotação, implementam `UseCase` de `core.usecase`, com `Input`/`Output` como `record` aninhados, cabeados por `@Bean` no `UseCasesConfiguration` de `collect`. As duas portas novas ficam em `application/gateways`; os adaptadores em `infra/gateways`. Nenhuma resposta de repositório do Spring Data atravessa `application`. | ✅ |
| **II. Domínio Que Se Valida** | Nada de domínio novo: as três entidades já existem e já se validam. Os dois erros novos estendem `ApplicationException` com `ErrorType.NOT_FOUND` e `code` em constante `private static final`. Ausência é `Optional` em todo acessor de saída (fechamento, versão do SDK, valor da resposta, prazo de retenção). | ✅ |
| **III. Teste É Contrato** | Cada caso de uso nasce com teste sobre fake antes do código; cada uma das quatro leituras nasce com E2E cobrindo caminho feliz com o estado lido do banco, 400 de validação, cada erro do OpenAPI e a ausência de mudança de estado nos caminhos de falha. Nenhum mock sobre porta do projeto. | ✅ |
| **IV. Consistência da Superfície Pública** | Erro só pelo `ApiExceptionHandler`, sem `try/catch` em controller. Rotas sem `/api`. Saída por presenter `final class` com `present` estático. Envelope de página é o `PageResponseDTO` já existente, com página 0 e tamanho 20 por padrão, máximo 100 — igual às listagens de aplicações, chaves e pesquisas. Uma interface `*Swagger` por controller, com todo status declarado. | ✅ |
| **V. Performance Por Construção** | Projeção explícita nas listagens (D-02) e `join fetch` das opções na consulta de respostas; nenhuma chamada de repositório em laço; toda listagem paginada; os campos de filtro, ordenação e junção cobertos pela migration de índice da mesma fatia (D-10). | ✅ |

**Gate de Phase 0**: aprovado, sem violação a justificar. `Complexity Tracking` fica vazio.

### Re-avaliação após Phase 1 (design)

Reavaliado contra `data-model.md` e `contracts/`. Dois pontos ganharam escrutínio no desenho e
sobreviveram sem violação:

- **A situação `EXPIRED` na saída** (D-07) não é estado de domínio: `AnswerStatus` continua com
  `ANSWERED` e `SKIPPED`, e a expiração é decidida no caso de uso e declarada no `Output`. O
  Princípio II segue intacto — o mapper nunca inventa estado que o banco não tem.
- **A projeção `DisplaySummary`** (D-02) mora na porta, em `application`, e é composta de tipos
  de domínio e primitivos. Não é tipo de `infra` atravessando camada; é o que o Princípio V
  chama de projeção explícita.

Veredito final: **aprovado**. Nenhuma entrada em `Complexity Tracking`.

## Project Structure

### Documentation (this feature)

```text
specs/006-collection-read-list/
├── plan.md              # This file
├── research.md          # Phase 0: D-01 a D-13
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── collection-read.md   # as quatro leituras HTTP
│   └── module-ports.md      # portas e projeções novas
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks — NÃO criado aqui)
```

### Source Code (repository root)

Arquivo marcado com **novo** nasce nesta fatia; com **alterado**, ganha membro novo sem quebrar
quem já o usa.

```text
src/main/java/com/renanloureiroo/pitaco/modules/collect/
├── application/
│   ├── errors/
│   │   ├── RespondentNotFound.java                      novo
│   │   └── SurveyNotFoundInApplication.java             novo
│   ├── gateways/
│   │   ├── ApplicationScopeGateway.java                 alterado  (+ retenção efetiva)
│   │   └── SurveyScopeGateway.java                      novo
│   ├── outputs/
│   │   ├── AnswerReadOutput.java                        novo
│   │   ├── AnswerReadStatus.java                        novo
│   │   ├── DisplayDetailOutput.java                     novo
│   │   ├── DisplaySummaryOutput.java                    novo
│   │   ├── RespondentDisplaySummaryOutput.java          novo
│   │   └── RespondentOutput.java                        novo
│   ├── repositories/
│   │   ├── AnswerRepository.java                        alterado  (+ opções em join fetch)
│   │   ├── RespondentRepository.java                    alterado  (+ findById, findPage)
│   │   └── SurveyDisplayRepository.java                 alterado  (+ findPage, projeções)
│   ├── services/
│   │   └── CollectScope.java                            novo      (existência + retenção)
│   └── usecases/
│       ├── GetSurveyDisplayUseCase.java                 novo
│       ├── ListRespondentDisplaysUseCase.java           novo
│       ├── ListRespondentsUseCase.java                  novo
│       └── ListSurveyDisplaysUseCase.java               novo
└── infra/
    ├── config/
    │   └── UseCasesConfiguration.java                   alterado  (+ 4 @Bean)
    ├── database/jpa/
    │   ├── repositories/
    │   │   ├── AnswerRepositoryJpa.java                 alterado
    │   │   ├── RespondentJpaRepository.java             alterado
    │   │   ├── RespondentRepositoryJpa.java             alterado
    │   │   ├── SurveyAnswerJpaRepository.java           alterado  (+ join fetch)
    │   │   ├── SurveyDisplayJpaRepository.java          alterado  (+ 4 consultas)
    │   │   └── SurveyDisplayRepositoryJpa.java          alterado
    │   └── projections/
    │       ├── DisplaySummaryProjection.java            novo
    │       └── RespondentDisplayProjection.java         novo
    ├── gateways/
    │   ├── ApplicationScopeGatewayApp.java              alterado
    │   ├── SurveyScopeGatewaySurvey.java                novo
    │   └── SurveyScopeJpaRepository.java                novo
    └── http/
        ├── controllers/
        │   ├── DisplayController.java                   novo
        │   ├── DisplaySwagger.java                      novo
        │   ├── RespondentController.java                novo
        │   ├── RespondentSwagger.java                   novo
        │   ├── SurveyDisplayController.java             novo
        │   └── SurveyDisplaySwagger.java                novo
        ├── dtos/
        │   ├── AnswerReadResponseDTO.java               novo
        │   ├── DisplayDetailResponseDTO.java            novo
        │   ├── DisplayOutcomeFilter.java                novo
        │   ├── DisplaySummaryResponseDTO.java           novo
        │   ├── ListDisplaysQueryDTO.java                novo
        │   ├── ListRespondentsQueryDTO.java             novo
        │   ├── RespondentDisplayResponseDTO.java        novo
        │   └── RespondentResponseDTO.java               novo
        └── presenters/
            ├── DisplayDetailPresenter.java              novo
            ├── DisplaySummaryPresenter.java             novo
            ├── RespondentDisplayPresenter.java          novo
            └── RespondentPresenter.java                 novo

src/main/resources/db/migration/
└── V20260909170000__index_collect_reads.sql             novo

src/test/java/com/renanloureiroo/pitaco/
├── modules/collect/
│   ├── application/usecases/                            4 classes de teste novas
│   └── infra/http/controllers/                          4 classes de E2E novas
└── testsupport/
    ├── gateways/
    │   ├── InMemoryCollectApplicationScopeGateway.java  alterado
    │   └── InMemorySurveyScopeGateway.java              novo
    └── repositories/
        ├── InMemoryRespondentRepository.java            alterado
        └── InMemorySurveyDisplayRepository.java         alterado
```

**Structure Decision**: nenhuma estrutura nova. A fatia cabe inteira dentro de
`modules/collect`, respeitando `domain` → `application` → `infra`, e o único arquivo fora do
módulo é a migration. `core` não muda: nada aqui é compartilhado por dois módulos —
`core.pagination` e `core.identity` já oferecem o que falta, e a projeção de listagem é
necessidade de um módulo só. As duas travessias para fora (`app` e `survey`) são portas em
`collect/application/gateways` com adaptador em `collect/infra/gateways`, como as duas que já
existem.

## Complexity Tracking

> Preenchido apenas quando o Constitution Check tem violação a justificar.

Sem violação. Nenhuma abstração nova entra por antecipação: `SurveyScopeGateway` nasce porque
FR-010 exige a distinção entre pesquisa inexistente e pesquisa sem exibição (D-04), e a retenção
efetiva entra como método em porta existente em vez de porta nova justamente para não criar
máquina sem segundo caso (D-08).
