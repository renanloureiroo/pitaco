---

description: "Task list — 004-response-collection"
---

# Tasks: Respondente, coleta e respostas — a superfície pública do SDK

**Input**: Documentos de projeto em `/specs/004-response-collection/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: incluídos e **obrigatórios**. A constituição (Princípio III, "Teste é contrato") e a
ordem de trabalho do `AGENTS.md` exigem que o teste venha antes do código que ele cobre. Em cada
par, o teste é a tarefa anterior à implementação e deve falhar antes dela.

**Organization**: agrupadas por user story, na ordem de prioridade da spec.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos distintos, sem dependência pendente)
- **[Story]**: US1…US5, conforme a spec
- Caminhos relativos a `backend/`

## Path Conventions

Monolito modular. Produção em `src/main/java/com/renanloureiroo/pitaco/`, testes em
`src/test/java/com/renanloureiroo/pitaco/`, migrations em `src/main/resources/db/migration/`.
Abaixo, `…` abrevia `src/main/java/com/renanloureiroo/pitaco` e `…test` abrevia
`src/test/java/com/renanloureiroo/pitaco`.

---

## Phase 1: Setup

**Purpose**: linha de base verde e o esqueleto do módulo novo

- [X] T001 Rodar `./mvnw verify` e registrar a suíte verde como linha de base antes de qualquer alteração (pré-requisito do quickstart.md)
- [X] T002 Criar a árvore de pacotes de `…/modules/collect/` — `domain/entities`, `domain/valueobjects`, `domain/eligibility`, `domain/collection`, `application/errors`, `application/gateways`, `application/outputs`, `application/repositories`, `application/usecases`, `infra/config`, `infra/database/jpa/{entities,mappers,repositories}`, `infra/gateways`, `infra/http/{controllers,dtos,presenters}`
- [X] T003 [P] Criar `…/modules/collect/infra/config/CollectProperties.java` como `@ConfigurationProperties("pitaco.collect")` com `sessionTimeout` (`Duration`) e `maxAttempts` (`int`), conforme D-11
- [X] T004 [P] Declarar `pitaco.collect.session-timeout: 30m` e `pitaco.collect.max-attempts: 3` em `src/main/resources/application.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: vocabulário compartilhado, esquema, agregados, portas, persistência e a porta de
entrada autenticada. Nada da superfície pública existe antes disto.

**⚠️ CRITICAL**: nenhuma user story pode começar antes do fim desta fase.

### 2.1 Vocabulário promovido para `core` (D-16)

- [X] T005 Criar o pacote `…/core/catalog/` e mover para ele `EventName.java`, `QuestionKey.java`, `QuestionOption.java`, `ScaleRange.java` e `SamplingRate.java` de `…/modules/survey/domain/valueobjects/`, ajustando `package` e todos os `import` em `modules/survey`
- [X] T006 Mover `QuestionType.java` e `RuleOperation.java` de `…/modules/survey/domain/entities/` para `…/core/catalog/`, ajustando `package` e `import`
- [X] T007 Mover `SurveyId.java` e `SurveyVersionId.java` de `…/modules/survey/domain/entities/` para `…/core/identity/`, ajustando `package` e `import`
- [X] T008 [P] Escrever `…test/core/catalog/SegmentationCriterionTest.java` cobrindo as invariantes do critério — atributo obrigatório e ≤ 80 caracteres, valor obrigatório para `EQUALS`/`NOT_EQUALS` e ausente para `PRESENT`/`ABSENT`
- [X] T009 Criar `…/core/catalog/SegmentationCriterion.java` como `record (String attribute, RuleOperation operation, Optional<String> value)` com validação no compact constructor
- [X] T010 Fazer `…/modules/survey/domain/valueobjects/SegmentationRule.java` expor `SegmentationCriterion criterion()`, mantendo o `SegmentationRuleId` no módulo, e atualizar `…test/modules/survey/domain/valueobjects/SegmentationRuleTest.java`
- [X] T011 Rodar `./mvnw verify` e confirmar que a suíte da autoria continua verde após a movimentação — nenhum comportamento mudou, só `import`

### 2.2 Esquema

- [X] T012 Criar a migration `src/main/resources/db/migration/V20260908210000__create_collect.sql` com as tabelas `respondents`, `survey_sessions`, `survey_session_attributes`, `survey_answers` e `survey_answer_options`, os índices `idx_survey_sessions_history`, `idx_survey_sessions_survey`, `idx_survey_versions_published_event` (parcial em `status = 'PUBLISHED'`), `idx_surveys_application_lifecycle`, as restrições `uq_respondents_identity` e `uq_survey_answers_question`, e a coluna `api_keys.last_used_at` — exatamente o SQL da seção 7 de data-model.md

### 2.3 Domínio de `collect` — value objects

