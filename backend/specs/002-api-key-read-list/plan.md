# Implementation Plan: Chaves de API — listagem e consulta

**Branch**: `002-api-key-read-list` | **Date**: 2026-09-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-api-key-read-list/spec.md`

## Summary

Dois caminhos de leitura sobre a `ApiKey` que a feature 001 já criou: listar as chaves de
uma aplicação, paginado e com filtro opcional por estado, e consultar uma chave pelo
identificador. Nenhum dado novo é guardado — a fatia é estritamente de leitura, e o segredo
continua existindo em exatamente um lugar: a resposta da emissão.

Três decisões carregam o plano. O estado (`active`/`revoked`) vira um enum `ApiKeyStatus`
**derivado** de `revokedAt`, sem coluna nova e sem mudar a entidade (D-04) — é o mesmo estado
que 001 já modelava pela ausência do instante de revogação, agora com nome para poder ser
filtrado e apresentado. A paginação é por deslocamento (`page`/`size`), porque a spec pede
total e navegação por página e o volume é administrativo (D-02); ela **não** ganha um tipo
`Page<T>` e `PageQuery` genéricos em `core/pagination`, com `PageResponseDTO<T>` como envelope
único na borda (D-03). E a ordenação é `created_at desc, id desc`,
determinística por construção, sustentada por um índice composto novo que torna o índice
`idx_api_keys_application_id` redundante (D-05).

FR-005 estava aberto na spec e é resolvido aqui como **D-01**: sem filtro, a listagem devolve
válidas e revogadas.

## Technical Context

**Language/Version**: Java 21 (virtual threads ligadas, `-Duser.timezone=UTC`)

**Primary Dependencies**: Spring Boot 4.1 (Web MVC, Data JPA, Validation), springdoc-openapi,
Lombok, Flyway. **Nenhuma dependência nova** — a paginação usa `Pageable`/`Page` do Spring Data,
que já está no projeto.

**Storage**: PostgreSQL. **Nenhuma tabela nova e nenhuma coluna nova**: a única migration é de
índice (D-05). `ddl-auto` segue `validate`. Redis não é tocado.

**Testing**: JUnit 5 + AssertJ nos três níveis — domínio (estado derivado), caso de uso sobre
`InMemoryApiKeyRepository`, E2E com `@E2E` + Testcontainers e `DatabaseCleaner`.

**Target Platform**: servidor Linux, JVM 21.

**Project Type**: web service (API REST monolítica, modular por contexto).

**Performance Goals**: sem SLO numérico (a constituição os adia até haver baseline de tráfego).
A regra estrutural desta fatia: ler uma página é O(size) sobre o índice composto, não O(total)
— o `limit` desce até o banco e a ordenação sai do índice, sem `sort` em memória (FR-020,
SC-008).

**Constraints**: nenhuma escrita em nenhum caminho, inclusive de falha (FR-018). O segredo em
claro e o hash **NÃO PODEM** aparecer em resposta, log ou mensagem de erro (FR-004) — o DTO de
resposta não tem campo para eles e a entidade não tem getter para o texto claro.

**Scale/Scope**: dois endpoints, dois casos de uso, um enum de domínio derivado, três DTOs,
dois presenters, uma migration de índice. Alterados: `ApiKey` (acessor `status()`),
`ApiKeyRepository` e seus dois adaptadores (produção e fake), `ApiKeyJpaRepository`,
`ApiKeyController`, `ApiKeyControllerSwagger`, `UseCasesConfiguration`. Fora de escopo: busca
textual, filtro por data, ordenação escolhida pelo cliente, consulta por prefixo, listagem
global entre aplicações e autenticação.

## Constitution Check

*GATE: verificado antes da Phase 0 e reavaliado após a Phase 1.*

| Princípio | Como este plano atende | Situação |
| --- | --- | --- |
| **I. Núcleo independente de framework** | `ApiKeyStatus` e os dois casos de uso são Java puro. `ListApiKeysUseCase` e `GetApiKeyUseCase` implementam `UseCase` de `core.usecase`, com `Input`/`Output` como `record` aninhados, sem `@Service` — cabeados por `@Bean` no `UseCasesConfiguration` do módulo. O tipo de página que atravessa a porta é `core.pagination.Page<T>`, Java puro, e a consulta implementa `core.pagination.PageQuery`: `Pageable`/`Page` do Spring Data existem **apenas** dentro de `ApiKeyRepositoryJpa` e `ApiKeyJpaRepository`, nunca em `application` (D-03). Persistência por métodos derivados de Spring Data, sem `EntityManager`. | ✅ |
| **II. Domínio que se valida** | Nenhuma invariante nova: a fatia não constrói entidade, só lê. `ApiKeyStatus` é derivado em `ApiKey.status()` a partir de `revokedAt`, sem campo e sem construtor novo (D-04) — o mapper segue chamando `restore`. `revokedAt()` continua `Optional`. Identificador em formato inválido continua virando "não encontrado" pelo mesmo caminho de 001 (D-08), reaproveitando `ApplicationNotFound` e `ApiKeyNotFound` — nenhum `code` novo é criado para motivo que já tem um. | ✅ |
| **III. Teste é contrato** | Cada artefato entra depois do teste que o exige. Zero mock sobre porta do projeto: `InMemoryApiKeyRepository` ganha a implementação da nova consulta, com a mesma ordenação e o mesmo recorte da versão JPA — é o fake que prova o contrato da porta. E2E com `@E2E`, `DatabaseCleaner` no `@BeforeEach`, sem `@Transactional`, cobrindo caminho feliz (status, corpo e conferência contra o estado do banco), lista vazia, travessia de páginas sem repetir nem omitir, isolamento entre duas aplicações, cada erro anunciado no OpenAPI e a ausência do segredo em toda resposta. Asserção sobre `code`, nunca sobre mensagem — exceto no teste do DTO de query, onde a mensagem é o contrato. | ✅ |
| **IV. Consistência da superfície pública** | Rotas sem `/api`, aninhadas sob a aplicação como em 001. Nenhum `try/catch` de tradução: `ApiExceptionHandler` segue o ponto único, e a validação dos parâmetros de página chega nele como `MethodArgumentNotValidException` porque a query vira um `record` DTO com Bean Validation (D-09), o mesmo caminho já usado pelo corpo de requisição. Interface `ApiKeyControllerSwagger` ganha um `@ApiResponse` por status possível (200/400/404 na listagem; 200/404 na consulta). Saída montada por presenter; o DTO de resposta não conhece o caso de uso. Português nas mensagens e `@Schema`, inglês nos campos JSON e nos valores de `status`. | ✅ |
| **V. Performance por construção** | Listagem **é** paginada, com padrão 20 e teto 100 (D-02) — não existe caminho que devolva resultado ilimitado. Índice composto `(application_id, created_at desc, id desc)` criado na mesma migration que introduz a consulta que o usa, e o `idx_api_keys_application_id` redundante é removido nela (D-05). Sem N+1: a listagem é uma consulta de página mais a contagem que o próprio Spring Data emite, ambas sobre o índice; nenhuma travessia de associação, nenhuma chamada a repositório dentro de laço. Sem `synchronized`, sem `ThreadLocal`. Cache não entra — não há problema medido. | ✅ |

**Portões adicionais**: `./mvnw verify` verde; nenhuma migration existente editada (a nova é
`V20260908160000__index_api_keys_listing.sql`, com `drop index` do redundante — remoção de
índice em migration nova, não edição da antiga); OpenAPI completo para os dois endpoints novos;
`README.md` só é tocado se alguma decisão arquitetural mudar — e nenhuma muda.

**Violações a justificar**: nenhuma. A seção Complexity Tracking fica vazia.

## Project Structure

### Documentation (this feature)

```text
specs/002-api-key-read-list/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── api-keys-read.openapi.yaml
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks — NÃO criado aqui)
```

### Source Code (repository root)

```text
src/main/java/com/renanloureiroo/pitaco/
├── core/
│   ├── pagination/Page.java                              + novo (items + total, com map)
│   ├── pagination/PageQuery.java                         + novo (page/size/offset)
│   ├── presenter/Presenter.java                          (reusado, sem alteração)
│   └── usecase/UseCase.java                              (reusado, sem alteração)
└── modules/app/
    ├── domain/entities/
    │   ├── ApiKey.java                                   ~ acessor status()
    │   └── ApiKeyStatus.java                             + enum derivado (ACTIVE, REVOKED)
    ├── application/
    │   ├── errors/
    │   │   ├── ApplicationNotFound.java                  (reusado)
    │   │   └── ApiKeyNotFound.java                       (reusado)
    │   ├── repositories/ApiKeyRepository.java            ~ + findPage, records Query e Page
    │   └── usecases/
    │       ├── ListApiKeysUseCase.java                   + novo
    │       └── GetApiKeyUseCase.java                     + novo
    └── infra/
        ├── config/UseCasesConfiguration.java             ~ + 2 @Bean
        ├── database/jpa/repositories/
        │   ├── ApiKeyJpaRepository.java                  ~ + 3 métodos derivados paginados
        │   └── ApiKeyRepositoryJpa.java                  ~ + findPage
        └── http/
            ├── controllers/
            │   ├── ApiKeyController.java                 ~ + 2 handlers
            │   └── ApiKeyControllerSwagger.java          ~ + 2 operações
            ├── dtos/
            │   ├── ListApiKeysQueryDTO.java              + novo (status, page, size)
            │   ├── ApiKeyResponseDTO.java                + novo (item, compartilhado)
            │   └── (envelope: infra/http/dtos/PageResponseDTO)
            └── presenters/
                ├── ListApiKeysPresenter.java             + novo
                └── GetApiKeyPresenter.java               + novo

