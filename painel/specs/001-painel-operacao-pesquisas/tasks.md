---
description: "Task list for feature implementation"
---

# Tasks: Painel de operação — aplicações, chaves e pesquisas ponta a ponta

**Input**: Design documents from `specs/001-painel-operacao-pesquisas/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/backend-api.md, contracts/ui-routes.md, quickstart.md

**Tests**: incluídos. O Princípio IV da constituição é não negociável (toda regra não óbvia vira teste Vitest) e o SC-006 exige cobertura E2E dos fluxos críticos. Server Component assíncrono **não** é testado em unidade ([R8](./research.md#r8)) — por isso cada rota tem casca fina e a lógica mora em módulo puro testável.

**Organization**: tarefas agrupadas por história de usuário, em ordem de prioridade (P1 → P3), para que cada história seja implementável e testável de forma independente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência pendente)
- **[Story]**: história de usuário a que a tarefa pertence (US1..US6)
- Todo caminho de arquivo é relativo à raiz do painel (`/Users/renanloureiro/www/pitaco/painel`)

## Path Conventions

Aplicação web única (o backend vive em `../backend`, fora deste repositório):

- Domínio: `src/features/<feature>/{api,schemas,components,__tests__}`, fronteira em `index.ts`
- Compartilhado: `src/shared/{api,components,lib,hooks}`
- Primitivos: `src/components/ui/` (shadcn CLI)
- Roteamento e composição apenas: `src/app/`
- E2E: `e2e/`, com o simulador de API em `e2e/stub-api/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dependências e primitivos que todo o resto pressupõe

- [X] T001 Adicionar `zod` como dependência de runtime em package.json (justificativa em plan.md § Complexity Tracking) e rodar `npm install`
- [X] T002 Instalar via CLI do shadcn em src/components/ui/ os primitivos `table`, `badge`, `alert-dialog`, `dialog`, `select`, `textarea`, `checkbox`, `skeleton`, `separator`, `breadcrumb`, `dropdown-menu`, `tooltip`, `sonner`, `empty`, `field`, `spinner`; registrar em research.md (R10) quais não existem no registro `radix-nova` e serão compostos a partir de primitivos existentes — nunca por segunda biblioteca
- [X] T003 [P] Criar .env.example na raiz com `PITACO_API_URL=http://localhost:8080/api` e documentar a variável em README.md

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: cliente HTTP, modelo de erro, componentes de estado e casca de roteamento — nada de história de usuário começa antes disto

**⚠️ CRITICAL**: nenhuma história pode ser implementada até esta fase terminar

### Cliente HTTP e fronteira de erro

- [X] T004 Criar src/shared/api/env.ts lendo `PITACO_API_URL` apenas no servidor, com falha rápida e mensagem explícita quando ausente (R7)
- [X] T005 Criar src/shared/api/errors.ts com o tipo `Result<T>` discriminado e o mapeamento `status → kind` (400 validation · 403 forbidden · 404 not_found · 409 conflict · demais unknown · rede/timeout unreachable), incluindo parse do corpo RFC 9457 (`code`, `detail`, `traceId`, `errors`) conforme data-model.md § ApiError
- [X] T006 Criar src/shared/api/client.ts com `request<T>()` sobre `fetch`, enviando apenas `Accept: application/json` e `Content-Type: application/json` nas escritas, sem cache e **sem nunca** enviar header de chave de aplicação (FR-007); depende de T004 e T005
- [X] T007 Criar src/shared/api/pagination.ts com o schema Zod de `PageResponse<T>` (genérico sobre o schema do item) e `parsePaginationParams(searchParams)` aplicando padrões `page=0`, `size=20`, `size` em 1..100 e caindo no padrão em valor inválido (R12)
- [X] T008 [P] Escrever src/shared/api/__tests__/errors.test.ts cobrindo cada `status → kind`, corpo `ApiValidationError` com `errors` por campo e corpo ausente ou malformado
- [X] T009 [P] Escrever src/shared/api/__tests__/client.test.ts com `vi.stubGlobal("fetch", ...)` — rede real é proibida — cobrindo sucesso, 400 com `errors`, 404, 409, 403 `api_key.forbidden_surface`, falha de rede virando `unreachable` e a ausência do header de chave na requisição
- [X] T010 [P] Escrever src/shared/api/__tests__/pagination.test.ts cobrindo padrões, limites de `size` e valores inválidos caindo no padrão sem quebrar
- [X] T011 Criar src/shared/api/index.ts exportando cliente, tipos de erro, `PageResponse` e utilitários de paginação como fronteira pública do compartilhado

### Apresentação compartilhada

