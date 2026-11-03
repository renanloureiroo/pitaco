# Implementation Plan: Painel com Design System v2 — shell com barra lateral, tema claro e escuro, Visão geral da aplicação e celular

**Branch**: `006-painel-design-system-v2` | **Date**: 2026-10-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-painel-design-system-v2/spec.md`

## Summary

Três frentes que se apoiam umas nas outras:

1. **Design System v2 como código**: tokens dos dois temas em `src/app/globals.css` (nomes do
   shadcn mais os papéis do Pitaco — superfícies, tinta, marca, status, gráfico), tipografia Inter
   e JetBrains Mono, os primitivos de `src/components/ui` ajustados aos tokens, e `DESIGN.md` como
   resumo de regras para quem escreve código ([R1](./research.md#r1)).
2. **Shell**: `AppSidebar` no layout de `/aplicacoes`, com seletor de aplicação, grupos de seções e
   gaveta no celular; `ThemeProvider` e `ThemeToggle` sobre `next-themes`, escuro por padrão
   ([R2](./research.md#r2), [R3](./research.md#r3)).
3. **Visão geral da aplicação**: feature nova `src/features/analytics/`, que soma no servidor do
   painel o que `results` apura por pesquisa, mais peças de dashboard agnósticas de domínio em
   `src/shared/components/dashboard/` ([R4](./research.md#r4), [R5](./research.md#r5)).

O resto do painel não muda de comportamento: as telas de 001 a 005 passam a usar os tokens e o
shell. A navegação da aplicação, que era uma fileira de abas no layout da aplicação, sai dali e vai
para a barra lateral; as abas da pesquisa continuam (`SectionNav`), restilizadas.

## Technical Context

**Language/Version**: TypeScript 5 em modo estrito, React 19.2, Node 20+

**Primary Dependencies**: Next.js 16.3, Tailwind CSS 4 (`@theme inline`, `@custom-variant dark`),
shadcn/ui sobre Radix, `lucide-react`, `next/font` (Inter, JetBrains Mono). **Nova dependência de
runtime**: `recharts`, via o primitivo `chart` do shadcn ([R6](./research.md#r6)). `next-themes`
já vinha com o `sonner` de 001 e passa a ser usado para o tema.

**Storage**: tema no armazenamento do navegador (`next-themes`); período e segmento na URL

**Testing**: Vitest 4 + Testing Library; Playwright 1.63

**Target Platform**: navegadores modernos, desktop e celular

**Project Type**: aplicação web (Next.js App Router)

**Performance Goals**: a Visão geral lê a lista de pesquisas e, em paralelo, o resultado de cada
pesquisa publicada no período atual e no anterior, os respondentes e o SDK; a análise por segmento
faz até seis leituras em paralelo depois disso. O corpo dos layouts não lê dado: barra lateral e
trilha carregam sob `<Suspense>`

**Constraints**:
- Nenhuma métrica nova: só somas de contagens já apuradas e o NPS recalculado sobre grupos somados
- `"use client"` só onde há estado de navegador: barra lateral (rota atual e gaveta), troca de
  tema, provedor de tema, gráfico
- Componentes de `src/shared/components/dashboard` sem tipo de feature: recebem números e rótulos

**Scale/Scope**: 1 feature nova, 5 componentes de dashboard, 1 shell, ~20 primitivos ajustados,
todas as telas restilizadas

## Constitution Check

*GATE: verificado antes da Phase 0 e reavaliado após a Phase 1.*

| Princípio | Como esta feature adere | Veredito |
|---|---|---|
| **I. Arquitetura Feature-Based** | A Visão geral é `src/features/analytics/`, que importa `results`, `surveys`, `collect` e `health` **só pelos `index.ts`**. Nenhuma dessas importa `analytics` — sem ciclo. Peças visuais sem domínio (`KpiCard`, `ChartCard`, `TrendChart`, `OutcomeBreakdown`, `NpsBreakdown`) vão para `src/shared/components/dashboard`, porque `results` e `analytics` as usam. `AppSidebar`, `ThemeProvider` e `ThemeToggle` ficam em `src/shared/components`. A página da aplicação em `app/` só compõe. | ✅ |
| **II. shadcn/ui** | O gráfico vem do primitivo `chart` do shadcn (`src/components/ui/chart.tsx`), não de uma biblioteca de componentes nova. A gaveta do celular é composição de `Button` e um `aside` com `role="dialog"`; o seletor é `DropdownMenu`. Os primitivos existentes foram ajustados por variantes e tokens, não copiados. | ✅ |
| **III. Tailwind** | Tokens em `globals.css`, a única folha global. A escala tipográfica do Design System usa valores arbitrários (`text-[11px]`, `text-[13px]`, `text-[15px]`, `text-[28px]`, `max-w-[1280px]`, `tracking-[0.06em]`), justificados em Complexity Tracking. `style` inline só para larguras proporcionais a dados. | ✅ (ver Complexity Tracking) |
| **IV. Vitest (não negociável)** | A regra de negócio desta feature é a agregação: `sumTotals`, `sumNps`, `mergeTimelines`, `relativeChange`, `pointChange`, `parseOverviewPeriod`, `periodWindows` testadas em `aggregate.test.ts`. Os componentes compartilhados de estado continuam testados (`shared-components.test.tsx`), e o primitivo `Button` tem teste próprio. | ✅ (ver Complexity Tracking) |
| **V. Playwright nos fluxos críticos** | Nenhum fluxo crítico novo; os existentes passam a navegar pela barra lateral (`coleta.spec.ts` → Respondentes, `saude.spec.ts` → Saúde, `privacidade.spec.ts` → Privacidade). `smoke.spec.ts` confere a raiz. Ativar e desativar coberto em `aplicacoes.spec.ts`. | ✅ |
| **Server first** | Visão geral é Server Component; o período e o segmento são vínculos (`Link`), não estado de cliente. | ✅ |
| **Validação de fronteira** | `periodo` por `parseOverviewPeriod`; `segmento` só vale se casar com um atributo lido. As leituras usam os schemas das features de origem. | ✅ |
| **Nova dependência justificada** | `recharts`, em [R6](./research.md#r6). | ✅ |

**Resultado do portão (pré-Phase 0)**: aprovado com uma dependência nova e valores arbitrários a
justificar.

**Resultado do portão (pós-Phase 1)**: aprovado, com as ressalvas de Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/006-painel-design-system-v2/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── backend-api.md   # O que a Visão geral lê (sem rota nova no backend)
│   └── ui-routes.md     # Shell, Visão geral, tema, celular
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md
```