src/main/resources/db/migration/
└── V20260908160000__index_api_keys_listing.sql           + novo

src/test/java/com/renanloureiroo/pitaco/
├── testsupport/
│   ├── factories/ApiKeyFactory.java                      ~ + revogada e lote com createdAt
│   └── repositories/InMemoryApiKeyRepository.java        ~ + findPage
└── modules/app/
    ├── domain/entities/ApiKeyTest.java                   ~ + status derivado
    ├── application/usecases/
    │   ├── ListApiKeysUseCaseTest.java                   + novo
    │   └── GetApiKeyUseCaseTest.java                     + novo
    └── infra/http/
        ├── dtos/ListApiKeysQueryDTOTest.java             + novo
        └── controllers/
            ├── ListApiKeysE2ETest.java                   + novo
            └── GetApiKeyE2ETest.java                     + novo
```

**Structure Decision**: nada de estrutura nova. A fatia continua dentro de `modules/app`, ao
lado de `Application` e da própria `ApiKey`, sob a rota já existente
`/applications/{applicationId}/api-keys` — a chave segue sem vida fora da aplicação, e a
decisão D-01 de 001 (não criar módulo próprio) continua valendo sem custo novo. `core` não
recebe `pagination/Page.java` e `pagination/PageQuery.java`, os dois tipos compartilhados por
toda consulta paginada que vier (D-03).

## Complexity Tracking

> Sem violações da constituição a justificar. Nenhuma abstração nova é introduzida: a fatia
> reaproveita `UseCase`, `Presenter`, os erros nomeados e a porta de repositório já existentes,
> O único tipo novo em `core` é o par `Page<T>`/`PageQuery` de `core/pagination`, antecipado por
> decisão do dono do projeto para que a segunda listagem já nasça sobre ele (D-03, revisada).
