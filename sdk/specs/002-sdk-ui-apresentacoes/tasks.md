---
description: "Task list — 002-sdk-ui-apresentacoes"
---

# Tasks: SDK React Native — UI padrão e apresentações

**Input**: documentos de desenho em `/specs/002-sdk-ui-apresentacoes/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/ui-public-api.md`, `contracts/presentation-behavior.md`

**Tests**: incluídos e **obrigatórios** (Princípio VIII). Teste de componente pelo comportamento observável antes do componente.

**Organization**: agrupadas por história de usuário.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável
- **[Story]**: US1 a US5
- Todo caminho é relativo à raiz do SDK (`sdk/`)

---

## Phase 1: Setup

**Purpose**: o contrato de customização e o lugar dele no contexto.

- [X] T001 Criar `src/ui/types.ts` com `QuestionRendererProps`, `QuestionRendererActions`, `PitacoQuestionRenderer`, `PitacoRendererMap`, `rendererKeyForQuestionType`, os cinco slots e `PitacoSlotMap`, `EdgeInsets` e `PitacoUiConfig` (D-01)
- [X] T002 Acrescentar `ui: PitacoUiConfig` a `src/react/context.ts` e o segundo parâmetro opcional de contexto técnico em `reportRenderError`
- [X] T003 Tipar `theme`, `strings`, `renderers`, `slots` e acrescentar `insets`/`getInsets` em `src/react/PitacoProvider.tsx`, montando `ui` com `presentation` sempre resolvido

---

## Phase 2: Foundational

**Purpose**: tema, textos, primitivos e isolamento, usados por todas as histórias.

**⚠️ CRITICAL**: nenhuma história começa antes desta fase.

- [X] T004 [P] Escrever `src/ui/theme/__tests__/theme.test.ts` (esquema pelo sistema e forçado, merge parcial, token inválido com e sem `__DEV__`, contraste e `'unspecified'`)
- [X] T005 [P] Escrever `src/ui/strings/__tests__/strings.test.ts` (padrões em pt-BR, merge parcial, sem substituto)
- [X] T006 Criar `src/ui/theme/tokens.ts`, `src/ui/theme/validate.ts`, `src/ui/theme/contrast.ts`, `src/ui/theme/useTheme.ts` e `src/ui/theme/index.ts` (D-02, D-03)
- [X] T007 Criar `src/ui/strings/strings.ts`, `src/ui/strings/useStrings.ts` e `src/ui/strings/index.ts` (D-04)
- [X] T008 [P] Escrever `src/ui/primitives/__tests__/primitives.test.tsx` (papel e rótulo, rótulo explícito, desabilitado, `hitSlop` e altura mínima de 44)
- [X] T009 Criar `src/ui/primitives/PitacoText.tsx` (com `DEFAULT_MAX_FONT_SIZE_MULTIPLIER`), `PitacoButton.tsx` (`MIN_TOUCH_TARGET`, `hitSlopFor`), `PitacoChip.tsx` e `src/ui/primitives/index.ts`
- [X] T010 [P] Escrever `src/ui/registry/__tests__/SafeCustom.test.tsx` (sem substituição, substituição que funciona, que lança, slots)
- [X] T011 Criar `src/ui/registry/SafeCustom.tsx` como elemento JSX sobre `PitacoErrorBoundary`, com aviso deduplicado e `reportRenderError` (D-05)

**Checkpoint**: aparência resolvida e isolamento prontos.

---

## Phase 3: User Story 1 — A pesquisa completa no sheet do SDK (Priority: P1) 🎯 MVP

**Goal**: os seis tipos, moldura e agradecimento.

**Independent Test**: Provider + `track` → folha → seis tipos → "Enviar" → "Obrigado!".

### Testes de US1 (escrever antes) ⚠️

