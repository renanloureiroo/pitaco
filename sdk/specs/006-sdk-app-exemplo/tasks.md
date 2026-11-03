---
description: "Task list — 006-sdk-app-exemplo"
---

# Tasks: SDK React Native — app de exemplo

**Input**: documentos de desenho em `/specs/006-sdk-app-exemplo/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/example-surface.md`, `contracts/proxy-gateway.md`

**Tests**: `typecheck` e `lint` do exemplo entram no `npm run verify` do SDK; o `expo export` prova o bundle; o roteiro manual do `quickstart.md` prova no aparelho (os fluxos Maestro da primeira grade provaram até ela ser retirada, T038). Defeito do SDK achado no aparelho é corrigido no SDK com teste antes (Princípio VIII).

**Organization**: agrupadas por história de usuário.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizável
- **[Story]**: US1 a US6
- Todo caminho é relativo à raiz do SDK (`sdk/`)

---

## Phase 1: Setup

**Purpose**: o app Expo ligado ao código-fonte do SDK.

- [X] T001 Criar `example/package.json` (Expo 57, Expo Router, React Native 0.86.3, React 19.2.3, `react-dom` 19.2.3, gorhom, Reanimated, Gesture Handler, safe-area-context, screens, AsyncStorage, `expo-clipboard`; `react-native-mmkv` só em desenvolvimento) e `example/app.json` (nome "Pitaco Exemplo", `userInterfaceStyle: automatic`) (D-01)
- [X] T002 Escrever `example/metro.config.js` com `watchFolders`, `resolveRequest` para os quatro pontos de entrada públicos, erro para outro subpath, origem do exemplo para imports de `sdk/src` e `blockList` de `sdk/node_modules` e `sdk/lib` (D-02)
- [X] T003 [P] Escrever `example/tsconfig.json` (estrito, `noUncheckedIndexedAccess`, `paths` só para os quatro pontos de entrada) e `example/eslint.config.js`
- [X] T004 [P] Acrescentar `example:typecheck` e `example:lint` ao `verify` em `package.json`, e ignorar `example/**` em `eslint.config.mjs` e `tsconfig.json` do SDK
- [X] T005 [P] Escrever `example/.env.example` documentado linha a linha com o endereço por alvo, e acrescentar `.env` e `scripts/.seed-state.json` a `example/.gitignore`

---

## Phase 2: Foundational

**Purpose**: Provider único, perfis, cenários e moldura.

**⚠️ CRITICAL**: nenhuma história começa antes desta fase.

- [X] T006 Criar `example/src/pitaco/config.ts` com os perfis `direto` e `proxy` e `defaultProfileId`
- [X] T007 Criar `example/src/pitaco/ExampleContext.tsx` (perfil, storage AsyncStorage, pilha de cenários, contextos de estado e de ações separados) (D-03)
- [X] T008 Criar `example/src/pitaco/PitacoRoot.tsx` com o `<PitacoProvider>` único e `example/src/pitaco/useScenario.ts`
- [X] T009 Criar `example/src/scenarios/registry.ts` (`SCENARIOS`, `scenarioHref`, `scenarioById`) e a moldura `example/src/ui/{palette.ts,ActionButton.tsx,Screen.tsx,Segmented.tsx,JsonBlock.tsx}`
- [X] T010 Criar `example/app/_layout.tsx` (`GestureHandlerRootView` → `ThemeProvider` → `ExampleProvider` → `PitacoRoot` → `BottomSheetModalProvider` → `Stack`), `example/app/(tabs)/_layout.tsx` (abas Cenários e Depuração) e `example/app/(tabs)/cenarios/_layout.tsx`

**Checkpoint**: o app abre no Expo Go com o Provider da raiz.

---

## Phase 3: User Story 1 — Do zero à primeira pesquisa (Priority: P1) 🎯 MVP

**Goal**: seed + Expo Go + um toque.

**Independent Test**: `npm run seed`, `npx expo start`, abrir, tocar em "Abrir pesquisa".

- [X] T011 [US1] Escrever `example/scripts/seed.ts`: flags, aplicação "demo" sem descanso, chave `sdk-example` com estado em `scripts/.seed-state.json`, catálogo de cinco pesquisas, perguntas de "Todos os tipos" e "Próxima feature", aviso de texto livre, disparo com janela e amostragem 1.0, publicação, `.env` preservando linhas e `src/generated/seed-survey.json` pela elegibilidade (D-08)
- [X] T012 [US1] Criar `example/src/pitaco/seed.ts` (`SeedSurvey`, `seedSurveys`, `seedSurvey`) validando o JSON gerado
- [X] T013 [US1] Criar `example/app/index.tsx` redirecionando para `/cenarios/04-comparar` e `example/app/(tabs)/cenarios/index.tsx` com a lista e o aviso de seed ausente
- [X] T014 [US1] Rodar o seed duas vezes contra o backend local e conferir uma aplicação, uma chave ativa e uma pesquisa por nome

---

## Phase 4: User Story 2 — Testar pesquisa × forma × tema (Priority: P1)

**Goal**: a bancada pelo fluxo real.

**Independent Test**: a mesma pesquisa nas três formas, mesma sequência no painel.

