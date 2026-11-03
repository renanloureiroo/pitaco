---
description: "Task list — 001-sdk-core"
---

# Tasks: SDK React Native — core headless

**Input**: documentos de desenho em `/specs/001-sdk-core/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/public-api.md`, `contracts/collect-routes.md`

**Tests**: incluídos e **obrigatórios**. O Princípio VIII da constituição ("Teste É Contrato") exige teste antes do código; o `quickstart.md` lista o que precisa estar verde.

**Organization**: agrupadas por história de usuário, para que cada uma seja implementável e testável isoladamente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável (arquivo distinto, sem dependência pendente)
- **[Story]**: US1 a US6
- Todo caminho é relativo à raiz do SDK (`sdk/`)

---

## Phase 1: Setup (Infraestrutura do pacote)

**Purpose**: o pacote `@pitaco/react-native` com build, lint, testes e contrato gerado.

- [X] T001 Criar `package.json` com `peerDependencies` só de `react >=18.2.0` e `react-native >=0.74.0`, `exports` para `.`, `./storage/async-storage`, `./storage/mmkv` e `./preview`, `sideEffects: false`, `files` sem `__tests__`, e os scripts `lint`, `typecheck`, `test`, `build`, `gen:api` e `verify`
- [X] T002 [P] Configurar `tsconfig.json` (estrito, `noUncheckedIndexedAccess`, `example/**` excluído) e `tsconfig.build.json` para o alvo de tipos do bob
- [X] T003 [P] Configurar `babel.config.js` e o bloco `react-native-builder-bob` (alvos `module`, `commonjs` e `typescript`) em `package.json`
- [X] T004 [P] Configurar `jest.config.js` (preset `@react-native/jest-preset`, testes em `__tests__/`, `lib/` ignorado) e `jest.setup.ts` trocando o `fetch` global por um que falha e fazendo `performance.now` seguir os timers falsos
- [X] T005 [P] Configurar `eslint.config.mjs` em flat config com `recommendedTypeChecked`, `react-hooks` 7, `no-console` permitindo só `warn` e `no-restricted-imports` dos seis pacotes nativos (D-01)
- [X] T006 Versionar o snapshot `openapi/pitaco.json` e escrever `scripts/gen-api.mjs` (baixa de `PITACO_OPENAPI_URL`, tira `servers`, cai no snapshot sem backend, gera `src/api/openapi.ts`) (D-02)
- [X] T007 Gerar `src/api/openapi.ts` e criar os aliases de `src/api/types.ts`, com `SubmissionAnswer.value` tipado à mão (D-03)
- [X] T008 [P] Escrever `src/__tests__/version.test.ts` (versão igual ao `package.json`, semver até 40 caracteres, sem dependência de runtime) e criar `src/version.ts`

---

## Phase 2: Foundational (Pré-requisitos bloqueantes)

**Purpose**: tempo, log, UUID, configuração, storage e o suporte de teste que todas as histórias usam.

**⚠️ CRITICAL**: nenhuma história pode começar antes desta fase terminar.

- [X] T009 [P] Criar o suporte de teste em `src/__tests__/support/fakeClock.ts`, `src/__tests__/support/fakeServer.ts` (com a idempotência do contrato) e `src/__tests__/support/fixtures.ts` (schema de referência com os seis tipos e uma condição)
- [X] T010 [P] Criar `src/core/clock.ts` (relógio injetável `{ mono, wall }`, monotônico que nunca volta) e `src/core/timers.ts` (resolvidos na hora da chamada)
- [X] T011 [P] Criar `src/core/logger.ts` com `warn`/`warnOnce`/`debug` só em `__DEV__`, prefixo `[Pitaco]` e `silentLogger`
- [X] T012 [P] Escrever `src/core/__tests__/uuid.test.ts` (randomUUID → getRandomValues → gerador próprio, crypto que lança, formato v4) e criar `src/core/uuid.ts` (D-15)
- [X] T013 Criar `src/core/config.ts`: `resolveConfig`, `normalizeBaseUrl`, `sanitizeEventName`, `sanitizeReference`, `sanitizeAttributes`, `LIMITS` e os padrões de timeout, com mensagens de correção
- [X] T014 [P] Escrever `src/core/storage/__tests__/storage.test.ts` (adaptadores, falha do adaptador, ordem das gravações, JSON ilegível) e criar `src/core/storage/types.ts`, `src/core/storage/memory.ts` e `src/core/storage/safe.ts` (D-13)

