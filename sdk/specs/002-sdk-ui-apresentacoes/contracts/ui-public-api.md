# Contrato — superfície pública da UI

**Feature**: `002-sdk-ui-apresentacoes`

Exportado por `@pitaco/react-native` (`src/index.ts` reexporta `src/ui/index.ts`).

---

## Props novas do `<PitacoProvider>`

| Prop | Tipo | Efeito |
| --- | --- | --- |
| `presentation` | `'bottom-sheet' \| 'modal' \| 'inline'` | contêiner desenhado pelo SDK (padrão `'bottom-sheet'`) |
| `theme` | `PitacoThemeConfig` | tema num ponto só |
| `strings` | `PartialPitacoStrings` | rótulos substituídos |
| `renderers` | `Partial<PitacoRendererMap>` | componente por tipo |
| `slots` | `Partial<PitacoSlotMap>` | moldura substituída |
| `insets` | `EdgeInsets` | safe area fixa |
| `getInsets` | `() => EdgeInsets` | safe area por função; tem prioridade |

Trocar `theme`, `strings`, `renderers`, `slots`, `insets` ou `getInsets` não recria o runtime;
trocar `presentation`, sim.

---

## `<PitacoSurveyContent />`

```tsx
<PitacoSurveyContent
  onFinish={(reason) => router.back()}   // 'completed' | 'dismissed'
  presentDeferred={false}                // true: o contêiner chama present() depois da animação
  dismissVia="navigation"                // via ao desmontar com a pesquisa em andamento
  thankYouDurationMs={2500}
  renderers={{ nps: MeuNps }}            // sobrepõe os do Provider
  slots={{ Footer: MeuRodape }}          // sobrepõe os do Provider
/>
```

| Prop | Tipo | Padrão |
| --- | --- | --- |
| `onFinish` | `(reason: PitacoSurveyFinishReason) => void` | — |
| `presentDeferred` | `boolean` | `false` |
| `dismissVia` | `DismissVia` | `'navigation'` |
| `thankYouDurationMs` | `number` | `2500` |
| `renderers` | `Partial<PitacoRendererMap>` | — |
| `slots` | `Partial<PitacoSlotMap>` | — |

Não desenha nada sem pesquisa, depois de dispensada ou descartada. Ao concluir, desenha o slot
`ThankYou` e chama `onFinish('completed')` quando ele termina. Ao ser dispensada por qualquer via,
chama `onFinish('dismissed')`.

---

## Renderizador de pergunta

```ts
interface QuestionRendererProps<T extends SurveyQuestion = SurveyQuestion> {
  question: T;
  value: AnswerValue | undefined;
  error: ValidationError | null;
  actions: QuestionRendererActions;
  theme: PitacoResolvedTheme;
  strings: PitacoStrings;
  freeTextNotice: string | null;   // só em FREE_TEXT com o aviso ligado
}
interface QuestionRendererActions {
  select(value: string | number): void;
  deselect(value?: string | number): void;
  setText(text: string): void;
  focusText(): void;
  blurText(): void;
}
type PitacoQuestionRenderer = (props: QuestionRendererProps) => ReactNode;
interface PitacoRendererMap {
  singleChoice; multipleChoice; rating; scale; nps; freeText: PitacoQuestionRenderer;
}
function rendererKeyForQuestionType(type: QuestionType): PitacoRendererKey;
```

O título e a mensagem de validação são desenhados por `<PitacoSurveyContent />`, fora do
renderizador. Renderizador que lança cai no padrão e é reportado.

---

## Slots de moldura

| Slot | Props |
| --- | --- |
| `Header` | `{ survey: SurveySummary; progress: SurveyProgress; theme; strings }` |
| `Progress` | `{ position: number; total: number; theme; strings }` |
| `Footer` | `{ canGoBack; canGoNext; isLast; onBack(); onNext(); theme; strings }` — `onNext` sempre chama `next()` |
| `CloseButton` | `{ onClose(); theme; strings }` — dispensa com `close_button` |
| `ThankYou` | `{ theme; strings; durationMs: number; onDone() }` |

`PitacoSlotMap = { Header; Progress; Footer; CloseButton; ThankYou }`, cada um
`(props) => ReactNode`.

---

## Tema e textos

| Export | Tipo |
| --- | --- |
| `usePitacoTheme()` | `() => PitacoResolvedTheme` — dentro ou fora do Provider |
| `usePitacoStrings()` | `() => PitacoStrings` |
| `DEFAULT_LIGHT_THEME`, `DEFAULT_DARK_THEME` | `PitacoThemeTokens` |
| `DEFAULT_STRINGS` | `PitacoStrings` |
| tipos | `PitacoThemeConfig`, `PitacoThemeTokens`, `PitacoThemeTokensOverride`, `PitacoColorTokens`, `PitacoTypographyToken(s)`, `PitacoRadiusTokens`, `PitacoSpacingTokens`, `PitacoResolvedTheme`, `ColorSchemeName`, `ColorSchemePreference`, `PitacoStrings`, `PartialPitacoStrings` |

---

## Primitivos

| Export | Uso |
| --- | --- |
| `PitacoText` (`variant`, `color`) | texto com tipografia do tema e teto de escala |
| `PitacoButton` (`label`, `variant`, `onPress`, `disabled`, `accessibilityLabel`) | botão com altura mínima 44 e `hitSlop` |
| `PitacoChip` | opção selecionável (`radio` ou `checkbox`) |
| `PitacoScalePoint` | ponto numérico quadrado de 44×44 |
| `hitSlopFor(alturaMedida, alvo?)` | `hitSlop` para chegar ao alvo |
| `MIN_TOUCH_TARGET` | `44` |

---

## Safe area

```ts
interface EdgeInsets { top: number; right: number; bottom: number; left: number }
```

Mesma forma de `useSafeAreaInsets()`: `getInsets={useSafeAreaInsets}` funciona direto.

---

## O que NÃO é público

`BottomSheet`, `FullScreenModal`, `gesture.ts`, `insets.ts`, `keyboard.ts`, `useReduceMotion`,
`SafeCustom`, `DEFAULT_RENDERERS`, `DEFAULT_SLOTS` e `PitacoSurfaceHost`.
