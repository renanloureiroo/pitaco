# Implementation Plan: Chaves de API — criação e exclusão

**Branch**: `001-api-key-management` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-api-key-management/spec.md`

## Summary

Emitir e revogar credenciais de aplicação. A emissão devolve, uma única vez, um segredo
aleatório de 256 bits; o sistema guarda apenas o hash SHA-256 e um prefixo público. A exclusão
é revogação com trilha preservada: a linha permanece, marcada com o instante da revogação, sem
volta e sem vestígio do segredo.

`ApiKey` nasce dentro do módulo `modules/app`, ao lado de `Application`, com rota aninhada
`/applications/{applicationId}/api-keys` — a chave não tem vida fora da aplicação nesta fatia, e
um módulo próprio custaria acoplamento entre módulos ou um tipo em `core` com um único
consumidor (D-01). A regra de "já revogada" mora no domínio; a corrida entre duas exclusões
simultâneas é resolvida por `update` condicional, sem lock nem transação explícita (D-05).
`ApplicationId` e `ApiKeyId` passam a validar formato UUID na construção, e o caso de uso traduz
essa falha em "não encontrado" para não entregar oráculo de formato (D-06).

Esta feature também paga a dívida registrada no Princípio IV: introduz `core/presenter/Presenter`
e migra `CreateApplicationResponseDTO.from(...)` para o formato de presenter (D-07).

## Technical Context

**Language/Version**: Java 21 (virtual threads ligadas, `-Duser.timezone=UTC`)

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator), Flyway,
springdoc-openapi, Lombok, OpenTelemetry. **Nenhuma dependência nova** — a geração e o hash do
segredo usam `java.security.SecureRandom` e `MessageDigest` do JDK (D-03).

**Storage**: PostgreSQL, schema por migration Flyway (`ddl-auto: validate`). Uma tabela nova,
`api_keys`. Redis não é tocado nesta fatia.

**Testing**: JUnit 5 + AssertJ nos três níveis (domínio, caso de uso sobre fake in-memory, E2E
com `@E2E` + Testcontainers e `DatabaseCleaner`).

**Target Platform**: servidor Linux, JVM 21.

**Project Type**: web service (API REST monolítica, modular por contexto).

**Performance Goals**: sem SLO numérico (a constituição os adia até haver baseline de tráfego).
As duas operações são O(1) sobre índice: emissão é um `insert`; revogação é um `select` por
chave primária mais um `update` condicional.

**Constraints**: o segredo em claro existe em exatamente um lugar — a resposta da emissão.
Não vai para o banco, nem para log, nem para mensagem de erro (FR-007, SC-003). Todo caminho de
falha deixa o estado armazenado intacto (FR-017).

**Scale/Scope**: dois endpoints, uma entidade, dois value objects, dois identificadores, dois
casos de uso, três erros nomeados, uma migration. Dois arquivos já existentes são alterados fora
da borda HTTP: `core/identity/Id.java` (predicado `isUuid`) e `ApplicationId` (validação).
Fora de escopo: verificar chave, listar, renomear, rotacionar, expirar, escopos, expurgo e
autenticação da própria gestão.

## Constitution Check

*GATE: verificado antes da Phase 0 e reavaliado após a Phase 1.*

| Princípio | Como este plano atende | Situação |
| --- | --- | --- |
| **I. Núcleo independente de framework** | `ApiKey`, `ApiKeyId`, `ApiKeyLabel`, `ApiKeySecret` e os dois casos de uso são Java puro (JDK + Lombok `@Getter`/`@Slf4j`). Portas em `application/repositories`, adaptadores em `infra`. Casos de uso cabeados por `@Bean` no `UseCasesConfiguration` do módulo, sem `@Service`. Ambos implementam interface de `core.usecase` — `UseCase` na emissão, `UseCaseWithoutOutput` na revogação. Persistência só via Spring Data; o `update` condicional é `@Modifying @Query`, não `EntityManager`. | ✅ |
| **II. Domínio que se valida** | `ApiKeyLabel` valida no compact constructor; `ApiKey` valida no construtor privado e em `revoke()`, lançando `DomainException` de dentro do domínio. `ApiKeyId` e `ApplicationId` estendem `Id` e validam o formato UUID no construtor privado, cada um com o próprio `code` (D-06). `create`/`restore` separados — o mapper chama `restore`. `revokedAt()` devolve `Optional`. `code` no formato `<contexto>.<motivo>`, em constante `private static final`. Erros recorrentes viram classe nomeada em `application/errors`. | ✅ |
| **III. Teste é contrato** | Todo artefato entra depois do teste que o exige. Nenhum mock sobre porta do projeto: `InMemoryApiKeyRepository` novo em `testsupport/repositories`, `ApiKeyFactory` novo em `testsupport/factories`. E2E com `@E2E`, `DatabaseCleaner` no `@BeforeEach`, sem `@Transactional`, cobrindo caminho feliz (status, corpo, `Location` e estado relido do banco), validação, cada erro do OpenAPI, JSON malformado, e a não-mudança de estado nas falhas. Asserção sobre `code`, nunca sobre mensagem — exceto no teste de DTO, onde a mensagem é o contrato. | ✅ |
| **IV. Consistência da superfície pública** | Rotas sem `/api`. Zero `try/catch` de tradução na borda: `ApiExceptionHandler` continua o ponto único. Interface `ApiKeyControllerSwagger` com um `@ApiResponse` por status possível (201/400/404/422 na emissão; 204/404/409 na revogação). DTO de entrada com Bean Validation espelhando as mensagens de `ApiKeyLabel`. Criação responde 201 com `Location`. **Presenter introduzido aqui**, com a migração de `CreateApplicationResponseDTO.from(...)` (D-07). Português nas mensagens, inglês nos identificadores e `code`. | ✅ |
| **V. Performance por construção** | Sem listagem nesta fatia, logo sem paginação a fazer e sem N+1 possível. Índices em `application_id` e `prefix` criados na mesma migration que introduz as consultas que os usam; `unique` em `secret_hash`, que é onde mora a identidade do segredo (D-08). Nenhuma chamada a repositório dentro de laço; nenhuma escrita em lote. Sem `synchronized` e sem `ThreadLocal`. Cache não entra — não há problema medido. | ✅ |

**Portões adicionais**: `./mvnw verify` verde; nenhuma migration existente editada (a nova é
`V20260908120000__create_api_keys.sql`); `README.md` atualizado apenas se alguma decisão
arquitetural mudar — o presenter muda, e entra no README.

**Emenda de constituição**: ao fim desta feature o Princípio IV deixa de ter ponto pendente. A
frase "*este é o único ponto ainda não implementado*" e o TODO correspondente do Sync Impact
Report saem por emenda PATCH (1.0.0 → 1.0.1), em PR próprio como o capítulo de Governança exige.
Deixá-la no ar é o que tornaria a constituição aspiracional — exatamente o que ela declara
não ser.

**Reavaliação pós-Phase 1**: sem violação nova. `data-model.md` e `contracts/` não introduziram
nenhum tipo de framework no núcleo, nenhum `ErrorType` novo e nenhuma dependência.
A tabela **Complexity Tracking** fica vazia.

## Project Structure

### Documentation (this feature)

```text
specs/001-api-key-management/
├── plan.md                          # Este arquivo
├── research.md                      # Phase 0 — D-01 a D-08
├── data-model.md                    # Phase 1 — domínio, persistência, portas, erros, casos de uso
├── quickstart.md                    # Phase 1 — como validar de ponta a ponta
├── contracts/
│   └── api-keys.openapi.yaml        # Phase 1 — contrato dos dois endpoints
├── checklists/
│   └── requirements.md              # já existente, todos os itens passando
└── tasks.md                         # Phase 2 — gerado por /speckit-tasks, não por este comando
```

### Source Code (repository root = `backend/`)

Arquivos **novos** marcados com `+`, **alterados** com `~`.

```text
src/main/java/com/renanloureiroo/pitaco/
├── core/
│   ├── identity/
│   │   └── Id.java                                               ~ (predicado isUuid)
│   └── presenter/
│       └── Presenter.java                                        + (dívida do Princípio IV)
└── modules/app/
    ├── domain/
    │   ├── entities/
    │   │   ├── ApiKey.java                                       +
    │   │   ├── ApiKeyId.java                                     +
    │   │   └── ApplicationId.java                                ~ (valida formato UUID)
    │   └── valueobjects/
    │       ├── ApiKeyLabel.java                                  +
    │       └── ApiKeySecret.java                                 +
    ├── application/
    │   ├── errors/
    │   │   ├── ApplicationNotFound.java                          +
    │   │   ├── ApplicationIsInactive.java                        +
    │   │   └── ApiKeyNotFound.java                               +
    │   ├── repositories/
    │   │   └── ApiKeyRepository.java                             +
    │   └── usecases/
    │       ├── IssueApiKeyUseCase.java                           +
    │       └── RevokeApiKeyUseCase.java                          +
    └── infra/
        ├── config/
        │   └── UseCasesConfiguration.java                        ~ (dois @Bean novos)
        ├── database/jpa/
        │   ├── entities/ApiKeyJpaEntity.java                     +
        │   ├── mappers/ApiKeyJpaMapper.java                      +
        │   └── repositories/
        │       ├── ApiKeyJpaRepository.java                      +
        │       └── ApiKeyRepositoryJpa.java                      +
        └── http/
            ├── controllers/
            │   ├── ApiKeyController.java                         +
            │   ├── ApiKeyControllerSwagger.java                  +
            │   └── ApplicationController.java                    ~ (passa a usar presenter)
            ├── dtos/
            │   ├── IssueApiKeyRequestDTO.java                    +
            │   ├── IssueApiKeyResponseDTO.java                   +
            │   └── CreateApplicationResponseDTO.java             ~ (perde o from estático)
            └── presenters/
                ├── IssueApiKeyPresenter.java                     +
                └── CreateApplicationPresenter.java               +