- [X] T012 [P] Criar src/shared/lib/format.ts com formatação de data ISO UTC para pt-BR e o helper de valor opcional que renderiza "não configurado" — nunca `0` (FR-011)
- [X] T013 [P] Escrever src/shared/lib/__tests__/format.test.ts provando que `undefined` vira "não configurado" e que `0` permanece `0`
- [X] T014 [P] Criar src/shared/components/empty-state.tsx (`data-testid="empty-state"`) com mensagem e ação em destaque
- [X] T015 [P] Criar src/shared/components/error-state.tsx (`data-testid="error-state"`, `retry-button`) recebendo `retry` — em Next 16.3 o `error.tsx` recebe `{ error, retry }`, não `reset` (R5)
- [X] T016 [P] Criar src/shared/components/pagination.tsx (`pagination-prev`, `pagination-next`, `pagination-info`) preservando os `searchParams` vigentes nos links
- [X] T017 [P] Criar src/shared/components/confirm-dialog.tsx sobre `AlertDialog` (`confirm-dialog`, `confirm-button`, `cancel-button`) para toda ação destrutiva (FR-005)
- [X] T018 [P] Criar src/shared/components/page-header.tsx (`page-header`, `breadcrumb`) sobre o `Breadcrumb` do shadcn
- [X] T019 [P] Escrever src/shared/components/__tests__/ com Testing Library para `EmptyState`, `ErrorState` (aciona `retry`), `Pagination` (preserva filtro na URL) e `ConfirmDialog` (só executa após confirmar), asserindo por papel acessível
- [X] T020 Criar src/shared/components/index.ts exportando os cinco componentes compartilhados

### Casca de roteamento

- [X] T021 Ajustar src/app/layout.tsx para `lang="pt-BR"`, metadata do painel e montagem do `Toaster` (sonner)
- [X] T022 Substituir src/app/page.tsx por `redirect("/aplicacoes")` de `next/navigation`
- [X] T023 [P] Criar src/app/error.tsx (usando `ErrorState` com `{ error, retry }`) e src/app/not-found.tsx com caminho de volta para `/aplicacoes`

### Infraestrutura de E2E

- [X] T024 Criar e2e/stub-api/server.ts — servidor HTTP Node sem dependências, com estado em memória, roteador por método e caminho, resposta RFC 9457 (`application/problem+json`) e helper de `PageResponse`; sem nenhuma rota de recurso ainda (R9)
- [X] T025 Ajustar playwright.config.ts para dois `webServer` (o stub e o `next start` com `PITACO_API_URL` apontando para o stub), mantendo `fullyParallel: true` e permitindo `E2E_API=real` para apontar ao backend real
- [X] T026 Criar e2e/support/helpers.ts com criação de aplicação própria por teste (via UI ou preparação no stub), garantindo isolamento sem reset global

**Checkpoint**: cliente HTTP testado, componentes de estado prontos, `/` redireciona e o Playwright sobe stub + app — histórias podem começar

---

## Phase 3: User Story 1 - Ver e criar aplicações (Priority: P1) 🎯 MVP

**Goal**: listar, filtrar, paginar, cadastrar e consultar aplicações — a raiz de todo o resto

**Independent Test**: cadastrar duas aplicações pelo painel, conferir que ambas aparecem na lista, filtrar por ativas, abrir uma e conferir que os dados exibidos são os informados no cadastro

### Schemas e leitura

- [X] T027 [P] [US1] Criar src/features/applications/schemas/application.ts com os schemas Zod de `ApplicationSummary` e `Application` (prazos opcionais **ausentes**, nunca `null`/`0`) conforme data-model.md
- [X] T028 [P] [US1] Criar src/features/applications/schemas/forms.ts com `CreateApplicationForm` (nome obrigatório até 120; slug opcional `^[a-z0-9]+(-[a-z0-9]+)*$` até 50; prazos opcionais `>= 1`) e o schema de `searchParams` da listagem (`status?`, `page?`, `size?`)
- [X] T029 [P] [US1] Escrever src/features/applications/__tests__/schemas.test.ts cobrindo slug inválido, nome acima do limite, prazo `0` recusado e ausência de prazo preservada como ausente
- [X] T030 [US1] Criar src/features/applications/api/index.ts com `listApplications`, `getApplication` e `createApplication` sobre o cliente compartilhado, conforme contracts/backend-api.md § Aplicações
- [X] T031 [US1] Escrever src/features/applications/__tests__/api.test.ts com `fetch` stubado, cobrindo query de filtro e paginação, `404 application.not_found`, `409` de slug em conflito e o `201 { id, slug }` da criação

### Mutação

- [X] T032 [US1] Criar src/features/applications/actions.ts (`"use server"`) com `createApplicationAction(prevState, formData)` validando `FormData` por schema e devolvendo `Result` como estado; em sucesso, `redirect` para o detalhe (contracts/backend-api.md § Revalidação)
- [X] T033 [US1] Escrever src/features/applications/__tests__/actions.test.ts provando que a recusa `validation` vira erro por campo no estado e que o que foi digitado é devolvido intacto (FR-004)

