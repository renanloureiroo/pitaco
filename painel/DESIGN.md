# Design System Pitaco (v2)

A fonte da verdade visual do painel é o Design System **Pitaco**, publicado como artifact
(tokens, guia de marca e componentes com preview). Este arquivo é o resumo para quem escreve
código aqui; os valores vivem em `src/app/globals.css`, que traduz os tokens para Tailwind 4.

## Princípios

1. **O dado é o herói.** Cor, peso e tamanho vão para o número; o resto recua. Um card, um número principal.
2. **Um amarelo, muito pouco.** `primary` (`#FCD535`) marca o CTA principal, o item ativo da navegação e no máximo um destaque por tela. Nunca é série de gráfico. Amarelo como texto usa `primary-ink`.
3. **Todo número carrega a régua.** Contagem junto da proporção, definição do cálculo ao alcance, recorte ativo visível. "Sem dado" é `—`, nunca zero.
4. **Escuro primeiro, claro de verdade.** Escuro é o padrão (`next-themes`, classe `.dark`); o claro tem os próprios passos. Os dois passam 4.5:1 em texto.
5. **Status nunca só na cor.** Seta, ícone ou rótulo junto (▲ ▼, "No ar", "Pausada").

## Tokens (classes Tailwind)

| Papel | Classes | Regra |
| --- | --- | --- |
| Planos | `bg-background` → `bg-card`/`bg-surface` → `bg-surface-raised` → `bg-surface-sunken`; `bg-sidebar` | Card sempre `bg-card border border-border rounded-xl` |
| Texto | `text-foreground`/`text-ink`, `text-ink-secondary`, `text-ink-muted` | `ink-muted` é o mínimo para qualquer texto |
| Marca | `bg-primary text-primary-foreground`, `text-primary-ink`, `bg-primary-soft` | Fundo amarelo sempre com `primary-foreground` |
| Status | `text-success` / `bg-success-fill` / `bg-success-soft` (idem `danger`, `warning`, `info`) | Sem sufixo = texto/ícone; `-fill` = marca; `-soft` = fundo de badge/alerta |
| Gráfico | `bg-chart-1`…`bg-chart-8`, `var(--chart-n)`, `stroke-chart-grid`, `bg-neutral-fill` | Ordem fixa, cor segue a entidade; validada para daltonismo nos dois temas |
| Bordas | `border-border`, `border-border-strong` | Hairline para cards; `strong` para inputs e eixo |

**NPS** usa semântica, não série: promotores `success-fill`, neutros `neutral-fill`,
detratores `danger-fill`, sempre com a legenda das faixas.

**Desfechos** de exibição: concluídas `chart-3`, dispensadas `chart-1`, abandonadas `chart-2`,
em andamento `neutral-fill`.

## Tipografia

Inter (`font-sans`) para tudo; JetBrains Mono (`font-mono`) para identificadores (prefixo de
chave, id, versão de SDK). Números em coluna com `tabular-nums`.

| Estilo | Uso | Classes |
| --- | --- | --- |
| kpi-xl | Número-herói (NPS, taxa) | `text-4xl font-semibold tracking-tight` |
| kpi | Valor de KPI | `text-[28px] leading-8 font-semibold tabular-nums` |
| h1 / h2 / h3 | Página / seção / card | `text-2xl` / `text-lg` / `text-[15px]`, `font-semibold` |
| label | Rótulo de KPI, cabeçalho | `text-[11px] font-medium tracking-[0.06em] uppercase text-ink-muted` |

## Espaço e forma

Escala de 4px. Card com `p-5`, grade de dashboard com `gap-4`. Raios: `rounded-sm` (badge,
ponta de barra), `rounded-md` (botão, input), `rounded-xl` (card). Profundidade por camada de
superfície e hairline, não por sombra.

## Layout

- **Shell** (`app/aplicacoes/layout.tsx`): `AppSidebar` de 240px com marca, troca de tema,
  seletor de aplicação e as seções (Análise: Visão geral, Pesquisas, Respondentes; Operação:
  Chaves, Saúde, Privacidade). Abaixo de `lg` vira gaveta.
- **Página**: conteúdo até 1280px, `px-4 sm:px-8`. Trilha → título + ações → filtros → KPIs (4
  colunas) → gráficos (2–3 colunas) → tabelas.
- **Pesquisa**: abas `SectionNav` Montagem · Disparo · Publicação · Versões · Exibições ·
  Resultados · Comportamento.

## Componentes de dashboard

Em `src/shared/components/dashboard` (agnósticos de domínio): `KpiCard`, `ChartCard`,
`TrendChart` (Recharts via `components/ui/chart.tsx`), `OutcomeBreakdown`, `NpsBreakdown`.
Peças específicas ficam nas features: `features/analytics` (visão geral, segmentos, desempenho
por pesquisa, SDK) e `features/results` (resultados por pergunta, comportamento).

## Gráficos

- A forma segue a tarefa: tempo → área/linha; partes de um todo → barra 100%; ranking de
  opções → barras horizontais; conversão por etapa → funil; um número → `KpiCard`.
- Linha de 2px, área em degradê, ponta de barra `rounded-sm`, 2px de vão entre segmentos.
- Tooltip com valor absoluto e proporção; legenda sempre que há 2+ séries.
- Nunca dois eixos Y. Grade recessiva (`chart-grid`), eixos `ink-muted` 11px.
