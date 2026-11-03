/**
 * Formatação de apresentação.
 *
 * Regra que este módulo existe para proteger (FR-011): um prazo **não configurado** chega
 * ausente do backend e é exibido como "não configurado". Nunca como `0` — que é um valor
 * legítimo e diferente em significado. Por isso a checagem é por `undefined`/`null`, jamais
 * por veracidade.
 */

export const NOT_CONFIGURED = "não configurado";

export const REFERENCE_TIME_ZONE = "America/Sao_Paulo";

export const TIMEZONE_NOTE = "Horários em Brasília (UTC−3)";

const dateTimeFormatter = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: REFERENCE_TIME_ZONE,
});

const dateFormatter = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeZone: REFERENCE_TIME_ZONE,
});

/** Data ISO UTC do backend em data e hora locais pt-BR. Entrada inválida volta como veio. */
export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : dateTimeFormatter.format(date);
}

export function formatDate(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : dateFormatter.format(date);
}

/** Valor opcional para exibição: ausente vira "não configurado", `0` continua `0`. */
export function orNotConfigured(value: string | number | undefined | null): string {
  return value === undefined || value === null ? NOT_CONFIGURED : String(value);
}

/** Prazo em dias, pluralizado. Ausente vira "não configurado". */
export function formatDays(value: number | undefined | null): string {
  if (value === undefined || value === null) {
    return NOT_CONFIGURED;
  }

  return value === 1 ? "1 dia" : `${value} dias`;
}

/** Taxa de amostragem 0.0..1.0 como percentual. `0` é exibido, não escondido. */
export function formatSamplingRate(value: number | undefined | null): string {
  if (value === undefined || value === null) {
    return NOT_CONFIGURED;
  }

  return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 2 }).format(value * 100)}%`;
}

const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(:\d{2})?$/;

function naiveMillis(value: string): number | undefined {
  const match = LOCAL_DATE_TIME.exec(value);
  if (match === null) {
    return undefined;
  }

  const [year, month, day, hour, minute] = match.slice(1, 6).map(Number);
  const millis = Date.UTC(year, month - 1, day, hour, minute);
  const rebuilt = new Date(millis);

  const sameDate =
    rebuilt.getUTCFullYear() === year &&
    rebuilt.getUTCMonth() === month - 1 &&
    rebuilt.getUTCDate() === day &&
    rebuilt.getUTCHours() === hour &&
    rebuilt.getUTCMinutes() === minute;

  return sameDate ? millis : undefined;
}

function offsetAt(millis: number): number {
  const name = new Intl.DateTimeFormat("en-US", {
    timeZone: REFERENCE_TIME_ZONE,
    timeZoneName: "longOffset",
  })
    .formatToParts(new Date(millis))
    .find((part) => part.type === "timeZoneName")?.value;

  const match = /GMT([+-])(\d{2}):(\d{2})/.exec(name ?? "");
  if (match === null) {
    return 0;
  }

  const minutes = Number.parseInt(match[2], 10) * 60 + Number.parseInt(match[3], 10);
  return (match[1] === "-" ? -1 : 1) * minutes * 60_000;
}

export function toUtcInstant(local: string | undefined): string | undefined {
  const asIfUtc = local === undefined ? undefined : naiveMillis(local);
  if (asIfUtc === undefined) {
    return undefined;
  }

  const guess = asIfUtc - offsetAt(asIfUtc);
  return new Date(asIfUtc - offsetAt(guess)).toISOString();
}

export function toLocalInput(iso: string | undefined): string | undefined {
  if (iso === undefined) {
    return undefined;
  }

  const millis = Date.parse(iso);
  if (Number.isNaN(millis)) {
    return undefined;
  }

  return new Date(millis + offsetAt(millis)).toISOString().slice(0, 16);
}
