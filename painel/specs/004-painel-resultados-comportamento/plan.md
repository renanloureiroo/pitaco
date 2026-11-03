# Implementation Plan: Painel de resultados e comportamento — números por pergunta, recorte, respostas abertas, export e interação

**Branch**: `004-painel-resultados-comportamento` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-painel-resultados-comportamento/spec.md`

## Summary

Duas abas novas na pesquisa — Resultados e Comportamento — sobre quatro leituras do backend
(resultado, respostas abertas, comportamento, export) e uma rota de repasse no próprio painel
para o CSV. A abordagem repete o que 001 e 002 estabeleceram: uma feature nova
`src/features/results/`, leitura em Server Components, Zod na fronteira, recorte na URL.

A feature é quase toda leitura. Não há Server Action: o único "efeito" é o download, e ele passa
por um Route Handler que repassa o fluxo do backend sem carregá-lo na memória, porque nada no
navegador fala com a API ([R3](./research.md#r3)).

Três decisões carregam o desenho: um único recorte, lido da URL uma vez e convertido para o
formato do backend uma vez, alimenta todas as leituras da tela ([R2](./research.md#r2)); cada
tipo de pergunta tem a sua forma, com contagem e proporção sempre juntas e ausência nunca como
zero ([R4](./research.md#r4)); e o comportamento é exibido como o backend o apura, com as
definições que ele devolve, sem recalcular nada além de somas e médias de apresentação
([R6](./research.md#r6)).

## Technical Context

**Language/Version**: TypeScript 5 em modo estrito, React 19.2, Node 20+

**Primary Dependencies**: Next.js 16.3 (App Router, Route Handlers), Zod 4, Tailwind CSS 4,
shadcn/ui. Nenhuma dependência nova nesta feature. A linha do tempo nasce como lista de dias
(`timeline-point`); o gráfico de área que hoje fica sobre ela veio depois, com o `TrendChart`
compartilhado de `006-painel-design-system-v2`.

**Storage**: N/A — recorte e busca vivem na URL

**Testing**: Vitest 4 + Testing Library; Playwright 1.63 contra o servidor de API simulado

**Target Platform**: navegadores modernos; renderização no servidor

**Project Type**: aplicação web (Next.js App Router) consumindo a API administrativa

**Performance Goals**: leituras de cada tela em paralelo (5 em Resultados, 3 em Comportamento);
nenhuma leitura cacheada; o CSV repassado em fluxo, sem buffer no painel

**Constraints**:
- O navegador nunca fala com o backend — inclusive no download
- Nenhum número que o backend não apure; o painel só soma ou faz média para resumir indicadores
  de apresentação, e diz no rótulo o que somou ("soma das medianas", "média entre as perguntas")
- Ausência nunca exibida como zero
- `"use client"` só nos formulários de recorte e de busca, no botão de export, no controle de
  atualização e no gráfico

**Scale/Scope**: 2 rotas de tela novas, 1 Route Handler, 1 feature nova, 4 leituras de API

## Constitution Check

*GATE: verificado antes da Phase 0 e reavaliado após a Phase 1.*

| Princípio | Como esta feature adere | Veredito |
|---|---|---|
| **I. Arquitetura Feature-Based** | Domínio novo em `src/features/results/` (`api/`, `schemas/`, `components/`, `lib/`, `__tests__/`, `index.ts`). `results` não importa nenhuma outra feature. As páginas em `app/` compõem `results`, `surveys` (`getVersionComparability`) e `health` (avisos de saúde) — a composição fica em `app/`, sem dependência entre features. | ✅ |
| **II. shadcn/ui** | Nenhum primitivo novo: `Card`, `Select`, `Input`, `Button`, `Table`, `Field` e o `ConfirmDialog` compartilhado. Barras, histograma e funil são composição de `div`s com tokens — não há primitivo shadcn para eles. | ✅ |
| **III. Tailwind** | Tokens do tema (`bg-chart-N`, `bg-success-fill`, `bg-danger-fill`, `bg-neutral-fill`); `style` inline só para larguras e alturas proporcionais aos dados, valores de execução. | ✅ |
| **IV. Vitest (não negociável)** | Testados: leitura e conversão do recorte (`filters.test.ts`), montagem de query e URL de export (`results-api.test.ts`), contagem e proporção, ausência, NPS, média, vazios, busca, export com confirmação (`results-components.test.tsx`), comparabilidade e não aplicáveis (`advanced-authoring-components.test.tsx`). | ✅ (ver Complexity Tracking) |
| **V. Playwright nos fluxos críticos** | `e2e/resultados.spec.ts` cobre números conferidos com o semeado, vazios, recorte na URL, busca e export com conteúdo do CSV. | ✅ |
| **Server first** | As duas páginas são Server Components; cliente só onde há interação. | ✅ |
| **Validação de fronteira** | Todo corpo passa por Zod; `searchParams` passa por `parseResultsFilters` e `parseTerm`, com valor inválido caindo no padrão. | ✅ |
| **Sem dependência nova** | Nenhuma. | ✅ |

**Resultado do portão (pré-Phase 0)**: aprovado.

**Resultado do portão (pós-Phase 1)**: aprovado com uma ressalva registrada em Complexity
Tracking — os painéis de comportamento não têm teste de componente próprio.

## Project Structure

### Documentation (this feature)

```text
specs/004-painel-resultados-comportamento/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── backend-api.md   # Resultado, respostas abertas, comportamento, export
│   └── ui-routes.md     # Abas, Route Handler de export, estados e testids
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md
```

### Source Code (repository root)

```text
src/
├── app/
│   ├── api/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados/export/
│   │   └── route.ts                           # NOVO: repasse do CSV em fluxo
│   └── aplicacoes/[applicationId]/pesquisas/[surveyId]/
│       ├── layout.tsx                         # ALTERADO: abas Resultados e Comportamento
│       ├── resultados/page.tsx                # NOVO
│       └── comportamento/page.tsx             # NOVO
│
└── features/results/                          # NOVA FEATURE
    ├── index.ts
    ├── api/
    │   ├── paths.ts                           # caminhos do backend e da rota de export do painel
    │   └── results.ts                         # getSurveyResults, listOpenAnswers, getSurveyBehavior, backendExportUrl, exportHref
    ├── schemas/
    │   ├── results.ts                         # resultado, agregados, comparabilidade, NPS, retenção, resposta aberta
    │   ├── behavior.ts                        # comportamento, vias, tempo ativo, definições
    │   └── filters.ts                         # recorte na URL ↔ query do backend
    ├── lib/
    │   └── results-labels.ts                  # formatos, textos, vazios, comparabilidade, retenção
    ├── components/
    │   ├── response-rate-card.tsx
    │   ├── nps-summary-card.tsx
    │   ├── question-result-card.tsx           # barras, histograma, NPS, comparabilidade
    │   ├── results-filters.tsx                # "use client"
    │   ├── active-filters.tsx
    │   ├── open-answers-list.tsx
    │   ├── open-answers-search.tsx            # "use client"
    │   ├── export-button.tsx                  # "use client"
    │   ├── refresh-control.tsx                # "use client"
    │   ├── retention-note.tsx                 # usado por 005
    │   └── behavior-panels.tsx                # funil, tempo, dispensas, atrito, definições, resumo
    └── __tests__/
        ├── filters.test.ts
        ├── results-api.test.ts
        ├── results-components.test.tsx
        ├── advanced-authoring-components.test.tsx
        └── retention-note.test.tsx            # de 005

e2e/
├── resultados.spec.ts                         # NOVO
└── stub-api/routes/
    ├── results.ts                             # NOVO: results, open-answers, export, behavior
    └── index.ts                               # ALTERADO
```

**Structure Decision**: feature nova `results`, espelhando o módulo `results` do backend. O
resultado não é da pesquisa no sentido do agregado de autoria — é uma leitura derivada da coleta,
com regras próprias (recorte, comparabilidade, amostra). Colocá-lo em `surveys` ou em `collect`
inflaria uma fronteira com uma responsabilidade que não é dela ([R1](./research.md#r1)).

## Complexity Tracking

| Ressalva | Por que existe | Encaminhamento |
|---|---|---|
| `behavior-panels.tsx` e a página de comportamento sem teste de componente próprio | Os painéis só apresentam números já apurados; os cálculos de apresentação são uma divisão (cobertura, proporção de dispensas) e uma média (troca de resposta) | A aba é coberta pelo cartão-resumo no E2E de resultados; um teste de componente para funil e vazios é dívida registrada |