- [X] T013 [P] Escrever `…test/modules/collect/domain/valueobjects/RespondentIdentityTest.java`: a referência do app prevalece sobre o dispositivo, só dispositivo vale na falta dela, sem nenhum dos dois lança `DomainException` `respondent.identity_required`, valor vazio/só espaços é rejeitado, limite de 200 caracteres
- [X] T014 [P] Escrever `…test/modules/collect/domain/valueobjects/AttributeSnapshotTest.java`: até 50 pares, nome ≤ 80 e valor ≤ 200, nomes normalizados com `strip()`, ordem de inserção preservada, atributo que nenhuma regra usa é aceito
- [X] T015 [P] Escrever `…test/modules/collect/domain/valueobjects/AnswerValueTest.java`: `TextValue` não vazio e ≤ 2000 caracteres, `NumericValue` inteiro, `ChoiceValue` não vazia e sem repetição
- [X] T016 [P] Criar `…/modules/collect/domain/valueobjects/RespondentIdentity.java` (record com `kind`/`value` e a fábrica `of(Optional<String> appReference, Optional<String> deviceId)`) e `RespondentIdentityKind.java` (`APP_REFERENCE`, `DEVICE`)
- [X] T017 [P] Criar `…/modules/collect/domain/valueobjects/AttributeSnapshot.java` como record sobre `Map<String,String>` com ordem de inserção preservada
- [X] T018 [P] Criar `…/modules/collect/domain/valueobjects/AnswerValue.java` (sealed interface com `TextValue`, `NumericValue`, `ChoiceValue`) e `AnswerText.java`

### 2.4 Domínio de `collect` — agregados

- [X] T019 [P] Escrever `…test/modules/collect/domain/entities/RespondentTest.java`: `create` grava `firstSeenAt` e `lastSeenAt` iguais, `seenAt(now)` só avança `lastSeenAt`, `restore` não recalcula nada
- [X] T020 [P] Escrever `…test/modules/collect/domain/entities/SurveySessionTest.java`: `complete`/`dismiss` a partir de `STARTED` gravam `closedAt`; sessão fechada recusa qualquer novo desfecho; `outcomeAt(now, timeout)` deriva `ABANDONED` de `STARTED` vencida e nunca de `COMPLETED`/`DISMISSED`; `SessionId.of` recusa o que não é UUID
- [X] T021 [P] Escrever `…test/modules/collect/domain/entities/AnswerTest.java`: `ANSWERED` exige valor, `SKIPPED` exige ausência de valor, `questionKey` obrigatória
- [X] T022 [P] Criar `…/modules/collect/domain/entities/Respondent.java` e `RespondentId.java`
- [X] T023 [P] Criar `…/modules/collect/domain/entities/SurveySession.java`, `SessionId.java` (só `of(String)`, validando UUID) e `SessionOutcome.java` (`STARTED`, `COMPLETED`, `DISMISSED`, `ABANDONED` — este nunca persistido)
- [X] T024 [P] Criar `…/modules/collect/domain/entities/Answer.java`, `AnswerId.java` e `AnswerStatus.java`

### 2.5 Portas e fakes

- [X] T025 [P] Criar `…/modules/collect/application/repositories/RespondentRepository.java`, `SurveySessionRepository.java` (com o record aninhado `SessionHistoryEntry`) e `AnswerRepository.java`, conforme a seção 2 de contracts/module-ports.md
- [X] T026 [P] Criar `…/modules/collect/application/gateways/PublishedSurveyCatalog.java` com `candidatesFor`, `contentOf`, `publishedVersionOf` e os records `SurveyCandidate`, `PublishedVersion`, `DeliverableSurvey`, `DeliverableQuestion`
- [X] T027 [P] Criar `…/modules/collect/application/gateways/ApplicationScopeGateway.java` **e** `ApplicationScopeState.java` — porta e enum próprios de `collect`, deliberadamente não compartilhados com `survey`, que declara os seus (dois módulos nunca se conhecem pelo domínio)
- [X] T028 [P] Criar `…test/testsupport/repositories/InMemoryRespondentRepository.java`
- [X] T029 [P] Criar `…test/testsupport/repositories/InMemorySurveySessionRepository.java`, incluindo `historyOf` em uma varredura só
- [X] T030 [P] Criar `…test/testsupport/repositories/InMemoryAnswerRepository.java`
- [X] T031 [P] Criar `…test/testsupport/gateways/InMemoryPublishedSurveyCatalog.java` com as três operações da porta, e `InMemoryCollectApplicationScopeGateway.java` para a porta de `collect` — o fake existente é tipado na porta de `survey` e não serve aqui
- [X] T032 [P] Criar `…test/testsupport/factories/RespondentFactory.java`, `SurveySessionFactory.java` e `AnswerFactory.java` no molde fluente de `ApplicationFactory`