src/main/resources/db/migration/
└── V20260908120000__create_api_keys.sql                          +

src/test/java/com/renanloureiroo/pitaco/
├── core/identity/IdTest.java                                     ~ (opacidade preservada)
├── modules/app/
│   ├── domain/
│   │   ├── entities/
│   │   │   ├── ApiKeyTest.java                                   +
│   │   │   ├── ApiKeyIdTest.java                                 +
│   │   │   └── ApplicationIdTest.java                            +
│   │   └── valueobjects/
│   │       ├── ApiKeyLabelTest.java                              +
│   │       └── ApiKeySecretTest.java                             +
│   ├── application/usecases/
│   │   ├── IssueApiKeyUseCaseTest.java                           +
│   │   └── RevokeApiKeyUseCaseTest.java                          +
│   └── infra/http/
│       ├── controllers/
│       │   ├── IssueApiKeyE2ETest.java                           +
│       │   └── RevokeApiKeyE2ETest.java                          +
│       └── dtos/IssueApiKeyRequestDTOTest.java                   +
└── testsupport/
    ├── factories/ApiKeyFactory.java                              +
    └── repositories/InMemoryApiKeyRepository.java                +
```

**Structure Decision**: mantém-se a estrutura modular já em uso — `core` compartilhado,
`modules/<contexto>` em três camadas. A chave de API entra em `modules/app` (D-01) porque não
existe segundo consumidor que justifique módulo próprio ou promoção de `ApplicationId` a `core`;
a constituição barra a abstração até o segundo caso concreto aparecer. Quando a verificação de
chave nascer — o contexto de autenticação, fora de escopo aqui —, extrair `ApiKey` para um
módulo próprio é movimento de arquivos, sem mudança de regra.

## Ordem de execução

Segue o fluxo obrigatório da constituição, com o teste sempre antes do código que ele cobre:

1. `Id.isUuid`, `ApplicationId`, `ApiKeyId` — com os testes de formato antes; depois
   `ApiKeyLabel`, `ApiKeySecret`, `ApiKey`, com os testes de invariante e de transição
   (incluindo "revogar duas vezes" e "não existe reativação").
2. `ApiKeyRepository` + `InMemoryApiKeyRepository` + `ApiKeyFactory`.
3. `ApplicationNotFound`, `ApplicationIsInactive`, `ApiKeyNotFound`; `IssueApiKeyUseCase` e
   `RevokeApiKeyUseCase` — com os testes sobre o fake antes.
4. Migration, `ApiKeyJpaEntity`, mapper, `ApiKeyJpaRepository`, `ApiKeyRepositoryJpa`.
5. `Presenter` no core + os dois presenters + migração do `CreateApplicationResponseDTO`
   (com o E2E de `POST /applications` seguindo verde, sem alteração — é a prova de que a
   migração não mudou o contrato).
6. `IssueApiKeyRequestDTO` (com teste de constraints antes), `IssueApiKeyResponseDTO`,
   `ApiKeyControllerSwagger`, `ApiKeyController`.
7. E2E dos dois endpoints.
8. `@Bean` dos dois casos de uso no `UseCasesConfiguration`.
9. `README.md`: o formato de resposta passa a ser montado por presenter.
10. Emenda PATCH da constituição, tirando o presenter da lista de pendências do Princípio IV.

## Complexity Tracking

> Preencher apenas se o Constitution Check tiver violação a justificar.

Nenhuma violação. Nenhuma dependência nova, nenhum `ErrorType` novo, nenhuma abstração
antecipada — `Transactor` continua sem uso (escrita única em ambos os casos de uso) e
`AggregateRoot` continua sem existir (não há evento de domínio nesta fatia).
