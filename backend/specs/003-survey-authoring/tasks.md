---
description: "Task list para a autoria de pesquisa — rascunho, perguntas, disparo, regras e versões"
---

# Tasks: Autoria de pesquisa — rascunho, perguntas, disparo, regras e versões

**Input**: documentos de design em `/specs/003-survey-authoring/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/surveys.openapi.yaml](./contracts/surveys.openapi.yaml)

**Tests**: incluídos e **obrigatórios**. A constituição (Princípio III) e o `AGENTS.md` exigem que
cada artefato entre depois do teste que o exige. Toda tarefa de teste vem antes da tarefa de
implementação que ela cobre e deve **falhar** antes dela.

**Organization**: agrupado por user story, na ordem de entrega que o `plan.md` fixa —
US1+US2 formam o MVP, US3 e US4 vêm em seguida, US5 e US6 fecham a fatia.

## Format: `[ID] [P?] [Story] Descrição`

- **[P]**: pode rodar em paralelo (arquivo diferente, sem dependência pendente)
- **[Story]**: US1 a US6, conforme as histórias da spec
- Caminhos relativos à raiz do repositório (`backend/`)

## Path Conventions

Projeto único, modular por contexto. Prefixo de produção
`src/main/java/com/renanloureiroo/pitaco/`, de teste
`src/test/java/com/renanloureiroo/pitaco/`, migrations em `src/main/resources/db/migration/`.

---

## Phase 1: Setup

**Purpose**: partir de uma base verde e com o esqueleto do módulo novo no lugar.

- [ ] T001 Rodar `./mvnw verify` na branch `003-survey-authoring` e confirmar a suíte verde antes de qualquer alteração (baseline; Docker precisa estar de pé)
- [ ] T002 Criar a estrutura de pacotes do módulo novo sob `src/main/java/com/renanloureiroo/pitaco/modules/survey/`: `domain/entities`, `domain/valueobjects`, `domain/publication`, `application/errors`, `application/gateways`, `application/repositories`, `application/usecases`, `infra/config`, `infra/gateways`, `infra/database/jpa/{entities,mappers,repositories}`, `infra/http/{controllers,dtos,presenters}` (D-01)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: a promoção de `ApplicationId`, os identificadores, o schema e a porta de acesso à
aplicação são usados por **todas** as histórias.

**⚠️ CRITICAL**: nenhuma user story começa antes desta fase terminar.

- [ ] T003 Mover `ApplicationId` de `src/main/java/com/renanloureiroo/pitaco/modules/app/domain/entities/ApplicationId.java` para `src/main/java/com/renanloureiroo/pitaco/core/identity/ApplicationId.java` e atualizar todos os imports em `modules/app` — mudança mecânica, sem alteração de comportamento, `code` `application.id_invalid` preservado (D-02)
- [ ] T004 Mover `src/test/java/com/renanloureiroo/pitaco/modules/app/domain/entities/ApplicationIdTest.java` para `src/test/java/com/renanloureiroo/pitaco/core/identity/ApplicationIdTest.java` e rodar `./mvnw test` confirmando que nada quebrou com a promoção
- [ ] T005 [P] Escrever os testes dos cinco identificadores em `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/{SurveyIdTest,SurveyVersionIdTest,QuestionIdTest,SegmentationRuleIdTest,SurveyStateTransitionIdTest}.java`: `generate()` produz UUID válido, `of()` aceita UUID e recusa formato inválido com o `code` próprio de cada um (devem falhar antes de T006)
- [ ] T006 Criar os cinco identificadores em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/{SurveyId,SurveyVersionId,QuestionId,SegmentationRuleId,SurveyStateTransitionId}.java`, todos estendendo `core.identity.Id` com `generate()`/`of()` e os `code` `survey.id_invalid`, `survey_version.id_invalid`, `question.id_invalid`, `segmentation_rule.id_invalid` e `survey_state_transition.id_invalid`
- [ ] T007 [P] Criar a migration `src/main/resources/db/migration/V20260908180000__create_surveys.sql` com as seis tabelas (`surveys`, `survey_versions`, `questions`, `question_options`, `segmentation_rules`, `survey_state_transitions`), as chaves estrangeiras com `on delete cascade`, o `unique (survey_id, number)` e o índice único parcial de rascunho único em `survey_versions`, o `unique (version_id, position) deferrable initially deferred` e o `unique (version_id, question_key)` em `questions`, o `unique (question_id, value)` em `question_options` e os cinco índices de listagem/ordenação de data-model.md — migration nova, nenhuma existente editada
- [ ] T008 [P] Criar a porta `ApplicationScope` com `Optional<ApplicationScopeState> stateOf(ApplicationId)` e o enum `ApplicationScopeState { ACTIVE, INACTIVE }` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/gateways/` — Java puro, sem import de `modules/app` (D-03)
- [ ] T009 Criar o fake `InMemoryApplicationScope` em `src/test/java/com/renanloureiroo/pitaco/testsupport/gateways/InMemoryApplicationScope.java`, com fluência para registrar aplicação ativa, inativa e inexistente
- [ ] T010 Criar o adaptador `ApplicationScopeGateway` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/gateways/ApplicationScopeGateway.java`, delegando para a porta `ApplicationRepository` de `modules/app` — a travessia entre módulos acontece só aqui
- [ ] T011 [P] Criar o erro `SurveyNotFound` (`NOT_FOUND`, `survey.not_found`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/errors/SurveyNotFound.java`, carregando o `SurveyId` procurado — é o erro que identificador malformado, pesquisa inexistente e pesquisa de outra aplicação compartilham (D-16)
- [ ] T012 [P] Criar `UseCasesConfiguration` do módulo em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/config/UseCasesConfiguration.java` como `@Configuration` vazia — os `@Bean` entram na tarefa final de cada história

**Checkpoint**: `ApplicationId` em `core`, schema criado, porta de aplicação pronta — US1 e US2 podem começar.

---

## Phase 3: User Story 1 - Criar e manter o rascunho de uma pesquisa (Priority: P1) 🎯 MVP

**Goal**: criar, consultar, listar, renomear e descartar pesquisa dentro de uma aplicação, com a
versão 1 em rascunho nascendo junto.

**Independent Test**: criar duas pesquisas em uma aplicação e uma em outra, listar as da primeira,
conferir que as duas aparecem como rascunho e que a da outra aplicação não aparece; renomear uma,
consultá-la e conferir o novo nome; descartar a outra e conferir que ela some da listagem.

### Domínio

- [ ] T013 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/valueobjects/SurveyNameTest.java`: recusa vazio e só-espaços, recusa acima de 120 caracteres, aplica `strip()`, aceita o limite exato
- [ ] T014 [US1] Criar `SurveyName` como `record(String value)` com a invariante no compact constructor em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/valueobjects/SurveyName.java`
- [ ] T015 [P] [US1] Criar os enums `SurveyLifecycle` (`DRAFT`, `PUBLISHED`, `PAUSED`, `ENDED`), `SurveyState` (`DRAFT`, `SCHEDULED`, `ACTIVE`, `PAUSED`, `ENDED`) e `SurveyVersionStatus` (`DRAFT`, `PUBLISHED`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/` — Java puro, `SurveyState` não persistido (D-05)
- [ ] T016 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyTest.java`: `create` nasce `DRAFT` com `draftVersionNumber = 1`, sem `publishedVersionNumber`; `restore` volta do banco sem revalidar como criação; `rename` troca o nome; `openDraft` recusa quando já há rascunho; `discardDraft` limpa o ponteiro; `stateAt` devolve `DRAFT` para ciclo de vida `DRAFT` e `PAUSED`/`ENDED` para os respectivos, ignorando a janela
- [ ] T017 [US1] Criar a entidade `Survey` estendendo `Entity<SurveyId>` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/Survey.java` com os campos de data-model.md, `create`/`restore`, `rename`, `openDraft`, `discardDraft` e `stateAt(Instant, Optional<TriggerWindow>)` cobrindo as linhas que não dependem de janela — as linhas de janela entram em T091
- [ ] T018 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersionTest.java`: `create` nasce com `number = 1`, `status = DRAFT`, sem perguntas, sem disparo, sem regras e sem `publishedAt`; `restore` preserva o que veio do banco
- [ ] T019 [US1] Criar a entidade `SurveyVersion` estendendo `Entity<SurveyVersionId>` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersion.java` como recipiente de conteúdo (D-04), com `questions`, `trigger`, `rules`, `changeKind`, `changeSummary`, `comparabilityGroup` e `publishedAt` — o comportamento de edição entra em US2/US3 e o de publicação em US4

### Portas, fakes e factories

- [ ] T020 [US1] Criar as portas `SurveyRepository` (`create`, `findByIdAndApplicationId`, `findPage`, `update`, `delete`) e `SurveyVersionRepository` (`create`, `findDraft`, `findByNumber`, `findPublished`, `findPublishedPage`, `findAllPublished`, `update`, `delete`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/repositories/`, com os `record` aninhados `ListSurveysQuery` e `ListSurveyVersionsQuery` implementando `core.pagination.PageQuery` e devolvendo `core.pagination.Page<T>` — Java puro, sem `Pageable`/`Page` do Spring Data (D-13)
- [ ] T021 [US1] Criar os fakes `InMemorySurveyRepository` e `InMemorySurveyVersionRepository` em `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/`, honrando o mesmo contrato do adaptador JPA: recorte por aplicação, ordenação `createdAt` desc com desempate por `id` desc, recorte de página por `skip`/`limit`, `total` do conjunto inteiro, página além do fim devolvendo lista vazia sem exceção, e devolvendo cópias
- [ ] T022 [P] [US1] Criar as factories fluentes `SurveyFactory` e `SurveyVersionFactory` em `src/test/java/com/renanloureiroo/pitaco/testsupport/factories/`, com fluência para pesquisa em rascunho, publicada, pausada e encerrada, e para `createdAt` controlado (necessário à ordenação determinística)

### Casos de uso

- [ ] T023 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/CreateSurveyUseCaseTest.java` sobre `InMemoryApplicationScope`, os dois fakes e `DirectTransactor`: caminho feliz criando pesquisa e versão 1 na mesma transação; `application.not_found` para aplicação inexistente e para `applicationId` malformado; `application.inactive` para aplicação inativa (FR-002) — asserções AssertJ sobre `code`, nunca sobre mensagem
- [ ] T024 [US1] Implementar `CreateSurveyUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/CreateSurveyUseCase.java` com `Input`/`Output` como `record` aninhados, `@Transactional` de `core.transaction` no `execute` (duas escritas, D-18), sem `@Service` e sem anotação de framework
- [ ] T025 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/GetSurveyUseCaseTest.java`: devolve nome, estado derivado, aplicação dona, `createdAt`, conteúdo do rascunho, disparo, regras e versões; `survey.not_found` para pesquisa inexistente, para `surveyId` malformado e para pesquisa de outra aplicação (D-16, SC-004)
- [ ] T026 [US1] Implementar `GetSurveyUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/GetSurveyUseCase.java` — leitura pura, sem transação
- [ ] T027 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/ListSurveysUseCaseTest.java`: ordenação da mais recente para a mais antiga com desempate determinístico, isolamento entre duas aplicações, lista vazia sem erro, página além do fim, cálculo de `totalPages`, `application.not_found` para aplicação inexistente
- [ ] T028 [US1] Implementar `ListSurveysUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/ListSurveysUseCase.java` sobre `Page<Survey>`, sem transação
- [ ] T029 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/RenameSurveyUseCaseTest.java`: renomeia rascunho, mantém o ciclo de vida, recusa nome inválido pelo `SurveyName`, `survey.not_found` fora do escopo da aplicação
- [ ] T030 [US1] Implementar `RenameSurveyUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/RenameSurveyUseCase.java` — uma escrita só, **sem** `@Transactional` (D-18)
- [ ] T031 [US1] Criar o erro `PublishedSurveyCannotBeDiscarded` (`BUSINESS_RULE`, `survey.published_cannot_be_discarded`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/errors/PublishedSurveyCannotBeDiscarded.java` e escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/DiscardSurveyUseCaseTest.java`: descarta pesquisa nunca publicada junto do conteúdo; recusa pesquisa publicada; `survey.not_found` fora do escopo
- [ ] T032 [US1] Implementar `DiscardSurveyUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/DiscardSurveyUseCase.java` com `@Transactional` (versão + conteúdo + pesquisa, D-18)

### Persistência

- [ ] T033 [US1] Criar as entidades JPA `SurveyJpaEntity` e `SurveyVersionJpaEntity` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/database/jpa/entities/`, separadas das de domínio e mapeando exatamente as colunas da migration T007
- [ ] T034 [US1] Criar os mappers explícitos `SurveyJpaMapper` e `SurveyVersionJpaMapper` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/database/jpa/mappers/`, chamando `restore` — nunca `create` — e traduzindo as quatro colunas do disparo em `Optional<Trigger>` (ou as quatro juntas, ou nenhuma; D-14)
- [ ] T035 [US1] Criar `SurveyJpaRepository`/`SurveyRepositoryJpa` e `SurveyVersionJpaRepository`/`SurveyVersionRepositoryJpa` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/database/jpa/repositories/`, com `PageRequest.of(page, size, Sort.by(desc("createdAt"), desc("id")))` na listagem — as únicas classes do módulo autorizadas a importar `org.springframework.data.domain`, sem `EntityManager`

### Borda HTTP

- [ ] T036 [P] [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/dtos/{CreateSurveyRequestDTOTest,RenameSurveyRequestDTOTest,ListSurveysQueryDTOTest}.java` cobrindo cada constraint com a respectiva mensagem em português — é o teste em que afirmar sobre mensagem é o contrato — e os padrões de `page=0`/`size=20` aplicados por `toInput`
- [ ] T037 [US1] Criar `CreateSurveyRequestDTO`, `RenameSurveyRequestDTO` e `ListSurveysQueryDTO` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/dtos/`, como `record` com Bean Validation cuja mensagem espelha a de `SurveyName`, e `ListSurveysQueryDTO` com `@ParameterObject`, `@Min(0)` em `page` e `@Min(1)`/`@Max(100)` em `size` (D-09 de 002)
- [ ] T038 [US1] Criar `SurveyResponseDTO` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/dtos/SurveyResponseDTO.java` com `id`, `applicationId`, `name`, `state`, `publishedVersionNumber`, `draftVersionNumber` e `createdAt`, com `@Schema` em português, sem factory estática
- [ ] T039 [US1] Criar `CreateSurveyPresenter`, `GetSurveyPresenter` e `ListSurveysPresenter` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/presenters/`, cada um `final class` de construtor privado com um `public static R present(O output)`, convertendo `SurveyState` para minúsculas e devolvendo `PageResponseDTO<SurveyResponseDTO>` na listagem (D-19, D-13)
- [ ] T040 [US1] Criar `SurveyControllerSwagger` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/SurveyControllerSwagger.java` com um `@ApiResponse` para **cada** status das cinco operações (201/400/404/422 em `createSurvey`; 200/400/404 em `listSurveys`; 200/404 em `getSurvey`; 200/400/404 em `renameSurvey`; 204/404/422 em `discardSurvey`), conforme `contracts/surveys.openapi.yaml`
- [ ] T041 [US1] Criar `SurveyController` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/SurveyController.java` com `@RequestMapping("/applications/{applicationId}/surveys")` — sem `/api` na rota —, os cinco handlers delegando ao caso de uso e montando a resposta pelo presenter, 201 com header `Location` na criação, `produces` fixando o media type na listagem, e nenhum `try/catch` de tradução
- [ ] T042 [US1] Registrar os `@Bean` de `CreateSurveyUseCase`, `GetSurveyUseCase`, `ListSurveysUseCase`, `RenameSurveyUseCase` e `DiscardSurveyUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/config/UseCasesConfiguration.java`

### E2E

- [ ] T043 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/CreateSurveyE2ETest.java` com `@E2E` e `DatabaseCleaner` no `@BeforeEach` (nunca `@Transactional`): 201 com `Location` e estado conferido no banco, incluindo a versão 1 em `DRAFT`; 400 de validação de nome; 404 de aplicação inexistente e de id malformado; 422 de aplicação inativa; JSON malformado; e que nada foi gravado nos caminhos de falha
- [ ] T044 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/GetSurveyE2ETest.java` e `ListSurveysE2ETest.java`: caminho feliz conferido contra o banco; isolamento entre duas aplicações em ambas as leituras (SC-004); lista vazia com `total = 0`; travessia de páginas sem repetir nem omitir; `400` para `page=-1`, `size=0` e `size=101`; `survey.not_found` para pesquisa de outra aplicação e para id malformado
- [ ] T045 [US1] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/RenameSurveyE2ETest.java` e `DiscardSurveyE2ETest.java`: renomeação conferida no banco; descarte removendo pesquisa e versão; `422 survey.published_cannot_be_discarded` para pesquisa publicada; `404` fora do escopo da aplicação; estado inalterado nos caminhos de falha
- [ ] T046 [US1] Rodar `./mvnw verify` e confirmar a suíte verde com US1 completa

**Checkpoint**: o recipiente existe — dá para organizar o trabalho de pesquisa por aplicação.

---

## Phase 4: User Story 2 - Montar as perguntas (Priority: P1) 🎯 MVP

**Goal**: acrescentar, reescrever, reordenar e remover perguntas dos seis tipos dentro do rascunho,
com chave estável e posições consecutivas.

**Independent Test**: montar num rascunho uma pergunta de cada um dos seis tipos, reordenar duas
delas, remover uma, consultar a pesquisa e conferir que as cinco restantes voltam na ordem
definida, com enunciado, tipo, obrigatoriedade e opções íntegros.

### Domínio

- [ ] T047 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/valueobjects/{QuestionStatementTest,QuestionOptionTest,ScaleRangeTest,QuestionKeyTest}.java`: enunciado vazio e acima de 500 recusados com `strip()` aplicado; opção com `label` ou `value` vazio recusada; `ScaleRange` com `min >= max` recusada com `question.scale_range_invalid`; `QuestionKey.generate()` produz UUID e `of()` recusa formato inválido
- [ ] T048 [US2] Criar `QuestionStatement`, `QuestionOption`, `ScaleRange` e `QuestionKey` como `record` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/valueobjects/` — `QuestionKey` é value object de linhagem, não `Id` (D-07)
- [ ] T049 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/QuestionTypeTest.java` cobrindo a tabela de exigências dos seis tipos: `acceptsOptions()`, `requiresOptions()` e `requiresRange()` para `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `RATING`, `SCALE`, `NPS` e `FREE_TEXT`
- [ ] T050 [US2] Criar o enum `QuestionType` com a tabela embutida em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/QuestionType.java` — uma classe, não uma hierarquia de seis (D-09)
- [ ] T051 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/QuestionTest.java` separando invariante de completude (D-08): recusa opção em tipo que não aceita (`question.options_not_allowed`), opções repetidas (`question.options_duplicated`), faixa em tipo que não aceita, faixa ausente em tipo que exige e faixa fora de 0–10 em `NPS` (`question.scale_range_invalid`); **aceita** pergunta de escolha sem nenhuma opção; `create` gera `QuestionKey` nova e `restore` preserva a que veio
- [ ] T052 [US2] Criar a entidade `Question` estendendo `Entity<QuestionId>` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/Question.java` com `key`, `statement`, `type`, `position`, `required`, `options` e `range`, com as invariantes no construtor privado lançando `DomainException`
- [ ] T053 [P] [US2] Escrever em `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersionQuestionsTest.java`: `addQuestion` põe na última posição com chave nova; `updateQuestion` preserva a `QuestionKey` (FR-011); `removeQuestion` recompacta as posições sem buraco (FR-014); `reorder` exige permutação exata e recusa lista incompleta ou com repetição (`question.order_invalid`), verificando a integridade **no resultado final**; todas as quatro recusam com `survey.content_frozen` quando `status = PUBLISHED`
- [ ] T054 [US2] Criar o erro `SurveyContentFrozen` (`BUSINESS_RULE`, `survey.content_frozen`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/errors/SurveyContentFrozen.java` e implementar `addQuestion`, `updateQuestion`, `removeQuestion` e `reorder` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersion.java`
- [ ] T055 [P] [US2] Criar a factory fluente `QuestionFactory` em `src/test/java/com/renanloureiroo/pitaco/testsupport/factories/QuestionFactory.java`, com atalho para cada um dos seis tipos e para lote com posições controladas

### Casos de uso

- [ ] T056 [US2] Criar o erro `QuestionNotFound` (`NOT_FOUND`, `question.not_found`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/errors/QuestionNotFound.java` e escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/AddQuestionUseCaseTest.java`: resolve a versão editável (a única em `DRAFT`, D-17), acrescenta na última posição, `survey.content_frozen` quando não há rascunho, `survey.not_found` fora do escopo da aplicação
- [ ] T057 [US2] Implementar `AddQuestionUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/AddQuestionUseCase.java` com `@Transactional` (pergunta + reposicionamento, D-18)
- [ ] T058 [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/UpdateQuestionUseCaseTest.java`: reescreve enunciado, tipo, obrigatoriedade e opções mantendo a `QuestionKey`; `question.not_found` para pergunta de outra versão; `survey.content_frozen` sem rascunho
- [ ] T059 [US2] Implementar `UpdateQuestionUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/UpdateQuestionUseCase.java` — uma escrita, **sem** `@Transactional` (D-18)
- [ ] T060 [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/RemoveQuestionUseCaseTest.java` e `ReorderQuestionsUseCaseTest.java`: remover a do meio deixa as demais consecutivas; reordenar devolve a nova ordem sem buraco nem repetição; permutação inexata recusada; `survey.content_frozen` em ambos sem rascunho
- [ ] T061 [US2] Implementar `RemoveQuestionUseCase` e `ReorderQuestionsUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/`, ambos com `@Transactional` e usando `saveAll` para o reposicionamento — nenhuma chamada a repositório dentro de laço (Princípio V)

### Persistência

- [ ] T062 [US2] Criar `QuestionJpaEntity` e `QuestionOptionJpaEntity` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/database/jpa/entities/` e o mapper `QuestionJpaMapper` em `.../mappers/QuestionJpaMapper.java`, mapeando `range_min`/`range_max` para `Optional<ScaleRange>`
- [ ] T063 [US2] Estender `SurveyVersionJpaRepository` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/database/jpa/repositories/SurveyVersionJpaRepository.java` para trazer perguntas e opções com `join fetch` em toda leitura de versão, e estender `SurveyVersionRepositoryJpa` para persistir a coleção com `saveAll` — a porta nunca devolve versão pela metade e não há travessia preguiçosa em laço

### Borda HTTP

- [ ] T064 [P] [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/dtos/{AddQuestionRequestDTOTest,UpdateQuestionRequestDTOTest,ReorderQuestionsRequestDTOTest}.java` cobrindo cada constraint com a mensagem que espelha a do value object correspondente
- [ ] T065 [US2] Criar `AddQuestionRequestDTO`, `UpdateQuestionRequestDTO`, `ReorderQuestionsRequestDTO`, `QuestionOptionDTO`, `ScaleRangeDTO` e `QuestionResponseDTO` (com `id`, `key`, `statement`, `type`, `position`, `required`, `options` e `range`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/dtos/`
- [ ] T066 [US2] Criar `QuestionPresenter` e `ReorderQuestionsPresenter` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/presenters/`, convertendo `QuestionType` para o texto minúsculo do contrato e achatando as opções; criar `QuestionControllerSwagger` em `.../controllers/QuestionControllerSwagger.java` com um `@ApiResponse` por status (201/400/404/422 em `addQuestion`; 200/400/404/422 em `updateQuestion`; 204/404/422 em `removeQuestion`; 200/400/404/422 em `reorderQuestions`)
- [ ] T067 [US2] Criar `QuestionController` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/QuestionController.java` com os quatro handlers sob `/applications/{applicationId}/surveys/{surveyId}/questions`, 201 com `Location` na criação, e registrar os quatro `@Bean` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/config/UseCasesConfiguration.java`

### E2E

- [ ] T068 [US2] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/QuestionE2ETest.java` com `@E2E` e `DatabaseCleaner`: uma pergunta de cada um dos seis tipos aceita e relida do banco na ordem; escolha **sem** opção aceita no rascunho; `400` para enunciado vazio, opção em tipo que não aceita, opções repetidas e faixa incoerente; reordenar e remover deixando posições consecutivas e sem repetição (FR-014); `404` para pergunta inexistente e fora do escopo; `422 survey.content_frozen` nas quatro escritas contra pesquisa publicada sem rascunho (SC-003); JSON malformado; e estado inalterado nos caminhos de falha

**Checkpoint**: MVP completo — um rascunho montável ponta a ponta, com as seis perguntas.

---

## Phase 5: User Story 3 - Configurar o disparo e as regras de segmentação (Priority: P2)

**Goal**: definir o disparo único da versão em rascunho — evento, janela e proporção — e pendurar
nele as regras de segmentação.

**Independent Test**: configurar o disparo de um rascunho com evento, janela e proporção,
acrescentar duas regras de segmentação, consultar a pesquisa e conferir que tudo volta íntegro;
depois tentar uma janela com fim antes do início e conferir a recusa.

### Domínio

- [ ] T069 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/valueobjects/{EventNameTest,TriggerWindowTest,SamplingRateTest,TriggerTest}.java`: `EventName` aceita `^[a-z][a-z0-9_.]{1,79}$` e recusa vazio, maiúscula e caractere fora do padrão; `TriggerWindow` aceita `end` ausente e recusa `end <= start`; `SamplingRate` recusa fora de `[0, 1]` e aceita os extremos; `Trigger` exige as três partes presentes
- [ ] T070 [US3] Criar `EventName`, `TriggerWindow`, `SamplingRate` e `Trigger` como `record` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/valueobjects/`, com as invariantes no compact constructor
- [ ] T071 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/valueobjects/SegmentationRuleTest.java`: `attribute` vazio recusado; `EQUALS`/`NOT_EQUALS` sem valor recusadas; `PRESENT`/`ABSENT` com valor recusadas (FR-021)
- [ ] T072 [US3] Criar o enum `RuleOperation` (`EQUALS`, `NOT_EQUALS`, `PRESENT`, `ABSENT`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/RuleOperation.java` e o `record SegmentationRule` em `.../domain/valueobjects/SegmentationRule.java`
- [ ] T073 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersionTriggerTest.java`: `defineTrigger` substitui e mantém sempre um só (FR-016); `addRule` exige disparo definido; `removeRule` deixa as demais intactas; os três recusam com `survey.content_frozen` quando `status = PUBLISHED`
- [ ] T074 [US3] Criar o erro `TriggerNotDefined` (`BUSINESS_RULE`, `trigger.not_defined`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/errors/TriggerNotDefined.java` e implementar `defineTrigger`, `addRule` e `removeRule` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersion.java`
- [ ] T075 [P] [US3] Criar a factory fluente `TriggerFactory` em `src/test/java/com/renanloureiroo/pitaco/testsupport/factories/TriggerFactory.java`, com atalhos para janela já aberta, janela futura, janela fechada e janela sem fim

### Casos de uso

- [ ] T076 [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/DefineTriggerUseCaseTest.java`: define e redefine o disparo da versão editável; `survey.content_frozen` sem rascunho; `survey.not_found` fora do escopo
- [ ] T077 [US3] Implementar `DefineTriggerUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/DefineTriggerUseCase.java`
- [ ] T078 [US3] Criar o erro `SegmentationRuleNotFound` (`NOT_FOUND`, `segmentation_rule.not_found`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/errors/SegmentationRuleNotFound.java` e escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/{AddSegmentationRuleUseCaseTest,RemoveSegmentationRuleUseCaseTest}.java`: acrescenta regra vinculada ao disparo; `trigger.not_defined` quando não há disparo; remover deixa as demais intactas; `segmentation_rule.not_found` para regra inexistente ou de outra versão; `survey.content_frozen` sem rascunho
- [ ] T079 [US3] Implementar `AddSegmentationRuleUseCase` e `RemoveSegmentationRuleUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/`

### Persistência

- [ ] T080 [US3] Criar `SegmentationRuleJpaEntity` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/database/jpa/entities/SegmentationRuleJpaEntity.java`, estender `SurveyVersionJpaMapper` para as quatro colunas do disparo e para as regras, e estender a leitura de versão em `SurveyVersionJpaRepository` para trazer as regras no mesmo `join fetch` (D-14)

### Borda HTTP

- [ ] T081 [P] [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/dtos/{DefineTriggerRequestDTOTest,AddSegmentationRuleRequestDTOTest}.java` cobrindo cada constraint com a mensagem que espelha a do value object
- [ ] T082 [US3] Criar `DefineTriggerRequestDTO`, `AddSegmentationRuleRequestDTO`, `TriggerResponseDTO` e `SegmentationRuleResponseDTO` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/dtos/`
- [ ] T083 [US3] Criar `TriggerPresenter` e `SegmentationRulePresenter` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/presenters/`; criar `TriggerControllerSwagger` e `TriggerController` em `.../controllers/` com `PUT /trigger`, `POST /trigger/rules` (201 com `Location`) e `DELETE /trigger/rules/{ruleId}`, um `@ApiResponse` por status conforme o contrato; registrar os três `@Bean` em `.../infra/config/UseCasesConfiguration.java`

### E2E

- [ ] T084 [US3] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/TriggerE2ETest.java` com `@E2E` e `DatabaseCleaner`: disparo definido e relido do banco; redefinição continuando um só; janela sem fim aceita; `400` para janela com fim ≤ início, proporção fora de `[0,1]`, evento vazio ou fora do formato, regra `present` com valor e regra `equals` sem valor; regra acrescentada e removida com as demais intactas; `422 trigger.not_defined` ao acrescentar regra sem disparo; `422 survey.content_frozen` nas três escritas contra pesquisa publicada; `404` fora do escopo; estado inalterado nos caminhos de falha

**Checkpoint**: o rascunho tem conteúdo e gatilho — falta congelá-lo.

---

## Phase 6: User Story 4 - Publicar, congelando o conteúdo em uma versão (Priority: P2)

**Goal**: expor os impedimentos de publicação, publicar congelando a versão 1 e ler a versão
publicada de volta.

**Independent Test**: montar um rascunho completo, publicar, conferir que a versão 1 nasceu com as
perguntas na ordem e que a pesquisa ficou agendada ou ativa conforme a janela; e tentar publicar um
rascunho sem perguntas, conferindo que a recusa aponta o motivo.

**Depende de**: US2 e US3 — a publicação valida perguntas e disparo.

### Domínio

- [ ] T085 [P] [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/publication/PublicationImpedimentsTest.java` cobrindo o catálogo fechado de data-model.md: `survey.no_questions`, `question.statement_missing`, `question.options_missing` (com a `questionKey` da pergunta), `trigger.missing` e `trigger.window_invalid`, e que a lista devolvida é **sempre completa**, nunca a primeira falha (D-10)
- [ ] T086 [US4] Criar o `record PublicationImpediment(String code, String field, Optional<QuestionKey> questionKey)` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/publication/PublicationImpediment.java` e implementar `publicationImpediments()` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersion.java`
- [ ] T087 [P] [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersionPublishTest.java`: `publish` grava `publishedAt`, muda `status` para `PUBLISHED`, atribui `comparabilityGroup = 1` na versão 1 e recusa toda escrita depois disso; publicar com impedimentos é recusado
- [ ] T088 [US4] Implementar `publish(Instant, Optional<SurveyVersion>, Optional<ChangeKind>, String)` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersion.java` — o único ponto que grava `publishedAt`, `comparabilityGroup` e muda `status`
- [ ] T089 [P] [US4] Criar a entidade `SurveyStateTransition` e o enum `TransitionReason` (`PUBLICATION`, `MANUAL_PAUSE`, `MANUAL_RESUME`, `MANUAL_END`, `WINDOW_OPENED`, `WINDOW_CLOSED`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/`, com `actor` como `Optional<String>` sempre vazio (D-06)
- [ ] T090 [US4] Criar a porta `SurveyStateTransitionRepository` (`record`, `findBySurveyId`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/repositories/SurveyStateTransitionRepository.java` e o fake `InMemorySurveyStateTransitionRepository` em `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemorySurveyStateTransitionRepository.java`
- [ ] T091 [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyStateDerivationTest.java` cobrindo a tabela completa de D-05 — inclusive publicar no instante exato da abertura dando `ACTIVE` (início inclusivo) e pausada com janela fechada continuando `PAUSED` — e completar `Survey.stateAt(...)` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/Survey.java` com as linhas que dependem da janela

### Casos de uso

- [ ] T092 [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/CheckSurveyPublicationUseCaseTest.java`: devolve a lista de impedimentos sem publicar e sem escrever nada; rascunho completo devolve lista vazia; `survey.not_found` fora do escopo
- [ ] T093 [US4] Implementar `CheckSurveyPublicationUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/CheckSurveyPublicationUseCase.java` chamando **o mesmo** `publicationImpediments()` que a publicação usa (D-10)
- [ ] T094 [US4] Criar os erros `SurveyNotPublishable` (`BUSINESS_RULE`, `survey.not_publishable`, carregando a lista de impedimentos) e `SurveyAlreadyPublished` (`CONFLICT`, `survey.already_published`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/errors/` e escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/PublishSurveyUseCaseTest.java`: publica a versão 1, marca a pesquisa como publicada, grava a transição `PUBLICATION`, deixa `scheduled` com janela futura e `active` com janela aberta; recusa com todos os impedimentos de uma vez; `survey.already_published` ao publicar de novo sem rascunho aberto
- [ ] T095 [US4] Implementar `PublishSurveyUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/PublishSurveyUseCase.java` com `@Transactional` (versão + pesquisa + transição, D-18)
- [ ] T096 [US4] Criar o erro `SurveyVersionNotFound` (`NOT_FOUND`, `survey_version.not_found`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/errors/SurveyVersionNotFound.java` e escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/GetSurveyVersionUseCaseTest.java`: devolve as perguntas com enunciado, tipo, ordem, obrigatoriedade, opções e chave estável; `survey_version.not_found` para número inexistente; `survey.not_found` fora do escopo
- [ ] T097 [US4] Implementar `GetSurveyVersionUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/GetSurveyVersionUseCase.java` — leitura pura

### Persistência

- [ ] T098 [US4] Criar `SurveyStateTransitionJpaEntity`, o mapper `SurveyStateTransitionJpaMapper` e o par `SurveyStateTransitionJpaRepository`/`SurveyStateTransitionRepositoryJpa` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/database/jpa/`, ordenando por `occurred_at`

### Borda HTTP

- [ ] T099 [US4] Estender `src/main/java/com/renanloureiroo/pitaco/infra/http/error/ApiExceptionHandler.java` para carregar a extensão `impediments` no corpo RFC 9457 de `SurveyNotPublishable`, no mesmo molde que `ApiValidationErrorResponse` já usa para `errors` — sem nenhum `try/catch` de tradução no controller
- [ ] T100 [P] [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/dtos/PublishSurveyRequestDTOTest.java` cobrindo `changeKind` opcional na versão 1 e `changeSummary` com limite de 500
- [ ] T101 [US4] Criar `PublishSurveyRequestDTO`, `PublicationImpedimentDTO`, `PublicationImpedimentsResponseDTO` e `SurveyVersionResponseDTO` (com `number`, `status`, `questions`, `trigger`, `rules`, `changeKind`, `changeSummary`, `comparabilityGroup` e `publishedAt`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/dtos/`
- [ ] T102 [US4] Criar `PublicationImpedimentsPresenter`, `SurveyVersionPresenter` e `GetSurveyVersionPresenter` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/presenters/`; acrescentar as operações `checkPublication` (200/404), `publishSurvey` (201/400/404/409/422) e `getSurveyVersion` (200/404) às interfaces `*Swagger` e os handlers `GET /publication-impediments`, `POST /publication` e `GET /versions/{number}` aos controllers correspondentes em `.../infra/http/controllers/`; registrar os três `@Bean` em `.../infra/config/UseCasesConfiguration.java`

### E2E

- [ ] T103 [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/PublishSurveyE2ETest.java` com `@E2E` e `DatabaseCleaner`: publicação com janela aberta deixando `active` e com janela futura deixando `scheduled`, ambas conferidas no banco; `201` com `Location`; recusa sem pergunta, com escolha sem opção e sem disparo, cada uma apontando o campo, e uma recusa com **vários** impedimentos de uma vez (SC-002); `409` ao publicar de novo; `422 survey.published_cannot_be_discarded` ao descartar publicada; e as seis escritas de conteúdo recusadas com `survey.content_frozen` (SC-003)
- [ ] T104 [US4] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/GetSurveyVersionE2ETest.java` e `CheckPublicationE2ETest.java`: publicar com 20 perguntas e ler as 20 de volta na ordem, idênticas ao montado (SC-005); a lista de impedimentos consultada sem publicar é **a mesma** que a publicação usa; `404` para número inexistente e fora do escopo; nenhuma escrita em nenhum caminho de leitura

**Checkpoint**: a autoria produz o artefato que a entrega vai consumir.

---

## Phase 7: User Story 5 - Controlar o que está no ar (Priority: P3)

**Goal**: pausar, retomar e encerrar pesquisa publicada, com o histórico de transições legível.

**Independent Test**: publicar uma pesquisa, pausá-la, conferir o estado e o registro da transição,
retomá-la, encerrá-la, e conferir que retomar depois de encerrada é recusado.

**Depende de**: US4 — não há o que pausar antes de existir publicação.

- [ ] T105 [P] [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyTransitionsTest.java`: `pause` recusa em `DRAFT` e em `ENDED`; `resume` recusa em `ENDED` e em `PUBLISHED`; `end` recusa em `DRAFT` e em `ENDED`; retomar leva a `active` ou a `scheduled` conforme a janela
- [ ] T106 [US5] Criar os erros `SurveyNotPublished` (`BUSINESS_RULE`, `survey.not_published`) e `SurveyTransitionNotAllowed` (`BUSINESS_RULE`, `survey.transition_not_allowed`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/errors/` e implementar `pause`, `resume` e `end` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/Survey.java`
- [ ] T107 [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/{PauseSurveyUseCaseTest,ResumeSurveyUseCaseTest,EndSurveyUseCaseTest}.java`: cada comando muda o ciclo de vida e grava a transição com motivo `MANUAL_PAUSE`/`MANUAL_RESUME`/`MANUAL_END` e o instante; cada recusa com o `code` próprio; `survey.not_found` fora do escopo
- [ ] T108 [US5] Implementar `PauseSurveyUseCase`, `ResumeSurveyUseCase` e `EndSurveyUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/`, os três com `@Transactional` (pesquisa + transição, D-18)
- [ ] T109 [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/ListStateTransitionsUseCaseTest.java`: devolve as transições comandadas **mais** as derivadas da janela (`WINDOW_OPENED` no início, `WINDOW_CLOSED` no fim), com o instante do limite e não o da leitura, tudo ordenado por instante e **sem nenhuma escrita** (D-06)
- [ ] T110 [US5] Implementar `ListStateTransitionsUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/ListStateTransitionsUseCase.java` — leitura pura, sem transação
- [ ] T111 [US5] Criar `SurveyStateTransitionResponseDTO` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/dtos/SurveyStateTransitionResponseDTO.java` e `ListStateTransitionsPresenter` em `.../infra/http/presenters/ListStateTransitionsPresenter.java`, convertendo `SurveyState` e `TransitionReason` para minúsculas
- [ ] T112 [US5] Criar `SurveyLifecycleControllerSwagger` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/SurveyLifecycleControllerSwagger.java` com um `@ApiResponse` por status das quatro operações (200/404/422 em `pauseSurvey`, `resumeSurvey` e `endSurvey`; 200/404 em `listStateTransitions`), conforme o contrato
- [ ] T113 [US5] Criar `SurveyLifecycleController` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/SurveyLifecycleController.java` com `POST /pause`, `POST /resume`, `POST /end` (sub-recursos de comando, D-15) e `GET /transitions`, reusando `CreateSurveyPresenter` nas três primeiras
- [ ] T114 [US5] Registrar os `@Bean` de `PauseSurveyUseCase`, `ResumeSurveyUseCase`, `EndSurveyUseCase` e `ListStateTransitionsUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/config/UseCasesConfiguration.java`
- [ ] T115 [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/SurveyLifecycleE2ETest.java` com `@E2E` e `DatabaseCleaner`: pausar, retomar e encerrar conferidos no banco; `422 survey.transition_not_allowed` ao retomar ou pausar encerrada; `422 survey.not_published` ao pausar ou encerrar rascunho; pesquisa agendada aparecendo como ativa depois que a janela abre e ativa aparecendo como encerrada depois que a janela fecha; pausada com janela fechada continuando pausada; `404` fora do escopo; estado inalterado nos caminhos de falha
- [ ] T116 [US5] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/StateTransitionsE2ETest.java`: o histórico traz a publicação, as transições manuais e as derivadas da janela, com origem, destino, motivo e instante, ordenadas — e a tabela `survey_state_transitions` continua sem nenhuma linha de janela depois da leitura (FR-029, D-06)

**Checkpoint**: existe alavanca de emergência — parar uma pesquisa não exige tocar no banco.

---

## Phase 8: User Story 6 - Criar uma nova versão de pesquisa publicada (Priority: P3)

**Goal**: abrir rascunho de versão a partir da publicada, publicar com classificação de mudança
verificada e expor os grupos de comparabilidade.

**Independent Test**: publicar uma pesquisa, abrir uma nova versão, corrigir o enunciado de uma
pergunta declarando a mudança como cosmética, publicar, e conferir que existem duas versões, que a
versão 1 continua com o texto original, que a pergunta corrigida manteve a chave estável e que a v2
está marcada como cosmética; depois abrir uma v3 removendo uma pergunta e conferir que a declaração
de cosmética é recusada.

**Depende de**: US4 — só se versiona o que já foi publicado.

### Domínio

- [ ] T117 [P] [US6] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersionCopyTest.java`: `copyAsDraft(N+1)` copia perguntas, disparo e regras, preservando cada `QuestionKey` e gerando `QuestionId` novos (D-07); a versão de origem continua intacta; pergunta acrescentada no rascunho nasce com chave própria
- [ ] T118 [US6] Implementar `copyAsDraft(int newNumber)` e `sameContentAs(SurveyVersion other)` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersion.java`
- [ ] T119 [P] [US6] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/publication/ChangeClassificationTest.java` cobrindo as quatro diferenças que derrubam a declaração cosmética, casando perguntas **por `QuestionKey`** (D-11): pergunta acrescentada, removida, retipada e com conjunto de opções alterado (ignorando a ordem das opções); e as três que **não** derrubam: enunciado reescrito, obrigatoriedade trocada e reordenação; `SEMANTIC` sempre aceita (FR-035)
- [ ] T120 [US6] Criar o enum `ChangeKind` (`COSMETIC`, `SEMANTIC`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/ChangeKind.java` e `ChangeClassification` em `.../domain/publication/ChangeClassification.java`, devolvendo a chave da pergunta e qual das quatro diferenças motivou a recusa (FR-034)
- [ ] T121 [P] [US6] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/ComparabilityGroupTest.java`: v1 recebe grupo 1; versão cosmética herda o grupo da anterior; versão semântica recebe o grupo da anterior mais um; a sequência v1 → v2 cosmética → v3 semântica → v4 cosmética produz `{v1,v2}` e `{v3,v4}` (D-12)
- [ ] T122 [US6] Estender `publish(...)` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/domain/entities/SurveyVersion.java` para exigir a declaração a partir da versão 2, verificá-la com `ChangeClassification` e atribuir `comparabilityGroup` pela regra de incremento

### Casos de uso

- [ ] T123 [US6] Criar o erro `SurveyDraftVersionAlreadyOpen` (`CONFLICT`, `survey_version.draft_already_open`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/errors/SurveyDraftVersionAlreadyOpen.java` e escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/OpenSurveyVersionUseCaseTest.java`: abre a versão N+1 em `DRAFT` com cópia integral; recusa quando já há rascunho aberto; `survey.transition_not_allowed` em pesquisa encerrada; `survey.not_published` em pesquisa nunca publicada
- [ ] T124 [US6] Implementar `OpenSurveyVersionUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/OpenSurveyVersionUseCase.java` com `@Transactional` (versão + cópia das perguntas e regras, D-18)
- [ ] T125 [US6] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/DiscardSurveyVersionUseCaseTest.java`: descartar o rascunho de versão devolve a pesquisa à versão publicada intacta; `survey_version.not_found` quando não há rascunho
- [ ] T126 [US6] Implementar `DiscardSurveyVersionUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/DiscardSurveyVersionUseCase.java` com `@Transactional`
- [ ] T127 [US6] Criar os erros `CosmeticDeclarationRefused` (`BUSINESS_RULE`, `survey_version.cosmetic_refused`, carregando chave e diferença) e `SurveyVersionHasNoChanges` (`BUSINESS_RULE`, `survey_version.no_changes`) em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/errors/` e estender `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/PublishSurveyUseCaseTest.java` com a publicação de versão a partir da segunda: cosmética aceita em correção de enunciado, recusada nas quatro diferenças, semântica sempre aceita, e `survey_version.no_changes` para rascunho idêntico à publicada (FR-036)
- [ ] T128 [US6] Estender `PublishSurveyUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/PublishSurveyUseCase.java` para receber `changeKind` e `changeSummary`, comparar com a versão publicada anterior e preservá-la intacta (FR-032)
- [ ] T129 [US6] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/{ListSurveyVersionsUseCaseTest,GetVersionComparabilityUseCaseTest}.java`: a listagem paginada devolve número, `publishedAt`, classificação e resumo, da mais recente para a mais antiga; a comparabilidade agrupa as versões pela coluna gravada, com a transitividade de D-12
- [ ] T130 [US6] Implementar `ListSurveyVersionsUseCase` e `GetVersionComparabilityUseCase` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/application/usecases/` — leituras puras

### Borda HTTP

- [ ] T131 [US6] Estender `src/main/java/com/renanloureiroo/pitaco/infra/http/error/ApiExceptionHandler.java` para carregar a extensão `differences` no corpo RFC 9457 de `CosmeticDeclarationRefused`, no mesmo molde de `impediments` (T099)
- [ ] T132 [US6] Criar `VersionComparabilityResponseDTO` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/dtos/VersionComparabilityResponseDTO.java` e os presenters `ListSurveyVersionsPresenter` (devolvendo `PageResponseDTO<SurveyVersionResponseDTO>`) e `VersionComparabilityPresenter` em `.../infra/http/presenters/`
- [ ] T133 [US6] Criar `SurveyVersionControllerSwagger` e `SurveyVersionController` em `src/main/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/` com `GET /versions` (200/400/404), `POST /versions` (201/404/409/422), `DELETE /versions/draft` (204/404) e `GET /versions/comparability` (200/404), e registrar os quatro `@Bean` em `.../infra/config/UseCasesConfiguration.java`

### E2E

- [ ] T134 [US6] Escrever `src/test/java/com/renanloureiroo/pitaco/modules/survey/infra/http/controllers/SurveyVersionE2ETest.java` com `@E2E` e `DatabaseCleaner`: abrir a v2 com as mesmas `key` e `id` novos; publicar cosmética e conferir que a v1 continua com o texto original e que o `comparabilityGroup` é o mesmo; as **quatro** recusas de SC-007, uma por diferença, com `422 survey_version.cosmetic_refused` e a diferença nomeada; a mesma mudança aceita como semântica, com o grupo incrementado; `409 survey_version.draft_already_open` ao abrir dois rascunhos; `422 survey_version.no_changes` para rascunho idêntico; descarte do rascunho devolvendo a pesquisa à versão publicada; uma pergunta atravessando três versões reconhecível pela `key` (SC-008); travessia de páginas da listagem de versões sem repetir nem omitir; `404` fora do escopo

**Checkpoint**: todas as seis histórias funcionam.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T135 [P] Atualizar `AGENTS.md` e `README.md` com a existência do segundo módulo (`modules/survey`) e com a promoção de `ApplicationId` para `core/identity` — é mudança de decisão arquitetural, e o portão da constituição a exige
- [ ] T136 [P] Conferir o OpenAPI gerado em `/api/v3/api-docs` contra `specs/003-survey-authoring/contracts/surveys.openapi.yaml`: os 23 caminhos, os parâmetros, os esquemas e **cada** código de status anunciado
- [ ] T137 Varrer `modules/survey/domain` e `modules/survey/application` procurando `import org.springframework`, `jakarta.persistence`, `jakarta.validation` e `io.swagger` — nenhum pode existir (Princípio I); conferir também que nenhum caso de uso tem `@Service` e que só os adaptadores JPA importam `org.springframework.data.domain`
- [ ] T138 Executar a validação manual de `specs/003-survey-authoring/quickstart.md`, das etapas 1 a 8 mais a seção de isolamento entre aplicações, com `./mvnw spring-boot:run`
- [ ] T139 Rodar `./mvnw verify` e confirmar a suíte verde, sem teste ignorado — portão obrigatório antes do PR

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependência
- **Foundational (Phase 2)**: depende do Setup — **bloqueia todas as histórias**
- **US1 (Phase 3)** e **US2 (Phase 4)**: dependem da Phase 2; US2 depende de US1 apenas por `SurveyVersion` (T019)
- **US3 (Phase 5)**: depende de US1 (T019); independente de US2
- **US4 (Phase 6)**: depende de US2 e US3 — a publicação valida perguntas e disparo
- **US5 (Phase 7)**: depende de US4 — não há o que pausar antes da publicação
- **US6 (Phase 8)**: depende de US4 — só se versiona o que já foi publicado
- **Polish (Phase 9)**: depende das histórias desejadas estarem completas

### User Story Dependencies

- **US1 (P1)**: começa após T012. Nenhuma dependência de outra história.
- **US2 (P1)**: começa após T019 (a entidade `SurveyVersion`). Independente de US3.
- **US3 (P2)**: começa após T019. Independente de US2 — toca partes distintas de `SurveyVersion`.
- **US4 (P2)**: começa após T068 e T084.
- **US5 (P3)**: começa após T104.
- **US6 (P3)**: começa após T104. Independente de US5.

### Dentro de cada história

- Teste antes da implementação que ele cobre, e deve falhar antes
- Value objects e entidade antes da porta; porta e fake antes do caso de uso
- Caso de uso antes da persistência e da borda
- Presenter e interface `*Swagger` antes do handler do controller
- E2E por último, depois do `@Bean` cabeado

### Parallel Opportunities

- T005, T007, T008, T011 e T012 são paralelos entre si na Phase 2
- Em US1: T013, T016, T018 e T022 são paralelos (arquivos de teste distintos); T036 é paralelo a T033–T035
- Em US2: T047, T049, T051 e T055 são paralelos entre si; T064 é paralelo a T062–T063
- US2 e US3 podem ser tocadas por duas pessoas ao mesmo tempo depois de T019 — o único arquivo compartilhado é `SurveyVersion.java` (T054 e T074), que exige coordenação
- US5 e US6 podem ser tocadas em paralelo depois de T104; o arquivo compartilhado é `UseCasesConfiguration.java` (T114 e T133)

---

## Parallel Example: US2

```bash
# Depois de T046, estas quatro frentes de teste andam juntas:
Task: "T047 testes de QuestionStatement, QuestionOption, ScaleRange e QuestionKey"
Task: "T049 QuestionTypeTest — a tabela de exigências dos seis tipos"
Task: "T051 QuestionTest — invariante versus completude (D-08)"
Task: "T055 QuestionFactory com atalho para cada tipo"
```

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Phase 1: Setup — baseline verde e esqueleto do módulo
2. Phase 2: Foundational — `ApplicationId` em `core`, identificadores, migration, `ApplicationScope`
3. Phase 3: US1 — o rascunho existe, é listável, renomeável e descartável
4. Phase 4: US2 — as perguntas dos seis tipos
5. **PARE e VALIDE**: quickstart etapas 1 a 3 mais a seção de isolamento
6. Entregável: um rascunho montável ponta a ponta

### Incremental Delivery

1. Setup + Foundational → base pronta
2. US1 + US2 → validar → entregar (MVP)
3. US3 → disparo e regras → validar → entregar
4. US4 → publicação e versão 1 congelada → validar → entregar
5. US5 → pausar, retomar, encerrar, histórico → validar → entregar
6. US6 → versões e classificação de mudança → validar → entregar
7. Polish → documentação, conferência do OpenAPI, quickstart completo e `./mvnw verify`

### Parallel Team Strategy

Depois da Phase 2 e de T019:

- Pessoa A: US1 → US4 → US5
- Pessoa B: US2 → US3 → US6

`SurveyVersion.java` e `UseCasesConfiguration.java` são os dois arquivos que exigem coordenação.

---

## Notes

- `[P]` = arquivo diferente, sem dependência pendente
- Nenhum caminho de leitura escreve — inclusive a derivação de estado (D-05) e a das transições de janela (D-06)
- Fora do escopo da aplicação dona é sempre `404`, nunca `403` (SC-004, D-16)
- Nenhum teste afirma sobre mensagem de erro, exceto os de DTO e os E2E, onde a mensagem é o contrato
- Mock sobre porta do projeto é proibido: os fakes de `testsupport/` são o contrato
- Migration nova, nunca edição de migration aplicada; `ddl-auto` segue `validate`
- Português nas mensagens, `@Schema` e `@DisplayName`; inglês nos identificadores, campos JSON, `code` e valores de enum
- Commit por tarefa ou grupo lógico; `./mvnw verify` antes do PR