### 2.6 Persistência de `collect`

- [X] T033 [P] Criar as entidades JPA em `…/modules/collect/infra/database/jpa/entities/`: `RespondentJpaEntity`, `SurveySessionJpaEntity`, `SurveySessionAttributeJpaEntity`, `SurveyAnswerJpaEntity`, `SurveyAnswerOptionJpaEntity`
- [X] T034 Criar os mappers explícitos domínio↔JPA em `…/modules/collect/infra/database/jpa/mappers/` (`RespondentMapper`, `SurveySessionMapper`, `AnswerMapper`), chamando sempre `restore(...)` na volta
- [X] T035 Criar as interfaces Spring Data e os adaptadores em `…/modules/collect/infra/database/jpa/repositories/`: `RespondentRepositoryJpa`, `SurveySessionRepositoryJpa` (com a consulta de `historyOf` em uma só ida ao banco), `AnswerRepositoryJpa` (com `saveAll`)
- [X] T036 Escrever `…test/modules/collect/infra/database/jpa/repositories/CollectRepositoriesTest.java` cobrindo ida e volta dos três agregados, a unicidade `(application_id, kind, value)`, a unicidade `(session_id, question_key)` e o recorte por `applicationId` em `findById`
- [X] T037 [P] Criar `…/modules/collect/infra/gateways/ApplicationScopeGatewayApp.java`, no molde do adaptador homônimo de `modules/survey`
- [X] T038 Criar `…/modules/collect/infra/gateways/PublishedSurveyCatalogSurvey.java` com as três consultas da seção 8 de data-model.md — candidatos com `join fetch` das regras de segmentação, conteúdo com `join fetch` de perguntas e opções ordenadas por posição, e a checagem de versão publicada por aplicação — sem nenhuma consulta em laço
- [X] T039 Escrever `…test/modules/collect/infra/gateways/PublishedSurveyCatalogSurveyTest.java` verificando que rascunho nunca aparece, que a janela é avaliada nos limites exatos e que versão de outra aplicação não é alcançada
- [X] T040 Criar `…/modules/collect/infra/config/UseCasesConfiguration.java` vazio, para receber os `@Bean` de cada story

### 2.7 Autenticação da superfície pública

- [X] T041 [P] Criar `…/modules/app/application/errors/ApiKeyMissing.java` (`UNAUTHORIZED`, `api_key.missing`), `ApiKeyInvalid.java` (`UNAUTHORIZED`, `api_key.invalid`) e `ApiKeyForbiddenSurface.java` (`FORBIDDEN`, `api_key.forbidden_surface`) — `ErrorType` já tem `UNAUTHORIZED` e `FORBIDDEN` mapeados para 401 e 403 em `ErrorTypeHttpStatus`, nenhum atalho novo na borda
- [X] T042 Acrescentar `findActiveBySecretHash(String)` e `touch(ApiKeyId, Instant)` a `…/modules/app/application/repositories/ApiKeyRepository.java`, implementá-los no adaptador JPA com `update` condicional, mapear a coluna `last_used_at` na entidade JPA e refletir os dois métodos em `…test/testsupport/repositories/InMemoryApiKeyRepository.java`
- [X] T043 Escrever `…test/modules/app/application/usecases/AuthenticateApiKeyUseCaseTest.java`: chave válida devolve `applicationId` e o estado da aplicação; chave revogada e chave desconhecida lançam o mesmo `ApiKeyInvalid`; `touch` só é chamado quando o último uso é mais antigo que um minuto (D-17)
- [X] T044 Criar `…/modules/app/application/usecases/AuthenticateApiKeyUseCase.java` como POJO sem anotação de framework
- [X] T045 Registrar `AuthenticateApiKeyUseCase` como `@Bean` em `…/modules/app/infra/config/UseCasesConfiguration.java`
- [X] T046 Criar `…/infra/http/security/AuthenticatedApplication.java` e `ApiKeyAuthenticationInterceptor.java`, lendo `X-Pitaco-Key` e guardando o resultado como **atributo da requisição** — nunca `ThreadLocal`, por causa das virtual threads
- [X] T047 [P] Criar `…/infra/http/security/AuthenticatedApplicationArgumentResolver.java`
- [X] T048 [P] Criar `…/infra/http/security/AdminSurfaceInterceptor.java`, recusando com `api_key.forbidden_surface` (403) toda requisição a `/applications/**` que apresente o header
- [X] T049 Criar `…/infra/http/security/SecurityWebMvcConfiguration.java` registrando os dois interceptors (`/collect/**` e as rotas administrativas) e o argument resolver
- [X] T050 Escrever `…test/infra/http/security/PublicSurfaceSecurityE2ETest.java` com `@E2E` e `DatabaseCleaner`: header ausente → 401 `api_key.missing`; chave desconhecida e chave revogada → 401 `api_key.invalid` com corpo idêntico; chave pública em rota administrativa → 403 `api_key.forbidden_surface`; e `api_keys.last_used_at` gravado após o primeiro uso

