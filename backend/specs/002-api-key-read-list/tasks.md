---
description: "Task list para a fatia de leitura de chaves de API"
---

# Tasks: Chaves de API — listagem e consulta

**Input**: documentos de design em `/specs/002-api-key-read-list/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/api-keys-read.openapi.yaml](./contracts/api-keys-read.openapi.yaml)

**Tests**: incluídos e **obrigatórios**. A constituição do projeto (Princípio III) e o AGENTS.md
exigem que cada artefato entre depois do teste que o exige. Toda tarefa de teste vem antes da
tarefa de implementação que ela cobre, e deve **falhar** antes dela.

**Organization**: agrupado por user story, para que US1 possa ser entregue e validada sozinha.

## Format: `[ID] [P?] [Story] Descrição`

- **[P]**: pode rodar em paralelo (arquivo diferente, sem dependência pendente)
- **[Story]**: US1 (listagem) ou US2 (consulta individual)
- Caminhos relativos à raiz do repositório (`backend/`)

## Path Conventions

Projeto único, modular por contexto: `src/main/java/com/renanloureiroo/pitaco/`,
`src/test/java/com/renanloureiroo/pitaco/`, migrations em `src/main/resources/db/migration/`.

---

## Phase 1: Setup

**Purpose**: garantir que a base de onde a fatia parte está verde.

- [X] T001 Rodar `./mvnw verify` na branch `002-api-key-read-list` e confirmar suíte verde antes de qualquer alteração (baseline; Docker precisa estar de pé)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: o estado derivado e o DTO de item são compartilhados pelas duas histórias (D-04, D-10).

**⚠️ CRITICAL**: nenhuma user story começa antes desta fase terminar.

- [X] T002 Escrever os testes de estado derivado em `src/test/java/com/renanloureiroo/pitaco/modules/app/domain/entities/ApiKeyTest.java`: `status()` devolve `ACTIVE` para chave recém-criada e `REVOKED` depois de `revoke()`, e `restore` de chave com `revokedAt` presente devolve `REVOKED` (devem falhar antes de T003/T004)
- [X] T003 Criar o enum `ApiKeyStatus` com os valores `ACTIVE` e `REVOKED` em `src/main/java/com/renanloureiroo/pitaco/modules/app/domain/entities/ApiKeyStatus.java` — Java puro, sem anotação de framework, sem persistência (D-04)
- [X] T004 Adicionar o acessor `public ApiKeyStatus status()` em `src/main/java/com/renanloureiroo/pitaco/modules/app/domain/entities/ApiKey.java`, derivando de `revokedAt` (`null ⇒ ACTIVE`, presente ⇒ `REVOKED`); manter `isRevoked()` e nenhum campo, construtor ou invariante nova
- [X] T005 Estender `src/test/java/com/renanloureiroo/pitaco/testsupport/factories/ApiKeyFactory.java` com fluência para chave revogada e para lote com `createdAt` controlado (necessário para ordenação e paginação determinísticas nos testes de US1 e US2)
- [X] T006 [P] Criar o DTO de item compartilhado `ApiKeyResponseDTO` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/dtos/ApiKeyResponseDTO.java` com `id`, `applicationId`, `label`, `prefix`, `status`, `createdAt` e `revokedAt` (anulável), com `@Schema` em português — **sem** nenhum campo de segredo ou hash (FR-004) e sem factory estática

**Checkpoint**: estado derivado e forma da resposta prontos — US1 e US2 podem começar em paralelo.

---

## Phase 3: User Story 1 - Ver as chaves de uma aplicação (Priority: P1) 🎯 MVP

**Goal**: `GET /applications/{applicationId}/api-keys` devolve, paginado e com filtro opcional por
estado, as chaves da aplicação informada — da mais recente para a mais antiga, sem nenhum segredo.

**Independent Test**: emitir duas chaves para uma aplicação, revogar uma, listar as chaves da
aplicação e conferir que as duas aparecem com o estado correto, que nenhum segredo em texto claro
está presente na resposta, e que chaves de outra aplicação não aparecem.

### Porta de repositório e fake

