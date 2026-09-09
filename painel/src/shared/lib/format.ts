/**
 * Formatação de apresentação.
 *
 * Regra que este módulo existe para proteger (FR-011): um prazo **não configurado** chega
 * ausente do backend e é exibido como "não configurado". Nunca como `0` — que é um valor
 * legítimo e diferente em significado. Por isso a checagem é por `undefined`/`null`, jamais
 * por veracidade.
 */

export const NOT_CONFIGURED = "não configurado";

/**
 * Fuso de referência do painel (R3 de 002).
 *
 * As telas são Server Components: o fuso de quem lê não existe no servidor, e obtê-lo exigiria
 * `"use client"` em toda célula de data. O painel formata num fuso fixo e **diz qual é** — é o
 * que preserva a intenção de FR-027, que nenhum instante apareça sem que se saiba em que fuso
 * ele está. O filtro de período interpreta o que a pessoa digita neste mesmo fuso.
 */
export const REFERENCE_TIME_ZONE = "America/Sao_Paulo";

/** Exibido uma vez por tela de coleta, junto das colunas de instante. */
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