### Source Code (repository root)

```text
DESIGN.md                                      # NOVO: resumo do Design System v2
package.json                                   # ALTERADO: recharts

src/
├── app/
│   ├── globals.css                            # REESCRITO: tokens v2, claro e escuro
│   ├── layout.tsx                             # ALTERADO: Inter + JetBrains Mono, ThemeProvider, tema escuro
│   └── aplicacoes/
│       ├── layout.tsx                         # NOVO: shell com AppSidebar sob <Suspense>
│       ├── page.tsx, nova/page.tsx            # restilizadas
│       └── [applicationId]/
│           ├── layout.tsx                     # ALTERADO: só trilha; a navegação foi para a barra lateral
│           └── page.tsx                       # REESCRITO: Visão geral
│
├── components/ui/                             # ajustados aos tokens
│   ├── chart.tsx                              # NOVO: primitivo de gráfico (Recharts)
│   ├── button.tsx, badge.tsx, card.tsx, table.tsx, …
│   └── __tests__/button.test.tsx
│
├── shared/
│   ├── components/
│   │   ├── app-sidebar.tsx                    # NOVO
│   │   ├── theme-provider.tsx                 # NOVO
│   │   ├── theme-toggle.tsx                   # NOVO
│   │   ├── section-nav.tsx                    # ALTERADO: rolagem horizontal, item ativo
│   │   ├── dashboard/
│   │   │   ├── index.ts                       # NOVO
│   │   │   ├── kpi-card.tsx                   # NOVO
│   │   │   ├── chart-card.tsx                 # NOVO
│   │   │   ├── trend-chart.tsx                # NOVO
│   │   │   ├── outcome-breakdown.tsx          # NOVO
│   │   │   └── nps-breakdown.tsx              # NOVO
│   │   └── index.ts                           # ALTERADO
│   └── lib/
│       ├── number-format.ts                   # NOVO: formatInt, formatPercent, formatNps, formatDelta, formatDay, formatDuration…
│       └── index.ts                           # ALTERADO
│
└── features/analytics/                        # NOVA FEATURE
    ├── index.ts
    ├── api/overview.ts                        # getApplicationOverview, getSegmentBreakdown
    ├── lib/aggregate.ts                       # somas, variações, períodos
    ├── components/
    │   ├── period-tabs.tsx
    │   ├── segment-breakdown.tsx
    │   ├── survey-performance-table.tsx
    │   ├── sparkline.tsx
    │   └── sdk-adoption.tsx
    └── __tests__/aggregate.test.ts

e2e/
├── smoke.spec.ts                              # raiz → listagem
└── coleta.spec.ts, saude.spec.ts,
    privacidade.spec.ts, aplicacoes.spec.ts    # ALTERADOS: navegação pela barra lateral; ativar/desativar
```

**Structure Decision**: `analytics` é feature própria porque a Visão geral é um agregado de
leitura **da aplicação**, que atravessa pesquisas — não pertence a `results` (por pesquisa) nem a
`applications` (cadastro). As peças de dashboard sobem para `shared` porque já nascem com dois
consumidores (`results` e `analytics`) e não carregam domínio ([R5](./research.md#r5)).

## Complexity Tracking

| Violação / custo | Por que é necessário | Alternativa mais simples rejeitada porque |
|---|---|---|
| Dependência nova `recharts` | Gráfico de área com tooltip acessível, legenda e grade nos dois temas | SVG à mão para série temporal com tooltip é o que a biblioteca resolve; o primitivo `chart` do shadcn já a assume ([R6](./research.md#r6)) |
| Valores arbitrários de tipografia e largura | São a escala do Design System v2 (`kpi` 28px, `label` 11px, corpo 13px, título de card 15px, conteúdo até 1280px), registrada em `DESIGN.md` | Os passos padrão do Tailwind não coincidem com a escala; criar tokens de fonte para cada passo foi adiado |
| Visão geral faz 2 leituras por pesquisa publicada (período atual e anterior) | Não existe endpoint consolidado da aplicação | Pedir um endpoint novo ao backend atrasaria a entrega; o teto é a página de 100 pesquisas ([R4](./research.md#r4)) |
| Componentes de dashboard, barra lateral e tema sem teste de componente próprio | São apresentação sem regra de negócio; a regra (agregação) está testada | Testes de componente para `KpiCard` (sentido da variação) e `AppSidebar` (item ativo) são dívida registrada |
