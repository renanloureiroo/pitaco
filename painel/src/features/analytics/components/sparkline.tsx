import type { TimelinePoint } from "@/features/results";

/** Miniatura da tendência de exibições: só forma, sem eixo — o número está na coluna ao lado. */
export function Sparkline({ points, className }: { points: TimelinePoint[]; className?: string }) {
  if (points.length < 2) {
    return <span className="text-xs text-ink-muted">—</span>;
  }
  const width = 96;
  const height = 28;
  const peak = Math.max(...points.map((point) => point.displayed), 1);
  const step = width / (points.length - 1);
  const path = points
    .map((point, index) => `${index === 0 ? "M" : "L"}${(index * step).toFixed(1)},${(height - 2 - (point.displayed / peak) * (height - 4)).toFixed(1)}`)
    .join(" ");

  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} className={className} aria-hidden>
      <path d={`${path} L${width},${height} L0,${height} Z`} className="fill-chart-1/15" />
      <path d={path} className="fill-none stroke-chart-1" strokeWidth={1.5} strokeLinejoin="round" />
    </svg>
  );
}
