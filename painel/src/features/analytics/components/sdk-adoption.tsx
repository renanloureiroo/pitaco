import type { SdkVersions } from "@/features/health";
import { cn } from "@/lib/utils";

import { formatPercent } from "@/shared/lib";

const COLORS = ["bg-chart-1", "bg-chart-2", "bg-chart-3"] as const;

/** Adoção das versões do SDK na janela recente: quem ainda está numa versão antiga. */
export function SdkAdoption({ sdk }: { sdk: SdkVersions | undefined }) {
  const versions = [...(sdk?.versions ?? [])]
    .filter((version) => version.recentRequestCount > 0)
    .sort((a, b) => b.recentRequestCount - a.recentRequestCount);
  if (versions.length === 0) {
    return <p className="text-sm text-ink-muted">Nenhum tráfego de SDK na janela recente.</p>;
  }
  const top = versions.slice(0, 3);
  const rest = versions.slice(3).reduce((sum, version) => sum + version.recentRequestCount, 0);
  const total = versions.reduce((sum, version) => sum + version.recentRequestCount, 0);
  const rows = [
    ...top.map((version, index) => ({ label: `v${version.version}`, count: version.recentRequestCount, color: COLORS[index] })),
    ...(rest > 0 ? [{ label: "Outras", count: rest, color: "bg-neutral-fill" }] : []),
  ];

  return (
    <div className="flex flex-col gap-3" data-testid="sdk-adoption">
      <div className="flex h-3 gap-0.5" role="img" aria-label="Proporção de requisições por versão do SDK">
        {rows.map((row, index) => (
          <span
            key={row.label}
            className={cn(row.color, index === 0 && "rounded-l-sm", index === rows.length - 1 && "rounded-r-sm")}
            style={{ flexGrow: row.count, flexBasis: 0 }}
          />
        ))}
      </div>
      <ul className="flex flex-col gap-1.5 text-xs">
        {rows.map((row) => (
          <li key={row.label} className="flex items-center gap-2">
            <span className={cn("size-2.5 rounded-[2px]", row.color)} aria-hidden />
            <span className="font-mono text-ink-secondary">{row.label}</span>
            <span className="ml-auto tabular-nums text-ink-muted">{formatPercent(row.count / total)}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
