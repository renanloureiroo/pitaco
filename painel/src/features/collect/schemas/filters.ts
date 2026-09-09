import { REFERENCE_TIME_ZONE } from "@/shared/lib";
import { parseStatusParam, type RawSearchParam } from "@/shared/api";

import { DISPLAY_OUTCOMES, type DisplayOutcome } from "./display";

/**
 * Filtros de listagem de exibição, lidos de `searchParams`.
 *
 * **Duas validações, propósitos diferentes.** A aparente contradição entre FR-007 ("recusar
 * apontando o campo") e a regra herdada de 001 ("URL inválida cai no padrão") se resolve porque
 * são momentos distintos:
 *
 * - No **formulário**, antes de navegar: início posterior ao fim é recusado com mensagem, e
 *   nada navega — é entrada de pessoa, e a pessoa pode corrigir. É `periodError`.
 * - Na **leitura do servidor**: par incoerente ou valor malformado é ignorado e cai no padrão —
 *   URL é entrada de usuário, não contrato, e nunca deve quebrar a tela. É `parseDisplayFilters`.
 *
 * O período viaja na URL no **fuso de referência do painel** (R3), no mesmo formato que o
 * `input type="datetime-local"` produz. Assim filtrar por "8 de setembro" casa com o que a tela
 * mostra como 8 de setembro, e repovoar o formulário é só devolver o que veio.
 */

export const VERSION_PARAM = "versao";
export const OUTCOME_PARAM = "desfecho";
export const FROM_PARAM = "de";
export const TO_PARAM = "ate";

/** Os parâmetros que a troca de filtro precisa limpar junto, além de `page`. */
export const FILTER_PARAMS = [VERSION_PARAM, OUTCOME_PARAM, FROM_PARAM, TO_PARAM] as const;

export const PERIOD_ERROR = "O início do período não pode ser posterior ao fim.";

export type DisplayFilters = {
  versionNumber?: number;
  outcome?: DisplayOutcome;
  /** Data-hora local do fuso de referência, como o formulário a escreveu. */
  from?: string;
  to?: string;
};

/** O recorte já traduzido para o que a API espera: instantes em ISO UTC. */
export type DisplayFilterQuery = {
  versionNumber?: number;
  outcome?: DisplayOutcome;
  openedFrom?: string;
  openedTo?: string;
};

const LOCAL_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(:\d{2})?$/;

/**
 * Data-hora local em milissegundos, como se fosse UTC. `undefined` quando a data não existe no
 * calendário: `Date.parse` transborda "31 de fevereiro" para 3 de março em vez de recusá-lo, e
 * um filtro que silenciosamente vira outra data é pior do que um filtro ignorado.
 */
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

function firstValue(raw: RawSearchParam): string | undefined {
  return Array.isArray(raw) ? raw[0] : raw;
}

function parseVersionNumber(raw: RawSearchParam): number | undefined {
  const value = firstValue(raw)?.trim();

  // Número de versão começa em 1: `0` e negativo são tão inválidos quanto letra.
  if (value === undefined || !/^\d+$/.test(value)) {
    return undefined;
  }

  const parsed = Number.parseInt(value, 10);
  return parsed >= 1 ? parsed : undefined;
}

function parseLocalDateTime(raw: RawSearchParam): string | undefined {
  const value = firstValue(raw)?.trim();

  if (value === undefined || naiveMillis(value) === undefined) {
    return undefined;
  }

  return value.slice(0, 16);
}

/** Deslocamento do fuso de referência no instante informado, em milissegundos. */
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

/** Data-hora do fuso de referência em instante ISO UTC. Entrada malformada vira `undefined`. */
export function toUtcInstant(local: string | undefined): string | undefined {
  const asIfUtc = local === undefined ? undefined : naiveMillis(local);
  if (asIfUtc === undefined) {
    return undefined;
  }

  // Duas passagens: o deslocamento do primeiro palpite pode não ser o do instante real, o que
  // importaria numa virada de horário de verão.
  const guess = asIfUtc - offsetAt(asIfUtc);
  return new Date(asIfUtc - offsetAt(guess)).toISOString();
}

/** Caminho de volta: instante ISO UTC no formato que o `datetime-local` aceita. */
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

/** Recusa do formulário (FR-007). `undefined` quando não há o que recusar. */
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

/**
 * Leitura do servidor. Valor desconhecido cai no padrão, e o par incoerente é descartado
 * **inteiro**: manter só uma das pontas mudaria o recorte para algo que ninguém pediu.
 */
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

/** Se algum recorte está aplicado — é o que distingue "nenhuma ainda" de "nenhuma no recorte". */
export function hasAnyFilter(filters: DisplayFilters): boolean {
  return Object.keys(filters).length > 0;
}
