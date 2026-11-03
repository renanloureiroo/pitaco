# Data Model: SDK React Native — pré-visualização sem transporte

**Feature**: `005-sdk-preview-web` · **Date**: 2026-09-14

Nada é persistido.

---

## 1. Opções do controlador — `PreviewOptions`

| Campo | Tipo | Padrão |
| --- | --- | --- |
| `schema` | `unknown` (formato `DeliverableSurvey`) | — |
| `presentation` | `Presentation` | `'bottom-sheet'` no `present()` sem argumento |
| `triggerEvent` | `string` | `'preview'` |
| `onEvent` | `(event: InteractionEvent) => void` | — |
| `clock`, `timers`, `uuid` | injetáveis | sistema |

## 2. Controlador — `PreviewController`

| Membro | O que faz |
| --- | --- |
| `survey` | o schema normalizado, ou `null` sem pergunta renderizável |
| `getSnapshot`, `subscribe` | para `useSyncExternalStore` |
| `present(presentation?)` | apresenta (emite `survey_presented`) |
| `dispatch(action)` | ações da UI |
| `restart()` | descarta a sessão e começa outra, com `displayId` novo |
| `setEventListener(listener)` | troca o `onEvent` |
| `dispose()` | encerra a sessão e os ouvintes |

## 3. Padrões de rascunho

Aplicados só quando ausentes: `surveyId: 'preview'`, `versionId: 'preview'`, `versionNumber: 0`.

## 4. Contexto entregue — `PitacoContextValue`

| Campo | Valor no preview |
| --- | --- |
| `runtime` | `null` |
| `controller` | o `PreviewController` |
| `reportRenderError` | repassa a `onRenderError` |
| `ui.presentation` | a prop, ou `'bottom-sheet'` |
| `ui.theme`, `ui.strings`, `ui.renderers`, `ui.slots` | as props |

`usePitaco()` dentro do preview devolve as ações inertes (não há runtime).

## 5. Recriação

| Muda | Efeito |
| --- | --- |
| `schema`, `presentation`, `triggerEvent` | controlador recriado |
| `onEvent` | `setEventListener`, sem recriar |
| `resetKey` | `PitacoPreviewProvider` remontado, controlador novo |
| `theme`, `strings`, `renderers`, `slots`, `onRenderError` | só o contexto muda |
