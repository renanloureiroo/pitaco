---
description: "Task list — 006-collection-read-list"
---

# Tasks: Coleta — leitura das exibições, respostas e respondentes

**Input**: documentos de desenho em `/specs/006-collection-read-list/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/collection-read.md`, `contracts/module-ports.md`

**Tests**: incluídos e **obrigatórios**. O Princípio III da constituição (“Teste É Contrato”) e o `plan.md` exigem teste de caso de uso sobre fake antes do código e E2E por leitura; o `quickstart.md` lista o que precisa estar verde.

**Organization**: agrupadas por história de usuário, para que cada uma seja implementável e testável isoladamente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável (arquivo distinto, sem dependência pendente)
- **[Story]**: US1, US2, US3, US4
- Todo caminho é relativo à raiz do backend (`/Users/renanloureiro/www/pitaco/backend`)

## Path Conventions

Módulo único: `src/main/java/com/renanloureiroo/pitaco/modules/collect/` (abreviado abaixo como `…/collect/`), testes em `src/test/java/com/renanloureiroo/pitaco/`.

---

## Phase 1: Setup (Infraestrutura compartilhada)

**Purpose**: o único artefato de infraestrutura que a fatia cria — nenhuma dependência nova, nenhuma estrutura nova.

- [X] T001 Criar a migration de índices em `src/main/resources/db/migration/V20260909170000__index_collect_reads.sql` com o conteúdo da seção 7 de `data-model.md`: `drop index idx_survey_displays_survey` e os três `create index` (`idx_survey_displays_survey_listing`, `idx_survey_displays_respondent_listing`, `idx_respondents_application_listing`), mantendo `idx_survey_displays_history`
- [X] T002 Confirmar que a migration aplica e o schema valida rodando `./mvnw test -Dtest=PitacoApplicationTests` (Docker de pé) — `ddl-auto` segue `validate`

---

## Phase 2: Foundational (Pré-requisitos bloqueantes)

**Purpose**: outputs, erros, portas de travessia e o serviço de escopo que as quatro histórias compartilham.

**⚠️ CRITICAL**: nenhuma história pode começar antes desta fase terminar.

### Outputs (`…/collect/application/outputs/`)

- [X] T003 [P] Criar `AnswerReadStatus` (`ANSWERED`, `SKIPPED`, `EXPIRED`) em `src/main/java/com/renanloureiroo/pitaco/modules/collect/application/outputs/AnswerReadStatus.java` — situação de saída, não de domínio (D-07)
- [X] T004 [P] Criar o `record DisplaySummaryOutput` (`id`, `versionId`, `versionNumber`, `comparabilityGroup`, `outcome`, `sdkVersion: Optional<String>`, `openedAt`, `closedAt: Optional<Instant>`) em `…/collect/application/outputs/DisplaySummaryOutput.java`
- [X] T005 [P] Criar o `record RespondentOutput` (`id`, `identityKind`, `identityValue`, `firstSeenAt`, `lastSeenAt`) em `…/collect/application/outputs/RespondentOutput.java`
- [X] T006 [P] Criar o `record AnswerReadOutput` (`questionKey`, `status`, `text: Optional<String>`, `number: Optional<Integer>`, `options: List<String>`) em `…/collect/application/outputs/AnswerReadOutput.java` (depende de T003)
- [X] T007 [P] Criar o `record RespondentDisplaySummaryOutput` (`DisplaySummaryOutput` + `surveyId`) em `…/collect/application/outputs/RespondentDisplaySummaryOutput.java` (depende de T004)
- [X] T008 [P] Criar o `record DisplayDetailOutput` (`summary`, `respondentId`, `surveyId`, `attributes: Map<String,String>`, `answers: List<AnswerReadOutput>`) em `…/collect/application/outputs/DisplayDetailOutput.java` (depende de T004, T006)

### Erros (`…/collect/application/errors/`)

