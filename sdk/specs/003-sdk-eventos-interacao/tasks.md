---
description: "Task list — 003-sdk-eventos-interacao"
---

# Tasks: SDK React Native — eventos de interação

**Input**: documentos de desenho em `/specs/003-sdk-eventos-interacao/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/event-catalog.md`, `contracts/collect-events-route.md`

**Tests**: incluídos e **obrigatórios** (Princípio VIII). Os dois testes de contrato são a definição de pronto desta feature.

**Organization**: agrupadas por história de usuário.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável
- **[Story]**: US1 a US5
- Todo caminho é relativo à raiz do SDK (`sdk/`)

---

## Phase 1: Setup

**Purpose**: o contrato do backend disponível no SDK.

- [X] T001 Regenerar `openapi/pitaco.json` e `src/api/openapi.ts` com `npm run gen:api` contra o backend que já publica `InteractionEventType`, `InteractionEvent` e `<Tipo>Data`
- [X] T002 Acrescentar `InteractionEventsReceipt` e `ContractInteractionEventType` a `src/api/types.ts`

---

## Phase 2: Foundational

**Purpose**: o catálogo tipado que todas as histórias usam.

**⚠️ CRITICAL**: nenhuma história começa antes desta fase.

- [X] T003 Criar `src/catalog/events.ts` com `CATALOG_VERSION`, `INTERACTION_EVENT_TYPES`, `QUESTION_EVENT_TYPES`, `InteractionEventDataMap`, `INTERACTION_EVENT_DATA_FIELDS`, `DISMISS_VIAS`, `PRESENTATIONS`, `InteractionEventOf`, `InteractionEvent`/`PitacoEvent` e os detectores `isInteractionEventType`, `isQuestionEventType`, `isInteractionEvent`, `isPresentation`, `isDismissVia` (D-01)
- [X] T004 Exportar o catálogo em `src/index.ts`
- [X] T005 Acrescentar ao estado da máquina em `src/core/machine/machine.ts` os campos de emissão (`seq`, `presentedAt`, `lastElapsedMs`, `visits`, `visitStartedAt`, `visitBackgroundMs`, `backgroundedAt`, `totalBackgroundMs`, `focusedKey`, `pendingEdit`) e o `Run.emit` que monta o envelope (D-02)

**Checkpoint**: catálogo e envelope prontos.

---

## Phase 3: User Story 1 — O servidor recebe o caminho (Priority: P1) 🎯 MVP

**Goal**: eventos na fila, depois da abertura, em lote, sem duplicar.

**Independent Test**: servidor falso recebe, depois da abertura, os mesmos eventos do `onEvent`.

### Testes de US1 (escrever antes) ⚠️

- [X] T006 [P] [US1] Escrever em `src/core/machine/__tests__/machine.test.ts` os blocos de apresentação, respostas, navegação e condição, dispensa e conclusão afirmando cada evento e a ordem (D-03)
- [X] T007 [P] [US1] Escrever em `src/core/queue/__tests__/queue.test.ts` "teto de eventos por exibição", "eventos saem em lotes de até 100" e o reenvio que volta como `duplicated`
- [X] T008 [P] [US1] Escrever em `src/core/runtime/__tests__/runtime.test.ts` "os eventos que o servidor recebe são os mesmos que o onEvent recebe" e "enquanto a pessoa responde, os eventos saem em lote alguns segundos depois"

### Implementação de US1

- [X] T009 [US1] Emitir os 18 tipos em `src/core/machine/machine.ts` (`present`, `select`, `deselect`, `focusText`, `blurText`, `next`, `back`, `complete`, `dismiss`, `enter`, `leave`, `passOver`, `finishCompleted`, `blockIfRequiredMissing`) na ordem fixa
- [X] T010 [US1] Acrescentar `sendEvents` a `src/core/transport/api.ts`
- [X] T011 [US1] Implementar o item `events` em `src/core/queue/queue.ts`: um por exibição, dedup por `seq`, teto de 500, lote de 100 em ordem de `seq`, remoção dos `seq` enviados e continuação na mesma passada (D-07)
- [X] T012 [US1] Em `src/core/runtime/runtime.ts`, enfileirar os eventos de cada transição depois da abertura, agendar o envio de 5 s (`scheduleFlush`, `EVENT_FLUSH_DELAY_MS`) e mandar na hora na abertura e no desfecho (D-06, D-07)

**Checkpoint**: o servidor recebe o caminho de cada exibição.

---

## Phase 4: User Story 2 — O app ouve (Priority: P1)

**Goal**: `onEvent` só leitura, que não derruba nada.

**Independent Test**: `onEvent` recebe o mesmo que o servidor; `onEvent` que lança não quebra.

### Testes de US2 (escrever antes) ⚠️

