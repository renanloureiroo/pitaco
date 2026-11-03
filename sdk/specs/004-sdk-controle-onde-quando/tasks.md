---
description: "Task list — 004-sdk-controle-onde-quando"
---

# Tasks: SDK React Native — controle de onde e quando a pesquisa aparece

**Input**: documentos de desenho em `/specs/004-sdk-controle-onde-quando/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/placement-api.md`

**Tests**: incluídos e **obrigatórios** (Princípio VIII), com timers falsos.

**Organization**: agrupadas por história de usuário.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável
- **[Story]**: US1 a US4
- Todo caminho é relativo à raiz do SDK (`sdk/`)

---

## Phase 1: Setup

- [X] T001 Acrescentar `DEFAULT_DEFER_TIMEOUT_MS`, `deferTimeoutMs` em `ConfigInput`/`ResolvedConfig` (1 s–1 h), `LIMITS.blockReason`, `DEFAULT_BLOCK_REASON` e `sanitizeBlockReason` a `src/core/config.ts`
- [X] T002 Acrescentar a prop `deferTimeoutMs` a `src/react/PitacoProvider.tsx`, na identidade de recriação do runtime

---

## Phase 2: Foundational

**⚠️ CRITICAL**: nenhuma história começa antes desta fase.

- [X] T003 Redesenhar `src/catalog/placement.ts` com os nove tipos, `PlacementEventDataMap`, envelope com `surveyId`/`versionId`/`triggerEvent` anuláveis, `PLACEMENT_EVENT_TYPES` e `isPlacementEvent` (D-08)
- [X] T004 Implementar `emitPlacement(type, candidate, data, triggerEvent?)` em `src/core/runtime/runtime.ts`, entregando só ao `onEvent`
- [X] T005 Exportar o catálogo `placement_` em `src/index.ts`

---

## Phase 3: User Story 1 — Uma pesquisa por sessão (Priority: P1) 🎯 MVP

**Independent Test**: exibir, dispensar, `track` de novo sem consulta; `simulateAppReopen` libera.

### Testes de US1 (escrever antes) ⚠️

- [X] T006 [US1] Escrever em `src/core/runtime/__tests__/runtime.test.ts` o bloco "sessão de app: no máximo uma pesquisa exibida" (três casos)

### Implementação de US1

- [X] T007 [US1] Acrescentar `sessionSurveyShown` ao runtime, ligado em `onTransition` no `survey_presented`, e checado em `track()` antes de `boot()` com `placement_session_limited` (D-01, D-02)
- [X] T008 [US1] Implementar `simulateAppReopen()` no runtime e expor em `src/react/usePitaco.ts`

---

## Phase 4: User Story 2 — Bloqueio (Priority: P1)

**Independent Test**: `<PitacoBlock />` retém; desmontar exibe; prazo descarta.

### Testes de US2 (escrever antes) ⚠️

- [X] T009 [P] [US2] Escrever em `src/core/runtime/__tests__/runtime.test.ts` o bloco "bloqueio: block/unblock" (seis casos)
- [X] T010 [P] [US2] Escrever `src/react/__tests__/PitacoBlock.test.tsx` (bloqueia enquanto montado; dois com o mesmo motivo)

### Implementação de US2

- [X] T011 [US2] Implementar `block`/`unblock` com `blockReasons` por contagem de referências e `placement_blocked` na transição para bloqueado (D-03)
- [X] T012 [US2] Implementar o portão padrão no construtor do runtime (`considerPlacement`, `holdCandidate`, `scheduleHoldTimeout`, `expireHeld`, `tryReleaseHeld`) com a causa fixada na retenção (D-04 a D-07)
- [X] T013 [US2] Criar `src/react/PitacoBlock.tsx` sobre `usePitaco()` e exportar em `src/index.ts`
- [X] T014 [US2] Acrescentar `block`/`unblock` a `PitacoActions` em `src/react/usePitaco.ts`, inertes fora do Provider

---

## Phase 5: User Story 3 — Adiamento (Priority: P2)

**Independent Test**: `defer` → `track` → retida; `release` → `placement_released` e exibição.

### Testes de US3 (escrever antes) ⚠️

- [X] T015 [US3] Escrever em `src/core/runtime/__tests__/runtime.test.ts` o bloco "adiamento: defer/release" (quatro casos, inclusive `defer` + `block`)

### Implementação de US3

- [X] T016 [US3] Implementar `defer`/`release` com `deferRequested`, `placement_deferred`, `placement_released {heldMs}` e `placement_expired {heldMs}` (D-05, D-06)
- [X] T017 [US3] Acrescentar `defer`/`release` a `PitacoActions` em `src/react/usePitaco.ts`

---

## Phase 6: User Story 4 — O app enxerga as decisões (Priority: P3)

**Independent Test**: `placement_` no `onEvent` e nunca na fila; `diagnostics()` coerente.

### Testes de US4 (escrever antes) ⚠️

- [X] T018 [P] [US4] Escrever em `src/core/runtime/__tests__/runtime.test.ts` "nunca entram na fila local nem são enviados ao servidor" e o bloco de `reset` (retida descartada; limite continua) (D-10)
- [X] T019 [P] [US4] Escrever em `src/react/__tests__/hooks.test.tsx` o bloco "usePitaco: diagnóstico" e a checagem de que `block`/`unblock`/`defer`/`release` fora do Provider não lançam
- [X] T020 [P] [US4] Escrever `src/react/__tests__/PitacoProvider.lifecycle.test.tsx` (runtime substituído não emite `placement_expired`; fila sai uma vez pelo novo)

### Implementação de US4

- [X] T021 [US4] Estender `diagnostics()` com `sessionSurveyShown`, `blockedReasons`, `deferred` e `held`
- [X] T022 [US4] Ajustar `reset()` e `setPlacementGate()` para descartar a retida sem exibição
- [X] T023 [US4] Implementar `dispose()` no runtime (`disposed`, temporizadores, `AppState`, fila, sessão, ouvintes) e chamá-lo no runtime substituído em `src/react/PitacoProvider.tsx` (D-11)

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T024 [P] Atualizar o comentário de `src/core/runtime/placement.ts`: o portão padrão é o do runtime; `immediatePlacement` continua exportado
- [X] T025 [P] Ajustar o teste pré-existente de `reset` em `runtime.test.ts` para chamar `simulateAppReopen()` antes da segunda pesquisa
- [X] T026 Rodar `npm run verify` — verde

---

## Dependencies & Execution Order

- **Setup** → **Foundational** → US1 → US2 → US3 → US4
- US3 depende do portão de US2 (mesma retenção)

### Parallel Opportunities

- US2: T009 e T010; US4: T018–T020

---

## Implementation Strategy

### MVP (US1 + US2)

Limite de sessão e bloqueio cobrem os dois pedidos mais concretos. **Parar e validar** com
`PitacoBlock.test.tsx` e o bloco de sessão.

### Entrega incremental

US3 (adiamento) → US4 (observabilidade e encerramento do runtime) → Polish.