- [X] T009 [P] Criar `SurveyNotFoundInApplication` estendendo `ApplicationException` com `ErrorType.NOT_FOUND` e `code` `survey.not_found` em constante `private static final`, em `…/collect/application/errors/SurveyNotFoundInApplication.java` (D-05)
- [X] T010 [P] Criar `RespondentNotFound` estendendo `ApplicationException` com `ErrorType.NOT_FOUND` e `code` `respondent.not_found` em `…/collect/application/errors/RespondentNotFound.java`

### Portas de travessia e adaptadores

- [X] T011 [P] Criar a porta `SurveyScopeGateway` com `boolean existsInApplication(SurveyId, ApplicationId)` em `…/collect/application/gateways/SurveyScopeGateway.java` (D-04)
- [X] T012 Acrescentar `Optional<Integer> effectiveOpenTextRetentionDaysOf(ApplicationId)` à porta `…/collect/application/gateways/ApplicationScopeGateway.java`, sem tocar em `stateOf` (D-08)
- [X] T013 [P] Criar `SurveyScopeJpaRepository` sobre `SurveyJpaEntity` em `…/collect/infra/gateways/SurveyScopeJpaRepository.java`, no molde de `PublishedSurveyJpaRepository`
- [X] T014 Criar o adaptador `SurveyScopeGatewaySurvey` em `…/collect/infra/gateways/SurveyScopeGatewaySurvey.java` (depende de T011, T013)
- [X] T015 Implementar `effectiveOpenTextRetentionDaysOf` em `…/collect/infra/gateways/ApplicationScopeGatewayApp.java` delegando a `Application.effectiveOpenTextRetentionDays()` (depende de T012)

### Porta de respondente (usada pelo escopo)

- [X] T016 Acrescentar `Optional<Respondent> findById(RespondentId, ApplicationId)` à porta `…/collect/application/repositories/RespondentRepository.java`
- [X] T017 Implementar `findById` em `…/collect/infra/database/jpa/repositories/RespondentRepositoryJpa.java` e a consulta correspondente em `…/collect/infra/database/jpa/repositories/RespondentJpaRepository.java` (depende de T016)

### Serviço de escopo

- [X] T018 Criar `CollectScope` (`final class`, construtor privado) com `existingApplicationIdOf`, `existingSurveyIdOf` e `existingRespondentOf` em `…/collect/application/services/CollectScope.java`, lançando os erros nomeados (depende de T009, T010, T011, T016)

### Fakes de `testsupport`

- [X] T019 [P] Criar `InMemorySurveyScopeGateway` (existência pelo par `surveyId`+`applicationId`) em `src/test/java/com/renanloureiroo/pitaco/testsupport/gateways/InMemorySurveyScopeGateway.java` (depende de T011)
- [X] T020 [P] Acrescentar `effectiveOpenTextRetentionDaysOf`, incluindo o caso ausente, a `src/test/java/com/renanloureiroo/pitaco/testsupport/gateways/InMemoryCollectApplicationScopeGateway.java` (depende de T012)
- [X] T021 [P] Acrescentar `findById(RespondentId, ApplicationId)` com escopo por aplicação a `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryRespondentRepository.java` (depende de T016)

**Checkpoint**: outputs, erros, portas, adaptadores, escopo e fakes prontos — as histórias podem começar.

---

## Phase 3: User Story 1 — Ver as exibições de uma pesquisa (Priority: P1) 🎯 MVP

**Goal**: `GET /applications/{applicationId}/surveys/{surveyId}/displays` devolvendo as exibições da pesquisa, paginadas, filtráveis por versão, desfecho e período.

**Independent Test**: publicar uma pesquisa, abrir três exibições (respondida, dispensada, aberta), listar e conferir desfecho, versão exibida, instantes e o total da paginação.

### Testes de US1 (escrever antes) ⚠️

- [X] T022 [P] [US1] Escrever `ListSurveyDisplaysUseCaseTest` em `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/ListSurveyDisplaysUseCaseTest.java` cobrindo: caminho feliz com ordenação `openedAt` desc + desempate `id` desc, pesquisa sem exibição (vazio, total 0), cada filtro isolado e combinados, período inclusive nos extremos, página além do fim, escopo por aplicação e recusa `SurveyNotFoundInApplication`
- [X] T023 [P] [US1] Escrever `ListDisplaysQueryDTOTest` em `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/dtos/ListDisplaysQueryDTOTest.java` cobrindo `page >= 0`, `size` em `1..100`, `outcome` desconhecido e período invertido (`@AssertTrue`, D-12)

