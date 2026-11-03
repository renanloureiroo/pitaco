# Implementation Plan: SDK React Native — app de exemplo

**Branch**: `006-sdk-app-exemplo` | **Date**: 2026-09-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-sdk-app-exemplo/spec.md`

## Summary

Criar `sdk/example/`, um app Expo SDK 57 com Expo Router que consome o código-fonte do SDK pelo
Metro, monta um `<PitacoProvider>` único na raiz e oferece: a tela "Testar pesquisas" (pesquisa ×
forma × tema pelo fluxo real), uma aba de depuração ao vivo, o reset de instalação, perfis direto e
proxy, e os scripts de seed, proxy e smoke. As capturas ficam versionadas ao lado.

Quatro movimentos:

1. **Ligação com o código-fonte** (D-01, D-02): `metro.config.js` com `watchFolders`,
   `resolveRequest` para os quatro pontos de entrada públicos e `blockList` para `sdk/node_modules`
   e `sdk/lib`; `tsconfig.json` com `paths` só para os mesmos quatro.
2. **Provider único com sobreposição por tela** (D-03): `ExampleContext` guarda perfil, storage e a
   pilha de cenários montados; `PitacoRoot` aplica a configuração do último; `useScenario` empilha
   e desempilha.
3. **Diagnóstico sem gancho no SDK** (D-05, D-06): eventos pelo `onEvent`, rede pelo embrulho do
   `fetch` global, estado por `usePitaco().diagnostics()`; logs fora do React com
   `useSyncExternalStore`.
4. **Ambiente reproduzível** (D-08 a D-10): seed idempotente com catálogo, proxy transparente em
   Node puro e smoke dos dois caminhos.

## Technical Context

**Language/Version**: TypeScript ~6.0 estrito (`noUncheckedIndexedAccess`) no app; scripts `.ts`
executados pelo Node 22.18+ com remoção de tipos nativa

**Primary Dependencies**: Expo ~57.0.22, Expo Router ~57.0.21, React 19.2.3, React Native 0.86.3,
`@gorhom/bottom-sheet` ^5.2.14, `react-native-reanimated` 4.5.1, `react-native-gesture-handler`
~2.32, `react-native-safe-area-context` ~5.7, `react-native-screens` ~4.26,
`@react-native-async-storage/async-storage` 2.2.0, `expo-clipboard`; `react-native-mmkv` ^4.3.2
só em desenvolvimento

**Storage**: AsyncStorage pelo adaptador do SDK; `scripts/.seed-state.json` para o seed

**Testing**: `tsc --noEmit` e `eslint .` no `verify` do SDK; `npx expo export -p ios|android` na
integração contínua; roteiro manual em aparelho

**Target Platform**: Expo Go em simulador iOS, emulador Android e aparelho físico

**Project Type**: app mobile de exemplo (privado)

**Performance Goals**: N/A

**Constraints**: nenhum build nativo; nenhuma API nova no SDK só para o exemplo; chave só no `.env`
(git-ignorado)

**Scale/Scope**: 1 cenário registrado (mais a rota "Tela"), 1 aba de depuração com 3 seções,
2 perfis, 3 scripts Node, 18 arquivos de captura

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Zero Módulo Nativo** | As bibliotecas nativas são dependências do exemplo; o exemplo roda no Expo Go consumindo `sdk/src`, e o `expo export` prova o bundle. Um cenário que exigisse build nativo apontaria defeito no SDK. | ✅ |
| **II. Degradação Silenciosa** | Sem `.env`, o Provider fica desligado e o app segue; avisos esperados do SDK ficam no console. | ✅ |
| **III. O Servidor Decide** | A tela de teste zera só o limite de sessão do cliente; reexibição depende do servidor (seed reexibível, identidade nova). | ✅ |
| **IV. Catálogo Fixo** | O painel só lê o `onEvent`. | ✅ |
| **V. Sem Dado Pessoal** | Log de rede trunca a chave; nenhum dado vai para fora do aparelho além do Pitaco local. | ✅ |
| **VI. Três Camadas** | As formas Gorhom e Tela usam só a API pública (`inline`, `<PitacoSurveyContent />`, `dismiss(via)`), provando que ela basta. | ✅ |
| **VII. Acessível** | O exemplo usa a UI padrão do SDK nas três formas. | ✅ |
| **VIII. Teste É Contrato** | `typecheck` e `lint` do exemplo no portão do SDK; roteiro manual no `quickstart.md`. | ✅ |

**Gate de Phase 0**: aprovado.

### Re-avaliação após Phase 1 (design)

- **Defeitos do SDK achados no aparelho foram corrigidos no SDK, com teste**: folha no topo da tela
  (`KeyboardAvoidingView` sem `flex: 1`), `'unspecified'` do `useColorScheme()` no RN 0.86,
  runtime substituído sem `dispose()`, contêiner vazio preso depois de `inline`, teclado cobrindo a
  folha no Android. Nenhum foi contornado só no exemplo.
- **A grade de cenários foi enxugada** para a tela de teste; o que os testes do SDK provam não ganha
  tela. Os fluxos Maestro e os componentes de cenário da primeira grade foram retirados com ela
  (ver Complexity Tracking).

Veredito final: **aprovado**.

## Project Structure

### Documentation (this feature)

```text
specs/006-sdk-app-exemplo/
├── plan.md
├── research.md          # D-01 a D-14
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── example-surface.md    # .env, scripts, rotas, testIDs, perfis
│   └── proxy-gateway.md      # o proxy local
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (raiz do SDK)

