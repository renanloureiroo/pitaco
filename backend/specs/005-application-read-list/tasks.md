---

description: "Task list template for feature implementation"
---

# Tasks: Aplicações — listagem e consulta

**Input**: Design documents from `/specs/005-application-read-list/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/applications-read.md](contracts/applications-read.md),
[quickstart.md](quickstart.md)

**Tests**: obrigatórios. O Princípio III da constituição é NÃO-NEGOCIÁVEL — "toda regra de
negócio nasce com teste que falha antes de existir código que a satisfaça". Aqui isso não é uma
escolha da fatia.

**Organization**: por história. As duas são independentes de verdade — a consulta usa o
`findById` que já existe e não depende de nada que a listagem introduz.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivo diferente, sem dependência pendente)
- **[Story]**: US1 (listagem) ou US2 (consulta)
- Caminho de arquivo em toda tarefa

## Path Conventions

Monólito modular. Raiz do backend em `/Users/renanloureiro/www/pitaco/backend`; os caminhos
abaixo são relativos a ela. Produção em `src/main/java/com/renanloureiroo/pitaco/`, teste em
`src/test/java/com/renanloureiroo/pitaco/`, migration em `src/main/resources/db/migration/`.
Tudo desta fatia vive em `modules/app`.

---

## Phase 1: Setup

**Purpose**: confirmar o ponto de partida. Não há projeto a inicializar nem dependência a
adicionar — a stack está de pé e a fatia não traz biblioteca nova.

- [X] T001 Rodar `./mvnw verify` e confirmar a suíte verde antes de tocar em qualquer arquivo, para que toda falha a partir daqui seja desta fatia

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: tirar `findAll()` da porta antes que qualquer história a altere (D-03). Sai
primeiro porque `ApplicationRepository` é o arquivo que a US1 também mexe — fazer a limpeza
depois seria mexer duas vezes no mesmo lugar.

**⚠️ CRITICAL**: T002 a T005 quebram a compilação enquanto não estiverem os quatro prontos. É um
passo só, em quatro arquivos.

- [X] T002 Remover `List<Application> findAll()` de `src/main/java/com/renanloureiroo/pitaco/modules/app/application/repositories/ApplicationRepository.java` — sem chamador em produção, e uma coleção ilimitada na porta contraria o Princípio V
- [X] T003 [P] Remover a implementação de `findAll()` de `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/database/jpa/repositories/ApplicationRepositoryJpa.java`, junto com o import de `java.util.List` que ficar órfão
- [X] T004 [P] Manter `findAll()` em `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryApplicationRepository.java` retirando o `@Override` — ele passa a ser o que sempre foi: espião de teste, não contrato
- [X] T005 [P] Trocar o tipo do campo `applications` de `ApplicationRepository` para `InMemoryApplicationRepository` em `src/test/java/com/renanloureiroo/pitaco/modules/app/application/usecases/CreateApplicationUseCaseTest.java` (linha 21), que é quem chama `findAll()` na linha 95
- [X] T006 Rodar `./mvnw test -Dtest='!*E2ETest,!ContextPathTest,!OpenApiConfigTest,!PitacoApplicationTests'` e confirmar que a remoção não deixou nada para trás

**Checkpoint**: porta limpa. As duas histórias podem começar, em paralelo se houver quem as toque.

---

## Phase 3: User Story 1 — Ver todas as aplicações cadastradas (Priority: P1) 🎯 MVP

**Goal**: `GET /applications` paginada, filtrável por estado, ordenada da mais recente para a
mais antiga, trazendo ativas e inativas. É a porta de entrada do painel: hoje não há como chegar
a uma aplicação sem já saber o identificador dela.

**Independent Test**: criar três aplicações, desativar uma, listar e conferir que as três
aparecem com o estado correto, com identificador, slug, nome e instante de criação, e que a
paginação informa o total. Entrega valor sem a US2 existir.

### Porta e fake

- [X] T007 [US1] Acrescentar `Page<Application> findPage(Query query)` e `record Query(Optional<Status> status, int page, int size) implements PageQuery` a `src/main/java/com/renanloureiroo/pitaco/modules/app/application/repositories/ApplicationRepository.java`, com o comentário do contrato no molde de `ApiKeyRepository.findPage`
- [X] T008 [US1] Implementar `findPage` em `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryApplicationRepository.java` honrando os cinco pontos do contrato em `data-model.md`: filtro vazio traz ativas e inativas, `total` conta o conjunto filtrado inteiro, ordem `createdAt desc` com desempate `id desc`, página além do fim devolve vazio sem erro, e nenhuma escrita

### Caso de uso — teste antes

- [X] T009 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/application/usecases/ListApplicationsUseCaseTest.java` sobre o fake, cobrindo: lista com ativas e inativas sem filtro (FR-003), filtro por cada estado com `total` refletindo o filtro (FR-004), ordem da mais recente para a mais antiga (FR-007), desempate determinístico entre criadas no mesmo instante, página além do fim vazia com `total` correto (FR-008), conjunto vazio com `total` zero, e `totalPages` calculado. AssertJ, `@DisplayName` em português. Deve falhar por não compilar
- [X] T010 [US1] Implementar `src/main/java/com/renanloureiroo/pitaco/modules/app/application/usecases/ListApplicationsUseCase.java` — `UseCase<Input, Output>`, POJO sem anotação de framework e sem `@Transactional` (D-10), com `Input`/`Output`/`Item` aninhados conforme `data-model.md`, usando `Page.map` e `totalPages(size)`, e `@Slf4j` logando o sucesso com filtro e total. Nunca logar valor de entrada
- [X] T011 [US1] Registrar `@Bean ListApplicationsUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/config/UseCasesConfiguration.java`, recebendo só `ApplicationRepository`