- [X] T012 [P] [US1] Criar o suporte `src/ui/questions/__tests__/support.tsx` (`rendererProps`, `renderQuestion` com contexto do esquema)
- [X] T013 [P] [US1] Escrever `src/ui/questions/__tests__/SingleChoiceQuestion.test.tsx` e `MultipleChoiceQuestion.test.tsx`
- [X] T014 [P] [US1] Escrever `src/ui/questions/__tests__/RatingQuestion.test.tsx`, `ScaleQuestion.test.tsx` e `NpsQuestion.test.tsx`
- [X] T015 [P] [US1] Escrever `src/ui/questions/__tests__/FreeTextQuestion.test.tsx` (ações, sem canal lateral, contador, `maxLength`, aviso anunciado, tema escuro, erro)
- [X] T016 [P] [US1] Escrever `src/ui/content/__tests__/PitacoSurveyContent.test.tsx` (`present()` ao montar, percurso até o agradecimento, fechar com `close_button`, desmonte em andamento, sem dispensa dupla, renderers/slots locais)

### Implementação de US1

- [X] T017 [P] [US1] Criar `src/ui/primitives/PitacoScalePoint.tsx` (D-16)
- [X] T018 [P] [US1] Criar `src/ui/questions/shared/QuestionErrorFrame.tsx`, `src/ui/questions/shared/NumericScale.tsx` e `src/ui/questions/shared/nps.ts`
- [X] T019 [US1] Criar `src/ui/questions/SingleChoiceQuestion.tsx`, `MultipleChoiceQuestion.tsx`, `RatingQuestion.tsx`, `ScaleQuestion.tsx`, `NpsQuestion.tsx`, `FreeTextQuestion.tsx` e `src/ui/questions/index.ts` (`DEFAULT_RENDERERS`) (D-15, D-16)
- [X] T020 [US1] Criar os slots padrão `src/ui/content/slots/DefaultHeader.tsx`, `DefaultProgress.tsx`, `DefaultFooter.tsx`, `DefaultCloseButton.tsx`, `DefaultThankYou.tsx` e `src/ui/content/slots/index.ts` (D-14)
- [X] T021 [US1] Criar `src/ui/content/PitacoSurveyContent.tsx` com título fora do renderizador, mensagem de validação, `presentDeferred`, `dismissVia`, `thankYouDurationMs` e refs atualizadas em efeito (D-12)
- [X] T022 [US1] Criar `src/ui/content/index.ts` e `src/ui/index.ts`, e exportar a UI em `src/index.ts`

**Checkpoint**: a pesquisa completa aparece com a UI padrão.

---

## Phase 4: User Story 2 — Apresentações (Priority: P1)

**Goal**: sheet, modal e inline, com as vias certas.

**Independent Test**: cada apresentação montada pelo host, cada forma de fechar com a via certa.

### Testes de US2 (escrever antes) ⚠️

- [X] T023 [P] [US2] Escrever `src/ui/presentation/__tests__/gesture.test.ts` (progresso, resistência, limiares, handlers, gesto interrompido, deslocamento mínimo)
- [X] T024 [P] [US2] Escrever `src/ui/presentation/__tests__/insets.test.ts` e `src/ui/presentation/__tests__/useReduceMotion.test.tsx`
- [X] T025 [P] [US2] Escrever `src/react/__tests__/SurfaceHost.test.tsx` (sheet: abertura, `close_button`, `backdrop`, `hardware_back`, `programmatic`, movimento reduzido, insets, ancoragem; modal: abertura, insets, `hardware_back`; inline: nada, sem contêiner preso, abre ao sair de inline; contenção de falha)
- [X] T026 [P] [US2] Escrever `src/ui/presentation/__tests__/keyboard.test.ts`

### Implementação de US2

- [X] T027 [US2] Criar `src/ui/presentation/gesture.ts` (D-08), `src/ui/presentation/insets.ts` (D-10), `src/ui/presentation/useReduceMotion.ts` (D-09) e `src/ui/presentation/keyboard.ts` (D-11)
- [X] T028 [US2] Criar `src/ui/presentation/BottomSheet.tsx` com `Modal` transparente, `Animated`, `PanResponder` na alça, fundo tocável, `BackHandler`, `KeyboardAvoidingView` com `flex: 1` e teto de 90% (D-07)
- [X] T029 [US2] Criar `src/ui/presentation/FullScreenModal.tsx` e `src/ui/presentation/index.ts`
- [X] T030 [US2] Reescrever `src/react/SurfaceHost.tsx` como `PitacoSurfaceHost` com `open`, `trackedAvailable` e `finishReason` derivados no render, `present()` no `onOpened` e reset em `inline` (D-06)