- [X] T007 [US1] Adicionar `Page<ApiKey> findPage(Query query)` e o `record` aninhado `Query(ApplicationId, Optional<ApiKeyStatus>, int page, int size) implements PageQuery` em `src/main/java/com/renanloureiroo/pitaco/modules/app/application/repositories/ApiKeyRepository.java` — sobre `core.pagination.Page`/`PageQuery`, Java puro, sem `Pageable`/`Page` do Spring Data (D-03 revisada)
- [X] T008 [US1] Implementar `findPage` em `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryApiKeyRepository.java` honrando o contrato de data-model.md: recorte por aplicação, filtro por estado, ordenação `createdAt` desc com desempate por `id` desc, `skip`/`limit` da página, `total` do conjunto filtrado inteiro, página além do fim devolvendo lista vazia sem exceção, devolvendo cópias

### Caso de uso

- [X] T009 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/application/usecases/ListApiKeysUseCaseTest.java` sobre `InMemoryApiKeyRepository` e `InMemoryApplicationRepository`: caminho feliz com ordenação, filtro `ACTIVE`/`REVOKED`/ausente, isolamento entre duas aplicações, lista vazia sem erro, página além do fim, cálculo de `totalPages`, `ApplicationNotFound` para aplicação inexistente e para `applicationId` malformado, e aplicação inativa listando normalmente (FR-011) — sem mock sobre porta do projeto, asserções em AssertJ sobre `code`, nunca sobre mensagem
- [X] T010 [US1] Implementar `ListApiKeysUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/app/application/usecases/ListApiKeysUseCase.java` com `Input`/`Output`/`Item` como `record` aninhados, seguindo o fluxo de data-model.md (traduz id, valida existência da aplicação sem consultar atividade, chama `findPage`, calcula `totalPages`), sem `@Service` e sem anotação de framework; log de sucesso com aplicação, filtro e quantidade — nunca rótulo, prefixo ou segredo

### Persistência

- [X] T011 [P] [US1] Criar a migration `src/main/resources/db/migration/V20260908160000__index_api_keys_listing.sql` com `create index idx_api_keys_application_id_created_at on api_keys (application_id, created_at desc, id desc)` e `drop index idx_api_keys_application_id` (D-05) — migration nova, nenhuma existente editada
- [X] T012 [US1] Adicionar os três métodos derivados paginados (`findByApplicationId`, `findByApplicationIdAndRevokedAtIsNull`, `findByApplicationIdAndRevokedAtIsNotNull`, todos com `Pageable`) em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/database/jpa/repositories/ApiKeyJpaRepository.java` — sem JPQL, sem `EntityManager`
- [X] T013 [US1] Implementar `findPage` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/database/jpa/repositories/ApiKeyRepositoryJpa.java` montando `PageRequest.of(page, size, Sort.by(desc("createdAt"), desc("id")))`, escolhendo o método derivado pelo filtro e mapeando com `ApiKeyJpaMapper::toDomain` — única classe da fatia autorizada a importar `org.springframework.data.domain`

### Borda HTTP

- [X] T014 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/infra/http/dtos/ListApiKeysQueryDTOTest.java` cobrindo os padrões aplicados por `toInput` (`page=0`, `size=20`, `status` ausente ⇒ `Optional.empty()`), a conversão de `status` para `ApiKeyStatus` e cada constraint com a respectiva mensagem — este é o teste em que afirmar sobre mensagem é o contrato
- [X] T015 [US1] Criar `ListApiKeysQueryDTO` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/dtos/ListApiKeysQueryDTO.java` como `record` com `status` (`@Pattern("active|revoked")`), `page` (`@Min(0)`) e `size` (`@Min(1)` `@Max(100)`), mensagens em português conforme data-model.md, e `toInput(String applicationId)` aplicando os padrões (D-09)
- [X] T016 [P] [US1] Criar o envelope com `items`, `page`, `size`, `total` (int64) e `totalPages`, com `@Schema` em português — entregue como `ApiKeyPageResponseDTO` e depois generalizado para `src/main/java/com/renanloureiroo/pitaco/infra/http/dtos/PageResponseDTO.java`, compartilhado por toda listagem (D-03 revisada)
- [X] T017 [US1] Criar `ListApiKeysPresenter` com `public static PageResponseDTO<ApiKeyResponseDTO> present(ListApiKeysUseCase.Output)` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/presenters/ListApiKeysPresenter.java`, convertendo `ApiKeyStatus` para texto minúsculo e `Optional<Instant>` para o campo anulável do JSON (D-10)
- [X] T018 [US1] Declarar a operação de listagem em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/ApiKeyControllerSwagger.java` com um `@ApiResponse` para cada status possível (200, 400, 404), conforme `contracts/api-keys-read.openapi.yaml`
- [X] T019 [US1] Adicionar o handler `GET /{applicationId}/api-keys` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/ApiKeyController.java`, recebendo `@Valid ListApiKeysQueryDTO`, delegando ao caso de uso e montando a resposta pelo presenter — sem `try/catch` de tradução e sem `/api` na rota
- [X] T020 [US1] Registrar o `@Bean` de `ListApiKeysUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/config/UseCasesConfiguration.java`