**Checkpoint**: esquema no ar, agregados e portas prontos, porta de entrada autenticada. As user
stories podem começar.

---

## Phase 3: User Story 1 — Descobrir se há pesquisa para este respondente agora (Priority: P1) 🎯 MVP

**Goal**: `POST /collect/eligibility` devolve no máximo uma pesquisa, com a versão publicada
inteira em uma resposta só, ou `{"survey": null}` sem escrever nada.

**Independent Test**: publicar uma pesquisa pela autoria e consultar com a chave, o evento e os
atributos que satisfazem as regras → perguntas na ordem definida; repetir com evento diferente,
fora da janela, com a pesquisa pausada e com atributo que viola a regra → `{"survey": null}` nos
quatro; repetir com chave revogada → recusa.

**Nota**: a camada 5 (histórico do respondente) entra na US4. Aqui as camadas aplicadas são
aplicação ativa, pesquisa no ar, janela, evento, segmentação e sorteio, nessa ordem.

### Testes primeiro

- [X] T051 [P] [US1] Escrever `…test/modules/collect/domain/eligibility/SamplingDecisionTest.java`: `rate = 0` recusa todos, `rate = 1` aceita todos, a mesma dupla pesquisa–respondente dá sempre a mesma decisão em cinco chamadas (US1.8, SC-005), e republicar não muda a decisão porque a chave é pesquisa–respondente e não versão–respondente
- [X] T052 [P] [US1] Escrever `…test/modules/collect/domain/eligibility/SegmentationEvaluationTest.java`: conjunção de todos os critérios; `EQUALS` com atributo ausente ou vazio não casa; `NOT_EQUALS` com atributo ausente não casa (falha fechado, FR-016); `PRESENT` e `ABSENT`; duas regras com uma satisfeita e outra violada → não casa
- [X] T053 [US1] Escrever `…test/modules/collect/application/usecases/FindEligibleSurveyUseCaseTest.java` sobre os fakes: a ordem exata das camadas da FR-011; aplicação inativa devolve vazio sem erro; rascunho nunca é entregue; janela avaliada nos limites; duas elegíveis devolvem exatamente uma e sempre a mesma (desempate por `published_at` mais antigo, depois `surveyId` lexicográfico — D-06); e **nenhuma escrita** em nenhum dos caminhos (D-10, SC-003)
- [X] T054 [P] [US1] Escrever `…test/modules/collect/infra/http/dtos/EligibilityRequestDTOTest.java` cobrindo as constraints: `event` obrigatório e ≤ 80, `reference` ≤ 200, `deviceId` UUID, no máximo 50 atributos com nome ≤ 80 e valor ≤ 200 — cada mensagem espelhando a do value object correspondente

### Implementação

- [X] T055 [P] [US1] Criar `…/modules/collect/domain/eligibility/SamplingDecision.java` com o hash SHA-256 de `surveyId + ":" + identity.value()` descrito em D-05, sem estado guardado
- [X] T056 [P] [US1] Criar `…/modules/collect/domain/eligibility/SegmentationEvaluation.java` avaliando a conjunção dos critérios sobre o `AttributeSnapshot`
- [X] T057 [P] [US1] Criar `…/modules/collect/application/outputs/DeliverableSurveyOutput.java` e `DeliverableQuestionOutput.java`
- [X] T058 [US1] Criar `…/modules/collect/application/usecases/FindEligibleSurveyUseCase.java` — POJO sem `@Transactional`, aplicando as camadas na ordem e chamando `contentOf` só para a pesquisa escolhida
- [X] T059 [P] [US1] Criar `…/modules/collect/infra/http/dtos/EligibilityRequestDTO.java`, `RespondentDTO.java` e `EligibilityResponseDTO.java`
- [X] T060 [P] [US1] Criar `…/modules/collect/infra/http/presenters/EligibilityPresenter.java` — `final class`, construtor privado, um `public static` `present`
- [X] T061 [US1] Criar `…/modules/collect/infra/http/controllers/CollectEligibilitySwagger.java` com um `@ApiResponse` para cada status possível (200, 400, 401) e o critério de desempate documentado
- [X] T062 [US1] Criar `…/modules/collect/infra/http/controllers/CollectEligibilityController.java` com `@RequestMapping("/collect")`, recebendo `AuthenticatedApplication` pelo resolver — o `applicationId` nunca vem do corpo nem da rota (FR-002)
- [X] T063 [US1] Registrar `FindEligibleSurveyUseCase` como `@Bean` em `…/modules/collect/infra/config/UseCasesConfiguration.java`
- [X] T064 [US1] Escrever `…test/modules/collect/infra/http/controllers/CollectEligibilityE2ETest.java` com `@E2E` e `DatabaseCleaner`: caminho feliz com as perguntas na ordem e o conteúdo integral; `{"survey": null}` para evento sem pesquisa, pesquisa pausada, fora da janela (antes e depois) e rascunho; contagem de linhas idêntica antes e depois em todos eles (SC-003); JSON malformado → 400; segmentação nas quatro operações e com o atributo ausente (SC-009b); **duas pesquisas ativas disputando o mesmo evento devolvem exatamente uma, e a consulta repetida devolve a mesma** (SC-002, US1.13); pesquisa de outra aplicação nunca alcançada (SC-012)

