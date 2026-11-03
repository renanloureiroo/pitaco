# Implementation Plan: SDK React Native — UI padrão e apresentações

**Branch**: `002-sdk-ui-apresentacoes` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-sdk-ui-apresentacoes/spec.md`

## Summary

Acrescentar ao SDK a camada `ui` (tema, textos, primitivos, seis renderizadores, slots e
`<PitacoSurveyContent />`) e a camada `presentation` (bottom sheet próprio, modal em tela cheia e
`inline`), ligadas ao Provider pelo `PitacoSurfaceHost`.

Cinco movimentos:

1. **Contrato de customização primeiro** (D-01): `src/ui/types.ts` fixa `QuestionRendererProps`,
   os cinco slots, `PitacoRendererMap`, `EdgeInsets` e `PitacoUiConfig`. Renderizadores e
   apresentações são escritos sobre ele.
2. **Tema e textos resolvidos por hook** (D-02, D-03): `usePitacoTheme()` mescla token a token e
   checa contraste em desenvolvimento; `usePitacoStrings()` mescla rasamente sobre os padrões em
   pt-BR. Ambos funcionam fora do Provider.
3. **Isolamento por `SafeCustom`** (D-05): todo renderizador e slot passa por um
   `PitacoErrorBoundary` com o padrão como `fallback`, remontado por pergunta.
4. **Apresentações só com React Native** (D-07 a D-11): `Modal` + `Animated` + `PanResponder` +
   `KeyboardAvoidingView` + `BackHandler`; a lógica do gesto e a regra do teclado ficam em funções
   puras testáveis.
5. **Host que adia `present()` e segura o desfecho** (D-06): `PitacoSurfaceHost` abre quando a
   pesquisa fica disponível, chama `present()` no fim da entrada e só fecha quando o conteúdo avisa
   o desfecho.

## Technical Context

**Language/Version**: TypeScript 5.9 estrito, React 19 e React Native 0.87 em desenvolvimento

**Primary Dependencies**: só `react` e `react-native` (peers). APIs usadas: `Modal`, `Animated`
(`useNativeDriver: true`), `PanResponder`, `KeyboardAvoidingView`, `BackHandler`, `StatusBar`,
`AccessibilityInfo`, `findNodeHandle`, `useColorScheme`.

**Storage**: N/A

**Testing**: Jest 29 + `@testing-library/react-native` 14; renderizadores testados isolados por
`QuestionRendererProps`; apresentações testadas pelo host com timers falsos; gesto testado pela
função pura `createDragHandlers`.

**Target Platform**: iOS e Android (Expo Go incluído); web via `react-native-web` para o conteúdo
inline

**Project Type**: biblioteca React Native

**Performance Goals**: animações no driver nativo; entrada em 280 ms e saída em 220 ms no sheet,
220/180 ms no modal; 1 ms com movimento reduzido.

**Constraints**: zero módulo nativo; nenhum acesso privilegiado da UI ao core; alvo mínimo de 44
pt; contraste mínimo 4,5:1 nos pares desenhados.

**Scale/Scope**: 6 renderizadores, 5 slots, 4 primitivos, 3 apresentações, 1 host, 2 hooks de
aparência.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Como esta fatia o satisfaz | Veredito |
| --- | --- | --- |
| **I. Zero Módulo Nativo** | Sheet com `Modal`/`Animated`/`PanResponder`; safe area por `insets`/`getInsets`; nenhuma importação nova fora de `react` e `react-native`. Os nomes dos pacotes nativos só aparecem em comentário. | ✅ |
| **II. Degradação Silenciosa** | `SafeCustom` isola cada substituição; o host roda no error boundary do Provider; `getInsets` que lança cai no padrão; `useReduceMotion` e o foco nunca deixam falha escapar. | ✅ |
| **III. O Servidor Decide** | A UI só desenha o que o core expõe; nenhum filtro de pergunta no cliente além do que o core já decidiu. | ✅ |
| **IV. Catálogo Fixo** | Nenhum componente emite evento: todas as ações vão para o core por `usePitacoSurvey()`. O botão de avançar não é desabilitado para não esconder `validation_blocked`. | ✅ |
| **V. Sem Dado Pessoal** | O renderizador de texto livre só chama `setText`/`focusText`/`blurText`; o relatório de falha de renderização leva tipo e nome do slot, nunca conteúdo. | ✅ |
| **VI. Três Camadas** | `ui/` usa só a API pública; `presentation/` não é exportada; o título fica em `<PitacoSurveyContent />` para o foco não depender do renderizador. | ✅ |
| **VII. Acessível** | Papéis e rótulos em todos os controles, `MIN_TOUCH_TARGET` 44, foco no título, anúncios de erro e agradecimento, teto de fonte 1,6×, movimento reduzido, contraste verificado. | ✅ |
| **VIII. Teste É Contrato** | Teste por renderizador, por primitivo, por apresentação, por nível de customização e de acessibilidade, escritos antes do componente. | ✅ |

**Gate de Phase 0**: aprovado.

### Re-avaliação após Phase 1 (design)

Três pontos exigiram padrão de código específico e sobreviveram ao lint `react-hooks` 7:

- **Estado derivado ajustado durante o render** no host e nas apresentações (`open`, `phase`),
  comparando com uma cópia rastreada, em vez de `setState` dentro de efeito.
- **`useState` preguiçoso a partir da própria prop**: sem isso, uma pesquisa já disponível na
  primeira renderização nunca abriria a folha.
- **Um `eslint-disable-next-line` justificado** na criação do `PanResponder`, cujos handlers leem
  refs apenas durante o gesto. Registrado em Complexity Tracking.

Veredito final: **aprovado**.

## Project Structure

### Documentation (this feature)

```text
specs/002-sdk-ui-apresentacoes/
├── plan.md
├── research.md          # D-01 a D-16
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── ui-public-api.md       # PitacoSurveyContent, renderers, slots, tema, textos, primitivos
│   └── presentation-behavior.md # host, vias de dispensa, insets, teclado, testIDs
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (raiz do SDK)

