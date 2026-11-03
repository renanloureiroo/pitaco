/** Formatos pt-BR da visão geral. Ausente é "—", nunca zero. */

const integer = new Intl.NumberFormat("pt-BR");
const percent = new Intl.NumberFormat("pt-BR", { style: "percent", maximumFractionDigits: 1 });
const compact = new Intl.NumberFormat("pt-BR", { notation: "compact", maximumFractionDigits: 1 });
const decimal = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 });

export const formatInt = (value: number): string => integer.format(value);
export const formatCompact = (value: number): string => compact.format(value);
export const formatPercent = (value: number | undefined): string =>
  value === undefined ? "—" : percent.format(value);

/** NPS sempre com sinal: +42, −8, 0. */
export function formatNps(score: number | undefined): string {
  if (score === undefined) {
    return "—";
  }
  const rounded = Math.round(score);
  return rounded > 0 ? `+${rounded}` : rounded < 0 ? `−${Math.abs(rounded)}` : "0";
}

/** Variação relativa (0.184 → "18,4%") ou em pontos ("2,1 p.p."). */
export function formatDelta(value: number, unit: "relative" | "points" | "nps"): string {
  const abs = Math.abs(value);
  if (unit === "relative") {
    return percent.format(abs);
  }
  if (unit === "points") {
    return `${decimal.format(abs * 100)} p.p.`;
  }
  return `${decimal.format(abs)} ${abs === 1 ? "ponto" : "pontos"}`;
}

const dayFormatter = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", timeZone: "UTC" });
const longDayFormatter = new Intl.DateTimeFormat("pt-BR", {
  weekday: "short",
  day: "2-digit",
  month: "short",
  timeZone: "UTC",
});

/** `YYYY-MM-DD` (dia UTC) como `01/10`. */
export const formatDay = (day: string): string => dayFormatter.format(new Date(`${day}T00:00:00Z`));
export const formatLongDay = (day: string): string => longDayFormatter.format(new Date(`${day}T00:00:00Z`));

export function formatDuration(ms: number | undefined): string {
  if (ms === undefined) {
    return "—";
  }
  const seconds = ms / 1000;
  return seconds < 60 ? `${decimal.format(seconds)}s` : `${Math.floor(seconds / 60)}min ${Math.round(seconds % 60)}s`;
}