### Persistência

- [X] T012 [P] [US1] Acrescentar `Page<ApplicationJpaEntity> findByStatus(String status, Pageable pageable)` a `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/database/jpa/repositories/ApplicationJpaRepository.java` (D-05)
- [X] T013 [US1] Implementar `findPage` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/database/jpa/repositories/ApplicationRepositoryJpa.java` montando `PageRequest.of(page, size, Sort.by(desc("createdAt"), desc("id")))`, delegando a `findByStatus` com filtro e ao `findAll(Pageable)` herdado sem filtro, e traduzindo para `core.pagination.Page`. `Pageable` e o `Page` do Spring Data não saem daqui
- [X] T014 [P] [US1] Criar `src/main/resources/db/migration/V<yyyyMMddHHmmss>__index_applications_listing.sql` com timestamp UTC e os dois índices de D-04 — `idx_applications_created_at (created_at desc, id desc)` e `idx_applications_status_created_at (status, created_at desc, id desc)` — comentando por que são dois. Só índice: nenhuma coluna nasce, nenhuma migration existente é tocada

### Borda — DTO de entrada com teste antes

- [X] T015 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/infra/http/dtos/ListApplicationsQueryDTOTest.java` cobrindo as constraints e as mensagens: `page` negativa, `size` zero, `size` acima de 100, `status` fora de `active|inactive`, os padrões aplicados quando os campos vêm ausentes, e `toInput()` convertendo o estado. Este é o teste em que afirmar sobre mensagem é legítimo
- [X] T016 [US1] Implementar `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/dtos/ListApplicationsQueryDTO.java` com `@Pattern("active|inactive")`, `@Min(0)` em `page`, `@Min(1)`/`@Max(100)` em `size`, padrões 0 e 20, `@Schema` em português e `toInput()`. Mensagens espelhando as de `ListApiKeysQueryDTO`

### Borda — saída