### Porta e fake de US1

- [X] T024 [US1] Acrescentar `Page<DisplaySummary> findPage(ListDisplaysQuery)` e os `record` `DisplaySummary` e `ListDisplaysQuery implements PageQuery` a `…/collect/application/repositories/SurveyDisplayRepository.java`, conforme a seção 1 de `contracts/module-ports.md`
- [X] T025 [US1] Implementar `findPage` em `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemorySurveyDisplayRepository.java` reproduzindo ordenação, desempate, recorte por `page*size`, total do conjunto filtrado, período inclusivo e escopo por aplicação (depende de T024)

### Implementação de US1

- [X] T026 [US1] Criar `ListSurveyDisplaysUseCase` (POJO, sem anotação, `Input`/`Output` aninhados no molde de `ListSurveysUseCase`) em `…/collect/application/usecases/ListSurveyDisplaysUseCase.java`, usando `CollectScope.existingSurveyIdOf` e logando sucesso via `@Slf4j` com identificador e total (depende de T018, T024)
- [X] T027 [P] [US1] Criar `DisplaySummaryProjection` em `…/collect/infra/database/jpa/projections/DisplaySummaryProjection.java`
- [X] T028 [US1] Acrescentar a consulta paginada com junção única a `survey_versions` (número da versão, D-03) e a contagem a `…/collect/infra/database/jpa/repositories/SurveyDisplayJpaRepository.java` (depende de T027)
- [X] T029 [US1] Implementar `findPage` em `…/collect/infra/database/jpa/repositories/SurveyDisplayRepositoryJpa.java`, traduzindo projeção → `DisplaySummary` (depende de T024, T028)
- [X] T030 [P] [US1] Criar o enum de borda `DisplayOutcomeFilter` (`STARTED`, `COMPLETED`, `DISMISSED`) em `…/collect/infra/http/dtos/DisplayOutcomeFilter.java` (D-11)
- [X] T031 [US1] Criar `ListDisplaysQueryDTO` com as constraints de paginação, filtros e o `@AssertTrue` do período em `…/collect/infra/http/dtos/ListDisplaysQueryDTO.java`, mensagens em português (depende de T030)
- [X] T032 [P] [US1] Criar `DisplaySummaryResponseDTO` com `@Schema` em português em `…/collect/infra/http/dtos/DisplaySummaryResponseDTO.java`
- [X] T033 [US1] Criar `DisplaySummaryPresenter` (`final class`, construtor privado, `public static present`) em `…/collect/infra/http/presenters/DisplaySummaryPresenter.java` (depende de T032)
- [X] T034 [US1] Criar `SurveyDisplaySwagger` em `…/collect/infra/http/controllers/SurveyDisplaySwagger.java` declarando 200, 400, 403 e 404 com o schema de erro
- [X] T035 [US1] Criar `SurveyDisplayController` (`@RequestMapping("/applications/{applicationId}/surveys/{surveyId}/displays")`, sem `/api`, sem `try/catch`) em `…/collect/infra/http/controllers/SurveyDisplayController.java`, devolvendo `PageResponseDTO<DisplaySummaryResponseDTO>` (depende de T026, T031, T033, T034)
- [X] T036 [US1] Registrar o `@Bean` de `ListSurveyDisplaysUseCase` em `…/collect/infra/config/UseCasesConfiguration.java` (depende de T026)

### E2E de US1

- [X] T037 [US1] Escrever `ListSurveyDisplaysE2ETest` em `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/ListSurveyDisplaysE2ETest.java` com `@E2E` + `DatabaseCleaner` (sem `@Transactional`): caminho feliz com os três desfechos e o estado lido de volta do banco, cada filtro, período inclusive, página além do fim, 400 de paginação/`outcome`/período invertido, 403 `api_key.forbidden_surface` com `X-Pitaco-Key`, 404 `survey.not_found` para pesquisa inexistente e de outra aplicação, e que nenhum caminho alterou o estado (depende de T035, T036)

