# Research: SDK React Native — UI padrão e apresentações

**Feature**: `002-sdk-ui-apresentacoes` · **Date**: 2026-09-13 · **Spec**: [spec.md](./spec.md)

---

## D-01 — O contrato de customização nasce antes dos componentes

**Decision**: `src/ui/types.ts` define `QuestionRendererProps` (`question`, `value`, `error`,
`actions`, `theme`, `strings`, `freeTextNotice`), `QuestionRendererActions` já ligadas à pergunta
atual (`select`, `deselect`, `setText`, `focusText`, `blurText`, sem `questionKey`), os cinco slots
com tema, textos e ações (`onBack`, `onNext`, `onClose`, `onDone`), `PitacoRendererMap` com chaves
em camelCase (`rendererKeyForQuestionType`) e `PitacoUiConfig` no contexto.

**Rationale**: renderizadores, slots e apresentações foram escritos sobre essa fronteira, e ela é
exatamente o que um app recebe ao substituir uma peça. Quem decide qual é a pergunta atual é o
core; passar a chave de outra pergunta não teria efeito.

**Alternatives considered**: o renderizador receber o hook inteiro (acoplaria o componente ao
estado global e permitiria navegar de dentro dele).

---

## D-02 — Tema: dois conjuntos de tokens e preferência de esquema

**Decision**: `PitacoThemeConfig = { colorScheme?: 'system' | 'light' | 'dark'; light?; dark? }`,
com sobreposição parcial profunda de `colors` (14), `typography` (5 estilos), `radius` (4) e
`spacing` (5). Merge validado token a token: cor que não é hex, `rgb[a]`, `hsl[a]` ou
`transparent`, número negativo ou peso de fonte fora de `400–700` caem no padrão daquele token,
com aviso em `__DEV__`. `'unspecified'` de `useColorScheme()` vale como ausência de esquema.

**Rationale**: deixa claro qual token pertence a qual esquema; um erro de digitação numa cor não
joga fora as outras substituições corretas.

**Alternatives considered**: um objeto só de cores (não cobre o escuro); validar o tema inteiro e
descartá-lo se houver erro (punitivo demais para um aviso).

---

## D-03 — Contraste checado só nos pares que a UI padrão desenha

**Decision**: razão WCAG calculada para texto principal e secundário sobre fundo, texto principal
sobre superfície, texto de botão sobre a cor primária e texto de perigo sobre a cor de perigo;
aviso único abaixo de 4,5:1, só em desenvolvimento. Texto desabilitado fica de fora; `hsl` e nomes
CSS pulam a checagem.

**Rationale**: o WCAG isenta componente inativo, e o par desabilitado padrão é apagado de
propósito. Checar pares que a UI não desenha geraria aviso falso.

**Alternatives considered**: bloquear tema com contraste baixo (o app decide a marca; o SDK só
avisa).

---

## D-04 — Textos em pt-BR com interpolação por função

**Decision**: `PitacoStrings` com 18 rótulos, dois deles funções (`progress`,
`questionA11yLabel`) de parâmetros nomeados. `mergeStrings` faz merge raso: cada rótulo
substituído entra inteiro.

**Rationale**: interpolação tipada evita formato de template próprio; o TypeScript acusa rótulo
faltando para quem tipa a tradução inteira como `PitacoStrings`.

**Alternatives considered**: i18n com chaves e pluralização (dependência e complexidade sem caso
real).

---

## D-05 — `SafeCustom`: isolamento por substituição

**Decision**: cada renderizador e slot substituído é desenhado como elemento JSX `<SafeCustom>`
dentro de `PitacoErrorBoundary`, com o padrão como `fallback`. Ao falhar: aviso deduplicado por
tipo e nome em `__DEV__` e `reportRenderError(error, { kind, name })`. O boundary do renderizador
recebe `key={question.key}`, então a próxima pergunta do mesmo tipo tenta de novo.

**Rationale**: chamar como função simples quebraria as regras dos hooks (um slot padrão com
`useEffect` entraria na lista de hooks do pai de forma condicional) e foi rejeitado pelo lint.

**Alternatives considered**: um boundary único em volta da pesquisa (uma falha num tipo apagaria a
pesquisa inteira).

---

## D-06 — O host abre na chegada e fecha no desfecho relatado

**Decision**: `PitacoSurfaceHost` mantém `open` ligado do instante em que `available` liga até
`<PitacoSurveyContent presentDeferred onFinish>` relatar o desfecho; `present()` é chamado no
`onOpened` (fim da animação de entrada); a apresentação decide quando a saída termina
(`onClosed`). Em `inline`, o host zera `open` e o rastreio de `available` e devolve `null`.

**Rationale**: `available` cai no instante em que o core conclui ou dispensa — cedo demais: o
agradecimento nem apareceria e a folha sumiria sem animar. Zerar em `inline` evita que um
contêiner vazio fique preso por cima do app quando a apresentação volta a ser um contêiner.

**Alternatives considered**: `open = available` (corta o agradecimento); `present()` ao montar o
conteúdo (abriria a exibição antes de a folha estar visível).

---

## D-07 — Bottom sheet com o que o React Native traz

**Decision**: `Modal` transparente com `animationType="none"` e `statusBarTranslucent` no Android;
`Animated.timing` com `useNativeDriver: true` para `translateY` e opacidade do fundo; alça com
`PanResponder`; `KeyboardAvoidingView` com `flex: 1` e `justifyContent: 'flex-end'`; `ScrollView`
com teto de 90% da altura; rodapé com o inset inferior; `BackHandler` e `onRequestClose` para o
voltar do Android.

