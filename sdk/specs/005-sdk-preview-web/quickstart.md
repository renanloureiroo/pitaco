# Quickstart — validar `005-sdk-preview-web`

Superfície em [`contracts/preview-api.md`](./contracts/preview-api.md).

## Pré-requisitos

- `cd sdk && npm ci && npm ci --prefix example`

---

## 1. O portão

```bash
cd sdk
npm run verify
```

## 2. Só esta fatia

```bash
npx jest --ci src/preview
npx jest --ci src/ui/content/__tests__/sameSequence   # roda sobre o PitacoPreviewProvider
```

## 3. O que precisa estar coberto

| # | Cenário | Onde |
| --- | --- | --- |
| 1 | Mesma máquina sobre o schema em memória, eventos só no `onEvent`, sem rede | `preview.test.tsx` |
| 2 | `restart` começa outra passada com outro `displayId` | `preview.test.tsx` |
| 3 | Schema sem pergunta renderizável não quebra | `preview.test.tsx`, `PitacoPreview.test.tsx` |
| 4 | `usePitacoSurvey` dentro do `PitacoPreviewProvider` | `preview.test.tsx` |
| 5 | UI padrão inline, sem `Modal`, apresentada sozinha, sem transporte | `PitacoPreview.test.tsx` |
| 6 | Percorre e conclui, chamando `onFinish` | `PitacoPreview.test.tsx` |
| 7 | Tema, textos, renderers e slots substituídos | `PitacoPreview.test.tsx` |
| 8 | `resetKey` reinicia com `displayId` novo | `PitacoPreview.test.tsx` |
| 9 | `bottom-sheet`: folha do SDK, `survey_presented` depois da entrada, sem rede | `PitacoPreview.container.test.tsx` |
| 10 | `bottom-sheet`: fundo → `backdrop`, `onFinish('dismissed')` depois da saída | `PitacoPreview.container.test.tsx` |
| 11 | `bottom-sheet`: concluir → agradecimento → `onFinish('completed')` depois da saída | `PitacoPreview.container.test.tsx` |
| 12 | `modal`: tela cheia, relata `modal`, sem rede | `PitacoPreview.container.test.tsx` |
| 13 | Renderizador que lança cai no padrão e chama `onRenderError` | `customization.levels.test.tsx` |

## 4. No navegador

No painel, com o SDK local (`"@pitaco/react-native": "file:../sdk"`) e `react-native` resolvido para
`react-native-web`, abrir o construtor de uma pesquisa:

- a pré-visualização aparece ao lado das perguntas, com o tema claro ou escuro do painel;
- responder lista os eventos emitidos ao lado (`survey_presented`, `question_viewed`, …);
- editar uma pergunta recomeça a pré-visualização;
- a aba de rede do navegador não mostra nenhuma chamada a `/collect`.