**Checkpoint**: a US1 funciona sozinha — uma pesquisa publicada chega a quem deveria e não chega
a quem não deveria.

---

## Phase 4: User Story 2 — Abrir a sessão e registrar as respostas (Priority: P1)

**Goal**: `POST /collect/sessions` abre a sessão de forma idempotente e
`POST /collect/sessions/{sessionId}/submission` grava respostas e desfecho num ato atômico.

**Independent Test**: consultar elegibilidade, abrir sessão, enviar um conjunto válido e ler de
volta do banco exatamente o que foi enviado, amarrado à versão publicada; reenviar o mesmo pacote
e confirmar uma resposta só; enviar um conjunto inválido e confirmar que nada foi gravado.

### Abertura da sessão

- [X] T065 [P] [US2] Escrever `…test/modules/collect/application/usecases/OpenSurveySessionUseCaseTest.java`: cria respondente e sessão; a segunda abertura com o mesmo `sessionId`, pesquisa e versão devolve a existente sem criar outra (FR-023); `sessionId` já usado para outra pesquisa ou versão lança `SessionIdentifierConflict`; versão em rascunho ou de outra aplicação lança `SurveyVersionNotDeliverable`; aplicação inativa lança `ApplicationIsInactive` (D-19); `lastSeenAt` do respondente é atualizado
- [X] T066 [P] [US2] Criar `…/modules/collect/application/errors/SurveyVersionNotDeliverable.java` (`NOT_FOUND`, `survey_version.not_found`), `SessionIdentifierConflict.java` (`CONFLICT`, `session.identifier_conflict`) e `ApplicationIsInactive.java` (`BUSINESS_RULE`, `application.inactive`) — classe própria de `collect`, no mesmo molde da homônima de `app` mas sem importá-la
- [X] T067 [P] [US2] Criar `…/modules/collect/application/outputs/SurveySessionOutput.java`
- [X] T068 [US2] Criar `…/modules/collect/application/usecases/OpenSurveySessionUseCase.java` com `@Transactional` de `core.transaction` no `execute` (D-18)
- [X] T069 [P] [US2] Escrever `…test/modules/collect/infra/http/dtos/OpenSessionRequestDTOTest.java`: `sessionId` UUID obrigatório, `surveyId`/`versionId` obrigatórios, respondente sem nenhuma identificação é recusado, `sdkVersion` ≤ 40
- [X] T070 [P] [US2] Criar `…/modules/collect/infra/http/dtos/OpenSessionRequestDTO.java` e `SurveySessionResponseDTO.java`
- [X] T071 [P] [US2] Criar `…/modules/collect/infra/http/presenters/SurveySessionPresenter.java`
- [X] T072 [US2] Criar `…/modules/collect/infra/http/controllers/CollectSessionSwagger.java` com `@ApiResponse` para 201, 200, 400, 401, 404, 409 e 422
- [X] T073 [US2] Criar `…/modules/collect/infra/http/controllers/CollectSessionController.java` respondendo 201 com header `Location: /api/collect/sessions/{sessionId}` na criação e 200 no reenvio
- [X] T074 [US2] Registrar `OpenSurveySessionUseCase` como `@Bean` em `…/modules/collect/infra/config/UseCasesConfiguration.java`

### Submissão

