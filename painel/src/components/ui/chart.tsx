"use client"

import * as React from "react"
import * as RechartsPrimitive from "recharts"
import { cn } from "cn"

/**
 * Primitivo de gráfico no molde do `chart` do shadcn/ui, sobre Recharts. Agnóstico de domínio:
 * recebe uma `config` que dá rótulo e cor (um token `--chart-n` ou semântico) a cada série, e
 * expõe a cor como `--color-<chave>` para os elementos do Recharts usarem.
 */

export type ChartConfig = Record<string, { label?: React.ReactNode; color?: string }>

type ChartContextProps = { config: ChartConfig }

const ChartContext = React.createContext<ChartContextProps | null>(null)

function useChart() {
  const context = React.useContext(ChartContext)
  if (!context) {
    throw new Error("useChart precisa estar dentro de <ChartContainer />")
  }
  return context
}

function ChartContainer({
  id,
  className,
  children,
  config,
  ...props
}: React.ComponentProps<"div"> & {
  config: ChartConfig
  children: React.ComponentProps<typeof RechartsPrimitive.ResponsiveContainer>["children"]
}) {
  const uniqueId = React.useId()
  const chartId = `chart-${id ?? uniqueId.replace(/:/g, "")}`
  const style = Object.fromEntries(
    Object.entries(config)
      .filter(([, item]) => item.color !== undefined)
      .map(([key, item]) => [`--color-${key}`, item.color]),
  ) as React.CSSProperties

  return (
    <ChartContext.Provider value={{ config }}>
      <div
        data-slot="chart"
        data-chart={chartId}
        style={style}
        className={cn(
          "flex aspect-video justify-center text-xs [&_.recharts-cartesian-axis-tick_text]:fill-ink-muted [&_.recharts-cartesian-axis-tick_text]:tabular-nums [&_.recharts-cartesian-grid_line]:stroke-chart-grid [&_.recharts-curve.recharts-tooltip-cursor]:stroke-border-strong [&_.recharts-layer]:outline-hidden [&_.recharts-rectangle.recharts-tooltip-cursor]:fill-surface-sunken/60 [&_.recharts-reference-line_[stroke='#ccc']]:stroke-border [&_.recharts-sector]:outline-hidden [&_.recharts-surface]:outline-hidden",
          className,
        )}
        {...props}
      >
        <RechartsPrimitive.ResponsiveContainer>{children}</RechartsPrimitive.ResponsiveContainer>
      </div>
    </ChartContext.Provider>
  )
}

const ChartTooltip = RechartsPrimitive.Tooltip

type TooltipPayloadItem = {
  dataKey?: string | number
  name?: string | number
  value?: number | string
  color?: string
  payload?: Record<string, unknown>
}

function ChartTooltipContent({
  active,
  payload,
  label,
  labelFormatter,
  valueFormatter,
  hideLabel = false,
  className,
}: {
  active?: boolean
  payload?: TooltipPayloadItem[]
  label?: React.ReactNode
  labelFormatter?: (label: React.ReactNode, payload: TooltipPayloadItem[]) => React.ReactNode
  valueFormatter?: (value: number | string, item: TooltipPayloadItem) => React.ReactNode
  hideLabel?: boolean
  className?: string
}) {
  const { config } = useChart()

  if (!active || !payload?.length) {
    return null
  }

  return (
    <div
      className={cn(
        "grid min-w-36 gap-1.5 rounded-lg border border-border bg-popover px-3 py-2 text-xs text-popover-foreground shadow-lg",
        className,
      )}
    >
      {hideLabel ? null : (
        <div className="font-medium">{labelFormatter ? labelFormatter(label, payload) : label}</div>
      )}
      <div className="grid gap-1">
        {payload.map((item, index) => {
          const key = String(item.dataKey ?? item.name ?? index)
          const entry = config[key]
          const color = item.color ?? `var(--color-${key})`
          return (
            <div key={key} className="flex items-center gap-2">
              <span className="size-2.5 shrink-0 rounded-[2px]" style={{ background: color }} />
              <span className="text-ink-muted">{entry?.label ?? item.name}</span>
              <span className="ml-auto pl-3 font-medium tabular-nums">
                {item.value === undefined
                  ? "—"
                  : valueFormatter
                    ? valueFormatter(item.value, item)
                    : typeof item.value === "number"
                      ? item.value.toLocaleString("pt-BR")
                      : item.value}
              </span>
            </div>
          )
        })}
      </div>
    </div>
  )
}

/** Legenda em HTML, fora do SVG: identidade nunca só pela cor (texto ao lado da marca). */
function ChartLegendList({
  config,
  keys,
  className,
}: {
  config: ChartConfig
  keys?: string[]
  className?: string
}) {
  const entries = Object.entries(config).filter(([key]) => keys === undefined || keys.includes(key))

  return (
    <ul className={cn("flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-ink-secondary", className)}>
      {entries.map(([key, item]) => (
        <li key={key} className="flex items-center gap-1.5">
          <span className="size-2.5 rounded-[2px]" style={{ background: item.color }} />
          {item.label}
        </li>
      ))}
    </ul>
  )
}

export { ChartContainer, ChartTooltip, ChartTooltipContent, ChartLegendList, useChart }