### E2E

- [X] T021 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/ListApiKeysE2ETest.java` com `@E2E` e `DatabaseCleaner` no `@BeforeEach` (nunca `@Transactional`): caminho feliz com status, corpo e conferência contra o estado lido do banco; lista vazia com `total=0`; travessia de páginas sem repetir nem omitir (SC-003); isolamento entre duas aplicações (SC-004); filtro por estado; aplicação inativa listando 200; `application.not_found` para aplicação inexistente e para id malformado; `request.invalid` para `page=-1`, `size=0`, `size=101` e `status` inválido; ausência de `secret`/`hash` em toda resposta (SC-002); e que nada mudou no banco após cada caminho, inclusive os de falha (FR-018, SC-006)

**Checkpoint**: US1 completa e entregável como MVP — a listagem devolve o controle sobre as chaves.

---

## Phase 4: User Story 2 - Consultar uma chave específica (Priority: P2)

**Goal**: `GET /applications/{applicationId}/api-keys/{apiKeyId}` devolve os mesmos campos de um
item da listagem para uma única chave, no contexto da aplicação que a emitiu.

**Independent Test**: emitir uma chave, consultá-la pelo identificador e conferir que os dados
batem com os da emissão, sem o segredo; depois consultá-la informando outra aplicação e conferir
que não é encontrada.

**Nota de independência**: US2 não depende de nenhuma tarefa de US1 — reaproveita
`findByIdAndApplicationId`, que já existe desde a feature 001 (D-08).

- [X] T022 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/application/usecases/GetApiKeyUseCaseTest.java` sobre os fakes: chave válida encontrada com `status = ACTIVE`; chave revogada encontrada com `status = REVOKED` e `revokedAt` presente (FR-017); `ApiKeyNotFound` para chave inexistente, para chave de outra aplicação e para `apiKeyId` malformado; `ApplicationNotFound` para aplicação inexistente e para `applicationId` malformado — asserções sobre `code`, nunca sobre mensagem
- [X] T023 [US2] Implementar `GetApiKeyUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/app/application/usecases/GetApiKeyUseCase.java` com `Input(String applicationId, String apiKeyId)` e `Output` de mesmos campos do `Item` de US1, seguindo os cinco passos de data-model.md, sem anotação de framework e sem nenhuma escrita
- [X] T024 [US2] Criar `GetApiKeyPresenter` com `public static ApiKeyResponseDTO present(GetApiKeyUseCase.Output)` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/presenters/GetApiKeyPresenter.java`
- [X] T025 [US2] Declarar a operação de consulta em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/ApiKeyControllerSwagger.java` com um `@ApiResponse` para cada status possível (200, 404), conforme `contracts/api-keys-read.openapi.yaml`
- [X] T026 [US2] Adicionar o handler `GET /{applicationId}/api-keys/{apiKeyId}` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/ApiKeyController.java`, delegando ao caso de uso e montando a resposta pelo presenter
- [X] T027 [US2] Registrar o `@Bean` de `GetApiKeyUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/config/UseCasesConfiguration.java`
- [X] T028 [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/GetApiKeyE2ETest.java` com `@E2E` e `DatabaseCleaner`: caminho feliz conferido contra o banco; chave revogada com `status` e `revokedAt`; `api_key.not_found` para chave inexistente, para chave de outra aplicação e para id malformado; `application.not_found` para aplicação inexistente; ausência de `secret`/`hash` em toda resposta; estado do banco inalterado em todos os caminhos