- [X] T075 [P] [US2] Escrever `…test/modules/collect/domain/collection/SubmissionValidationTest.java` cobrindo os dez códigos da seção 5 de data-model.md, e sobretudo que **todos** os problemas de um envio saem de uma vez, na ordem documentada (FR-035, SC-007)
- [X] T076 [US2] Criar `…/modules/collect/domain/collection/SubmissionProblem.java` e `SubmissionValidation.java`, no molde de `PublicationImpediment` da autoria
- [X] T077 [P] [US2] Escrever `…test/modules/collect/application/usecases/SubmitSurveySessionUseCaseTest.java`: conjunto válido grava tudo e conclui a sessão; envio inválido lança `SubmissionRejected` e **não grava nada** (SC-008); sessão inexistente ou de outra aplicação lança o mesmo `SessionNotFound`; reenvio idêntico não cria segunda resposta (FR-036); envio aceito mesmo com a pesquisa pausada, encerrada ou republicada depois da abertura (FR-038, SC-014); a validação usa a versão da sessão, não a publicada corrente
- [X] T078 [P] [US2] Criar `…/modules/collect/application/errors/SessionNotFound.java` (`NOT_FOUND`, `session.not_found`), `SessionAlreadyClosed.java` (`CONFLICT`, `session.already_closed`) e `SubmissionRejected.java` (`BUSINESS_RULE`, `submission.rejected`, carregando os problemas em `extensions().errors[]`)
- [X] T079 [US2] Criar `…/modules/collect/application/usecases/SubmitSurveySessionUseCase.java` com `@Transactional` de `core.transaction` no `execute`, validando o envio inteiro antes de qualquer gravação e usando `saveAll`
- [X] T080 [P] [US2] Escrever `…test/modules/collect/infra/http/dtos/SubmissionRequestDTOTest.java`: `outcome` obrigatório e restrito a `COMPLETED`/`DISMISSED`, lista de respostas não nula, `questionKey` obrigatória, `value` polimórfico aceito nas quatro formas
- [X] T081 [P] [US2] Criar `…/modules/collect/infra/http/dtos/SubmissionRequestDTO.java` e `AnswerDTO.java`, aceitando `value` como string, inteiro, array ou ausente
- [X] T082 [US2] Acrescentar a `CollectSessionSwagger` a operação de submissão, com `@ApiResponse` para 204, 400, 401, 404, 409 e 422, e o corpo de `submission.rejected` documentado
- [X] T083 [US2] Acrescentar a `CollectSessionController` o endpoint `POST /collect/sessions/{sessionId}/submission` respondendo 204
- [X] T084 [US2] Registrar `SubmitSurveySessionUseCase` como `@Bean` em `…/modules/collect/infra/config/UseCasesConfiguration.java`
- [X] T085 [US2] Escrever `…test/modules/collect/infra/http/controllers/CollectSessionE2ETest.java` com `@E2E` e `DatabaseCleaner`: abertura 201 com `Location` e a linha lida de volta do banco; reabertura 200 sem segunda linha; submissão 204 com as respostas lidas de volta amarradas à versão; reenvio do mesmo pacote sem segunda resposta; 400 de validação; JSON malformado; 404 de sessão de outra aplicação; 409 de `sessionId` conflitante; 422 `submission.rejected` com os três problemas listados de uma vez e **zero** linhas gravadas; abertura com versão em **rascunho** → 404 `survey_version.not_found`, fechando SC-004 na terceira operação pública

**Checkpoint**: o ciclo completo fecha — pesquisa entra pela autoria, sai pela entrega e volta
como resposta guardada (SC-001).

---

## Phase 5: User Story 3 — Registrar a dispensa com o mesmo peso de uma resposta (Priority: P1)

**Goal**: a sessão dispensada é registrada com instante, preserva o que já foi respondido, e o
desfecho avança numa direção só.

**Independent Test**: abrir sessão, responder a primeira pergunta, dispensar, e ler de volta a
sessão como dispensada com a primeira resposta preservada; tentar concluí-la depois e confirmar a
recusa.

- [X] T086 [P] [US3] Acrescentar a `…test/modules/collect/domain/collection/SubmissionValidationTest.java` os casos de `outcome = DISMISSED`: pergunta obrigatória ausente **não** é problema, e o parcial é aceito como está (FR-037)
- [X] T087 [P] [US3] Acrescentar a `…test/modules/collect/application/usecases/SubmitSurveySessionUseCaseTest.java` os casos da dispensa: `DISMISSED` fecha a sessão preservando as respostas já gravadas; sessão dispensada recusa resposta nova com `SessionAlreadyClosed`; sessão concluída recusa virar dispensada; dispensa sem nenhuma resposta ainda guarda a versão exibida
- [X] T088 [US3] Ajustar `…/modules/collect/domain/collection/SubmissionValidation.java` e `…/modules/collect/application/usecases/SubmitSurveySessionUseCase.java` para o desfecho `DISMISSED`, aplicando a regra da primeira gravação vencer (D-08)
- [X] T089 [US3] Escrever `…test/modules/collect/infra/http/controllers/CollectSessionDismissalE2ETest.java`: dispensar depois de responder a primeira pergunta → 204, sessão `DISMISSED` com `closed_at` e a primeira resposta ainda no banco (SC-011); nova submissão na sessão dispensada → 409; tentativa de concluir sessão dispensada → 409 e o desfecho permanece

**Checkpoint**: "viram e não responderam" passa a ser distinguível de "ninguém viu" (SC-010).

---

## Phase 6: User Story 4 — Não incomodar de novo quem já resolveu (Priority: P2)

