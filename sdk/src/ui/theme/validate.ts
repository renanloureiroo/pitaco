// Validação e merge dos tokens de tema. Um valor inválido (cor que não é cor, número negativo,
// tipo errado) cai no padrão daquele token, um a um, com aviso em `__DEV__`.

import type { Logger } from '../../core/logger';
import {
  type DeepPartial,
  type PitacoColorTokens,
  type PitacoFontWeight,
  type PitacoRadiusTokens,
  type PitacoSpacingTokens,
  type PitacoThemeTokens,
  type PitacoTypographyToken,
  type PitacoTypographyTokens,
} from './tokens';

const COLOR_PATTERN =
  /^(#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})|rgb\(\s*[\d.]+%?\s*,\s*[\d.]+%?\s*,\s*[\d.]+%?\s*\)|rgba\(\s*[\d.]+%?\s*,\s*[\d.]+%?\s*,\s*[\d.]+%?\s*,\s*[\d.]+\s*\)|hsl\(\s*[\d.]+\s*,\s*[\d.]+%\s*,\s*[\d.]+%\s*\)|hsla\(\s*[\d.]+\s*,\s*[\d.]+%\s*,\s*[\d.]+%\s*,\s*[\d.]+\s*\)|transparent)$/;

export function isValidColor(value: unknown): value is string {
  return typeof value === 'string' && COLOR_PATTERN.test(value.trim());
}

function isValidNonNegativeNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0;
}

const FONT_WEIGHTS: ReadonlySet<string> = new Set(['400', '500', '600', '700']);

function isValidFontWeight(value: unknown): value is PitacoFontWeight {
  return typeof value === 'string' && FONT_WEIGHTS.has(value);
}

function warnInvalid(logger: Logger, path: string, value: unknown): void {
  logger.warnOnce(
    `theme-token-${path}`,
    `token de tema inválido em "${path}" (recebido ${JSON.stringify(value)}); usando o padrão. Corrija o valor em theme.light/theme.dark.`,
  );
}

function pickColor(logger: Logger, path: string, base: string, override: unknown): string {
  if (override === undefined) return base;
  if (isValidColor(override)) return override.trim();
  warnInvalid(logger, path, override);
  return base;
}

function pickNonNegative(logger: Logger, path: string, base: number, override: unknown): number {
  if (override === undefined) return base;
  if (isValidNonNegativeNumber(override)) return override;
  warnInvalid(logger, path, override);
  return base;
}

function mergeColors(
  logger: Logger,
  base: PitacoColorTokens,
  override: DeepPartial<PitacoColorTokens> | undefined,
): PitacoColorTokens {
  const keys = Object.keys(base) as (keyof PitacoColorTokens)[];
  const result = {} as { -readonly [K in keyof PitacoColorTokens]: PitacoColorTokens[K] };
  for (const key of keys) {
    result[key] = pickColor(logger, `colors.${key}`, base[key], override?.[key]);
  }
  return result;
}

function mergeTypographyToken(
  logger: Logger,
  path: string,
  base: PitacoTypographyToken,
  override: DeepPartial<PitacoTypographyToken> | undefined,
): PitacoTypographyToken {
  const fontSize = pickNonNegative(logger, `${path}.fontSize`, base.fontSize, override?.fontSize);
  const lineHeight = pickNonNegative(logger, `${path}.lineHeight`, base.lineHeight, override?.lineHeight);
  let fontWeight = base.fontWeight;
  if (override?.fontWeight !== undefined) {
    if (isValidFontWeight(override.fontWeight)) fontWeight = override.fontWeight;
    else warnInvalid(logger, `${path}.fontWeight`, override.fontWeight);
  }
  return { fontSize, lineHeight, fontWeight };
}

function mergeTypography(
  logger: Logger,
  base: PitacoTypographyTokens,
  override: DeepPartial<PitacoTypographyTokens> | undefined,
): PitacoTypographyTokens {
  return {
    title: mergeTypographyToken(logger, 'typography.title', base.title, override?.title),
    body: mergeTypographyToken(logger, 'typography.body', base.body, override?.body),
    label: mergeTypographyToken(logger, 'typography.label', base.label, override?.label),
    caption: mergeTypographyToken(logger, 'typography.caption', base.caption, override?.caption),
    button: mergeTypographyToken(logger, 'typography.button', base.button, override?.button),
  };
}

function mergeRadius(
  logger: Logger,
  base: PitacoRadiusTokens,
  override: DeepPartial<PitacoRadiusTokens> | undefined,
): PitacoRadiusTokens {
  return {
    sm: pickNonNegative(logger, 'radius.sm', base.sm, override?.sm),
    md: pickNonNegative(logger, 'radius.md', base.md, override?.md),
    lg: pickNonNegative(logger, 'radius.lg', base.lg, override?.lg),
    pill: pickNonNegative(logger, 'radius.pill', base.pill, override?.pill),
  };
}

function mergeSpacing(
  logger: Logger,
  base: PitacoSpacingTokens,
  override: DeepPartial<PitacoSpacingTokens> | undefined,
): PitacoSpacingTokens {
  return {
    xs: pickNonNegative(logger, 'spacing.xs', base.xs, override?.xs),
    sm: pickNonNegative(logger, 'spacing.sm', base.sm, override?.sm),
    md: pickNonNegative(logger, 'spacing.md', base.md, override?.md),
    lg: pickNonNegative(logger, 'spacing.lg', base.lg, override?.lg),
    xl: pickNonNegative(logger, 'spacing.xl', base.xl, override?.xl),
  };
}

// Merge parcial e validado: cada token substituído passa pela própria checagem, e um valor
// inválido não derruba o resto do tema, só aquele token específico.
export function mergeThemeTokens(
  logger: Logger,
  base: PitacoThemeTokens,
  override: DeepPartial<PitacoThemeTokens> | undefined,
): PitacoThemeTokens {
  if (override === undefined || override === null || typeof override !== 'object') return base;
  return {
    colors: mergeColors(logger, base.colors, override.colors),
    typography: mergeTypography(logger, base.typography, override.typography),
    radius: mergeRadius(logger, base.radius, override.radius),
    spacing: mergeSpacing(logger, base.spacing, override.spacing),
  };
}