- [X] T017 [P] [US1] Criar `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/dtos/ApplicationSummaryResponseDTO.java` com `id`, `slug`, `name`, `status`, `createdAt`, todos `RequiredMode.REQUIRED`, com `@Schema` em português (D-06)
- [X] T018 [US1] Criar `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/presenters/ListApplicationsPresenter.java` — `final class`, construtor privado, um `public static PageResponseDTO<ApplicationSummaryResponseDTO> present(Output)`, com `status.name().toLowerCase(Locale.ROOT)`
- [X] T019 [US1] Declarar `GET /applications` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/ApplicationControllerSwagger.java` com `@Operation`, os parâmetros de consulta e um `@ApiResponse` para cada status de `contracts/applications-read.md`: 200, 400 com `ApiValidationErrorResponse`, 403 com `ApiErrorResponse`
- [X] T020 [US1] Acrescentar `list()` a `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/ApplicationController.java` com `@GetMapping(produces = APPLICATION_JSON_VALUE)` e `@Valid @ModelAttribute`, chamando o caso de uso e devolvendo o presenter. Sem `try/catch` e sem montar DTO no controller

### E2E

- [X] T021 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/ListApplicationsE2ETest.java` com `@E2E`, `DatabaseCleaner` no `@BeforeEach` e sem `@Transactional`, cobrindo: caminho feliz com ativas e inativas e o estado relido do banco, ordem por criação, filtro por cada estado, página além do fim, conjunto vazio, `400` para `size=0` e para `status` desconhecido, `403` com `api_key.forbidden_surface` apresentando o header `X-Pitaco-Key`, e que o conjunto de aplicações não mudou depois de cada falha

**Checkpoint**: a listagem responde sozinha. O painel já tem primeira tela, com ou sem a US2.

---

## Phase 4: User Story 2 — Consultar uma aplicação específica (Priority: P2)

**Goal**: `GET /applications/{applicationId}` devolvendo a aplicação inteira — prazos de
política e instante da última alteração inclusos. Fecha o contrato que o `Location` do `201` da
criação já promete e hoje não cumpre.

**Independent Test**: criar uma aplicação com prazos configurados, consultá-la e conferir que os
campos batem com o que foi definido na criação; depois consultar um identificador inexistente e
conferir a recusa. Não depende de nada da US1: usa o `findById` que já existe.

### Caso de uso — teste antes

