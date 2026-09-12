import type { RawSearchParam } from "@/shared/api";

import { isSdkErrorKind, type SdkErrorKind } from "./health";

export const KIND_PARAM = "tipo";
export const SDK_VERSION_PARAM = "versao";

const MAX_VERSION_LENGTH = 40;

export type SdkErrorFilters = { kind?: SdkErrorKind; sdkVersion?: string };

function firstValue(raw: RawSearchParam): string | undefined {
  return Array.isArray(raw) ? raw[0] : raw;
}

/** URL é entrada de usuário: filtro que não faz sentido é ignorado, nunca vira erro de tela. */
export function parseSdkErrorFilters(query: Record<string, RawSearchParam>): SdkErrorFilters {
  const kind = firstValue(query[KIND_PARAM])?.trim();
  const version = firstValue(query[SDK_VERSION_PARAM])?.trim();

  return {
    ...(kind !== undefined && isSdkErrorKind(kind) ? { kind } : {}),
    ...(version !== undefined && version !== ""
      ? { sdkVersion: version.slice(0, MAX_VERSION_LENGTH) }
      : {}),
  };
}