**Checkpoint**: fundação pronta.

---

## Phase 3: User Story 1 — Integrar e receber a pesquisa (Priority: P1) 🎯 MVP

**Goal**: `track` → elegibilidade → oferta → `present()` → abertura → envio.

**Independent Test**: com o servidor falso, `track`, `present()`, responder, concluir e conferir abertura e envio.

### Testes de US1 (escrever antes) ⚠️

- [X] T015 [P] [US1] Escrever `src/core/survey/__tests__/schema.test.ts` cobrindo `survey` nulo/ausente, corpo malformado, normalização da pesquisa de referência, ordenação por `position` e `null` igual a ausente
- [X] T016 [P] [US1] Escrever em `src/core/runtime/__tests__/runtime.test.ts` o bloco "exibição e envio": abertura só no `present()`, dispensa antes de visível sem envio, outra pesquisa ignorada com uma em andamento, dispensa na segunda pergunta preservando a primeira

### Implementação de US1

- [X] T017 [US1] Criar `src/core/survey/schema.ts` com `normalizeSurvey` e `parseEligibilityResponse`
- [X] T018 [US1] Criar `src/core/session/controller.ts` (interface `SurveyController`), `src/core/session/session.ts` (`SurveySession`) e `src/core/session/snapshot.ts` (`deriveSnapshot`)
- [X] T019 [US1] Criar `src/core/machine/submission.ts` (`buildSubmission`, D-19)
- [X] T020 [US1] Criar `src/core/transport/api.ts` (`CollectApi` com as rotas `eligibility`, `displays`, `submission`, `events`, `suppressions`, `sdk-errors`)
- [X] T021 [US1] Criar `src/core/runtime/placement.ts` (`PlacementGate`, `SurveyCandidate`, `PlacementControls`, `immediatePlacement`)
- [X] T022 [US1] Criar `src/core/runtime/runtime.ts` (`PitacoRuntime.create`, `attach`, `detach`, `dispose`, `track`, `consider`, `offer`, `onTransition`, cadeia `pipeline`) (D-05)
- [X] T023 [US1] Criar `src/react/context.ts`, `src/react/ErrorBoundary.tsx`, `src/react/usePitacoSurvey.ts` (`createSurveyActions`, estado `idle`) e `src/react/usePitaco.ts`
- [X] T024 [US1] Criar `src/react/PitacoProvider.tsx` com o runtime criado no render e ligado no efeito, identidade de recriação e setters de `onEvent`, respondente e atributos (D-12)
- [X] T025 [US1] Exportar a superfície da seção "Tipos exportados" de `contracts/public-api.md` em `src/index.ts`

**Checkpoint**: um app integra, dispara e coleta pelo hook.

---

## Phase 4: User Story 2 — O app nunca quebra (Priority: P1)

**Goal**: toda falha vira silêncio e aviso de desenvolvimento.

**Independent Test**: cada falha simulada no servidor falso, sem exceção e sem pesquisa.

### Testes de US2 (escrever antes) ⚠️

- [X] T026 [P] [US2] Escrever `src/core/transport/__tests__/http.test.ts`: cabeçalhos, 2xx sem corpo, corpo não JSON, rede fora, timeout com abort, 5xx, 401/403 com `code`, outros 4xx, 429 e `Retry-After` em segundos e data HTTP com piso e teto
- [X] T027 [P] [US2] Escrever em `src/core/runtime/__tests__/runtime.test.ts` os blocos "configuração" e "elegibilidade e degradação silenciosa" (inválida em produção silenciosa, sem storage persistente avisa, padrões, API fora do ar, lenta, timeout configurável, chave inválida em dev e produção, 429, `onEvent` que lança)
- [X] T028 [P] [US2] Escrever `src/react/__tests__/hooks.test.tsx` no bloco "degradação silenciosa na árvore" (fora do Provider, configuração inválida, erro de renderização isolado e reportado)

### Implementação de US2