### Componentes

- [X] T034 [P] [US1] Criar src/features/applications/components/applications-table.tsx (Server Component de apresentação) com nome, slug, badge de situação e data, `data-testid` `applications-table` e `application-row`
- [X] T035 [P] [US1] Criar src/features/applications/components/application-status-filter.tsx (`"use client"`, `application-status-filter`) escrevendo o filtro nos `searchParams` sem perder a página
- [X] T036 [P] [US1] Criar src/features/applications/components/application-form.tsx (`"use client"`, `application-form`) com `useActionState`, `submit-button` desabilitado por `pending` (FR-006) e `field-error-<campo>` / `form-error`
- [X] T037 [P] [US1] Criar src/features/applications/components/application-detail.tsx (`application-detail`, `quiet-period`, `retention`, `open-text-retention`) exibindo prazo ausente como "não configurado"
- [X] T038 [P] [US1] Escrever src/features/applications/__tests__/components.test.tsx cobrindo o formulário (erro por campo exibido, valores preservados, submit bloqueado enquanto pendente) e o detalhe (prazo ausente ≠ `0`)
- [X] T039 [US1] Criar src/features/applications/index.ts expondo apenas leituras, actions e componentes usados por `app/`

### Rotas

- [X] T040 [US1] Criar src/app/aplicacoes/page.tsx — casca fina que valida `searchParams` (Promise em Next 16), chama `listApplications` e compõe tabela, filtro, paginação e `EmptyState`
- [X] T041 [P] [US1] Criar src/app/aplicacoes/loading.tsx com esqueleto de tabela e src/app/aplicacoes/error.tsx usando `ErrorState` com `retry`
- [X] T042 [US1] Criar src/app/aplicacoes/nova/page.tsx compondo `ApplicationForm`
- [X] T043 [US1] Criar src/app/aplicacoes/[applicationId]/layout.tsx com o breadcrumb da aplicação lido sob `<Suspense>`, sem leitura bloqueante no corpo do layout (R6)
- [X] T044 [US1] Criar src/app/aplicacoes/[applicationId]/page.tsx chamando `getApplication`, com `not_found` virando `notFound()`, e links para chaves e pesquisas
- [X] T045 [P] [US1] Criar src/app/aplicacoes/[applicationId]/not-found.tsx com caminho de volta à listagem e o `error.tsx` do segmento

### Verificação de ponta a ponta

- [X] T046 [US1] Implementar em e2e/stub-api/routes/applications.ts as rotas `GET /applications` (com `status`, `page`, `size`, ordenação `createdAt` desc), `GET /applications/{id}` e `POST /applications` (incluindo `400` de slug inválido e `409` de conflito)
- [X] T047 [US1] Escrever e2e/aplicacoes.spec.ts: cadastrar, ver na lista, filtrar preservando o filtro ao voltar do detalhe, abrir o detalhe conferindo "não configurado", e acessar identificador inexistente

**Checkpoint**: US1 entregue e testável sozinha — o painel já é utilizável de ponta a ponta para aplicações

---

## Phase 4: User Story 3 - Criar e montar o rascunho de uma pesquisa (Priority: P1)

**Goal**: criar, listar, renomear e descartar pesquisas; adicionar, editar, remover e reordenar perguntas

**Independent Test**: criar uma pesquisa, adicionar três perguntas de tipos diferentes, editar uma, remover outra, reordenar as restantes, sair da página e voltar conferindo que a montagem foi preservada

### Schemas e leitura

- [X] T048 [P] [US3] Criar src/features/surveys/schemas/survey.ts com `Survey`, `SurveyDetail` (incluindo `content.source`) e `SurveyState` (`draft`/`scheduled`/`active`/`paused`/`ended`)
- [X] T049 [P] [US3] Criar src/features/surveys/schemas/question.ts com `Question`, `QuestionOption`, `QuestionType` e as regras de **forma** por tipo (opções exigidas em `single_choice`/`multiple_choice`; `range` aceito em `rating`/`scale`) — sem replicar invariante de backend
- [X] T050 [P] [US3] Escrever src/features/surveys/__tests__/question-schema.test.ts provando que tipo de escolha sem opção é recusado antes do envio (FR-022) e que `nps`/`free_text` não exigem nem opção nem faixa
- [X] T051 [US3] Criar src/features/surveys/api/surveys.ts com `createSurvey`, `listSurveys`, `getSurvey`, `renameSurvey` e `discardSurvey`
- [X] T052 [US3] Criar src/features/surveys/api/questions.ts com `addQuestion`, `updateQuestion`, `removeQuestion` e `reorderQuestions` (permutação **exata** dos identificadores da versão)
- [X] T053 [US3] Escrever src/features/surveys/__tests__/surveys-api.test.ts e question-api.test.ts com `fetch` stubado, cobrindo o `409` de descarte de pesquisa já publicada e o corpo da reordenação