**Checkpoint**: US1 funcional e testável sozinha — o painel tem a primeira tela de coleta.

---

## Phase 4: User Story 2 — Ver o que foi respondido em uma exibição (Priority: P1)

**Goal**: `GET /applications/{applicationId}/displays/{displayId}` devolvendo a exibição com atributos, versão do SDK, respondente e as respostas na ordem da versão exibida.

**Independent Test**: abrir uma exibição, responder parte das perguntas e pular uma, consultar e conferir situação por pergunta, ausência de valor na pulada e a ordem espelhando a versão.

### Testes de US2 (escrever antes) ⚠️

- [X] T038 [P] [US2] Escrever `GetSurveyDisplayUseCaseTest` em `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/GetSurveyDisplayUseCaseTest.java` cobrindo: as três situações `ANSWERED`/`SKIPPED`/`EXPIRED` na mesma exibição (texto expirado suprimido, escolha e numérica intactas), prazo de retenção ausente = sem expiração, ordem das respostas contra a ordem da versão exibida, o ramo da chave que não existe na versão (vai para o fim, desempate estável pela chave), exibição aberta (`answers` vazio, sem fechamento), exibição dispensada, escopo por aplicação e recusa `DisplayNotFound`

### Porta e fake de US2

- [X] T039 [US2] Ajustar a consulta de `…/collect/infra/database/jpa/repositories/SurveyAnswerJpaRepository.java` para trazer `options` em `join fetch`, sem mudar a assinatura de `findByDisplay` (seção 3 de `contracts/module-ports.md`)
- [X] T040 [P] [US2] Conferir/ajustar `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryAnswerRepository.java` para `findByDisplay` devolver as respostas com suas opções

### Implementação de US2

- [X] T041 [US2] Criar `GetSurveyDisplayUseCase` em `…/collect/application/usecases/GetSurveyDisplayUseCase.java`: busca a exibição por `findById(DisplayId, ApplicationId)`, as respostas, o conteúdo da versão por `PublishedSurveyCatalog.contentOf` (D-06) e a retenção efetiva (D-07), aplicando a regra de expiração da seção 4 de `data-model.md`; loga sucesso sem `identityValue`, sem conteúdo de resposta e sem valor de atributo (depende de T003–T008, T012, T039)
- [X] T042 [P] [US2] Criar `AnswerReadResponseDTO` (`questionKey`, `status`, `text`, `number`, `options`) com `@Schema` em português em `…/collect/infra/http/dtos/AnswerReadResponseDTO.java`
- [X] T043 [US2] Criar `DisplayDetailResponseDTO` em `…/collect/infra/http/dtos/DisplayDetailResponseDTO.java`, corpo plano conforme `contracts/collection-read.md` (depende de T042)
- [X] T044 [US2] Criar `DisplayDetailPresenter` em `…/collect/infra/http/presenters/DisplayDetailPresenter.java` (depende de T043)
- [X] T045 [US2] Criar `DisplaySwagger` em `…/collect/infra/http/controllers/DisplaySwagger.java` declarando 200, 403 e 404
- [X] T046 [US2] Criar `DisplayController` (`@RequestMapping("/applications/{applicationId}/displays")`, rota plana, D-09) em `…/collect/infra/http/controllers/DisplayController.java` (depende de T041, T044, T045)
- [X] T047 [US2] Registrar o `@Bean` de `GetSurveyDisplayUseCase` em `…/collect/infra/config/UseCasesConfiguration.java` (depende de T041)

### E2E de US2

- [X] T048 [US2] Escrever `GetSurveyDisplayE2ETest` em `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/GetSurveyDisplayE2ETest.java`: caminho feliz com atributos, `sdkVersion`, respostas na ordem da versão e o estado lido de volta do banco; `sdkVersion` ausente; exibição aberta e dispensada; o cenário de expiração da seção “O cenário de expiração” do `quickstart.md` (texto `EXPIRED` sem `text` ao lado de escolha e numérica `ANSWERED`); `SKIPPED` distinguível de `EXPIRED`; 403 com `X-Pitaco-Key`; 404 `display.not_found` inexistente e de outra aplicação; nenhum caminho alterou o estado (depende de T046, T047)