- [X] T015 [P] [US2] Criar `example/src/scenarios/04-comparar/themes.ts` (`COMPARE_THEMES`, `THEME_OPTIONS`, `frameColors`, `parseThemeChoice`) e `example/src/scenarios/04-comparar/configs.ts` (constantes por forma e tema) (D-04)
- [X] T016 [P] [US2] Criar `example/src/scenarios/shared/useSurveyArrival.ts`, `example/src/scenarios/shared/useLeavingRef.ts` e `example/src/scenarios/shared/TriggerButton.tsx` (`ResetHint`)
- [X] T017 [US2] Criar `example/src/scenarios/02-gorhom/GorhomSurveySheet.tsx`, `SheetBody.tsx` e `SheetContainer.tsx` (`FullWindowOverlay` no iOS, vias de dispensa, `present` no `onChange`, 92% com foco no texto) (D-11)
- [X] T018 [US2] Criar `example/app/(tabs)/cenarios/04-comparar.tsx` ("Testar Pesquisas": pesquisa, forma, tema, `simulateAppReopen` + `track`, gorhom com `key` por exibição, push da rota Tela)
- [X] T019 [US2] Criar `example/app/(tabs)/cenarios/04-comparar-tela.tsx` com `<PitacoSurveyContent />` e o guarda de saída (D-12)

**Checkpoint**: as três formas abrem a mesma pesquisa pelo backend.

---

## Phase 5: User Story 3 — Painel de depuração (Priority: P1)

**Goal**: eventos, rede e estado ao vivo.

**Independent Test**: abrir uma pesquisa e conferir as três seções.

- [X] T020 [P] [US3] Criar `example/src/debug/logStore.ts` (fora do React, `useSyncExternalStore`, notificação em microtarefa) e `example/src/debug/eventLog.ts` (`publishEvent`, `usePublishEvent`) (D-06)
- [X] T021 [P] [US3] Criar `example/src/debug/networkLog.ts` (`installNetworkLogger`, só `/collect/`, chave truncada, `clone()` da resposta) e `example/src/debug/networkConditions.ts` (D-05)
- [X] T022 [US3] Criar `example/src/debug/panel/{EventsView,EventRow,NetworkView,StateView,DebugActions}.tsx`, com a via da dispensa na linha do evento
- [X] T023 [US3] Criar `example/app/(tabs)/depuracao.tsx` com as seções Eventos, Rede e Fila e identidade
- [X] T024 [US3] Instalar o embrulho do `fetch` antes do Provider em `example/app/_layout.tsx`

---

## Phase 6: User Story 4 — Recomeçar como instalação nova (Priority: P2)

- [X] T025 [US4] Implementar `requestFreshInstall` em `example/src/pitaco/ExampleContext.tsx` (memória → 300 ms → apaga `@pitaco/` → limpa logs → adaptador novo) (D-07)
- [X] T026 [US4] Criar `example/src/ui/FloatingResetButton.tsx` ("Resetar Estado") e montá-lo na raiz
- [X] T027 [US4] Silenciar no LogBox só o aviso de storage em memória e o do Expo Router por deep link (D-14)

---

## Phase 7: User Story 5 — Topologia com gateway (Priority: P2)

- [X] T028 [US5] Escrever `example/proxy/server.mjs` conforme `contracts/proxy-gateway.md` (D-09)
- [X] T029 [US5] Criar `example/src/pitaco/ProfileSelector.tsx` e montá-lo na lista de cenários

---

## Phase 8: User Story 6 — Ciclo sem abrir o app (Priority: P3)

- [X] T030 [US6] Escrever `example/scripts/smoke.ts` (direto e proxy, identidades descartáveis, `--only`, `--check-429`) (D-10)
- [X] T031 [US6] Acrescentar os scripts `seed`, `proxy` e `smoke` a `example/package.json`

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T032 [P] Escrever `example/.maestro/_comum/{abrir-cenario,abrir-link,fechar-app,fechar-tutorial-teclado}.yaml`, os fluxos `example/.maestro/*.yaml`, `example/.maestro/README.md` e os scripts `e2e:ios`/`e2e:android` (D-13)
- [X] T033 [P] Gerar as capturas e os vídeos em `example/docs/capturas/` (pergunta de NPS com 9, três formas, claro e escuro, iOS e Android)
- [X] T034 [P] Seguir o `ThemeProvider` da navegação pelo esquema do sistema em `example/app/_layout.tsx`
- [X] T035 Corrigir no SDK, com teste, os defeitos achados no aparelho: folha no topo (`src/ui/presentation/BottomSheet.tsx`, `src/react/__tests__/SurfaceHost.test.tsx`), `'unspecified'` (`src/ui/theme/useTheme.ts`), runtime substituído (`src/react/PitacoProvider.tsx`, `src/react/__tests__/PitacoProvider.lifecycle.test.tsx`), contêiner preso depois de `inline` (`src/react/SurfaceHost.tsx`), teclado no Android (`src/ui/presentation/keyboard.ts`)
- [X] T036 Enxugar o registro para `04-comparar` e trocar a tela de teste do preview local para o `track` real
- [X] T037 Rodar `npm run verify` no SDK e `npx expo export -p ios` / `-p android` no exemplo — verdes
- [X] T038 Retirar os fluxos `example/.maestro/`, os scripts `e2e:ios`/`e2e:android` e os componentes de cenário sem rota (`example/src/scenarios/05`–`15`, `shared/FlowModes`, `shared/LocalPreview`, `ScenarioPlaceholder`), e atualizar `example/README.md` para a tela "Testar pesquisas"

---

## Dependencies & Execution Order

- **Setup** → **Foundational** → US1 → US2 → US3; US4, US5 e US6 depois de US1
- T035 depende de rodar o exemplo no aparelho (US1–US3)

### Parallel Opportunities

- Setup: T003–T005; US2: T015 e T016; US3: T020 e T021; Polish: T032–T034

---

## Implementation Strategy

### MVP (US1 + US2 + US3)

Ligação com o SDK → Provider na raiz → seed → tela de teste → painel. **Parar e validar** o roteiro
do `quickstart.md` em iOS e Android.

### Entrega incremental

US4 (reset) → US5 (proxy) → US6 (smoke) → Polish (capturas, correções no SDK, limpeza da grade).
