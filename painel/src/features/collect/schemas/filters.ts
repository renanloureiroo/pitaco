import { toUtcInstant } from "@/shared/lib";
import { parseStatusParam, type RawSearchParam } from "@/shared/api";

import { DISPLAY_OUTCOMES, type DisplayOutcome } from "./display";

export const VERSION_PARAM = "versao";
export const OUTCOME_PARAM = "desfecho";
export const FROM_PARAM = "de";
export const TO_PARAM = "ate";

export const FILTER_PARAMS = [VERSION_PARAM, OUTCOME_PARAM, FROM_PARAM, TO_PARAM] as const;

export const PERIOD_ERROR = "O início do período não pode ser posterior ao fim.";

export type DisplayFilters = {
  versionNumber?: number;
  outcome?: DisplayOutcome;
  from?: string;
  to?: string;
};

export type DisplayFilterQuery = {
  versionNumber?: number;
  outcome?: DisplayOutcome;
  openedFrom?: string;
  openedTo?: string;
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

export function parseDisplayFilters(
  searchParams: Record<string, RawSearchParam> | undefined,
): DisplayFilters {
  const versionNumber = parseVersionNumber(searchParams?.[VERSION_PARAM]);
  const outcome = parseStatusParam(searchParams?.[OUTCOME_PARAM], DISPLAY_OUTCOMES);
  let from = parseLocalDateTime(searchParams?.[FROM_PARAM]);
  let to = parseLocalDateTime(searchParams?.[TO_PARAM]);

  if (periodError(from, to) !== undefined) {
    from = undefined;
    to = undefined;
  }

  return {
    ...(versionNumber !== undefined ? { versionNumber } : {}),
    ...(outcome !== undefined ? { outcome } : {}),
    ...(from !== undefined ? { from } : {}),
    ...(to !== undefined ? { to } : {}),
  };
}

export function toDisplayFilterQuery(filters: DisplayFilters): DisplayFilterQuery {
  const openedFrom = toUtcInstant(filters.from);
  const openedTo = toUtcInstant(filters.to);

  return {
    ...(filters.versionNumber !== undefined ? { versionNumber: filters.versionNumber } : {}),
    ...(filters.outcome !== undefined ? { outcome: filters.outcome } : {}),
    ...(openedFrom !== undefined ? { openedFrom } : {}),
    ...(openedTo !== undefined ? { openedTo } : {}),
  };
}

export function hasAnyFilter(filters: DisplayFilters): boolean {
  return Object.keys(filters).length > 0;
}