```text
src/ui/
├── index.ts                         barril público da UI
├── types.ts                         contratos de customização
├── theme/{tokens,validate,contrast,useTheme,index}.ts
├── strings/{strings,useStrings,index}.ts
├── primitives/{PitacoText,PitacoButton,PitacoChip,PitacoScalePoint,index}.tsx
├── registry/SafeCustom.tsx
├── questions/
│   ├── SingleChoiceQuestion.tsx  MultipleChoiceQuestion.tsx  RatingQuestion.tsx
│   ├── ScaleQuestion.tsx  NpsQuestion.tsx  FreeTextQuestion.tsx
│   ├── shared/{NumericScale.tsx,QuestionErrorFrame.tsx,nps.ts}
│   └── index.ts                     DEFAULT_RENDERERS
├── content/
│   ├── PitacoSurveyContent.tsx  focus.ts  index.ts
│   └── slots/{DefaultHeader,DefaultProgress,DefaultFooter,DefaultCloseButton,DefaultThankYou,index}.tsx
└── presentation/                    interno, não exportado
    ├── BottomSheet.tsx  FullScreenModal.tsx
    ├── gesture.ts  insets.ts  keyboard.ts  useReduceMotion.ts  index.ts

src/react/
├── SurfaceHost.tsx                  PitacoSurfaceHost
├── PitacoProvider.tsx               alterado: theme, strings, renderers, slots, insets, getInsets
└── context.ts                       alterado: ui: PitacoUiConfig

testes:
src/ui/theme/__tests__/theme.test.ts
src/ui/strings/__tests__/strings.test.ts
src/ui/primitives/__tests__/primitives.test.tsx
src/ui/registry/__tests__/SafeCustom.test.tsx
src/ui/questions/__tests__/{support.tsx, *Question.test.tsx}
src/ui/content/__tests__/{PitacoSurveyContent,customization.levels,a11y.focus,a11y.fontScale}.test.tsx
src/ui/content/slots/__tests__/DefaultThankYou.test.tsx
src/ui/presentation/__tests__/{gesture,insets,keyboard,useReduceMotion}.test.ts(x)
src/react/__tests__/{SurfaceHost,SurfaceHost.a11y}.test.tsx
```

**Structure Decision**: `ui/` é pública (exportada por `src/index.ts`), `ui/presentation/` é
detalhe do `PitacoSurfaceHost`. Quem quer outro contêiner usa `presentation="inline"` e
`<PitacoSurveyContent />`, não as peças internas.

## Complexity Tracking

| Violação / risco | Por que é necessário | Alternativa mais simples rejeitada porque |
| --- | --- | --- |
| `eslint-disable-next-line react-hooks/refs` na criação do `PanResponder` | Os handlers precisam ler a altura escondida e o início da saída por ref, só durante o gesto | `useMemo`, `useState` preguiçoso e inicialização condicional de ref foram todos rejeitados pela mesma regra |
| Bottom sheet próprio em vez de biblioteca | Princípio I | `@gorhom/bottom-sheet` exige Reanimated e Gesture Handler |