- [X] T029 [US2] Criar `src/core/transport/http.ts` (`HttpClient`, `HttpOutcome`, `parseRetryAfter`, `restoreRateLimit`) (D-06)
- [X] T030 [US2] Ligar `readEligibility`, `onUnauthorized` e `warnMalformed` no runtime, com `keyRejected` e os avisos únicos de rede e de chave (D-10, D-11)
- [X] T031 [US2] Fazer `usePitaco`/`usePitacoSurvey` devolverem ações inertes fora do Provider, com `warnOutsideProvider`, e envolver o `PitacoSurfaceHost` no `PitacoErrorBoundary` em `src/react/PitacoProvider.tsx`

**Checkpoint**: nenhuma falha do Pitaco alcança o app.

---

## Phase 5: User Story 3 — Máquina de estado (Priority: P1)

**Goal**: a pesquisa percorrida por ações de uma função pura.

**Independent Test**: roteiro de ações sobre o schema de referência com relógio falso.

### Testes de US3 (escrever antes) ⚠️

- [X] T032 [P] [US3] Escrever `src/core/survey/__tests__/condition.test.ts` (escolha única, múltipla com "contém", numéricas com `between` inclusivo e números como texto, falha fechado)
- [X] T033 [P] [US3] Escrever `src/core/machine/__tests__/machine.test.ts` nos blocos de apresentação, respostas, navegação e condição, dispensa, conclusão e "submissão da conclusão"
- [X] T034 [P] [US3] Escrever `src/__tests__/support/script.ts` com `ScriptStep`, `runScript`, `createMachineDriver`, `STANDARD_SCRIPT`, `DISMISS_SCRIPT` e `signature`

### Implementação de US3

- [X] T035 [US3] Criar `src/core/survey/answers.ts` (`isAnswered`, `acceptsChoice`, `toWireValue`, `MAX_FREE_TEXT_LENGTH`) e `src/core/survey/condition.ts` (`isConditionSatisfied`) (D-18)
- [X] T036 [US3] Criar `src/core/machine/path.ts` (`resolveStatuses`, `findNextApplicable`)
- [X] T037 [US3] Criar `src/core/machine/machine.ts` com `createInitialState`, `transition` e as treze ações (D-04)
- [X] T038 [US3] Completar `src/core/session/snapshot.ts` com o progresso estimado (`countAhead`), `canGoBack`, `canGoNext` e `isLast`
- [X] T039 [US3] Agendar o debounce de texto por `tick` em `src/core/session/session.ts`

**Checkpoint**: a mesma regra de pesquisa vale para qualquer UI.

---

## Phase 6: User Story 4 — Fila offline (Priority: P2)

**Goal**: entregar uma vez, em ordem, mesmo sem rede.

**Independent Test**: concluir sem rede, recriar o runtime sobre o mesmo storage com rede e conferir uma abertura e um envio.

### Testes de US4 (escrever antes) ⚠️

- [X] T040 [P] [US4] Escrever `src/core/queue/__tests__/queue.test.ts` cobrindo os quinze casos: gravar antes, ordem, dependência da abertura, backoff, sem rede ao reabrir, reenvio sem duplicar, item repetido, idade, tamanho, teto de eventos, lotes de 100, 429 persistido, 404 na abertura levando dependentes, 401, conteúdo corrompido
- [X] T041 [P] [US4] Escrever em `src/core/runtime/__tests__/runtime.test.ts` "resposta sem rede é enviada exatamente uma vez ao reabrir o app", "reenvio sem duplicar" e "AppState: segundo plano… e a volta também"

### Implementação de US4

- [X] T042 [US4] Criar `src/core/queue/queue.ts` (`DeliveryQueue`, `DEFAULT_QUEUE_LIMITS`, `load`, `enqueue*`, `flush`, `drain`, `apply`, `prune`, `discard`, `scheduleWake`) (D-08, D-09)
- [X] T043 [US4] Ligar a fila ao runtime: chave por hash de `baseUrl|apiKey`, `onRejected` como relatório, `flush({ force: true })` no `attach` e na volta do `AppState`

**Checkpoint**: a resposta sobrevive a modo avião e reinício.

---

## Phase 7: User Story 5 — Identidade (Priority: P2)

**Goal**: `deviceId` estável, referência opcional, logout limpo.