**Goal**: acrescentar a camada 5 da elegibilidade — pesquisa resolvida no mesmo grupo de
comparabilidade não é entregue de novo; abandono não resolve mas conta tentativa; versão
semântica reabre, cosmética não.

**Independent Test**: responder uma pesquisa e consultar de novo sem receber nada; repetir com uma
sessão dispensada; abandonar e voltar a receber até o limite; publicar uma versão semântica e
voltar a receber, depois uma cosmética e não receber.

- [X] T090 [P] [US4] Escrever `…test/modules/collect/domain/eligibility/ResolvedHistoryTest.java`: `COMPLETED` e `DISMISSED` no mesmo grupo resolvem; `STARTED` dentro do prazo não resolve nem conta tentativa; `STARTED` além do prazo é `ABANDONED`, não resolve e conta tentativa; atingido `maxAttempts` a pesquisa deixa de ser entregue; grupo de comparabilidade diferente não resolve nada
- [X] T091 [US4] Criar `…/modules/collect/domain/eligibility/ResolvedHistory.java` com `of(List<SessionHistoryEntry>, Instant, Duration, int)`, `isResolved(surveyId, group)` e `exhaustedAttempts(surveyId, group)`
- [X] T092 [US4] Acrescentar a `…test/modules/collect/application/usecases/FindEligibleSurveyUseCaseTest.java` a camada 5: o histórico é consultado **uma vez** para todos os candidatos e **depois** de janela/evento e **antes** de segmentação e sorteio (FR-011); respondentes distintos não interferem entre si; o mesmo identificador em duas aplicações mantém históricos independentes
- [X] T093 [US4] Inserir a camada 5 em `…/modules/collect/application/usecases/FindEligibleSurveyUseCase.java`, usando `SurveySessionRepository.historyOf` e recebendo `Duration sessionTimeout` e `int maxAttempts` **como parâmetros de construtor** — o caso de uso continua POJO e nunca referencia `CollectProperties`, que é tipo de `infra` (Princípio I)
- [X] T094 [US4] Ajustar o `@Bean` de `FindEligibleSurveyUseCase` em `…/modules/collect/infra/config/UseCasesConfiguration.java` para ler `CollectProperties` e repassar `sessionTimeout` e `maxAttempts` ao construtor — o cabeamento é decisão da infraestrutura
- [X] T095 [US4] Escrever `…test/modules/collect/infra/http/controllers/CollectEligibilityHistoryE2ETest.java`: quem concluiu não recebe de novo; quem dispensou não recebe; quem abandonou recebe até o limite e para nele; versão semântica nova volta a entregar a quem havia concluído e a quem havia dispensado; versão cosmética não reabre nada (SC-009)

**Checkpoint**: a elegibilidade aplica as sete camadas na ordem da FR-011.

---

## Phase 7: User Story 5 — Reconhecer o respondente sem saber quem ele é (Priority: P2)

**Goal**: a referência do app prevalece sobre o dispositivo a partir do momento em que aparece,
aplicações diferentes são respondentes diferentes, e nada pessoalmente identificável é guardado
ou logado.

**Independent Test**: consultar com referência do app e depois com o mesmo dispositivo e outra
referência, verificando respondentes distintos; consultar duas vezes só com dispositivo e
verificar que é o mesmo respondente.

- [X] T096 [P] [US5] Acrescentar a `…test/modules/collect/application/usecases/OpenSurveySessionUseCaseTest.java`: duas aberturas só com dispositivo pertencem ao mesmo respondente; quem passa a informar a referência do app vira um respondente novo dali em diante, sem fusão de históricos (D-09); a mesma referência em duas aplicações são dois respondentes
- [X] T097 [US5] Ajustar `…/modules/collect/application/usecases/OpenSurveySessionUseCase.java` para resolver a identidade por `RespondentIdentity.of` e buscar por `(applicationId, identity)`, criando na ausência e apenas atualizando `lastSeenAt` na presença
- [X] T098 [P] [US5] Escrever `…test/modules/collect/infra/http/controllers/CollectRespondentIdentityE2ETest.java`: os quatro cenários da US5 verificados por leitura de `respondents` no banco, incluindo históricos independentes entre aplicações
- [X] T099 [US5] Escrever `…test/modules/collect/PrivacyLoggingTest.java` capturando a saída de log durante um ciclo completo e afirmando que nem `reference`, nem `deviceId`, nem nome ou valor de atributo, nem conteúdo de resposta aparecem (FR-008, FR-039, SC-013)

