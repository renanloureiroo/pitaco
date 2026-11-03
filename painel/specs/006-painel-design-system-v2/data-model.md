# Phase 1 — Modelo de dados do painel

Esta entrega não cria tipo de fronteira novo: a Visão geral lê com os schemas de `surveys`,
`results`, `collect` e `health`. O que ela acrescenta são tipos internos de agregação
(`features/analytics/lib/aggregate.ts`) e o vocabulário de tokens do Design System.

---

## Tokens (`src/app/globals.css`)

| Papel | Tokens | Uso |
|---|---|---|
| Planos | `background`, `card`/`surface`, `surface-raised`, `surface-sunken`, `sidebar` | fundo → card → destaque → poço |
| Texto | `foreground`/`ink`, `ink-secondary`, `ink-muted` | `ink-muted` é o mínimo para texto |
| Marca | `primary`, `primary-foreground`, `primary-ink`, `primary-soft`, `primary-active` | CTA, item ativo, um destaque por tela |
| Status | `success`, `danger`, `warning`, `info` + `-fill`, `-soft` | sem sufixo: texto/ícone; `-fill`: marca; `-soft`: fundo |
| Gráfico | `chart-1` … `chart-8`, `chart-grid`, `neutral-fill` | ordem fixa; cor segue a entidade |
| Bordas | `border`, `border-strong`, `input`, `ring` | hairline em card; forte em input e eixo |
| Raios | `xs` 2, `sm` 4, `md` 6, `lg` 8, `xl` 12, `pill` | badge, botão, card |

Cada token tem valor em `:root` (claro) e em `.dark` (escuro).

**Cores fixas por entidade**:

| Entidade | Cor |
|---|---|
| Concluídas | `chart-3` |
| Dispensadas | `chart-1` |
| Abandonadas | `chart-2` |
| Em andamento | `neutral-fill` |
| Promotores / Neutros / Detratores | `success-fill` / `neutral-fill` / `danger-fill` |

---

## Agregação (`features/analytics`)

### `OverviewPeriod`

`"7" | "30" | "90"`, padrão `"30"`. Rótulos: "7 dias", "30 dias", "90 dias".

`periodWindows(period, now)`:

| Campo | Valor |
|---|---|
| `current` | `{ from: now − N dias }` |
| `previous` | `{ from: now − 2N dias, to: now − N dias }` |
| `from`, `to` | limites do período atual, para preencher a linha do tempo |

### `Totals`

| Campo | Tipo | Observação |
|---|---|---|
| `displayed`, `completed`, `dismissed`, `abandoned`, `inProgress` | `number` | somas |
| `rate` | `number?` | `completed / displayed` sobre as somas; **ausente se `displayed = 0`** |

### `NpsTotals`

| Campo | Tipo | Observação |
|---|---|---|
| `respondents`, `promoters`, `passives`, `detractors` | `number` | somas |
| `score` | `number?` | `(promoters − detractors) / respondents × 100`; ausente sem respondente |

`sumNps` devolve `undefined` quando nenhuma pesquisa tem resumo de NPS.

### `ApplicationOverview`

| Campo | Tipo | Observação |
|---|---|---|
| `period` | `OverviewPeriod` | |
| `totals`, `previous` | `Totals` | período atual e anterior |
| `nps`, `previousNps` | `NpsTotals?` | |
| `timeline` | `{ day, displayed, completed }[]` | um ponto por dia UTC do período, zeros incluídos |
| `surveys` | `SurveyPerformance[]` | da mais exibida para a menos |
| `stateCounts` | `Record<estado, number>` | para "N no ar" |
| `respondents` | `number` | total da listagem de respondentes; 0 se a leitura falhar |
| `sdk` | `SdkVersions?` | ausente se a leitura falhar |
| `sdkErrors` | `number` | total da listagem de erros; 0 se a leitura falhar |
| `featured` | `{ id, name, attributes }?` | pesquisa de NPS com mais respondentes |

### `SurveyPerformance`

| Campo | Tipo | Observação |
|---|---|---|
| `id`, `name`, `state` | `string` | |
| `template` | `string?` | modelo de origem |
| `totals` | `Totals` | zeros para pesquisa nunca publicada |
| `previousRate` | `number?` | taxa no período anterior |
| `nps` | `NpsTotals?` | |
| `timeline` | `TimelinePoint[]` | para a miniatura |

Variação de taxa exibida só a partir de 0,5 ponto.

### `SegmentRow`

| Campo | Tipo | Observação |
|---|---|---|
| `value` | `string` | valor do atributo |
| `displayed`, `completed` | `number` | |
| `rate` | `number?` | |
| `nps` | `NpsTotals?` | |

Até seis linhas, dos valores mais frequentes. Valor cuja leitura falha é omitido.

### `KpiDelta`

| Campo | Tipo | Observação |
|---|---|---|
| `value` | `number` | variação |
| `unit` | `"relative" \| "points" \| "nps"` | "18,4%", "2,1 p.p.", "3 pontos" |
| `goodWhen` | `"up" \| "down"` | decide a cor; a seta segue o sinal |

---

## Formatos (`shared/lib/number-format.ts`)

| Função | Exemplo | Ausente |
|---|---|---|
| `formatInt` | 1.234 | — |
| `formatPercent` | 50% | "—" |
| `formatNps` | +42, −8, 0 | "—" |
| `formatDelta` | 18,4% · 2,1 p.p. · 3 pontos | — |
| `formatDay` / `formatLongDay` | 01/10 · qua., 01 de out. | — |
| `formatDuration` | 4,2s · 1min 5s | "—" |
| `formatCompact` | 1,2 mil | — |

---

## Estado de navegação (`AppSidebar`)

| Dado | Origem |
|---|---|
| aplicação atual | primeiro segmento após `/aplicacoes/`, exceto `nova` |
| aplicações do seletor | `{ id, name, status }`, da listagem (até 100) |
| item ativo | caminho exato para Visão geral; prefixo para os demais |
| gaveta aberta | estado local; fecha com Escape, fundo, botão e navegação |
