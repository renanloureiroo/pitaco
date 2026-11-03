# Contrato — `@pitaco/react-native/preview`

**Feature**: `005-sdk-preview-web`

Declarado em `package.json#exports["./preview"]` (`src/preview/index.ts`). `PitacoPreview` e
`PitacoPreviewProps` também saem do ponto de entrada principal.

## Exports

| Export | Tipo |
| --- | --- |
| `PitacoPreview` | componente |
| `PitacoPreviewProps` | tipo |
| `PitacoPreviewProvider` | componente de contexto |
| `PitacoPreviewProviderProps` | tipo |
| `createPreviewController` | `(options: PreviewOptions) => PreviewController` |
| `PreviewOptions`, `PreviewController` | tipos |
| `usePitacoSurvey`, `UsePitacoSurveyResult` | o mesmo hook do Provider real |

---

## `<PitacoPreview />`

```tsx
<PitacoPreview
  schema={survey}                                  // formato da elegibilidade; rascunho aceito
  presentation="inline"                            // padrão; 'bottom-sheet' | 'modal' abrem o contêiner do SDK
  triggerEvent="checkout_completed"
  theme={{ colorScheme: 'dark' }}
  strings={{ next: 'Next' }}
  renderers={{ nps: MeuNps }}
  slots={{ Footer: MeuRodape }}
  onEvent={(event) => setEvents((prev) => [...prev, event])}
  onFinish={(reason) => console.warn(reason)}      // 'completed' | 'dismissed'
  thankYouDurationMs={2500}
  resetKey={JSON.stringify(perguntas)}             // muda → recomeça do zero
/>
```

| Prop | Tipo | Padrão |
| --- | --- | --- |
| `schema` | `unknown` | — |
| `presentation` | `Presentation` | `'inline'` |
| `triggerEvent` | `string` | `'preview'` |
| `theme` | `PitacoThemeConfig` | — |
| `strings` | `PartialPitacoStrings` | — |
| `renderers` | `Partial<PitacoRendererMap>` | — |
| `slots` | `Partial<PitacoSlotMap>` | — |
| `onEvent` | `(event: InteractionEvent) => void` | — |
| `onFinish` | `(reason: 'completed' \| 'dismissed') => void` | — |
| `thankYouDurationMs` | `number` | `2500` |
| `resetKey` | `string \| number` | — |

Comportamento:

- `inline`: desenha só `<PitacoSurveyContent />`, que apresenta ao montar; sem `Modal`.
- `bottom-sheet` / `modal`: o mesmo contêiner do `<PitacoProvider>`; `survey_presented` só depois
  da entrada; `onFinish` só depois da saída.
- Nenhuma requisição de rede, nenhum storage, nenhum relatório de erro.

---

## `<PitacoPreviewProvider>`

| Prop | Tipo |
| --- | --- |
| `schema` | `unknown` |
| `presentation` | `Presentation` |
| `triggerEvent` | `string` |
| `onEvent` | `(event: InteractionEvent) => void` |
| `theme`, `strings`, `renderers`, `slots` | como no Provider |
| `onRenderError` | `(error: unknown, context?: Readonly<Record<string, string>>) => void` |
| `children` | `ReactNode` |

Dentro dele, `usePitacoSurvey()`, `usePitacoTheme()`, `usePitacoStrings()` e
`<PitacoSurveyContent />` funcionam como no Provider real.

---

## `createPreviewController(options)`

```ts
interface PreviewOptions {
  schema: unknown;
  presentation?: Presentation;
  triggerEvent?: string;
  onEvent?: (event: InteractionEvent) => void;
  clock?: Clock; timers?: Timers; uuid?: () => string;
}
interface PreviewController extends SurveyController {
  readonly survey: Survey | null;
  readonly restart: () => void;
  readonly dispose: () => void;
  readonly setEventListener: (listener?: (event: InteractionEvent) => void) => void;
}
```

---

## Uso no navegador

O consumidor carrega o subpath só no cliente e resolve `react-native` para `react-native-web`.
Exemplo do lado do painel:

```tsx
const PitacoPreview = dynamic<PitacoPreviewProps>(
  () => import('@pitaco/react-native/preview').then((mod) => mod.PitacoPreview),
  { ssr: false },
);
```
