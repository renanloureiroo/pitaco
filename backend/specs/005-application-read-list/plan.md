# Implementation Plan: Aplicações — listagem e consulta

**Branch**: `005-application-read-list` | **Date**: 2026-09-08 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-application-read-list/spec.md`

## Summary

Duas rotas de leitura no módulo `app`: `GET /applications`, paginada e com filtro por estado, e
`GET /applications/{applicationId}`, com os prazos de política que a linha da listagem não
carrega. É a fatia gêmea da `002-api-key-read-list`, um nível acima — e paga uma dívida
concreta: o `201` da criação já devolve `Location: /api/applications/{id}` para uma rota que
hoje não existe.

Abordagem: nenhuma entidade, coluna ou código de erro novo. Entram uma porta paginada
(`findPage` no mesmo molde de `ApiKeyRepository`), dois casos de uso sem transação, dois DTOs de
resposta — resumo e detalhe —, dois presenters e uma migration só de índice. Sai `findAll()` da
porta, hoje sem chamador em produção. A superfície administrativa (FR-019) já cobre
`/applications/**` pelo `AdminSurfaceInterceptor`: vira cobertura de teste, não implementação.

Decisões e alternativas descartadas em [`research.md`](research.md).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Data JPA, Validation), springdoc-openapi,
Lombok (`@Getter`, `@Slf4j`), OpenTelemetry

**Storage**: PostgreSQL + Flyway. Tabela `applications` já existe; esta fatia só acrescenta
índice.

**Testing**: JUnit 5 + AssertJ; fakes in-memory de `testsupport/repositories`; Testcontainers
(Postgres + LGTM) no E2E via `@E2E` e `DatabaseCleaner`

**Target Platform**: servidor Linux, JVM com virtual threads e `-Duser.timezone=UTC`

**Project Type**: web service (API REST, módulo `app` de um monólito modular)

**Performance Goals**: sem SLO numérico — a constituição os adia até haver baseline de tráfego
real. A meta estrutural é FR-018: o custo de ler uma página não cresce com o total cadastrado.

**Constraints**: leitura estrita, sem escrita em nenhum caminho; nenhum segredo de chave em
resposta ou log; ordenação determinística entre páginas; `page`/`size` validados na borda

**Scale/Scope**: dezenas de aplicações no horizonte visível; página padrão 20, máxima 100. Dois
endpoints, dois casos de uso, uma migration de índice.

Nenhum `NEEDS CLARIFICATION` remanescente.

## Constitution Check

*GATE: passa antes da Phase 0 e recheado após a Phase 1 — resultado idêntico nas duas passagens.*

| Princípio | Como esta fatia atende | Situação |
|---|---|---|
| **I. Núcleo independente de framework** | Casos de uso são POJOs implementando `UseCase`, com `Input`/`Output` aninhados e cabeados por `@Bean` no `UseCasesConfiguration`. Persistência só pela porta do módulo, com o adaptador sobre Spring Data. `Pageable`/`Page` do Spring Data não saem de `infra`. Nenhum import de framework em `domain`. | ✅ |
| **II. Domínio que se valida** | Nenhum tipo de domínio novo. `ApplicationNotFound` (`application.not_found`) é reusado — nenhum código de erro nasce. Ausência de prazo continua `Optional` até a borda, onde vira campo ausente, nunca `0`. | ✅ |
| **III. Teste é contrato** | Três níveis, teste antes do código: caso de uso sobre `InMemoryApplicationRepository` (sem mock de porta), DTO de entrada sobre as constraints, E2E cobrindo caminho feliz com estado relido do banco, `400` de validação e cada erro anunciado no OpenAPI. Dado de `ApplicationFactory`. | ✅ |
| **IV. Consistência da superfície pública** | Rotas sem `/api`; erro só pelo `ApiExceptionHandler` em RFC 9457, sem `try/catch` na borda; entrada em `record` DTO com Bean Validation e mensagens espelhando `ListApiKeysQueryDTO`; saída por presenter `final class` com `static present`; OpenAPI em `ApplicationControllerSwagger` com todo status declarado. | ✅ |
| **V. Performance por construção** | Listagem paginada com teto de 100; os dois índices nascem na migration que introduz a consulta; sem N+1 e sem repositório em laço — uma consulta por caso de uso; ordenação servida pelo índice, sem sort em memória. | ✅ |

**Sem violação a justificar** — a seção de Complexity Tracking fica de fora.

Dois pontos que a constituição decide e vale registrar:

- **`findAll()` sai da porta** (D-03). "Um endpoint de coleção NUNCA devolve resultado
  ilimitado; todo `findAll` sem filtro é uso interno ou de teste." Aqui não há uso interno
  algum: é código morto, e deixá-lo ao lado do `findPage` novo ofereceria o atalho errado a
  quem escrever o próximo caso de uso.
- **`applicationIdOf` duplicado fica** (D-11). "Abstração entra quando existe o segundo caso
  concreto" já foi ultrapassado — o padrão aparece 12 vezes. Mas extrair agora tocaria seis
  casos de uso fora desta fatia; fica registrado como candidata a fatia própria em vez de
  virar refactor transversal embutido em dois endpoints.

## Project Structure

### Documentation (this feature)

```text
specs/005-application-read-list/
├── plan.md                          # este arquivo
├── spec.md
├── research.md                      # Phase 0 — D-01 a D-11
├── data-model.md                    # Phase 1
├── quickstart.md                    # Phase 1
├── contracts/
│   └── applications-read.md         # Phase 1
├── checklists/
│   └── requirements.md
└── tasks.md                         # /speckit-tasks — ainda não criado
```

### Source Code (repository root)

Tudo dentro do módulo `app` já existente. `+` nasce, `~` é alterado.

```text
src/main/java/com/renanloureiroo/pitaco/modules/app/
├── application/
│   ├── repositories/
│   │   └── ApplicationRepository.java                    ~ +findPage/Query, −findAll
│   └── usecases/
│       ├── ListApplicationsUseCase.java                  +
│       └── GetApplicationUseCase.java                    +
└── infra/
    ├── config/
    │   └── UseCasesConfiguration.java                    ~ dois @Bean
    ├── database/jpa/repositories/
    │   ├── ApplicationJpaRepository.java                 ~ +findByStatus(Pageable)
    │   └── ApplicationRepositoryJpa.java                 ~ +findPage, −findAll
    └── http/
        ├── controllers/
        │   ├── ApplicationController.java                ~ list() e get()
        │   └── ApplicationControllerSwagger.java         ~ OpenAPI das duas rotas
        ├── dtos/
        │   ├── ListApplicationsQueryDTO.java             +
        │   ├── ApplicationSummaryResponseDTO.java        +
        │   └── ApplicationResponseDTO.java               +
        └── presenters/
            ├── ListApplicationsPresenter.java            +
            └── GetApplicationPresenter.java              +

src/main/resources/db/migration/
└── V2026<...>__index_applications_listing.sql            +

src/test/java/com/renanloureiroo/pitaco/
├── testsupport/repositories/
│   └── InMemoryApplicationRepository.java                ~ +findPage, findAll sem @Override
└── modules/app/
    ├── application/usecases/
    │   ├── ListApplicationsUseCaseTest.java              +
    │   └── GetApplicationUseCaseTest.java                +
    └── infra/http/
        ├── dtos/ListApplicationsQueryDTOTest.java        +
        ├── controllers/ListApplicationsE2ETest.java      +
        └── controllers/GetApplicationE2ETest.java        +
```

**Structure Decision**: monólito modular com `core` compartilhado e módulos em
`modules/<contexto>`, cada um em `domain` → `application` → `infra`. Esta fatia vive inteira em
`modules/app` e não toca `core` — `core.pagination` e `PageResponseDTO` já existem e são
consumidos como estão. Nada sobe para `core`: não há segundo módulo precisando disto.

## Ordem de trabalho

A ordem da constituição, sem a etapa 1 — não há domínio novo a criar.

1. **Porta + fake**: `findPage`/`Query` em `ApplicationRepository`, `findAll()` fora;
   `InMemoryApplicationRepository` implementa o contrato dos cinco pontos de `data-model.md`.
2. **Casos de uso, com os testes antes**: `ListApplicationsUseCase` (filtro, ordem, página além
   do fim, conjunto vazio, total) e `GetApplicationUseCase` (encontrado, inativa encontrada,
   prazos ausentes, inexistente, malformado).
3. **Adaptador JPA + migration**: `findByStatus(Pageable)`, `findPage` no
   `ApplicationRepositoryJpa`, os dois índices.
4. **DTO de entrada, com o teste de constraints antes**: `ListApplicationsQueryDTO`.
5. **Saída e borda**: os dois DTOs de resposta, os dois presenters, o OpenAPI no
   `ApplicationControllerSwagger`, os dois métodos no `ApplicationController`.
6. **E2E**: `ListApplicationsE2ETest` e `GetApplicationE2ETest` — caminho feliz das duas rotas com o estado relido do
   banco, `400` de validação, `404` inexistente e malformado, `403` de superfície, e o conjunto
   inalterado depois de cada falha.
7. **Cabeamento**: os dois `@Bean` no `UseCasesConfiguration`.

O detalhamento em tarefas é trabalho do `/speckit-tasks`.
