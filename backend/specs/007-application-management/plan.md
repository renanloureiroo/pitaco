# Implementation Plan: Aplicação — cadastro, edição, ativação e eventos observados

**Branch**: `007-application-management` | **Date**: 2026-09-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-application-management/spec.md`

## Summary

Completar o ciclo de vida da aplicação no módulo `app` e dar à coleta um catálogo dos eventos que
o app hospedeiro já enviou. A abordagem técnica cabe em cinco movimentos:

1. **Invariantes dos prazos no domínio** (D-02): `Application` valida "ao menos um dia" e "texto
   livre nunca acima do geral" nos próprios setters privados, e a edição chama
   `defineRetention` antes de `defineOpenTextRetention` (D-04).
2. **PATCH de três estados** (D-03): `core.usecase.Patch<T>` distingue ausente, nulo e valor, e o
   `UpdateApplicationRequestDeserializer` lê a árvore JSON para enxergar a diferença que o Jackson
   esconde.
3. **Ativação idempotente** (D-05): os dois casos de uso respondem o estado atual sem gravar
   quando não há transição.
4. **Silêncio da aplicação inativa** (D-06) sem código novo de segurança: cada caso de uso da
   coleta já consulta `ApplicationScopeGateway.stateOf`.
5. **Catálogo de eventos por upsert amortizado** (D-07, D-08): `application_events` com chave
   única `(application_id, name)` e um `insert … on conflict do update … where last_seen_at <
   :threshold`, numa transação própria que não derruba a elegibilidade.

Quatro casos de uso (criar, editar, desativar, reativar), uma porta nova na coleta
(`ObservedEventRepository`), duas migrations.

## Technical Context

**Language/Version**: Java 21 (records, sealed interfaces, virtual threads ligadas)

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Data JPA, Validation), springdoc-openapi,
Jackson 3 (`tools.jackson`), Lombok (`@Getter`, `@Slf4j`). **Nenhuma dependência nova.**

**Storage**: PostgreSQL + Flyway. Tabela `applications` (`V20260905180000__create_applications.sql`)
e catálogo `application_events` (`V20260911160000__create_application_events.sql`). `ddl-auto`
segue `validate`.

**Testing**: JUnit 5 + AssertJ. Domínio em JUnit puro (`ApplicationTest`, `SlugTest`,
`NameTest`, `ObservedEventTest`); casos de uso sobre `InMemoryApplicationRepository` e
`InMemoryObservedEventRepository`; DTO com Bean Validation real; E2E com `@E2E` + Testcontainers +
`DatabaseCleaner`.

**Target Platform**: Linux server, JVM com `-Duser.timezone=UTC`

**Project Type**: web service (API REST monolítica modular)

**Performance Goals**: sem SLO numérico (Princípio V). Garantias estruturais: o catálogo de
eventos custa um comando por consulta de elegibilidade, sem `select` antes, e no máximo uma
escrita real por evento a cada cinco minutos.

**Constraints**: slug único também no banco (`unique`); nenhum valor de entrada no log; a
gravação do catálogo nunca muda a resposta ao SDK; `AdminSurfaceInterceptor` recusa
`X-Pitaco-Key` nas rotas `/applications/**`.

**Scale/Scope**: 4 rotas administrativas, 4 casos de uso, 1 porta nova em `collect`, 3 erros
nomeados reaproveitados ou novos em `app`, 2 migrations, 1 deserializer próprio.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Núcleo Independente de Framework** | `Application`, `Slug`, `Name` e `ObservedEvent` são Java puro. Os casos de uso são POJOs cabeados por `@Bean` em `modules/app/infra/config/UseCasesConfiguration.java`. `Patch<T>` subiu para `core.usecase` porque a edição de pesquisa passou a precisar dele (segundo caso). | ✅ |
| **II. Domínio Que Se Valida** | Prazos validados nos setters privados de `Application` com códigos `application.quiet_period_invalid`, `application.retention_invalid` e `application.open_text_retention_invalid`; `Slug` e `Name` validam no compact constructor. Ausência é `Optional` (`quietPeriodDays()`, `retentionDays()`, `openTextRetentionDays()`). | ✅ |
| **III. Teste É Contrato** | Cada invariante tem teste de domínio; cada caso de uso tem teste sobre fake; cada rota tem E2E com estado lido do banco, 400, 404, 409/422 quando aplicável, 403 da chave e ausência de mudança nos caminhos de falha. | ✅ |
| **IV. Consistência da Superfície Pública** | Erros só pelo `ApiExceptionHandler`; criação responde 201 com `Location`; presenters `final class` (`ApplicationPresenter`, `CreateApplicationPresenter`); OpenAPI em `ApplicationControllerSwagger`; mensagens das constraints espelham as dos value objects e da entidade. | ✅ |
| **V. Performance Por Construção** | Unicidade do slug por constraint; catálogo com chave única e índice de listagem `(application_id, last_seen_at desc, name)` na mesma migration; upsert num comando só. | ✅ |

**Gate de Phase 0**: aprovado.

### Re-avaliação após Phase 1 (design)

Um ponto ganhou escrutínio: o catálogo de eventos mora em `collect` e não em `app`, embora a
tabela se chame `application_events`. O nome descreve o dono do dado (a aplicação); o módulo
descreve quem o produz e lê (a coleta, no caminho quente). Nenhum tipo de `collect` atravessa para
`app`. Veredito final: **aprovado**.

## Project Structure

### Documentation (this feature)

```text
specs/007-application-management/
├── plan.md
├── research.md          # D-01 a D-09
├── data-model.md
├── quickstart.md
├── contracts/
│   └── application-management.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
src/main/java/com/renanloureiroo/pitaco/
├── core/usecase/Patch.java
├── modules/app/
│   ├── domain/
│   │   ├── entities/Application.java, Status.java
│   │   └── valueobjects/Name.java, Slug.java
│   ├── application/
│   │   ├── errors/ApplicationAlreadyExistsWithSameSlug.java, ApplicationNotFound.java,
│   │   │          ApplicationIsInactive.java
│   │   ├── outputs/ApplicationOutput.java
│   │   ├── repositories/ApplicationRepository.java
│   │   └── usecases/CreateApplicationUseCase.java, UpdateApplicationUseCase.java,
│   │                ActivateApplicationUseCase.java, DeactivateApplicationUseCase.java
│   └── infra/
│       ├── config/UseCasesConfiguration.java
│       ├── database/jpa/entities/ApplicationJpaEntity.java
│       ├── database/jpa/mappers/ApplicationJpaMapper.java
│       ├── database/jpa/repositories/ApplicationJpaRepository.java, ApplicationRepositoryJpa.java
│       └── http/
│           ├── controllers/ApplicationController.java, ApplicationControllerSwagger.java
│           ├── dtos/CreateApplicationRequestDTO.java, CreateApplicationResponseDTO.java,
│           │        UpdateApplicationRequestDTO.java, UpdateApplicationRequestDeserializer.java,
│           │        ApplicationResponseDTO.java
│           └── presenters/CreateApplicationPresenter.java, ApplicationPresenter.java
└── modules/collect/
    ├── domain/entities/ObservedEvent.java, ObservedEventId.java
    ├── application/
    │   ├── gateways/ApplicationScopeGateway.java, ApplicationScopeState.java
    │   ├── repositories/ObservedEventRepository.java
    │   └── usecases/FindEligibleSurveyUseCase.java          (alterado: observe())
    └── infra/
        ├── database/jpa/entities/ApplicationEventJpaEntity.java
        ├── database/jpa/mappers/ObservedEventMapper.java
        ├── database/jpa/repositories/ApplicationEventJpaRepository.java,
        │                             ObservedEventRepositoryJpa.java
        └── gateways/ApplicationScopeGatewayApp.java

src/main/resources/db/migration/
├── V20260905180000__create_applications.sql
└── V20260911160000__create_application_events.sql

src/test/java/com/renanloureiroo/pitaco/
├── modules/app/
│   ├── domain/entities/ApplicationTest.java
│   ├── domain/valueobjects/NameTest.java, SlugTest.java
│   ├── application/usecases/CreateApplicationUseCaseTest.java, UpdateApplicationUseCaseTest.java,
│   │                        ApplicationStatusUseCasesTest.java
│   ├── infra/http/dtos/CreateApplicationRequestDTOTest.java, UpdateApplicationRequestDTOTest.java
│   └── infra/http/controllers/CreateApplicationE2ETest.java, UpdateApplicationE2ETest.java,
│                              ApplicationStatusE2ETest.java
├── modules/collect/
│   ├── domain/entities/ObservedEventTest.java
│   ├── application/usecases/FindEligibleSurveyUseCaseTest.java
│   └── infra/http/controllers/CollectEligibilityE2ETest.java
└── testsupport/
    ├── factories/ApplicationFactory.java
    └── repositories/InMemoryApplicationRepository.java, InMemoryObservedEventRepository.java
```

**Structure Decision**: tudo cabe em `modules/app` e `modules/collect`, sem estrutura nova. `core`
só ganha `Patch<T>`, que nasceu aqui e subiu quando a edição de pesquisa o reutilizou.

## Complexity Tracking

| Item | Por que existe | Alternativa mais simples descartada |
| --- | --- | --- |
| `UpdateApplicationRequestDeserializer` | O Jackson entrega `Optional` vazio tanto para campo ausente quanto para `null`; o PATCH precisa dos dois separados (D-03). | `JsonNullable` de biblioteca externa — dependência nova para um caso só. |
