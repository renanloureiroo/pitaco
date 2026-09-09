import type { DisplayFilters } from "../schemas/filters";
import { hasAnyFilter } from "../schemas/filters";

/**
 * Os três vazios da listagem por pesquisa (R6).
 *
 * FR-025 exige distinguir "nunca houve coleta" de "o filtro não encontrou nada". Aqui há um
 * terceiro caso, que os edge cases da spec trazem: a pesquisa **nunca foi publicada**, e
 * portanto nunca poderia ter sido exibida — dizer "nenhuma exibição ainda" nesse caso sugeriria
 * espera, quando o que falta é publicar.
 *
 * A decisão é uma função pura porque a ordem entre as condições é a parte fácil de errar:
 * nunca publicada vence o recorte, já que filtrar o que não existe não muda o que a pessoa
 * precisa fazer a seguir.
 */

export type DisplaysEmptyVariant = "never_published" | "no_displays" | "no_matches";

export function displaysEmptyVariant(input: {
  publishedVersionNumber?: number;
  filters: DisplayFilters;
}): DisplaysEmptyVariant {
  if (input.publishedVersionNumber === undefined) {
    return "never_published";
  }

  return hasAnyFilter(input.filters) ? "no_matches" : "no_displays";
}

/** Os dois vazios do histórico do respondente: lá não existe "nunca publicada". */
export type HistoryEmptyVariant = "no_displays" | "no_matches";

export function historyEmptyVariant(filters: DisplayFilters): HistoryEmptyVariant {
  return hasAnyFilter(filters) ? "no_matches" : "no_displays";
}