**Checkpoint**: todas as user stories funcionam de forma independente.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T100 [P] Revisar todo o código novo contra o Princípio V: nenhuma consulta dentro de laço, nenhum `findAll` sem filtro, `saveAll` na gravação das respostas, e o caminho vazio com exatamente uma consulta e zero escritas
- [X] T101 [P] Revisar os `@Slf4j` do módulo: erro logado uma vez só na borda, e nunca com valor de entrada do usuário — campo e motivo, jamais o valor
- [X] T102 [P] Conferir no Swagger (`/api/swagger-ui.html`) que cada endpoint público anuncia todos os status que pode devolver, com `@Schema` e mensagens em português
- [X] T103 [P] Atualizar `README.md` com as três rotas públicas, o header `X-Pitaco-Key` e os `code` de erro novos
- [X] T104 Executar os doze cenários de [quickstart.md](./quickstart.md) contra a aplicação rodando
- [X] T105 Rodar `./mvnw verify` como portão final antes do PR

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)**: sem dependências
- **Foundational (2)**: depende da 1 — **bloqueia todas as stories**. Dentro dela, 2.1 → 2.2 é ordem obrigatória (a migration referencia colunas que a movimentação não altera, mas T011 é o portão que prova que a autoria continua verde antes de qualquer coisa nova)
- **US1 (3)**: depende da 2
- **US2 (4)**: depende da 2; na prática é validada depois da US1, que produz a pesquisa a exibir
- **US3 (5)**: depende da 4 — a dispensa é um desfecho do mesmo endpoint de submissão
- **US4 (6)**: depende da 3 (a camada entra na elegibilidade) e da 4/5 (precisa de sessões resolvidas para haver histórico)
- **US5 (7)**: depende da 4 — completa e prova as regras de identidade que a abertura de sessão usa
- **Polish (8)**: depois de todas as stories desejadas

### Within Each User Story

- Teste antes da implementação, sempre — falhando primeiro
- Value object e entidade → porta e fake → caso de uso → DTO → presenter, Swagger, controller → E2E → `@Bean`
- **Desvio consciente**: a migration (T012) e os adaptadores JPA (T033–T036) vêm na Foundational, antes dos casos de uso, invertendo os passos 3 e 4 da ordem de trabalho da constituição. A migration é uma só para o módulo inteiro e precisa existir antes de as cinco stories poderem correr em paralelo; a alternativa — uma migration por story — colocaria a segunda alterando o que a primeira acabou de criar, e migration aplicada não se edita. Justificado em Complexity Tracking do plan.md

### Parallel Opportunities

- Fase 1: T003 e T004 juntas
- Fase 2: os testes de domínio T013–T015 e T019–T021 todos juntos; as implementações T016–T018 e T022–T024 juntas; os fakes T028–T032 juntos; T037 e T041 independentes do resto
- US1: T051, T052 e T054 juntos; depois T055, T056, T057 juntos; T059 e T060 juntos
- US2: T065, T069, T075, T077 e T080 são arquivos distintos; T066, T067, T078 juntos; T070, T071, T081 juntos
- US4 e US5 podem ser feitas por pessoas diferentes assim que a US2 e a US3 fecharem

---

## Parallel Example: Foundational, domínio de `collect`

```bash
# Os seis testes de domínio, escritos juntos (arquivos distintos, nenhuma implementação ainda):
Task: "RespondentIdentityTest em …test/modules/collect/domain/valueobjects/"
Task: "AttributeSnapshotTest em …test/modules/collect/domain/valueobjects/"
Task: "AnswerValueTest em …test/modules/collect/domain/valueobjects/"
Task: "RespondentTest em …test/modules/collect/domain/entities/"
Task: "SurveySessionTest em …test/modules/collect/domain/entities/"
Task: "AnswerTest em …test/modules/collect/domain/entities/"
```

---

## Implementation Strategy

### MVP (US1)

1. Fase 1 → Fase 2 (T001–T050)
2. Fase 3 (T051–T064)
3. **PARE E VALIDE**: cenários 2, 3 e 4 do quickstart — o caminho vazio não escreve, a segmentação
   falha fechado, o sorteio é estável
4. O MVP já prova, ponta a ponta, que uma pesquisa publicada chega a quem deveria

### Entrega incremental

1. Setup + Foundational → fundação pronta
2. + US1 → a entrega funciona (MVP)
3. + US2 → o ciclo fecha: a resposta volta e fica guardada (SC-001)
4. + US3 → a taxa de resposta passa a ser calculável (SC-010)
5. + US4 → para de incomodar quem já resolveu, e a repesquisa por versão semântica funciona
6. + US5 → a identidade fica completa e provada, com o teste de privacidade fechando SC-013

### Notas

- `[P]` = arquivos distintos, sem dependência pendente
- Nenhum caso de uso leva anotação de framework; todos entram por `@Bean` no `UseCasesConfiguration` do módulo
- Mock sobre porta do projeto é proibido — use os fakes de `testsupport/`
- E2E nunca leva `@Transactional`; usa `@E2E` + `DatabaseCleaner` no `@BeforeEach`
- Migration aplicada não se edita: correção é migration nova
- Commit a cada tarefa ou grupo lógico