- [X] T022 [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/application/usecases/GetApplicationUseCaseTest.java` sobre o fake, cobrindo: aplicação encontrada com todos os campos, aplicação sem prazo nenhum devolvendo `Optional` vazio e não zero (FR-012), aplicação inativa encontrada (FR-014), identificador inexistente e identificador malformado lançando `ApplicationNotFound`. Afirmar sobre `type` e `code`, nunca sobre a mensagem
- [X] T023 [US2] Implementar `src/main/java/com/renanloureiroo/pitaco/modules/app/application/usecases/GetApplicationUseCase.java` — `UseCase<Input, Output>`, sem `@Transactional`, com o helper privado `applicationIdOf` traduzindo `DomainException` em `ApplicationNotFound` e o comentário que explica a escolha (D-11), `Output` com os três prazos como `Optional<Integer>`, e `@Slf4j` logando o sucesso. Não expor `effectiveOpenTextRetentionDays()` (D-07)
- [X] T024 [US2] Registrar `@Bean GetApplicationUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/config/UseCasesConfiguration.java`

### Borda

- [X] T025 [P] [US2] Criar `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/dtos/ApplicationResponseDTO.java` — os cinco campos do resumo mais `quietPeriodDays`, `retentionDays` e `openTextRetentionDays` com `nullable = true`, e `updatedAt` obrigatório, com `@Schema` em português deixando claro que prazo ausente é prazo não configurado
- [X] T026 [US2] Criar `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/presenters/GetApplicationPresenter.java` — `final class` com `public static ApplicationResponseDTO present(Output)`, fazendo `orElse(null)` nos três prazos e minúsculo no estado
- [X] T027 [US2] Declarar `GET /applications/{applicationId}` em `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/ApplicationControllerSwagger.java` com 200, 403 e 404 com `ApiErrorResponse`, registrando na descrição que malformado e inexistente respondem igual
- [X] T028 [US2] Acrescentar `get()` a `src/main/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/ApplicationController.java` com `@GetMapping("/{applicationId}")`

### E2E

- [X] T029 [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/app/infra/http/controllers/GetApplicationE2ETest.java` com `@E2E`, `DatabaseCleaner` e sem `@Transactional`, cobrindo: caminho feliz com prazos configurados e o estado relido do banco, aplicação sem prazo nenhum com as três chaves **ausentes** do JSON, aplicação inativa encontrada, `404` com `application.not_found` para inexistente e para malformado, `403` com `api_key.forbidden_surface`, e o `Location` do `201` da criação abrindo a consulta com sucesso — é o cenário que prova a dívida paga

**Checkpoint**: as duas rotas respondem, cada uma testável por si.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T030 Conferir que nenhum arquivo de `modules/app/domain` ganhou import de `org.springframework`, `jakarta.persistence`, `jakarta.validation` ou `io.swagger` nesta fatia, e que `Pageable`/`Page` do Spring Data não saíram de `infra`
- [X] T031 Percorrer o checklist de [quickstart.md](quickstart.md) com a app de pé, incluindo a conferência dos dois índices no banco
- [X] T032 Rodar `./mvnw verify` — o portão da constituição, sem teste ignorado ou desabilitado

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependência
- **Foundational (Phase 2)**: depende do Setup — **bloqueia as duas histórias**, porque toca o arquivo de porta que a US1 também altera
- **US1 (Phase 3)** e **US2 (Phase 4)**: dependem só da Phase 2 e **não dependem uma da outra**
- **Polish (Phase 5)**: depende das histórias que se quiser entregar

### User Story Dependencies

- **US1 (P1)**: independente. Introduz `findPage`, o índice e o DTO de filtro — nada disso é usado pela US2
- **US2 (P2)**: independente. Usa `findById`, que já existe desde a fatia 001

O único ponto de encontro são dois arquivos compartilhados — `ApplicationController`,
`ApplicationControllerSwagger` e `UseCasesConfiguration` — onde cada história acrescenta o seu
método ou bean sem tocar no do outro. Se as duas forem tocadas em paralelo, é aí que o merge
encosta.

### Within Each User Story

- Porta e fake antes do teste de caso de uso (o teste precisa do fake para compilar)
- Teste de caso de uso escrito e **falhando** antes do caso de uso
- Teste de DTO escrito antes do DTO
- Presenter e Swagger antes do controller
- E2E por último, quando há rota para exercitar

### Parallel Opportunities

- **Phase 2**: T003, T004 e T005 em paralelo depois de T002 — três arquivos distintos
- **US1**: T012 e T014 em paralelo com a trilha do caso de uso (T009→T011); T017 em paralelo com T015/T016
- **US2**: T025 em paralelo com a trilha do caso de uso (T022→T024)
- **Entre histórias**: US1 e US2 inteiras em paralelo, respeitando os três arquivos compartilhados

Exemplo, com duas pessoas depois da Phase 2:

```
pessoa A: T007 → T008 → T009 → T010 → T011 → T013 → T015 → T016 → T018 → T019 → T020 → T021
          (T012 e T014 e T017 encaixam onde couber)
pessoa B: T022 → T023 → T024 → T026 → T027 → T028 → T029
          (T025 encaixa onde couber)
```

---

## Implementation Strategy

### MVP

**Phase 1 + Phase 2 + Phase 3 (US1)** — 21 tarefas. Entrega a listagem, que é o que devolve o
identificador perdido e dá primeira tela ao painel. Parável aqui com valor real na mão.

### Incremento seguinte

**Phase 4 (US2)** — 8 tarefas. Transforma a linha da lista em tela e faz o `Location` da criação
apontar para algo que existe.

### Fechamento

**Phase 5** — 3 tarefas de portão.

### Ordem recomendada para uma pessoa só

Sequencial, T001 a T032. A Phase 2 primeiro não é preferência: fazer a limpeza de `findAll()`
depois da US1 significaria mexer duas vezes no mesmo arquivo de porta.