**Checkpoint**: US1 e US2 funcionam independentemente — do identificador da pesquisa ao conteúdo de uma resposta em duas consultas (SC-001).

---

## Phase 5: User Story 3 — Ver os respondentes de uma aplicação (Priority: P2)

**Goal**: `GET /applications/{applicationId}/respondents` paginada, ordenada por último contato desc.

**Independent Test**: gerar exibições para dois respondentes identificados de formas diferentes, listar e conferir tipo de identificação e os instantes de primeiro e último contato.

### Testes de US3 (escrever antes) ⚠️

- [X] T049 [P] [US3] Escrever `ListRespondentsUseCaseTest` em `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/ListRespondentsUseCaseTest.java`: os dois tipos de identificação, ordenação `lastSeenAt` desc + desempate `id` desc, escopo por aplicação, aplicação sem respondente (vazio, total 0), página além do fim e recusa `application.not_found`
- [X] T050 [P] [US3] Escrever `ListRespondentsQueryDTOTest` em `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/dtos/ListRespondentsQueryDTOTest.java` cobrindo os limites de `page` e `size`

### Porta e fake de US3

- [X] T051 [US3] Acrescentar `Page<Respondent> findPage(ListRespondentsQuery)` e o `record ListRespondentsQuery implements PageQuery` a `…/collect/application/repositories/RespondentRepository.java` (seção 2 de `contracts/module-ports.md`)
- [X] T052 [US3] Implementar `findPage` em `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemoryRespondentRepository.java` com ordenação, desempate, recorte, total e escopo (depende de T051)

### Implementação de US3

- [X] T053 [US3] Criar `ListRespondentsUseCase` em `…/collect/application/usecases/ListRespondentsUseCase.java` usando `CollectScope.existingApplicationIdOf`, logando sucesso **sem** `identityValue` (FR-030) (depende de T018, T051)
- [X] T054 [US3] Acrescentar a consulta paginada e a contagem por aplicação a `…/collect/infra/database/jpa/repositories/RespondentJpaRepository.java`
- [X] T055 [US3] Implementar `findPage` em `…/collect/infra/database/jpa/repositories/RespondentRepositoryJpa.java` (depende de T051, T054)
- [X] T056 [P] [US3] Criar `ListRespondentsQueryDTO` com as constraints de paginação em `…/collect/infra/http/dtos/ListRespondentsQueryDTO.java`
- [X] T057 [P] [US3] Criar `RespondentResponseDTO` com `@Schema` em português em `…/collect/infra/http/dtos/RespondentResponseDTO.java`
- [X] T058 [US3] Criar `RespondentPresenter` em `…/collect/infra/http/presenters/RespondentPresenter.java` (depende de T057)
- [X] T059 [US3] Criar `RespondentSwagger` em `…/collect/infra/http/controllers/RespondentSwagger.java` declarando 200, 400, 403 e 404 da listagem de respondentes
- [X] T060 [US3] Criar `RespondentController` (`@RequestMapping("/applications/{applicationId}/respondents")`) em `…/collect/infra/http/controllers/RespondentController.java` com o `GET` da listagem (depende de T053, T056, T058, T059)
- [X] T061 [US3] Registrar o `@Bean` de `ListRespondentsUseCase` em `…/collect/infra/config/UseCasesConfiguration.java` (depende de T053)

### E2E de US3

- [X] T062 [US3] Escrever `ListRespondentsE2ETest` em `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/ListRespondentsE2ETest.java`: caminho feliz com os dois `identityKind` e o estado lido do banco, isolamento entre aplicações, aplicação sem respondente, página além do fim, 400 de paginação, 403 com `X-Pitaco-Key`, 404 `application.not_found`, e nenhuma alteração de estado (depende de T060, T061)

**Checkpoint**: as três primeiras histórias funcionam independentemente.

