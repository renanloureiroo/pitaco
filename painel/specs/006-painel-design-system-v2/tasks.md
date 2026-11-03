---

description: "Task list — Painel com Design System v2"
---

# Tasks: Painel com Design System v2 — shell com barra lateral, tema claro e escuro, Visão geral da aplicação e celular

**Input**: documentos de design em `/specs/006-painel-design-system-v2/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/ui-routes.md)

**Tests**: **incluídos e obrigatórios** para a regra de negócio (agregação da Visão geral) e para
os fluxos que mudaram de navegação. Testes antes da implementação em cada história.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo
- **[Story]**: US1 … US7
- Caminhos relativos à raiz do painel

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: o Design System como código, antes de qualquer tela

- [X] T001 Escrever `DESIGN.md` na raiz do painel: princípios, tabela de tokens, tipografia, espaço e forma, layout, componentes de dashboard e gráficos ([R1](./research.md#r1))
- [X] T002 Registrar no `README.md` do painel que `DESIGN.md` resume as regras e `src/app/globals.css` guarda os valores
- [X] T003 Reescrever `src/app/globals.css` com `@custom-variant dark`, `@theme inline` (nomes do shadcn e papéis do Pitaco, raios) e os valores de `:root` (claro) e `.dark` (escuro)
- [X] T004 Instalar o primitivo `chart` do shadcn em `src/components/ui/chart.tsx` (traz `recharts` para `package.json`), com grade, eixos e cursor nos tokens ([R6](./research.md#r6))
- [X] T005 [P] Ajustar os primitivos de `src/components/ui/` (`button`, `badge`, `card`, `table`, `input`, `select`, `dialog`, `alert-dialog`, `dropdown-menu`, `tooltip`, `skeleton`, `empty`, `field`, …) aos tokens, por variantes
- [X] T006 [P] Cobrir o primitivo `Button` em `src/components/ui/__tests__/button.test.tsx`: papel acessível, clique, desabilitado

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: fontes, formatos e peças de dashboard que as histórias consomem

**⚠️ CRITICAL**: nenhuma história começa antes desta fase

- [X] T007 Carregar Inter (`--font-sans`) e JetBrains Mono (`--font-mono`) com `next/font` e aplicar `bg-background text-foreground` no corpo em `src/app/layout.tsx`
- [X] T008 [P] Criar `src/shared/lib/number-format.ts` (`formatInt`, `formatCompact`, `formatPercent`, `formatNps`, `formatDelta`, `formatDay`, `formatLongDay`, `formatDuration`) com ausente como "—", exportados por `src/shared/lib/index.ts`
- [X] T009 [P] Criar `src/shared/components/dashboard/kpi-card.tsx` (`KpiCard`, `KpiDelta`): um número, seta pelo sinal, cor pelo sentido bom, texto "subiu"/"caiu"/"estável" para leitor de tela
- [X] T010 [P] Criar `src/shared/components/dashboard/chart-card.tsx` (`ChartCard`)
- [X] T011 [P] Criar `src/shared/components/dashboard/trend-chart.tsx` (`TrendChart`): área de exibidas e concluídas, legenda, tooltip com a taxa do dia, sem segundo eixo
- [X] T012 [P] Criar `src/shared/components/dashboard/outcome-breakdown.tsx` (`OutcomeBreakdown`): barra 100% com 2px de vão, contagem, proporção e explicação de cada desfecho
- [X] T013 [P] Criar `src/shared/components/dashboard/nps-breakdown.tsx` (`NpsBreakdown`): pontuação com sinal e grupos com faixa, cores semânticas
- [X] T014 Criar `src/shared/components/dashboard/index.ts` e reexportar em `src/shared/components/index.ts`
- [X] T015 [P] Usar `TrendChart` sobre a lista de dias em `src/features/results/components/response-rate-card.tsx`

**Checkpoint**: vocabulário visual pronto

---

## Phase 3: User Story 1 - Navegar pela barra lateral e trocar de aplicação (Priority: P1) 🎯 MVP

**Goal**: shell com barra lateral, seletor e grupos de seções.

**Independent Test**: trocar de aplicação pelo seletor e seguir Saúde e Privacidade pela barra.

### Tests for User Story 1

- [X] T016 [US1] Ajustar os fluxos ponta a ponta à navegação nova: Respondentes pela barra em `e2e/coleta.spec.ts`, Saúde em `e2e/saude.spec.ts`, Privacidade em `e2e/privacidade.spec.ts`; e a raiz com título de página em `e2e/smoke.spec.ts`

### Implementation for User Story 1

- [X] T017 [US1] Criar `src/shared/components/app-sidebar.tsx` (`"use client"`): marca, `ThemeToggle`, `ApplicationSwitcher` sobre `DropdownMenu`, grupos Análise e Operação, item ativo por `aria-current`, aplicação atual pelo caminho com `nova` reservado ([R3](./research.md#r3))
- [X] T018 [US1] Criar o shell `src/app/aplicacoes/layout.tsx` com `AppSidebar` alimentada por `listApplications({ size: 100 })` sob `<Suspense>` e fallback sem aplicações
- [X] T019 [US1] Reduzir `src/app/aplicacoes/[applicationId]/layout.tsx` à trilha sob `<Suspense>`, removendo o `SectionNav` da aplicação
- [X] T020 [P] [US1] Restilizar `src/shared/components/section-nav.tsx` (borda inferior do item ativo em `primary`, rolagem horizontal) e manter as abas em `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/layout.tsx`
- [X] T021 [P] [US1] Restilizar `src/app/aplicacoes/page.tsx`, `src/app/aplicacoes/nova/page.tsx` e `src/shared/components/page-header.tsx` ao layout de página (trilha → título e ações → conteúdo)

**Checkpoint**: o painel tem esqueleto

---

## Phase 4: User Story 2 - Ver como estão as pesquisas da aplicação (Priority: P1)

**Goal**: Visão geral com KPIs, tendência, desfechos, NPS consolidado e blocos de SDK e respondentes.

**Independent Test**: trocar o período e conferir KPIs, tendência e URL; aplicação sem pesquisas.

### Tests for User Story 2

- [X] T022 [P] [US2] Testar em `src/features/analytics/__tests__/aggregate.test.ts`: taxa da aplicação pelas contagens somadas, não média; sem exibição não há taxa; NPS recalculado sobre grupos somados; sem NPS é ausente; linha do tempo somada e preenchida com zero; variação relativa precisa de base; variação em pontos precisa dos dois lados; período fora da lista cai em 30; período anterior com a mesma duração terminando onde o atual começa

### Implementation for User Story 2

- [X] T023 [P] [US2] Criar `src/features/analytics/lib/aggregate.ts` com `OVERVIEW_PERIODS`, `DEFAULT_OVERVIEW_PERIOD`, `OVERVIEW_PERIOD_LABELS`, `parseOverviewPeriod`, `sumTotals`, `sumNps`, `utcDay`, `mergeTimelines`, `relativeChange`, `pointChange`, `surveyPerformance` e `periodWindows` ([R4](./research.md#r4))
- [X] T024 [US2] Criar `src/features/analytics/api/overview.ts` com `getApplicationOverview`: lista de pesquisas; resultados de cada publicada no período atual e no anterior; respondentes, versões e erros do SDK em paralelo; tolerância a falha por bloco; pesquisa de NPS em destaque
- [X] T025 [P] [US2] Criar `src/features/analytics/components/period-tabs.tsx` (vínculos que preservam os demais parâmetros) e `src/features/analytics/components/sdk-adoption.tsx` (três versões mais usadas e "Outras")
- [X] T026 [US2] Criar a fronteira `src/features/analytics/index.ts`
- [X] T027 [US2] Reescrever `src/app/aplicacoes/[applicationId]/page.tsx` como Visão geral: cabeçalho com contagens, período, KPIs com variação, tendência, desfechos, NPS consolidado, respondentes, SDK, erros e a seção Configuração (`ApplicationDetail`), com o vazio de aplicação sem pesquisas

**Checkpoint**: "como estão as pesquisas deste app?" em uma tela

---

## Phase 5: User Story 3 - Comparar segmentos (Priority: P2)

**Goal**: NPS divergente e taxa por valor de atributo, com o atributo na URL.

**Independent Test**: trocar atributo e período e conferir as linhas e a URL.

### Implementation for User Story 3

- [X] T028 [US3] Acrescentar `getSegmentBreakdown` em `src/features/analytics/api/overview.ts`: até seis valores mais frequentes, uma leitura de resultado por valor, omissão do valor cuja leitura falha ([R7](./research.md#r7))
- [X] T029 [P] [US3] Criar `src/features/analytics/components/segment-breakdown.tsx` com a barra divergente de NPS e a barra de taxa, e a coluna de NPS só quando há pontuação
- [X] T030 [US3] Compor a análise por segmento na Visão geral `src/app/aplicacoes/[applicationId]/page.tsx`: atributo da URL (`segmento`) ou o de menos valores entre os que têm ao menos dois; vínculos que preservam o período; textos de ausência

**Checkpoint**: o segmento que puxa o número para baixo aparece

---

## Phase 6: User Story 4 - Ver o desempenho de cada pesquisa (Priority: P2)

**Goal**: tabela por pesquisa com miniatura e variação da taxa.

**Independent Test**: três pesquisas com volumes diferentes; conferir a ordem e o vínculo.

### Implementation for User Story 4

- [X] T031 [P] [US4] Criar `src/features/analytics/components/sparkline.tsx` (SVG simples; "—" com menos de dois pontos)
- [X] T032 [US4] Criar `src/features/analytics/components/survey-performance-table.tsx`: nome e modelo com vínculo para os resultados, estado com rótulo, miniatura, exibições, concluídas, taxa com variação a partir de 0,5 ponto (seta e texto para leitor de tela), NPS; dentro de contêiner com rolagem horizontal
- [X] T033 [US4] Compor a tabela na Visão geral `src/app/aplicacoes/[applicationId]/page.tsx` com "Ver todas"

**Checkpoint**: qual pesquisa olhar primeiro

---

## Phase 7: User Story 5 - Ativar e desativar a aplicação pela Visão geral (Priority: P3)

**Goal**: Desativar com confirmação e Reativar sem confirmação, no cabeçalho.

**Independent Test**: desativar, conferir nos dois lugares, reativar; cancelar.

### Tests for User Story 5

- [X] T034 [P] [US5] Testar em `src/features/applications/__tests__/actions.test.ts` que `setApplicationStatusAction` desativa e realinha, e devolve a recusa como estado exibível
- [X] T035 [P] [US5] Testar em `src/features/applications/__tests__/components.test.tsx` que desativar pede confirmação e só chama a action depois; cancelar não desativa; inativa oferece reativar sem confirmação; a recusa é exibida
- [X] T036 [P] [US5] Cobrir em `e2e/aplicacoes.spec.ts` desativar com confirmação e reativar, com o estado no detalhe e na lista, e cancelar mantendo ativa

### Implementation for User Story 5

- [X] T037 [P] [US5] Acrescentar `activateApplication` e `deactivateApplication` em `src/features/applications/api/index.ts` e `setApplicationStatusAction` em `src/features/applications/actions.ts`
- [X] T038 [US5] Criar `src/features/applications/components/application-status-button.tsx` sobre o `ConfirmDialog` compartilhado e compô-lo, com `EditApplicationDialog`, no cabeçalho da Visão geral em `src/app/aplicacoes/[applicationId]/page.tsx`
- [X] T039 [P] [US5] Aceitar `POST …/activate` e `…/deactivate` em `e2e/stub-api/routes/applications.ts`

**Checkpoint**: operação da aplicação sem API direta

---

## Phase 8: User Story 6 - Alternar entre tema claro e escuro (Priority: P2)

**Goal**: escuro por padrão, alternância em todo o painel.

**Independent Test**: alternar e navegar por três telas.

### Implementation for User Story 6

- [X] T040 [P] [US6] Criar `src/shared/components/theme-provider.tsx` (`next-themes`, classe, escuro por padrão, sem tema do sistema) e envolver o corpo em `src/app/layout.tsx`, com `dark` e `suppressHydrationWarning` no `<html>` ([R2](./research.md#r2))
- [X] T041 [P] [US6] Criar `src/shared/components/theme-toggle.tsx` com os dois ícones no DOM e o rótulo "Alternar tema claro e escuro"
- [X] T042 [US6] Conferir que gráficos (`var(--chart-n)`) e a pré-visualização da pesquisa acompanham o tema

**Checkpoint**: claro e escuro de verdade

---

## Phase 9: User Story 7 - Usar o painel no celular (Priority: P2)

**Goal**: gaveta abaixo de `lg`, conteúdo em uma coluna, tabelas com rolagem própria.

**Independent Test**: em 360px, abrir o menu, navegar e conferir a página sem rolagem horizontal.

### Implementation for User Story 7

- [X] T043 [US7] Acrescentar a barra superior e a gaveta modal (Escape, fundo, botão de fechar, fecha ao navegar) em `src/shared/components/app-sidebar.tsx`
- [X] T044 [P] [US7] Aplicar `px-4 sm:px-8` e largura máxima de 1280px no conteúdo em `src/app/aplicacoes/[applicationId]/layout.tsx`, e grades responsivas (KPIs 1/2/4, gráficos 1/3) na Visão geral, em Resultados e em Comportamento ([R9](./research.md#r9))
- [X] T045 [P] [US7] Garantir contêiner com `overflow-x-auto` nas tabelas da Visão geral, de pesquisas, de exibições, de respondentes, de versões do SDK e de exclusões

**Checkpoint**: as sete histórias funcionam

---

## Phase 10: Polish & Cross-Cutting Concerns

- [X] T046 Revisar as telas de 001 a 005 contra `DESIGN.md`: um amarelo por tela, status nunca só na cor, "—" para ausente, `tabular-nums` em colunas de número
- [X] T047 Conferir contraste ≥ 4.5:1 dos textos nos dois temas
- [X] T048 Rodar o roteiro manual de [quickstart.md](./quickstart.md) com a demo carregada
- [X] T049 Rodar `npm run verify`

---

## Dependencies & Execution Order

- **Setup (Phase 1)** antes de tudo: tokens e primitivos
- **Foundational (Phase 2)** bloqueia as histórias
- **US1** (shell) antes de **US7** (gaveta), que estende `app-sidebar.tsx`
- **US2** antes de **US3**, **US4** e **US5**, que compõem a mesma `page.tsx`
- **US6** depende só da Phase 1 e da `ThemeToggle` estar na barra (US1)

## Implementation Strategy

1. Phase 1 + Phase 2 → o vocabulário visual existe
2. US1 → shell (**PARE E VALIDE**: a suíte ponta a ponta navega pela barra)
3. US2 → Visão geral
4. US3, US4, US5 → segmentos, desempenho, ativação
5. US6, US7 → tema e celular
6. Polish

## Notes

- `"use client"` em: `app-sidebar.tsx`, `theme-provider.tsx`, `theme-toggle.tsx`,
  `trend-chart.tsx` (gráfico), `section-nav.tsx`
- Valores arbitrários de tipografia seguem a escala de `DESIGN.md`, registrados em Complexity
  Tracking
- Componentes de dashboard, barra lateral e tema sem teste de componente próprio — dívida
  registrada em Complexity Tracking