### Mutação

- [X] T054 [US3] Criar src/features/surveys/actions.ts (`"use server"`) com as actions de pesquisa: criar (`redirect` para a montagem), renomear e descartar (`revalidatePath` da listagem)
- [X] T055 [US3] Acrescentar em src/features/surveys/actions.ts as actions de pergunta — adicionar, editar, remover e reordenar — todas com `refresh()` de `next/cache` após mutar (contracts/backend-api.md § Revalidação)
- [X] T056 [US3] Escrever src/features/surveys/__tests__/survey-actions.test.ts cobrindo recusa exibida sem perder o formulário e a montagem da permutação completa na reordenação

### Componentes

- [X] T057 [P] [US3] Criar src/features/surveys/lib/survey-labels.ts (rótulo pt-BR por `SurveyState` e por `QuestionType`) e seu teste em src/features/surveys/__tests__/survey-labels.test.ts
- [X] T058 [P] [US3] Criar src/features/surveys/components/survey/surveys-table.tsx (`surveys-table`, `survey-row`, `survey-state-badge`)
- [X] T059 [P] [US3] Criar src/features/surveys/components/survey/survey-form.tsx (`"use client"`) para criação por nome
- [X] T060 [P] [US3] Criar src/features/surveys/components/survey/rename-survey.tsx (`"use client"`, `rename-survey-button`)
- [X] T061 [P] [US3] Criar src/features/surveys/components/survey/discard-survey-button.tsx (`"use client"`, `discard-survey-button`) usando `ConfirmDialog` e só renderizado quando `publishedVersionNumber` está ausente (FR-020)
- [X] T062 [P] [US3] Criar src/features/surveys/components/questions/questions-list.tsx (`questions-list`, `question-item`) ordenando por `position`
- [X] T063 [P] [US3] Criar src/features/surveys/components/questions/question-form.tsx (`"use client"`, `question-form`, `question-type-select`, `question-options`, `add-option-button`) mostrando opções só nos tipos de escolha e faixa só em `rating`/`scale`
- [X] T064 [P] [US3] Criar src/features/surveys/components/questions/remove-question-button.tsx (`"use client"`, `remove-question-button`) com `ConfirmDialog`
- [X] T065 [P] [US3] Criar src/features/surveys/components/questions/move-question-buttons.tsx (`"use client"`, `move-question-up`, `move-question-down`) enviando a permutação completa e **não** oferecendo a ação quando há uma única pergunta
- [X] T066 [P] [US3] Escrever src/features/surveys/__tests__/question-components.test.tsx cobrindo a troca de tipo revelando/ocultando campos, a recusa de envio sem opção, a confirmação obrigatória na remoção e a ausência de mover com uma pergunta só
- [X] T067 [US3] Criar src/features/surveys/index.ts como fronteira pública da feature (será estendido por US4, US5 e US6 — mesmo arquivo, tarefas sequenciais)

### Rotas

- [X] T068 [US3] Criar src/app/aplicacoes/[applicationId]/pesquisas/page.tsx e loading.tsx, listando apenas as pesquisas daquela aplicação (FR-017)
- [X] T069 [US3] Criar src/app/aplicacoes/[applicationId]/pesquisas/nova/page.tsx
- [X] T070 [US3] Criar src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/layout.tsx com cabeçalho (nome editável, estado) e navegação entre montagem, disparo, publicação e versões — leitura sob `<Suspense>`
- [X] T071 [US3] Criar src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/page.tsx (montagem), em somente leitura quando `state` é `ended` ou `content.source` é `published` sem rascunho aberto
- [X] T072 [P] [US3] Criar error.tsx e not-found.tsx do segmento da pesquisa

### Verificação de ponta a ponta

- [X] T073 [US3] Implementar em e2e/stub-api/routes/surveys.ts as rotas de pesquisa e de pergunta do contrato, incluindo validação da permutação na reordenação e o `409` de descarte de pesquisa publicada
- [X] T074 [US3] Escrever e2e/pesquisas.spec.ts: criar pesquisa, recusar `single_choice` sem opções, adicionar três tipos, editar, remover com confirmação, reordenar, recarregar conferindo persistência e descartar

**Checkpoint**: US1 e US3 funcionam independentemente — já dá para escrever e organizar pesquisas

---

## Phase 5: User Story 2 - Emitir e revogar chaves de acesso (Priority: P2)

**Goal**: listar, emitir (com segredo exibido uma única vez) e revogar chaves de uma aplicação

**Independent Test**: emitir uma chave, conferir que o segredo aparece uma única vez, recarregar a página e conferir que ele não é mais exibido, revogar a chave e conferir a mudança de situação