```text
example/
├── package.json  app.json  tsconfig.json  eslint.config.js  metro.config.js
├── .env.example  .gitignore
├── app/
│   ├── _layout.tsx                    GestureHandlerRootView → ThemeProvider → ExampleProvider
│   │                                  → PitacoRoot → BottomSheetModalProvider → Stack + FloatingResetButton
│   ├── index.tsx                      redireciona para /cenarios/04-comparar
│   └── (tabs)/
│       ├── _layout.tsx                abas Cenários e Depuração
│       ├── depuracao.tsx              painel de depuração
│       └── cenarios/
│           ├── _layout.tsx            pilha com títulos do registro
│           ├── index.tsx              lista + seletor de perfil
│           ├── 04-comparar.tsx        "Testar pesquisas"
│           └── 04-comparar-tela.tsx   forma "Tela"
├── src/
│   ├── pitaco/{ExampleContext.tsx, PitacoRoot.tsx, useScenario.ts, ProfileSelector.tsx, config.ts, seed.ts}
│   ├── debug/{logStore.ts, eventLog.ts, networkLog.ts, networkConditions.ts}
│   ├── debug/panel/{EventsView, EventRow, NetworkView, StateView, DebugActions}.tsx
│   ├── scenarios/registry.ts
│   ├── scenarios/02-gorhom/{GorhomSurveySheet, SheetBody, SheetContainer}.tsx
│   ├── scenarios/04-comparar/{configs.ts, themes.ts}
│   ├── scenarios/shared/{useSurveyArrival.ts, useLeavingRef.ts, TriggerButton.tsx}
│   ├── ui/{ActionButton, Screen, Segmented, JsonBlock, FloatingResetButton}.tsx, palette.ts
│   └── generated/seed-survey.json     escrito pelo seed
├── scripts/{seed.ts, smoke.ts}
├── proxy/server.mjs
└── docs/capturas/                     12 PNG + 6 MP4

package.json (SDK)                     example:typecheck e example:lint no verify
eslint.config.mjs, tsconfig.json (SDK) example/** ignorado
../.github/workflows/sdk.yml           verify + expo export do exemplo
```

**Structure Decision**: o exemplo vive dentro de `sdk/`, com `node_modules` próprio; o SDK ignora
`example/**` no lint e nos tipos e o exemplo roda os próprios, chamados pelo `verify` do SDK.

## Complexity Tracking

| Risco | Por que existe | Plano |
| --- | --- | --- |
| Sem automação de aparelho para "Testar pesquisas" | Os fluxos Maestro foram escritos para a primeira grade, de quinze telas, e retirados quando ela foi enxugada (T038) | Escrever um fluxo novo para a tela atual quando houver CI com simulador; até lá, o roteiro manual do `quickstart.md` e os testes do SDK cobrem o comportamento |