**Checkpoint**: as três apresentações funcionam com as seis vias.

---

## Phase 5: User Story 3 — A cara do app (Priority: P2)

**Goal**: tema, textos e slots pelo componente de verdade.

**Independent Test**: tema escuro forçado, rótulo trocado e cada slot substituído desenhados.

### Testes de US3 (escrever antes) ⚠️

- [X] T031 [US3] Escrever em `src/ui/content/__tests__/customization.levels.test.tsx` os blocos "tema, através do componente de verdade", "textos substituídos" e "cada slot substituído"

### Implementação de US3

- [X] T032 [US3] Fazer `<PitacoSurveyContent />` mesclar `renderers` e `slots` locais sobre os do Provider e passar tema, textos e ações ligadas a cada slot

---

## Phase 6: User Story 4 — Renderizador por tipo (Priority: P2)

**Goal**: substituir um tipo com rede de segurança.

**Independent Test**: NPS próprio desenhado; renderizador que lança cai no padrão e reporta.

### Testes de US4 (escrever antes) ⚠️

- [X] T033 [US4] Escrever em `src/ui/content/__tests__/customization.levels.test.tsx` os blocos "renderizador por tipo substituído", "renderizador/slot que lança erro" e "headless total"

### Implementação de US4

- [X] T034 [US4] Remontar o boundary do renderizador por `question.key` em `src/ui/content/PitacoSurveyContent.tsx` (`resetKey`) e ligar `reportRenderError` com `{ kind, name }`

---

## Phase 7: User Story 5 — Acessível sem configuração (Priority: P2)

**Goal**: foco, anúncios, alvos e escala.

**Independent Test**: foco no título a cada navegação, alça de 44 pt, anúncio do agradecimento, teto de fonte.

### Testes de US5 (escrever antes) ⚠️

- [X] T035 [P] [US5] Escrever `src/ui/content/__tests__/a11y.focus.test.tsx` (`moveAccessibilityFocus` isolado; título como alvo sozinho, no sheet e no modal)
- [X] T036 [P] [US5] Escrever `src/ui/content/__tests__/a11y.fontScale.test.tsx`
- [X] T037 [P] [US5] Escrever `src/ui/content/slots/__tests__/DefaultThankYou.test.tsx` e `src/react/__tests__/SurfaceHost.a11y.test.tsx`

### Implementação de US5

- [X] T038 [US5] Criar `src/ui/content/focus.ts` e usar em `<PitacoSurveyContent />`, com foco no elemento do DOM em `Platform.OS === 'web'` (D-13)
- [X] T039 [US5] Aplicar `maxFontSizeMultiplier` no título e no `TextInput` do texto livre
- [X] T040 [US5] Anunciar título e corpo em `src/ui/content/slots/DefaultThankYou.tsx` e dar `minHeight: 44` à área da alça em `src/ui/presentation/BottomSheet.tsx`

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T041 [P] Aceitar `'unspecified'` de `useColorScheme()` em `src/ui/theme/useTheme.ts` (`SystemColorScheme`), com teste em `src/ui/theme/__tests__/theme.test.ts`
- [X] T042 [P] Conferir que `src/ui/presentation/` não é exportada por `src/index.ts`
- [X] T043 [P] Conferir no build que os nomes dos pacotes nativos só aparecem em comentário em `lib/`
- [X] T044 Rodar `npm run verify` — verde

---

## Dependencies & Execution Order

- **Setup** → **Foundational** → US1 → US2; US3, US4 e US5 dependem de US1 e podem andar em paralelo
- US2 depende de US1 (o host monta `<PitacoSurveyContent />`)
- **Polish** depois das histórias

### Parallel Opportunities

- Fase 2: T004, T005, T008, T010
- US1: T012–T016 e T017–T018
- US2: T023–T026
- US5: T035–T037

---

## Implementation Strategy

### MVP (US1 + US2)

Contrato → tema e textos → seis renderizadores → conteúdo → sheet e host. **Parar e validar** no
aparelho com a forma "Sheet do SDK".

### Entrega incremental

US3 (aparência) → US4 (renderizador próprio) → US5 (acessibilidade) → Polish.