- [X] T075 [P] [US2] Criar src/features/api-keys/schemas/api-key.ts com `ApiKey` (**sem** campo de segredo) e `IssuedApiKey` (único tipo com `secret`), mais `IssueApiKeyForm` (`label` obrigatório até 80)
- [X] T076 [P] [US2] Escrever src/features/api-keys/__tests__/schemas.test.ts provando que o schema de leitura descarta qualquer `secret` que venha na resposta — a ausência é estrutural (FR-014)
- [X] T077 [US2] Criar src/features/api-keys/api/index.ts com `listApiKeys`, `getApiKey`, `issueApiKey` e `revokeApiKey`
- [X] T078 [US2] Escrever src/features/api-keys/__tests__/api.test.ts cobrindo o filtro por situação, o `201` com `secret` só na emissão, o `204` da revogação e o `409` de chave já revogada
- [X] T079 [US2] Criar src/features/api-keys/actions.ts (`"use server"`) com `issueApiKeyAction` (devolve o segredo no estado da action) e `revokeApiKeyAction`, ambas com `refresh()` após mutar
- [X] T080 [US2] Escrever src/features/api-keys/__tests__/actions.test.ts provando que o segredo aparece apenas no retorno da emissão e que a revogação trata o `409` como recusa exibível
- [X] T081 [P] [US2] Criar src/features/api-keys/components/api-keys-table.tsx (`api-keys-table`, `api-key-row`) com rótulo, prefixo, situação e datas — nunca o segredo
- [X] T082 [P] [US2] Criar src/features/api-keys/components/issue-key-form.tsx (`"use client"`, `issue-key-button`, `issue-key-form`)
- [X] T083 [P] [US2] Criar src/features/api-keys/components/secret-dialog.tsx (`"use client"`, `secret-dialog`, `secret-value`, `copy-secret-button`, `close-secret-dialog`) guardando o segredo em `useState`, avisando que não poderá ser recuperado e **descartando o estado ao fechar** — sem URL, `localStorage`, cookie ou log (R13)
- [X] T084 [P] [US2] Criar src/features/api-keys/components/revoke-key-button.tsx (`"use client"`, `revoke-key-button`) com `ConfirmDialog`, renderizado apenas para chaves `active` (FR-015)
- [X] T085 [P] [US2] Escrever src/features/api-keys/__tests__/components.test.tsx provando que fechar o diálogo remove `secret-value` do documento e que a ação de revogar não existe em chave já revogada
- [X] T086 [US2] Criar src/features/api-keys/index.ts como fronteira pública da feature
- [X] T087 [US2] Criar src/app/aplicacoes/[applicationId]/chaves/page.tsx, loading.tsx e error.tsx compondo tabela, emissão, `EmptyState` e paginação
- [X] T088 [US2] Implementar em e2e/stub-api/routes/api-keys.ts as quatro rotas do contrato, devolvendo `secret` **apenas** no `POST`
- [X] T089 [US2] Escrever e2e/chaves.spec.ts: emitir, ver o segredo uma vez, fechar e recarregar afirmando a ausência do segredo em todo o documento (SC-004), revogar com confirmação e conferir a mudança de situação

**Checkpoint**: US1, US2 e US3 funcionam independentemente

---

## Phase 6: User Story 4 - Configurar o disparo e as regras de segmentação (Priority: P2)

**Goal**: definir e redefinir o disparo; adicionar e remover regras de segmentação, exibidas junto do disparo

**Independent Test**: definir o disparo de uma pesquisa, adicionar duas regras, remover uma, recarregar e conferir que a configuração exibida é a que ficou salva