**Rationale**: atende a restrição de zero módulo nativo com o gesto e a sensação esperados.
Sem `flex: 1` no `KeyboardAvoidingView` a folha aparecia no topo — os testes não pegavam porque o
renderizador de teste não faz layout; há teste que confere o estilo.

**Alternatives considered**: `@gorhom/bottom-sheet` (módulos nativos); `Modal` com
`animationType="slide"` (sem arrastar para fechar).

---

## D-08 — Gesto como função pura

**Decision**: `gesture.ts` exporta `dragProgress` (resistência de 0,25 para cima),
`shouldCloseFromGesture` (mais de 120 pt ou velocidade acima de 0,8) e `createDragHandlers`,
usados pelo `PanResponder` da alça; o gesto só é assumido depois de 4 pt de deslocamento vertical.

**Rationale**: o `PanResponder` calcula o `gestureState` a partir do histórico de toque nativo, que
não dá para fabricar por evento sintético; testar a função que o componente usa cobre a decisão
sem a maquinaria por baixo.

**Alternatives considered**: simular toque nativo no teste (impossível no renderizador de teste).

---

## D-09 — Movimento reduzido como duração de 1 ms

**Decision**: `useReduceMotion` lê `AccessibilityInfo.isReduceMotionEnabled()` e acompanha
`reduceMotionChanged`, começando em `false` e nunca deixando a API falhar para o componente; com
ele ligado, as animações usam 1 ms.

**Rationale**: mantém o mesmo caminho de código (estado `phase` → efeito → animação → callback), e o
resultado prático é o mesmo de não animar.

**Alternatives considered**: um ramo sem animação (duplicaria a lógica de abrir e fechar).

---

## D-10 — Safe area sem dependência

**Decision**: `resolveInsets(insets, getInsets)`: `getInsets` tem prioridade; se lançar, vale
`insets` ou o padrão; sem os dois, `platformDefaultInsets()` — iOS `{ top: 47, bottom: 34 }`,
Android `{ top: StatusBar.currentHeight ?? 24, bottom: 0 }`. Resolvido uma vez por render no host.

**Rationale**: quem já usa `react-native-safe-area-context` passa o próprio `useSafeAreaInsets()`;
quem não usa recebe um padrão conservador.

**Alternatives considered**: importar `react-native-safe-area-context` (módulo nativo).

---

## D-11 — Teclado com `behavior` nas duas plataformas

**Decision**: `keyboardAvoidingBehavior()` devolve `padding` no iOS e `height` no Android; sheet e
modal usam a mesma regra.

**Rationale**: no Android ponta a ponta, a janela de um `Modal` não encolhe com `adjustResize`; sem
`behavior`, o teclado cobria o campo, o contador e "Enviar". O `KeyboardAvoidingView` não repassa
`behavior` ao `View`, então a regra vive numa função testável.

**Alternatives considered**: `behavior` só no iOS (o defeito acima).

---

## D-12 — O título fica fora do renderizador

**Decision**: `<PitacoSurveyContent />` desenha o enunciado (com "(obrigatória)") como `Text` com
ref, papel `header` e rótulo de acessibilidade completo; a mensagem de validação aparece abaixo
dele. O renderizador só desenha a área interativa.

**Rationale**: o alvo do foco precisa ser estável, não importa qual renderizador está montado; e o
renderizador substituído nunca precisa expor ref.

**Alternatives considered**: o renderizador desenhar o título (cada substituição teria de cuidar
do foco).

---

## D-13 — Foco por função extraída

**Decision**: `moveAccessibilityFocus(handle, setFocus)` em `content/focus.ts` chama
`setAccessibilityFocus` quando há handle e engole falha. No web, o foco vai para o elemento do DOM
do título com `preventScroll`.

**Rationale**: o renderizador de teste não tem nó nativo (`findNodeHandle` devolve `null`);
extraindo a decisão, a regra fica testável.

**Alternatives considered**: não testar (a regra viraria acidental).

---

## D-14 — Botão de avançar nunca desabilitado

**Decision**: o `Footer` padrão sempre chama `next()`; o rótulo muda para "Enviar" com `isLast`.

**Rationale**: o core valida e emite `validation_blocked`; um botão desabilitado impediria a
tentativa e o evento.

**Alternatives considered**: desabilitar por `canGoNext` (o valor continua nas props para quem
quiser).

---

## D-15 — Escolha única sempre chama `select`

**Decision**: tocar numa opção (inclusive a já escolhida) de escolha única, avaliação, escala e
NPS chama `select`; o core não emite nada quando o valor repete. Múltipla escolha alterna
`select`/`deselect`.

**Rationale**: a decisão de "já escolhido" é do core, não da UI.

**Alternatives considered**: a UI decidir quando desmarcar (duplicaria regra do core).

---

## D-16 — Estrelas e pontos com primitivos próprios

**Decision**: avaliação com glifos `★`/`☆` desenhados com `Text`/`View`, papel `radio` e rótulo
"X de N"; escala e NPS com `PitacoScalePoint` (quadrado de ao menos 44×44 pt) num contêiner com
quebra de linha; opções de texto com `PitacoChip`. `PitacoButton` garante 44 pt por altura mínima
e `hitSlop`.

**Rationale**: avaliação e escala são conceitos distintos mesmo com a mesma faixa; um dígito numa
pílula não garante 44 pt de largura.

**Alternatives considered**: estender `PitacoChip` (mexeria num primitivo já testado); slider por
arrasto (exigiria gesto próprio sem caso real).