---

## Phase 6: User Story 4 — Ver o histórico de exibições de um respondente (Priority: P3)

**Goal**: `GET /applications/{applicationId}/respondents/{respondentId}/displays` — a mesma listagem de exibições pelo eixo do respondente, com `surveyId`.

**Independent Test**: gerar exibições de duas pesquisas para o mesmo respondente, listar e conferir que as duas aparecem apontando pesquisa e versão.

### Testes de US4 (escrever antes) ⚠️

- [X] T063 [P] [US4] Escrever `ListRespondentDisplaysUseCaseTest` em `src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/ListRespondentDisplaysUseCaseTest.java`: exibições de duas pesquisas com `surveyId` correto, respondente sem exibição (vazio, total 0), filtro por desfecho e por período, ordenação e desempate, escopo por aplicação e recusa `RespondentNotFound`

### Porta e fake de US4

- [X] T064 [US4] Acrescentar `Page<RespondentDisplaySummary> findPageByRespondent(ListRespondentDisplaysQuery)` e os `record` `RespondentDisplaySummary` e `ListRespondentDisplaysQuery` a `…/collect/application/repositories/SurveyDisplayRepository.java` (depende de T024)
- [X] T065 [US4] Implementar `findPageByRespondent` em `src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemorySurveyDisplayRepository.java` com a mesma semântica de ordenação, recorte, total e escopo (depende de T064)

### Implementação de US4

- [X] T066 [US4] Criar `ListRespondentDisplaysUseCase` em `…/collect/application/usecases/ListRespondentDisplaysUseCase.java` usando `CollectScope.existingRespondentOf` (depende de T018, T064)
- [X] T067 [P] [US4] Criar `RespondentDisplayProjection` em `…/collect/infra/database/jpa/projections/RespondentDisplayProjection.java`
- [X] T068 [US4] Acrescentar a consulta paginada por respondente e a contagem a `…/collect/infra/database/jpa/repositories/SurveyDisplayJpaRepository.java` (depende de T067)
- [X] T069 [US4] Implementar `findPageByRespondent` em `…/collect/infra/database/jpa/repositories/SurveyDisplayRepositoryJpa.java` (depende de T064, T068)
- [X] T070 [P] [US4] Criar `RespondentDisplayResponseDTO` (corpo de `DisplaySummaryResponseDTO` mais `surveyId`) em `…/collect/infra/http/dtos/RespondentDisplayResponseDTO.java`
- [X] T071 [US4] Criar `RespondentDisplayPresenter` em `…/collect/infra/http/presenters/RespondentDisplayPresenter.java` (depende de T070)
- [X] T072 [US4] Acrescentar a `RespondentSwagger` o `GET` de exibições do respondente com 200, 400, 403 e 404 `respondent.not_found` em `…/collect/infra/http/controllers/RespondentSwagger.java`
- [X] T073 [US4] Acrescentar o endpoint `/{respondentId}/displays` a `…/collect/infra/http/controllers/RespondentController.java`, reaproveitando `ListDisplaysQueryDTO` sem `versionId` (FR-025) (depende de T066, T071, T072)
- [X] T074 [US4] Registrar o `@Bean` de `ListRespondentDisplaysUseCase` em `…/collect/infra/config/UseCasesConfiguration.java` (depende de T066)

### E2E de US4

- [X] T075 [US4] Escrever `ListRespondentDisplaysE2ETest` em `src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/ListRespondentDisplaysE2ETest.java`: caminho feliz com duas pesquisas e o estado lido do banco, respondente sem exibição, filtros de desfecho e período, 400 de validação, 403 com `X-Pitaco-Key`, 404 `respondent.not_found` inexistente e de outra aplicação, e nenhuma alteração de estado (depende de T073, T074)