- [X] T090 [P] [US4] Criar src/features/surveys/schemas/trigger.ts com `Trigger` (`eventName` `^[a-z][a-z0-9_.]{1,79}$`, `windowStart` obrigatório, `windowEnd` opcional, `samplingRate` 0.0..1.0 com ausente ⇒ `0.0`) e `SegmentationRule` (`operation` em `equals|not_equals|present|absent`)
- [X] T091 [P] [US4] Escrever src/features/surveys/__tests__/trigger-schema.test.ts provando que `value` é exigido em `equals`/`not_equals` e recusado em `present`/`absent`, e que `eventName` fora do padrão é recusado
- [X] T092 [US4] Criar src/features/surveys/api/trigger.ts com `defineTrigger` (PUT idempotente), `addSegmentationRule` e `removeSegmentationRule`
- [X] T093 [US4] Escrever src/features/surveys/__tests__/trigger-api.test.ts cobrindo a redefinição que substitui sem duplicar (FR-025) e a recusa de combinação inválida
- [X] T094 [US4] Acrescentar em src/features/surveys/actions.ts as actions de disparo e de regra, com `refresh()` após mutar
- [X] T095 [US4] Escrever src/features/surveys/__tests__/trigger-actions.test.ts provando que a recusa do backend é exibida sem que nenhuma regra seja criada
- [X] T096 [P] [US4] Criar src/features/surveys/components/trigger/trigger-panel.tsx (`trigger-panel`) exibindo disparo e regras **em conjunto** (FR-028), com estado explícito "não configurado"
- [X] T097 [P] [US4] Criar src/features/surveys/components/trigger/trigger-form.tsx (`"use client"`, `trigger-form`, `event-name-input`, `window-start-input`, `window-end-input`, `sampling-rate-input`)
- [X] T098 [P] [US4] Criar src/features/surveys/components/trigger/rules-list.tsx e rule-form.tsx (`rules-list`, `rule-item`, `add-rule-button`, `rule-operation-select`, `rule-value-input`, `remove-rule-button`), com o campo de valor aparecendo só em `equals`/`not_equals` e remoção sob `ConfirmDialog`
- [X] T099 [P] [US4] Escrever src/features/surveys/__tests__/trigger-components.test.tsx cobrindo o campo de valor que aparece e some conforme a operação e a exibição conjunta de disparo e regras
- [X] T100 [US4] Criar src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/disparo/page.tsx e loading.tsx; estender src/features/surveys/index.ts com as exportações de disparo
- [X] T101 [US4] Implementar em e2e/stub-api/routes/trigger.ts as três rotas do contrato, com o `PUT` substituindo o disparo existente
- [X] T102 [US4] Escrever e2e/disparo.spec.ts: definir disparo, redefinir conferindo que não duplica, adicionar uma regra `equals` (com valor) e uma `present` (sem campo de valor), remover uma e recarregar

**Checkpoint**: a pesquisa já pode ser inteiramente configurada, faltando apenas publicar

---

## Phase 7: User Story 5 - Publicar e acompanhar versões (Priority: P2)

**Goal**: consultar impedimentos, publicar, listar versões, ver conteúdo congelado, abrir e descartar rascunho de versão e consultar comparabilidade

**Independent Test**: com uma pesquisa incompleta, consultar os impedimentos e conferir que a publicação está bloqueada; completar o que falta, publicar, e conferir que a versão aparece na listagem com o conteúdo congelado

- [X] T103 [P] [US5] Criar src/features/surveys/schemas/publication.ts com `PublicationImpediment` (os cinco `code` do contrato, `field?`, `questionKey?`) e `PublishSurveyForm` (`changeKind` obrigatório a partir da versão 2, ignorado na versão 1)
- [X] T104 [P] [US5] Criar src/features/surveys/schemas/version.ts com `SurveyVersion`, `SurveyVersionDetail` (conteúdo congelado) e `VersionComparability`
- [X] T105 [P] [US5] Escrever src/features/surveys/__tests__/publication-schema.test.ts provando que `changeKind` só é exigido quando há `publishedVersionNumber`
- [X] T106 [US5] Criar src/features/surveys/api/publication.ts com `getPublicationImpediments` e `publishSurvey`
- [X] T107 [US5] Criar src/features/surveys/api/versions.ts com `listVersions`, `getVersion`, `openDraftVersion`, `discardDraftVersion` e `getVersionComparability`
- [X] T108 [US5] Escrever src/features/surveys/__tests__/publication-api.test.ts e versions-api.test.ts cobrindo o `409` de publicação com impedimentos e o `404` de versão inexistente
- [X] T109 [US5] Acrescentar em src/features/surveys/actions.ts as actions de publicar, abrir versão de rascunho e descartar rascunho, com `revalidatePath` do segmento da pesquisa (afeta cabeçalho, versões e publicação)
- [X] T110 [US5] Escrever src/features/surveys/__tests__/publication-actions.test.ts provando que a publicação recusada por impedimento devolve a lista ao estado da action
- [X] T111 [P] [US5] Criar src/features/surveys/lib/impediment-messages.ts traduzindo cada `code` em frase acionável em pt-BR, e seu teste cobrindo os cinco códigos
- [X] T112 [P] [US5] Criar src/features/surveys/components/publication/impediments-list.tsx (`impediments-list`, `impediment-item`) ligando o item à pergunta quando há `questionKey`
- [X] T113 [P] [US5] Criar src/features/surveys/components/publication/publish-form.tsx (`"use client"`, `publish-button`, `publish-form`, `change-kind-select`, `change-summary-input`) com o botão desabilitado enquanto houver impedimento (FR-030) e enquanto `pending` — clique duplo não cria duas versões
- [X] T114 [P] [US5] Criar src/features/surveys/components/versions/versions-table.tsx (`versions-table`, `version-row`) da mais recente para a mais antiga
- [X] T115 [P] [US5] Criar src/features/surveys/components/versions/comparability-panel.tsx (`comparability-panel`) exibindo os grupos
- [X] T116 [P] [US5] Criar src/features/surveys/components/versions/version-detail.tsx (`version-detail`, `version-questions`) somente leitura
- [X] T117 [P] [US5] Criar src/features/surveys/components/versions/draft-version-actions.tsx (`"use client"`, `open-draft-version-button`, `discard-draft-version-button`) com confirmação no descarte
- [X] T118 [P] [US5] Escrever src/features/surveys/__tests__/publication-components.test.tsx cobrindo botão desabilitado com impedimento, campos de mudança ausentes na versão 1 e presentes a partir da 2
- [X] T119 [US5] Criar src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/publicacao/page.tsx
- [X] T120 [US5] Criar src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/versoes/page.tsx e loading.tsx
- [X] T121 [US5] Criar src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/versoes/[number]/page.tsx com `notFound()` para versão inexistente; estender src/features/surveys/index.ts com publicação e versões
- [X] T122 [US5] Implementar em e2e/stub-api/routes/publication.ts e versions.ts as rotas do contrato, calculando impedimentos a partir do estado em memória e congelando o conteúdo na publicação
- [X] T123 [US5] Escrever e2e/publicacao.spec.ts: consultar impedimentos com pesquisa incompleta e conferir o botão desabilitado, completar, publicar, conferir a versão 1 na listagem com conteúdo congelado, abrir e descartar rascunho de versão

