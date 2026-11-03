# Research: SDK React Native — pré-visualização sem transporte

**Feature**: `005-sdk-preview-web` · **Date**: 2026-09-14 · **Spec**: [spec.md](./spec.md)

---

## D-01 — O preview implementa `SurveyController`

**Decision**: `createPreviewController(options)` normaliza o schema com `normalizeSurvey`, cria uma
`SurveySession` com `displayId` novo e expõe `getSnapshot`, `subscribe`, `present`, `dispatch`, mais
`survey`, `restart`, `dispose` e `setEventListener`.

**Rationale**: é a mesma interface do `PitacoRuntime`; a UI padrão, o hook e qualquer UI própria
funcionam sem saber qual dos dois está por baixo. É o que permite o teste de mesma sequência rodar
sobre o preview.

**Alternatives considered**: uma máquina de preview própria (divergiria do SDK de verdade).

---

## D-02 — Rascunho aceito com padrões

**Decision**: `withDraftDefaults` preenche `surveyId: 'preview'`, `versionId: 'preview'` e
`versionNumber: 0` quando o schema não os traz. Sem pergunta renderizável, `survey` é `null` e não
há sessão.

**Rationale**: o painel pré-visualiza pesquisa ainda não publicada.

**Alternatives considered**: exigir pesquisa publicada (inútil no construtor).

---

## D-03 — `onEvent` desde a criação

**Decision**: o `PitacoPreviewProvider` passa `onEvent` na criação do controlador (dentro do
`useMemo`) e acompanha trocas por efeito com `setEventListener`.

**Rationale**: o React comita efeitos de baixo para cima; `<PitacoSurveyContent />` chama
`present()` no próprio efeito de montagem, antes do efeito do Provider. Ligado só no efeito, o
`survey_presented` se perdia sempre. O defeito apareceu nos testes do conteúdo.

**Alternatives considered**: atrasar o `present()` (mudaria o comportamento da UI no Provider real).

---

## D-04 — Componente só declarativo, reinício por `resetKey`

**Decision**: `<PitacoPreview />` é `PitacoPreviewProvider` (com `key={resetKey}`) mais o conteúdo;
não expõe método imperativo.

**Rationale**: a `key` do React já resolve o reinício; `forwardRef` e `useImperativeHandle` seriam
superfície sem caso. O painel passa a lista de perguntas serializada como `resetKey`.

**Alternatives considered**: `restart()` via ref (o controlador cru já tem).

---

## D-05 — `inline` por padrão; contêiner do SDK opcional

**Decision**: com `inline`, `<PitacoPreview />` desenha `<PitacoSurveyContent />`; com
`bottom-sheet` ou `modal`, desenha o `PitacoSurfaceHost` dentro de um `PitacoErrorBoundary`. O host
ganhou `onFinish` (chamado só depois da animação de saída, com o desfecho relatado pelo conteúdo,
ou `dismissed` sem relato) e `thankYouDurationMs`; o Provider real continua o montando sem props.

**Rationale**: no navegador não há `Modal` útil; no aparelho, comparar o sheet do SDK com o
contêiner do app sem gastar elegibilidade pede o contêiner de verdade.

**Alternatives considered**: exportar `BottomSheet`/`FullScreenModal` (viraria superfície pública
de detalhe interno).

---

## D-06 — Falha de renderização vai a `onRenderError`

**Decision**: `reportRenderError` do contexto do preview repassa para `onRenderError(error, contexto)`
do `PitacoPreviewProvider`.

**Rationale**: sem runtime não há relatório por rede; o teste de "renderizador que lança" precisa de
um canal observável.

**Alternatives considered**: engolir em silêncio (o integrador não teria como saber).

---

## D-07 — Foco no web pelo elemento do DOM

**Decision**: em `Platform.OS === 'web'`, `<PitacoSurveyContent />` chama `focus({ preventScroll:
true })` no elemento do título, em vez de `findNodeHandle` e `setAccessibilityFocus`.

**Rationale**: no `react-native-web` o ref é o elemento do DOM, e `findNodeHandle` não existe; o
foco nativo do navegador resolve a acessibilidade. `preventScroll` evita a página do painel pular a
cada pergunta.

**Alternatives considered**: não mover foco no web (regrediria a acessibilidade do painel).