**Checkpoint**: as quatro leituras funcionam independentemente.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T076 [P] Conferir que nenhum log da fatia registra `identityValue`, conteúdo de resposta, valor de atributo ou segredo de chave (FR-029, FR-030, SC-006) — varredura em `…/collect/application/usecases/` e `…/collect/infra/http/controllers/`
- [X] T077 [P] Conferir que `domain` não ganhou importação de framework e que nenhum caso de uso tem `@Service` ou `@Transactional` (D-13)
- [X] T078 [P] Conferir no Swagger gerado (`/api/swagger-ui.html`) que as quatro rotas declaram todos os status, 400/403/404 inclusive
- [X] T079 Rodar o roteiro manual de ponta a ponta e a verificação de índices do `quickstart.md` (itens 3 e 4)
- [X] T080 Rodar `./mvnw verify` — verde, sem teste ignorado; é o portão antes do PR

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sem dependência
- **Foundational (Fase 2)**: depende da Fase 1 — **bloqueia todas as histórias**
- **US1 (Fase 3)**: depois da Fase 2
- **US2 (Fase 4)**: depois da Fase 2 — independente de US1
- **US3 (Fase 5)**: depois da Fase 2 — independente de US1 e US2
- **US4 (Fase 6)**: depois da Fase 2; T064 depende de T024 (US1) porque acrescenta membro ao mesmo arquivo de porta, e T073 reaproveita o `ListDisplaysQueryDTO` de T031
- **Polish (Fase 7)**: depois das histórias desejadas

### Dependências entre histórias

- **US1 (P1)**: independente
- **US2 (P1)**: independente de US1 — rota plana, sem passar pela listagem
- **US3 (P2)**: independente
- **US4 (P3)**: compartilha arquivos com US1 (porta `SurveyDisplayRepository`, fake, `SurveyDisplayJpaRepository`, `ListDisplaysQueryDTO`) e o controller de US3 — fazer depois das duas

### Dentro de cada história

Teste primeiro (e falhando) → porta e fake → caso de uso → adaptador JPA → DTO e presenter → Swagger e controller → `@Bean` → E2E.

### Parallel Opportunities

- Fase 2: T003–T005, T009–T011, T013 em paralelo; depois T006–T008; depois T014, T015, T019–T021
- US1: T022 e T023 em paralelo; T027, T030 e T032 em paralelo
- US3: T049 e T050 em paralelo; T056 e T057 em paralelo
- Com equipe: uma pessoa em US1, outra em US2, outra em US3 assim que a Fase 2 fecha

---

## Parallel Example: Fase 2

```bash
Task: "Criar AnswerReadStatus em …/collect/application/outputs/AnswerReadStatus.java"
Task: "Criar DisplaySummaryOutput em …/collect/application/outputs/DisplaySummaryOutput.java"
Task: "Criar RespondentOutput em …/collect/application/outputs/RespondentOutput.java"
Task: "Criar SurveyNotFoundInApplication em …/collect/application/errors/SurveyNotFoundInApplication.java"
Task: "Criar RespondentNotFound em …/collect/application/errors/RespondentNotFound.java"
Task: "Criar SurveyScopeGateway em …/collect/application/gateways/SurveyScopeGateway.java"
```

---

## Implementation Strategy

### MVP (US1 + US2)

1. Fase 1 (migration) → Fase 2 (fundação)
2. Fase 3 (US1) → **parar e validar**: a listagem sozinha já prova que a coleta funciona em produção
3. Fase 4 (US2) → o par que fecha SC-001: da pesquisa ao conteúdo de uma resposta em duas consultas

### Entrega incremental

1. Fundação pronta
2. US1 → testar isoladamente → demo (MVP)
3. US2 → testar isoladamente → demo
4. US3 → testar isoladamente → demo
5. US4 → testar isoladamente → demo

---

## Notes

- Toda leitura é estritamente de leitura: nenhuma `@Transactional`, nenhuma escrita, nem no caminho de recusa (FR-027, D-13)
- Recurso de outra aplicação recusa como inexistente — 404, nunca 403 (FR-034)
- Nenhum teste afirma sobre texto de mensagem, exceto teste de DTO e E2E
- Nenhum mock sobre porta do projeto: fakes de `testsupport`
- Mensagens, `@Schema` e `@DisplayName` em português; identificadores, campos JSON e `code` em inglês
- Commit após cada tarefa ou grupo lógico