**Independent Test**: gerar, persistir, recriar, girar no `reset()`.

### Testes de US5 (escrever antes) ⚠️

- [X] T044 [P] [US5] Escrever `src/core/__tests__/identity.test.ts` (gera e persiste, simultâneas, valor não UUID trocado, `rotate`, storage quebrado)
- [X] T045 [P] [US5] Escrever em `src/core/runtime/__tests__/runtime.test.ts` "reset (logout) dispensa a pesquisa aberta e troca o deviceId"
- [X] T046 [P] [US5] Escrever em `src/core/storage/__tests__/storage.test.ts` os casos dos adaptadores AsyncStorage e MMKV 2/3/4 com instância errada caindo para memória

### Implementação de US5

- [X] T047 [US5] Criar `src/core/identity.ts` (`DeviceIdentity`, `DEVICE_ID_KEY`) (D-16)
- [X] T048 [US5] Implementar `setRespondent`, `setAttributes` e `reset` no runtime (D-17)
- [X] T049 [P] [US5] Criar `src/storage/async-storage.ts` e `src/storage/mmkv.ts` (D-14)

**Checkpoint**: identidade estável e trocável.

---

## Phase 8: User Story 6 — Supressão e relatório de erro (Priority: P3)

**Goal**: o servidor sabe o que o SDK não desenhou e onde ele falhou.

**Independent Test**: pesquisa só com tipos desconhecidos gera supressão; falha interna gera um relatório sem dado de usuário.

### Testes de US6 (escrever antes) ⚠️

- [X] T050 [P] [US6] Escrever `src/core/errors/__tests__/reporter.test.ts` (payload, desligado, contexto sem dado de usuário, nome e não mensagem, dedup e teto, falha silenciosa)
- [X] T051 [P] [US6] Escrever em `src/core/runtime/__tests__/runtime.test.ts` o bloco "perguntas desconhecidas e supressão" e "relatório de erro desligado não manda nada"

### Implementação de US6

- [X] T052 [US6] Criar `src/core/errors/reporter.ts` (`ErrorReporter`, `sanitizeContext`, `describeError`) (D-20)
- [X] T053 [US6] Implementar `suppress` no runtime, enfileirando `SuppressionRequest` e emitindo `placement_suppressed`

**Checkpoint**: as seis histórias funcionam.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T054 [P] Escrever `src/react/__tests__/PitacoProvider.lifecycle.test.tsx` e implementar o `dispose()` do runtime substituído em `src/react/PitacoProvider.tsx` e `src/core/runtime/runtime.ts` (D-12)
- [X] T055 [P] Conferir que `src/core/` não importa `react`, `react-native`, `ui/` nem `react/`
- [X] T056 [P] Conferir que nenhum aviso ou relatório imprime resposta, texto, valor de atributo ou a chave inteira
- [X] T057 Rodar `npm run build` e conferir que `lib/` só importa `react`, `react-native` e `react/jsx-runtime`
- [X] T058 Rodar `npm run verify` — verde, sem teste ignorado

---

## Dependencies & Execution Order

- **Setup (Fase 1)** → **Foundational (Fase 2)** → histórias
- **US1** depende da fundação; **US2** e **US3** podem andar em paralelo com US1 a partir do T018
- **US4** depende de US1 (o runtime grava na fila)
- **US5** depende da fundação e de US1 (o runtime usa a identidade)
- **US6** depende de US1 (oferta/supressão) e da fundação
- **Polish** depois de todas

### Dentro de cada história

Teste primeiro (e falhando) → módulo do core → ligação no runtime → superfície React.

### Parallel Opportunities

- Fase 1: T002–T005 juntos
- Fase 2: T009–T012 e T014 juntos
- US2: T026–T028 juntos; US3: T032–T034 juntos; US5: T044–T046 juntos

---

## Implementation Strategy

### MVP (US1 + US2 + US3)

1. Setup e fundação
2. US3 (máquina) e US1 (runtime e Provider) — o caminho feliz
3. US2 — sem ela o SDK não pode ir para app nenhum
4. **Parar e validar** com o servidor falso

### Entrega incremental

US4 (fila) → US5 (identidade) → US6 (saúde) → Polish.
