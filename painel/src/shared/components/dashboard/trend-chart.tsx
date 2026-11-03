"use client";

import { Area, AreaChart, CartesianGrid, XAxis, YAxis } from "recharts";

import {
  ChartContainer,
  ChartLegendList,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";

import { formatDay, formatLongDay, formatPercent } from "@/shared/lib/number-format";

export type TrendPoint = { day: string; displayed: number; completed: number };

const config = {
  displayed: { label: "Exibidas", color: "var(--chart-1)" },
  completed: { label: "Concluídas", color: "var(--chart-3)" },
} satisfies ChartConfig;

/**
 * Exibições e conclusões por dia (UTC). Duas séries na mesma unidade — contagem — num eixo só;
 * a taxa do dia aparece no tooltip, nunca num segundo eixo.
 */
export function TrendChart({ points, className }: { points: TrendPoint[]; className?: string }) {
  return (
    <div className={className} data-testid="trend-chart">
      <ChartLegendList config={config} className="mb-3" />
      <ChartContainer config={config} className="aspect-auto h-64 w-full">
        <AreaChart data={points} margin={{ left: 0, right: 8, top: 8, bottom: 0 }}>
          <defs>
            <linearGradient id="fill-displayed" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="var(--color-displayed)" stopOpacity={0.28} />
              <stop offset="95%" stopColor="var(--color-displayed)" stopOpacity={0.02} />
            </linearGradient>
            <linearGradient id="fill-completed" x1="0" y1="0" x2="0" y2="1">
              <stop offset="5%" stopColor="var(--color-completed)" stopOpacity={0.3} />
              <stop offset="95%" stopColor="var(--color-completed)" stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid vertical={false} />
          <XAxis
            dataKey="day"
            tickLine={false}
            axisLine={false}
            tickMargin={8}
            minTickGap={28}
            tickFormatter={formatDay}
          />
          <YAxis tickLine={false} axisLine={false} width={36} allowDecimals={false} />
          <ChartTooltip
            cursor={{ strokeDasharray: "3 3" }}
            content={
              <ChartTooltipContent
                labelFormatter={(_, payload) => {
                  const point = payload[0]?.payload as TrendPoint | undefined;
                  if (point === undefined) return null;
                  const rate = point.displayed > 0 ? point.completed / point.displayed : undefined;
                  return (
                    <span className="flex items-center justify-between gap-3">
                      <span className="capitalize">{formatLongDay(point.day)}</span>
                      <span className="font-normal text-ink-muted">taxa {formatPercent(rate)}</span>
                    </span>
                  );
                }}
              />
            }
          />
          <Area
            dataKey="displayed"
            type="monotone"
            stroke="var(--color-displayed)"
            strokeWidth={2}
            fill="url(#fill-displayed)"
            activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--card)" }}
          />
          <Area
            dataKey="completed"
            type="monotone"
            stroke="var(--color-completed)"
            strokeWidth={2}
            fill="url(#fill-completed)"
            activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--card)" }}
          />
        </AreaChart>
      </ChartContainer>
    </div>
  );
}
