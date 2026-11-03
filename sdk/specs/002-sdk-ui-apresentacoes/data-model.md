# Data Model: SDK React Native — UI padrão e apresentações

**Feature**: `002-sdk-ui-apresentacoes` · **Date**: 2026-09-13

Nada aqui é persistido. São os tipos e valores que a camada de UI resolve a cada render.

---

## 1. Tema — `ui/theme/tokens.ts`

```ts
interface PitacoThemeConfig {
  colorScheme?: 'system' | 'light' | 'dark';     // ausente = 'system'
  light?: DeepPartial<PitacoThemeTokens>;
  dark?: DeepPartial<PitacoThemeTokens>;
}
interface PitacoResolvedTheme { scheme: 'light' | 'dark'; tokens: PitacoThemeTokens }
```

### Cores (`PitacoColorTokens`)

| Token | Claro | Escuro |
| --- | --- | --- |
| `background` | `#FFFFFF` | `#15171C` |
| `surface` | `#F5F6F8` | `#1E2126` |
| `overlay` | `rgba(15, 17, 21, 0.4)` | `rgba(0, 0, 0, 0.6)` |
| `border` | `#E1E4E8` | `#33373F` |
| `textPrimary` | `#12151A` | `#F4F5F7` |
| `textSecondary` | `#535B66` | `#B4BAC4` |
| `textInverse` | `#FFFFFF` | `#12151A` |
| `primary` | `#3454D1` | `#7C93FF` |
| `primaryText` | `#FFFFFF` | `#12151A` |
| `danger` | `#B3261E` | `#F2B8B5` |
| `dangerText` | `#FFFFFF` | `#12151A` |
| `disabled` | `#E1E4E8` | `#33373F` |
| `disabledText` | `#8A909B` | `#6B7180` |
| `focusRing` | `#3454D1` | `#7C93FF` |

### Tipografia, raio e espaçamento (iguais nos dois esquemas)

| Grupo | Valores |
| --- | --- |
| `typography` | `title` 18/24/700 · `body` 16/22/400 · `label` 14/20/500 · `caption` 12/16/400 · `button` 16/20/600 (`fontSize`/`lineHeight`/`fontWeight`) |
| `radius` | `sm` 6 · `md` 12 · `lg` 20 · `pill` 999 |
| `spacing` | `xs` 4 · `sm` 8 · `md` 16 · `lg` 24 · `xl` 32 |

### Validação (`ui/theme/validate.ts`)

| Tipo de token | Válido |
| --- | --- |
| cor | hex de 3, 4, 6 ou 8 dígitos, `rgb()`, `rgba()`, `hsl()`, `hsla()`, `transparent` |
| número (tamanho, altura de linha, raio, espaço) | finito e ≥ 0 |
| `fontWeight` | `'400'`, `'500'`, `'600'`, `'700'` |

Inválido → padrão daquele token + `warnOnce` em `__DEV__`.

### Pares de contraste verificados (`ui/theme/contrast.ts`)

`textPrimary/background`, `textPrimary/surface`, `textSecondary/background`,
`primaryText/primary`, `dangerText/danger`. Mínimo 4,5:1.

---

## 2. Textos — `ui/strings/strings.ts`

| Chave | Padrão |
| --- | --- |
| `next` | Próxima |
| `back` | Voltar |
| `submit` | Enviar |
| `close` | Fechar |
| `closeA11yLabel` | Fechar pesquisa |
| `requiredBadge` | obrigatória |
| `optionalBadge` | opcional |
| `progress({ position, total })` | Pergunta {position} de {total} |
| `questionA11yLabel({ position, total, statement, required })` | Pergunta {position} de {total}[, obrigatória]: {statement} |
| `thankYouTitle` | Obrigado! |
| `thankYouBody` | Sua resposta foi enviada. |
| `freeTextNoticeDefault` | Evite escrever dados pessoais nesta resposta. |
| `freeTextPlaceholder` | Digite sua resposta |
| `validationRequired` | Essa pergunta é obrigatória. |
| `npsMinLabelDefault` | Nada provável |
| `npsMaxLabelDefault` | Muito provável |
| `multipleChoiceHint` | Selecione uma ou mais opções |
| `loadingLabel` | Carregando… |

---

## 3. Registro de renderizadores — `ui/types.ts`, `ui/questions/index.ts`

| Tipo da pergunta | Chave | Padrão | Papel de acessibilidade |
| --- | --- | --- | --- |
| `SINGLE_CHOICE` | `singleChoice` | `SingleChoiceQuestion` | `radio` por opção |
| `MULTIPLE_CHOICE` | `multipleChoice` | `MultipleChoiceQuestion` | `checkbox` por opção |
| `RATING` | `rating` | `RatingQuestion` | `radio` por estrela, rótulo "X de N" |
| `SCALE` | `scale` | `ScaleQuestion` | `radio` por ponto |
| `NPS` | `nps` | `NpsQuestion` | `radio` por ponto, 0 a 10 |
| `FREE_TEXT` | `freeText` | `FreeTextQuestion` | campo multilinha, `maxLength` 2000 |

Precedência: renderizador local de `<PitacoSurveyContent renderers>` → do Provider → padrão. Mesma
regra para `slots`.

---

## 4. Configuração de UI no contexto — `PitacoUiConfig`

| Campo | Origem |
| --- | --- |
| `theme`, `strings` | props do Provider, ainda não resolvidas |
| `renderers`, `slots` | props do Provider |
| `presentation` | sempre resolvido (padrão `'bottom-sheet'`) |
| `insets`, `getInsets` | props do Provider |

---

## 5. Estado do host — `react/SurfaceHost.tsx`

| Estado | Inicial | Muda quando |
| --- | --- | --- |
| `open` | `available` no primeiro render | liga quando `available` passa a verdadeiro; desliga no `onFinish` do conteúdo ou no fim da saída |
| `trackedAvailable` | `available` no primeiro render | acompanha `available` durante o render |
| `finishReason` | `'dismissed'` | o desfecho relatado pelo conteúdo, entregue a `onFinish` depois da saída |

Em `inline`, `open` e `trackedAvailable` são zerados e nada é desenhado.

---

## 6. Constantes de apresentação

| Constante | Valor | Onde |
| --- | --- | --- |
| entrada / saída do sheet | 280 / 220 ms | `BottomSheet.tsx` |
| entrada / saída do modal | 220 / 180 ms | `FullScreenModal.tsx` |
| duração com movimento reduzido | 1 ms | ambos |
| altura máxima do sheet | 90% da janela | `BottomSheet.tsx` |
| limiar de distância / velocidade | 120 pt / 0,8 | `gesture.ts` |
| resistência para cima | 0,25 | `gesture.ts` |
| deslocamento para assumir o gesto | 4 pt | `gesture.ts` |
| insets padrão iOS | top 47, bottom 34 | `insets.ts` |
| insets padrão Android | top = barra de status ou 24 | `insets.ts` |
| alvo mínimo de toque | 44 pt | `PitacoButton.tsx` (`MIN_TOUCH_TARGET`) |
| teto de escala de fonte | 1,6× | `PitacoText.tsx` (`DEFAULT_MAX_FONT_SIZE_MULTIPLIER`) |
| duração do agradecimento | 2500 ms | `PitacoSurveyContent.tsx` |
| comportamento do teclado | iOS `padding`, Android `height` | `keyboard.ts` |
