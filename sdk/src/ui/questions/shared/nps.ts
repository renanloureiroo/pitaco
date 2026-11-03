import type { NumericRange } from '../../../core/survey/schema';

// O schema já normaliza `NPS` com a faixa 0-10 (ver `normalizeQuestion` em
// `core/survey/schema.ts`); este é só o fallback local caso um schema em memória (preview,
// teste) chegue sem `range`.
export const NPS_RANGE_FALLBACK: NumericRange = { min: 0, max: 10, minLabel: null, maxLabel: null };