**Checkpoint**: o ciclo de autoria está completo, do cadastro à publicação (SC-001)

---

## Phase 8: User Story 6 - Controlar o que está no ar (Priority: P3)

**Goal**: oferecer apenas as transições autorizadas pela API e executar pausar, retomar e encerrar

**Independent Test**: publicar uma pesquisa, pausá-la e conferir a mudança de estado, retomá-la, encerrá-la, e conferir que depois do encerramento nenhuma transição é oferecida

- [X] T124 [P] [US6] Criar src/features/surveys/schemas/transition.ts com `SurveyStateTransition` (estados e `reason` em `publication|manual_pause|manual_resume|manual_end|window_opened|window_closed`)
- [X] T125 [P] [US6] Escrever src/features/surveys/__tests__/transition-schema.test.ts cobrindo os seis motivos e a lista vazia
- [X] T126 [US6] Criar src/features/surveys/api/lifecycle.ts com `getTransitions`, `pauseSurvey`, `resumeSurvey` e `endSurvey`
- [X] T127 [US6] Escrever src/features/surveys/__tests__/lifecycle-api.test.ts cobrindo a recusa de transição não permitida
- [X] T128 [US6] Acrescentar em src/features/surveys/actions.ts as actions de pausar, retomar e encerrar, com `revalidatePath` do segmento da pesquisa
- [X] T129 [US6] Escrever src/features/surveys/__tests__/lifecycle-actions.test.ts provando que a recusa por estado alterado por terceiros é exibida e a UI se realinha
- [X] T130 [P] [US6] Criar src/features/surveys/components/lifecycle/transition-actions.tsx (`"use client"`, `pause-survey-button`, `resume-survey-button`, `end-survey-button`) renderizando **apenas** o que `getTransitions` autoriza — nada derivado do estado (FR-035) — com `ConfirmDialog` de irreversibilidade no encerrar
- [X] T131 [P] [US6] Criar src/features/surveys/components/lifecycle/transitions-history.tsx (`transitions-history`) com o histórico e seus motivos
- [X] T132 [P] [US6] Escrever src/features/surveys/__tests__/lifecycle-components.test.tsx provando que lista de transições vazia não oferece nenhuma ação e que encerrar exige confirmação explícita
- [X] T133 [US6] Integrar as transições no layout da pesquisa (src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/layout.tsx), aplicando somente leitura quando `state` é `ended` (FR-037); estender src/features/surveys/index.ts com o ciclo de vida
- [X] T134 [US6] Implementar em e2e/stub-api/routes/lifecycle.ts as quatro rotas do contrato, com `GET /transitions` refletindo a máquina de estados de data-model.md
- [X] T135 [US6] Escrever e2e/ciclo-de-vida.spec.ts: publicar, pausar, retomar, encerrar com confirmação e conferir que a pesquisa encerrada não oferece transição e aparece somente leitura