- [X] T013 [P] [US2] Escrever em `src/core/runtime/__tests__/runtime.test.ts` "erro no onEvent do app não derruba nada"

### Implementação de US2

- [X] T014 [US2] Implementar `deliver()` em `src/core/runtime/runtime.ts` com `try/catch`, aviso único e sem relatório; `setEventListener` para trocar o ouvinte
- [X] T015 [US2] Ligar `onEvent` em `src/react/PitacoProvider.tsx` na criação do runtime e por efeito nas trocas, sem recriar o runtime

---

## Phase 5: User Story 3 — Mesma sequência com qualquer UI (Priority: P1)

**Goal**: prova de que a UI não interfere no tracking.

**Independent Test**: roteiros completo e de dispensa em três UIs contra a máquina.

### Testes de US3 (escrever antes) ⚠️

- [X] T016 [US3] Completar `src/__tests__/support/script.ts` com `STANDARD_SCRIPT` e `DISMISS_SCRIPT` cobrindo os 18 tipos e `signature()` sem os campos de tempo
- [X] T017 [P] [US3] Escrever em `src/core/machine/__tests__/machine.test.ts` o bloco "roteiro completo" (sequência esperada, cobertura dos 18 tipos, envelope válido, assinatura)
- [X] T018 [P] [US3] Escrever em `src/react/__tests__/hooks.test.tsx` o bloco "usePitacoSurvey: roteiro de interação" com os dois roteiros headless sobre o `PitacoProvider` real, inclusive `background`/`foreground`
- [X] T019 [US3] Escrever `src/ui/content/__tests__/sameSequence.contract.test.tsx` com UI padrão, UI com NPS e escolha única substituídos e UI headless sobre `PitacoPreviewProvider`, comparando com a máquina e conferindo que nenhum `fetch` saiu (D-10)

### Implementação de US3

- [X] T020 [US3] Garantir em `src/core/machine/machine.ts` que sair com foco emite `text_blurred` pelo core e que `text_edited` pendente sai antes de qualquer saída (D-04)

**Checkpoint**: a mesma sequência vale para toda UI.

---

## Phase 6: User Story 4 — Catálogo igual dos dois lados (Priority: P2)

**Goal**: divergência com o backend quebra o portão.

**Independent Test**: os dois testes de contrato.

### Testes de US4

- [X] T021 [P] [US4] Escrever `src/catalog/__tests__/catalog.contract.test.ts` (tipos, 18, versão, campos e obrigatórios por `data`, `questionKey`, `discriminator.mapping`, `placement_` fora, checagem de tipo `Same<…>`) (D-08)
- [X] T022 [P] [US4] Escrever `src/catalog/__tests__/contract.test.ts` lendo a enumeração `InteractionEventType.java` do backend (D-09)

---

## Phase 7: User Story 5 — Tempos (Priority: P2)

**Goal**: tempos por visita, monotônicos, descontando o segundo plano.

**Independent Test**: relógio falso, volta, segundo plano no meio da pergunta.

### Testes de US5 (escrever antes) ⚠️

- [X] T023 [P] [US5] Escrever em `src/core/machine/__tests__/machine.test.ts` os blocos "texto livre", "segundo plano" e "back: cada visita tem os próprios tempos"
- [X] T024 [P] [US5] Escrever em `src/core/runtime/__tests__/runtime.test.ts` "AppState: segundo plano vira evento e manda a fila; a volta também"

### Implementação de US5

- [X] T025 [US5] Implementar `background`/`foreground` e o cálculo de `durationMs`, `activeMs` e `survey_completed.activeMs` em `src/core/machine/machine.ts` (D-05)
- [X] T026 [US5] Agendar o `tick` do debounce em `src/core/session/session.ts` e despachar `background`/`foreground` a partir do `AppState` em `src/core/runtime/runtime.ts`

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T027 [P] Conferir que nenhum evento leva rótulo de opção nem texto digitado (só `length`)
- [X] T028 [P] Documentar a ordem por transição no topo de `src/core/machine/machine.ts`
- [X] T029 Rodar `npm run verify` — verde

---

## Dependencies & Execution Order

- **Setup** → **Foundational** → US1 → (US2, US3, US5 em paralelo) ; US4 só depende da Foundational
- US3 depende da UI da `002-sdk-ui-apresentacoes` e do preview da `005-sdk-preview-web` para o teste de mesma sequência

### Parallel Opportunities

- US1: T006–T008; US4: T021 e T022; US5: T023 e T024

---

## Implementation Strategy

### MVP (US1 + US2)

Catálogo → emissão → fila → `onEvent`. **Parar e validar** que o servidor falso recebe o mesmo que o
`onEvent`.

### Entrega incremental

US3 (mesma sequência) → US4 (contrato) → US5 (tempos) → Polish.
