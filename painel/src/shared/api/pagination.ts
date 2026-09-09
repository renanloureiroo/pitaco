import { z } from "zod";

/**
 * Paginação (R12).
 *
 * Página, tamanho e filtro vivem em `searchParams`, o que torna o estado da listagem
 * endereçável e faz o "voltar" do navegador funcionar sem código. Um valor inválido na URL
 * **cai no padrão** em vez de quebrar a tela: URL é entrada de usuário, não contrato.
 */

export const DEFAULT_PAGE = 0;
export const DEFAULT_SIZE = 20;
export const MIN_SIZE = 1;
export const MAX_SIZE = 100;

export type PaginationParams = { page: number; size: number };

export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

/** `PageResponse<T>` é genérico sobre o schema do item. */
export function pageResponseSchema<T>(itemSchema: z.ZodType<T>): z.ZodType<PageResponse<T>> {
  return z.object({
    items: z.array(itemSchema),
    page: z.number(),
    size: z.number(),
    total: z.number(),
    totalPages: z.number(),
  });
}

/** Valor cru vindo de `searchParams`: ausente, único, ou repetido na URL. */
export type RawSearchParam = string | string[] | undefined;

function firstValue(raw: RawSearchParam): string | undefined {
  if (Array.isArray(raw)) {
    return raw[0];
  }
  return raw;
}

function parseInteger(raw: RawSearchParam): number | undefined {
  const value = firstValue(raw);
  if (value === undefined || value.trim() === "") {
    return undefined;
  }

  // `Number` aceitaria "1e3" e " 12 "; a URL de paginação só admite dígitos.
  if (!/^\d+$/.test(value.trim())) {
    return undefined;
  }

  return Number.parseInt(value, 10);
}

export function parsePaginationParams(
  searchParams: Record<string, RawSearchParam> | undefined,
): PaginationParams {
  const page = parseInteger(searchParams?.page);
  const size = parseInteger(searchParams?.size);

  return {
    page: page === undefined ? DEFAULT_PAGE : page,
    size: size === undefined || size < MIN_SIZE || size > MAX_SIZE ? DEFAULT_SIZE : size,
  };
}

/** Situação lida de `searchParams`: só os valores conhecidos passam; o resto vira "todas". */
export function parseStatusParam<const T extends readonly string[]>(
  raw: RawSearchParam,
  allowed: T,
): T[number] | undefined {
  const value = firstValue(raw);
  return value !== undefined && (allowed as readonly string[]).includes(value)
    ? (value as T[number])
    : undefined;
}