**Checkpoint**: todas as histórias funcionam de forma independente; SC-002 satisfeito (toda capacidade da API tem caminho no painel)

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T136 [P] Atualizar README.md com a configuração de `PITACO_API_URL`, os scripts e a nota de que o E2E não precisa de backend
- [X] T137 [P] Revisar todos os `"use client"` do repositório confirmando que cada um está no menor componente possível e justificado por interatividade (Princípio "server first")
- [X] T138 [P] Revisar a estilização confirmando uso exclusivo de utilitários Tailwind com `cn()`, sem CSS-in-JS, sem CSS Module e sem folha por componente (Princípio III)
- [X] T139 Auditar imports entre features confirmando que todos passam pelo `index.ts` de destino e que nada em `src/app/` implementa regra de negócio (Princípio I)
- [X] T140 Rodar `npm run verify` (lint + typecheck + vitest + playwright) e deixar a suíte verde — portão de merge da constituição
- [ ] T141 Executar manualmente a validação por história de quickstart.md (US1 a US6) contra o backend real em `PITACO_API_URL` — **pendente**: exige o backend de `../backend` no ar (`http://localhost:8080/api`), que não estava respondendo. Todo o resto foi verificado contra o simulador de API, que segue `contracts/backend-api.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sem dependência — começa imediatamente
- **Foundational (Fase 2)**: depende da Fase 1 — **bloqueia todas as histórias**
- **US1 (Fase 3)**: depende da Fase 2
- **US3 (Fase 4)**: depende da Fase 2; usa as rotas de `[applicationId]` criadas em US1 para navegação, mas é testável por URL direta
- **US2 (Fase 5)**: depende da Fase 2; independente de US3
- **US4 (Fase 6)**: depende de US3 (o disparo pertence a uma pesquisa e reusa `features/surveys`)
- **US5 (Fase 7)**: depende de US3 e de US4 (só se publica pesquisa com perguntas e disparo)
- **US6 (Fase 8)**: depende de US5 (só se controla o que já está no ar)
- **Polish (Fase 9)**: depende de todas as histórias desejadas

### Conflitos de arquivo a respeitar

- `src/features/surveys/actions.ts` é tocado por T054, T055, T094, T109 e T128 — **sequenciais**, nunca em paralelo
- `src/features/surveys/index.ts` é tocado por T067, T100, T121 e T133 — **sequenciais**
- `e2e/stub-api/` cresce por arquivo de rota (T046, T073, T088, T101, T122, T134), que são independentes entre si depois de T024

### Parallel Opportunities

- Fase 2: T008–T010 juntos; T012–T018 juntos; T023 com qualquer um deles
- US1: T027–T029 juntos; T034–T038 juntos
- US3: T048–T050 juntos; T057–T066 juntos
- US2: T075–T076 juntos; T081–T085 juntos
- US4: T090–T091 juntos; T096–T099 juntos
- US5: T103–T105 juntos; T111–T118 juntos
- US6: T124–T125 juntos; T130–T132 juntos
- Com equipe: depois da Fase 2, **US1, US2 e US3 podem correr em paralelo** por serem features distintas (`applications`, `api-keys`, `surveys`); US4 → US5 → US6 seguem em série dentro de `surveys`

---

## Parallel Example: User Story 1

```bash
# Schemas e seus testes, juntos:
Task: "Criar src/features/applications/schemas/application.ts"
Task: "Criar src/features/applications/schemas/forms.ts"
Task: "Escrever src/features/applications/__tests__/schemas.test.ts"

# Componentes da história, juntos:
Task: "Criar components/applications-table.tsx"
Task: "Criar components/application-status-filter.tsx"
Task: "Criar components/application-form.tsx"
Task: "Criar components/application-detail.tsx"
```

---

## Implementation Strategy

### MVP First (US1)

1. Fase 1: Setup
2. Fase 2: Foundational (bloqueia tudo)
3. Fase 3: US1
4. **PARAR e VALIDAR**: `e2e/aplicacoes.spec.ts` verde e o roteiro US1 do quickstart executado
5. Já é demonstrável: o painel cadastra e consulta aplicações sem chamada direta à API

### Incremental Delivery

1. Setup + Foundational → base pronta
2. + US1 → MVP (aplicações)
3. + US3 → autoria de pesquisa (o maior valor da spec)
4. + US2 → chaves de acesso
5. + US4 → disparo e segmentação
6. + US5 → publicação e versões (fecha SC-001)
7. + US6 → controle operacional (fecha SC-002)

### Parallel Team Strategy

Depois da Fase 2: dev A em US1, dev B em US2, dev C em US3 — três features distintas, sem import cruzado. US4, US5 e US6 entram em série com quem terminou US3.

---

## Notes

- `[P]` = arquivos diferentes, sem dependência pendente
- Server Component assíncrono não é testado em Vitest ([R8](./research.md#r8)); por isso cada `page.tsx` é casca fina e a lógica vive em módulo puro
- Rede real é proibida em Vitest: `fetch` é substituído por `vi.stubGlobal`
- Cada teste E2E cria sua própria aplicação e opera só dentro dela ([R9](./research.md#r9))
- Nenhuma tarefa é dada por concluída com a suíte vermelha
- Commit em português, no padrão do repositório (`feat:`, `fix:`, `chore:`)
