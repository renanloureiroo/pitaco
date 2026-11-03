---
description: "Task list — 005-sdk-preview-web"
---

# Tasks: SDK React Native — pré-visualização sem transporte

**Input**: documentos de desenho em `/specs/005-sdk-preview-web/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/preview-api.md`

**Tests**: incluídos e **obrigatórios** (Princípio VIII), sempre com `fetch` espião.

**Organization**: agrupadas por história de usuário.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável
- **[Story]**: US1 a US5
- Todo caminho é relativo à raiz do SDK (`sdk/`)

---

## Phase 1: Setup

- [X] T001 Declarar o subpath `./preview` em `package.json#exports` (module, commonjs e tipos)

---

## Phase 2: Foundational

**⚠️ CRITICAL**: nenhuma história começa antes desta fase.

- [X] T002 [P] Escrever `src/preview/__tests__/preview.test.tsx` (mesma máquina sem rede, `restart`, schema sem pergunta, hook dentro do Provider de preview)
- [X] T003 Criar `src/preview/controller.ts` com `createPreviewController` (padrões de rascunho, `SurveySession`, `restart`, `dispose`, `setEventListener`) e `PitacoPreviewProvider` (contexto com `runtime: null`) (D-01, D-02)
- [X] T004 Criar `src/preview/index.ts` como barril de `controller.ts`, `PitacoPreview.tsx` e `usePitacoSurvey`

---

## Phase 3: User Story 1 — Ver a pesquisa (Priority: P1) 🎯 MVP

**Independent Test**: `<PitacoPreview />` percorrido até o agradecimento sem rede.

### Testes de US1 (escrever antes) ⚠️

- [X] T005 [US1] Escrever em `src/preview/__tests__/PitacoPreview.test.tsx` "desenha a UI padrão inline", "percorre a pesquisa e conclui" e "schema sem pergunta renderizável não quebra"

### Implementação de US1

- [X] T006 [US1] Criar `src/preview/PitacoPreview.tsx` com `presentation` padrão `'inline'` desenhando `<PitacoSurveyContent />`
- [X] T007 [US1] Exportar `PitacoPreview` e `PitacoPreviewProps` também em `src/index.ts`

---

## Phase 4: User Story 2 — Ver o que vai ser medido (Priority: P1)

**Independent Test**: `onEvent` recebe `survey_presented` primeiro e os mesmos eventos da máquina.

### Testes de US2 (escrever antes) ⚠️

- [X] T008 [US2] Cobrir em `src/ui/content/__tests__/PitacoSurveyContent.test.tsx` e em `src/preview/__tests__/PitacoPreview.test.tsx` que `survey_presented` chega ao `onEvent` (como primeiro evento, com `presentation: 'inline'`) quando o conteúdo apresenta no efeito de montagem

### Implementação de US2

- [X] T009 [US2] Passar `onEvent` na criação do controlador em `PitacoPreviewProvider` e acompanhar trocas por `setEventListener` (D-03)

---

## Phase 5: User Story 3 — Mesma customização (Priority: P2)

**Independent Test**: tema, textos, renderers, slots, `onRenderError`, `resetKey`.

### Testes de US3 (escrever antes) ⚠️

- [X] T010 [P] [US3] Escrever em `src/preview/__tests__/PitacoPreview.test.tsx` "aceita tema, textos, renderers e slots substituídos" e "resetKey reinicia a pré-visualização do zero"

### Implementação de US3

- [X] T011 [US3] Acrescentar `theme`, `strings`, `renderers`, `slots`, `onRenderError` e `triggerEvent` a `PitacoPreviewProviderProps`, com `reportRenderError` repassando a `onRenderError` (D-06)
- [X] T012 [US3] Repassar as props de customização, `onFinish`, `thankYouDurationMs` e `resetKey` (como `key`) em `src/preview/PitacoPreview.tsx` (D-04)

---

## Phase 6: User Story 4 — Contêiner do SDK sem servidor (Priority: P2)

**Independent Test**: folha e modal do SDK no preview, sem `fetch`.

### Testes de US4 (escrever antes) ⚠️

- [X] T013 [US4] Escrever `src/preview/__tests__/PitacoPreview.container.test.tsx` (quatro casos: abertura do sheet, fundo, conclusão, modal)

### Implementação de US4

- [X] T014 [US4] Acrescentar as props opcionais `onFinish` e `thankYouDurationMs` a `PitacoSurfaceHost` em `src/react/SurfaceHost.tsx`, chamando `onFinish` só depois da saída (D-05)
- [X] T015 [US4] Desenhar `PitacoSurfaceHost` dentro de `PitacoErrorBoundary` em `src/preview/PitacoPreview.tsx` quando `presentation` for `bottom-sheet` ou `modal`

---

## Phase 7: User Story 5 — UI própria sobre o schema (Priority: P3)

**Independent Test**: controlador cru + hook + `restart`.

- [X] T016 [US5] Garantir que o teste de mesma sequência (`src/ui/content/__tests__/sameSequence.contract.test.tsx`) roda a UI headless sobre `PitacoPreviewProvider` sem `fetch`

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T017 [P] Mover o foco para o elemento do DOM do título em `Platform.OS === 'web'` em `src/ui/content/PitacoSurveyContent.tsx` (D-07)
- [X] T018 [P] Conferir que nada importado por `src/preview/` usa transporte (`core/transport/*`) nem storage persistente (`storage/*`)
- [X] T019 Rodar `npm run verify` — verde

---

## Dependencies & Execution Order

- **Setup** → **Foundational** → US1 → US2; US3 e US4 depois de US1; US5 depois da Foundational
- Depende de `002-sdk-ui-apresentacoes` (conteúdo e host) e de `001-sdk-core` (máquina e sessão)

---

## Implementation Strategy

MVP = controlador + `<PitacoPreview />` inline com `onEvent` (US1 + US2). Depois customização,
contêiner e foco no web.
