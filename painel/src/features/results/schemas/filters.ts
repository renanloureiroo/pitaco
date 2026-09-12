import { parseStatusParam, type RawSearchParam } from "@/shared/api";
import { toUtcInstant } from "@/shared/lib";

/**
 * O recorte vive na URL (R12): compartilhável, e o "voltar" do navegador devolve a mesma tela.
 *
 * Período por atalho (`periodo=7|30|90`) vence o período personalizado (`de`/`ate`). Atributo
 * vem em duas formas: com valor (`atributo=plano&valor=pro`) ou ausente
 * (`atributo=plano&ausente=1`), que é o recorte de quem não enviou o atributo.
 */

export const PERIOD_PARAM = "periodo";
export const FROM_PARAM = "de";
export const TO_PARAM = "ate";
export const ATTRIBUTE_PARAM = "atributo";
export const VALUE_PARAM = "valor";
export const ABSENT_PARAM = "ausente";
export const VERSION_PARAM = "versao";
export const TERM_PARAM = "q";

export const FILTER_PARAMS = [
  PERIOD_PARAM,
  FROM_PARAM,
  TO_PARAM,
  ATTRIBUTE_PARAM,
  VALUE_PARAM,
  ABSENT_PARAM,
  VERSION_PARAM,
] as const;

export const PERIOD_SHORTCUTS = ["7", "30", "90"] as const;
export type PeriodShortcut = (typeof PERIOD_SHORTCUTS)[number];

export const PERIOD_LABELS: Record<PeriodShortcut, string> = {
  "7": "Últimos 7 dias",
  "30": "Últimos 30 dias",
  "90": "Últimos 90 dias",
};

export const PERIOD_ERROR = "O início do período não pode ser posterior ao fim.";

export type ResultsFilters = {
  period?: PeriodShortcut;
  from?: string;
  to?: string;
  attribute?: string;
  attributeValue?: string;
  attributeAbsent?: boolean;
  versionNumber?: number;
};

/** Parâmetros no formato do backend, prontos para a query string. */
export type ResultsQuery = {
  from?: string;
  to?: string;
  attribute?: string;
  attributeValue?: string;
  version?: number;
};

function firstValue(raw: RawSearchParam): string | undefined {
  return Array.isArray(raw) ? raw[0] : raw;
}

function parseVersionNumber(raw: RawSearchParam): number | undefined {
  const value = firstValue(raw)?.trim();
  if (value === undefined || !/^\d+$/.test(value)) {
    return undefined;
  }
  const parsed = Number.parseInt(value, 10);
  return parsed >= 1 ? parsed : undefined;
}

function parseLocalDateTime(raw: RawSearchParam): string | undefined {
  const value = firstValue(raw)?.trim();
  if (value === undefined || toUtcInstant(value) === undefined) {
    return undefined;
  }
  return value.slice(0, 16);
}

function parseText(raw: RawSearchParam, max: number): string | undefined {
  const value = firstValue(raw)?.trim();
  return value === undefined || value === "" || value.length > max ? undefined : value;
}

export function periodError(from?: string, to?: string): string | undefined {
  if (from === undefined || to === undefined || from === "" || to === "") {
    return undefined;
  }
  const start = toUtcInstant(from);
  const end = toUtcInstant(to);
  if (start === undefined || end === undefined) {
    return undefined;
  }
  return start > end ? PERIOD_ERROR : undefined;
}

export function parseResultsFilters(
  searchParams: Record<string, RawSearchParam> | undefined,
): ResultsFilters {
  const period = parseStatusParam(searchParams?.[PERIOD_PARAM], PERIOD_SHORTCUTS);
  let from = parseLocalDateTime(searchParams?.[FROM_PARAM]);
  let to = parseLocalDateTime(searchParams?.[TO_PARAM]);
  if (period !== undefined || periodError(from, to) !== undefined) {
    from = undefined;
    to = undefined;
  }

  const attribute = parseText(searchParams?.[ATTRIBUTE_PARAM], 80);
  const attributeValue = attribute === undefined ? undefined : parseText(searchParams?.[VALUE_PARAM], 200);
  const attributeAbsent =
    attribute !== undefined && attributeValue === undefined && firstValue(searchParams?.[ABSENT_PARAM]) === "1";
  const versionNumber = parseVersionNumber(searchParams?.[VERSION_PARAM]);

  return {
    ...(period !== undefined ? { period } : {}),
    ...(from !== undefined ? { from } : {}),
    ...(to !== undefined ? { to } : {}),
    // Atributo sem valor e sem `ausente=1` não é recorte nenhum: a URL foi montada pela metade.
    ...(attribute !== undefined && (attributeValue !== undefined || attributeAbsent) ? { attribute } : {}),
    ...(attribute !== undefined && attributeValue !== undefined ? { attributeValue } : {}),
    ...(attributeAbsent ? { attributeAbsent: true } : {}),
    ...(versionNumber !== undefined ? { versionNumber } : {}),
  };
}

export function parseTerm(searchParams: Record<string, RawSearchParam> | undefined): string | undefined {
  return parseText(searchParams?.[TERM_PARAM], 200);
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** Recorte da URL na forma que o backend entende. O atalho de período é resolvido agora. */
export function toResultsQuery(filters: ResultsFilters, now: Date = new Date()): ResultsQuery {
  const from =
    filters.period !== undefined
      ? new Date(now.getTime() - Number.parseInt(filters.period, 10) * DAY_MS).toISOString()
      : toUtcInstant(filters.from);
  const to = filters.period !== undefined ? undefined : toUtcInstant(filters.to);

  return {
    ...(from !== undefined ? { from } : {}),
    ...(to !== undefined ? { to } : {}),
    ...(filters.attribute !== undefined ? { attribute: filters.attribute } : {}),
    ...(filters.attributeValue !== undefined ? { attributeValue: filters.attributeValue } : {}),
    ...(filters.versionNumber !== undefined ? { version: filters.versionNumber } : {}),
  };
}

export function hasAnyFilter(filters: ResultsFilters): boolean {
  return Object.keys(filters).length > 0;
}

/** Texto curto do recorte ativo, para ficar visível junto dos números. */
export function describeFilters(filters: ResultsFilters): string[] {
  const parts: string[] = [];

  if (filters.period !== undefined) {
    parts.push(PERIOD_LABELS[filters.period]);
  } else if (filters.from !== undefined || filters.to !== undefined) {
    const from = filters.from === undefined ? "o início" : filters.from.replace("T", " ");
    const to = filters.to === undefined ? "agora" : filters.to.replace("T", " ");
    parts.push(`De ${from} até ${to}`);
  }

  if (filters.attribute !== undefined) {
    parts.push(
      filters.attributeAbsent
        ? `Sem o atributo "${filters.attribute}"`
        : `${filters.attribute} = ${filters.attributeValue}`,
    );
  }

  if (filters.versionNumber !== undefined) {
    parts.push(`Versão ${filters.versionNumber}`);
  }

  return parts;
}
