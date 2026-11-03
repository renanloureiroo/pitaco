import type { DisplayFilters } from "../schemas/filters";
import { hasAnyFilter } from "../schemas/filters";

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

export type HistoryEmptyVariant = "no_displays" | "no_matches";

export function historyEmptyVariant(filters: DisplayFilters): HistoryEmptyVariant {
  return hasAnyFilter(filters) ? "no_matches" : "no_displays";
}