**Checkpoint**: as duas histórias funcionam de forma independente.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T029 [P] Conferir a resposta gerada do OpenAPI em `/api/v3/api-docs` contra `specs/002-api-key-read-list/contracts/api-keys-read.openapi.yaml` — caminhos, parâmetros, esquemas e códigos de status
- [X] T030 Executar a validação manual de `specs/002-api-key-read-list/quickstart.md` do passo 1 ao 9 com `./mvnw spring-boot:run`, em especial a varredura por `secret|hash` (SC-002) e a releitura final que prova que nada mudou (SC-006)
- [X] T031 Rodar `./mvnw verify` e confirmar a suíte verde, sem teste ignorado — portão obrigatório antes do PR

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependência
- **Foundational (Phase 2)**: depende do Setup — **bloqueia as duas user stories**
- **US1 (Phase 3)** e **US2 (Phase 4)**: dependem só da Phase 2; entre si são independentes
- **Polish (Phase 5)**: depende das histórias desejadas estarem completas

### User Story Dependencies

- **US1 (P1)**: começa após T006. Nenhuma dependência de US2.
- **US2 (P2)**: começa após T006. Nenhuma dependência de US1 — usa `findByIdAndApplicationId`, já existente.

### Dentro de cada história

- Teste antes da implementação que ele cobre, e deve falhar antes
- Porta e fake antes do caso de uso (T007, T008 → T009, T010)
- Caso de uso antes da borda HTTP
- Presenter e Swagger antes do handler do controller (T017, T018 → T019)
- E2E por último, depois do `@Bean` cabeado (T020 → T021; T027 → T028)

### Parallel Opportunities

- T006 é paralelo a T002–T005 (arquivo novo, sem dependência)
- T011 (migration) é paralelo a T009/T010 — nada em Java depende dela até T013
- T014 e T016 são paralelos entre si e a T009–T013 (arquivos distintos)
- Após T006, um par de pessoas pode tocar US1 e US2 ao mesmo tempo; o único arquivo compartilhado
  é `ApiKeyController.java`/`ApiKeyControllerSwagger.java`/`UseCasesConfiguration.java`, que exigem
  coordenação em T019/T026, T018/T025 e T020/T027

---

## Parallel Example: fase 3

```bash
# Depois de T008, estas três frentes andam juntas:
Task: "T009 ListApiKeysUseCaseTest sobre os fakes"
Task: "T011 migration V20260908160000__index_api_keys_listing.sql"
Task: "T014 ListApiKeysQueryDTOTest"
```

---

## Implementation Strategy

### MVP First (US1)

1. Phase 1: Setup — baseline verde
2. Phase 2: Foundational — `ApiKeyStatus`, `status()`, factory, DTO de item
3. Phase 3: US1 — listagem completa com E2E
4. **PARE e VALIDE**: quickstart passos 1–4, 6, 7 e 9
5. Entregável: o identificador de qualquer chave deixa de ser informação perecível (SC-001)

### Incremental Delivery

1. Setup + Foundational → base pronta
2. US1 → validar → entregar (MVP)
3. US2 → validar → entregar
4. Polish → conferência de OpenAPI, quickstart completo e `./mvnw verify`

---

## Notes

- Nenhuma tarefa desta fatia escreve no banco em nenhum caminho (FR-018)
- Nenhum DTO, log ou mensagem de erro pode conter segredo em claro ou hash (FR-004)
- Nenhum `code` de erro novo: `application.not_found` e `api_key.not_found` são reaproveitados (FR-019)
- Commit por tarefa ou grupo lógico; `./mvnw verify` antes do PR
