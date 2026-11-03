// Verificação de contraste mínimo (WCAG 4.5:1 para texto normal) entre pares relevantes do tema,
// só em desenvolvimento e só como aviso: o tema continua o que o app configurou.

import type { Logger } from '../../core/logger';
import type { PitacoThemeTokens } from './tokens';

const MIN_CONTRAST_NORMAL_TEXT = 4.5;

function parseColorToRgb(color: string): readonly [number, number, number, number] | null {
  const value = color.trim();
  if (value === 'transparent') return null;

  const hex = /^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.exec(value);
  if (hex) {
    let digits = hex[1] as string;
    if (digits.length === 3 || digits.length === 4) {
      digits = digits
        .split('')
        .map((digit) => digit + digit)
        .join('');
    }
    const r = parseInt(digits.slice(0, 2), 16);
    const g = parseInt(digits.slice(2, 4), 16);
    const b = parseInt(digits.slice(4, 6), 16);
    const a = digits.length === 8 ? parseInt(digits.slice(6, 8), 16) / 255 : 1;
    return [r, g, b, a];
  }

  const rgb = /^rgba?\(\s*([\d.]+)%?\s*,\s*([\d.]+)%?\s*,\s*([\d.]+)%?\s*(?:,\s*([\d.]+)\s*)?\)$/.exec(value);
  if (rgb) {
    const r = Number(rgb[1]);
    const g = Number(rgb[2]);
    const b = Number(rgb[3]);
    const a = rgb[4] === undefined ? 1 : Number(rgb[4]);
    if ([r, g, b, a].some((part) => Number.isNaN(part))) return null;
    return [r, g, b, a];
  }

  // hsl/hsla e nomes CSS não são resolvidos aqui: a checagem é pulada em silêncio para eles.
  return null;
}

function relativeLuminance([r, g, b]: readonly [number, number, number, number]): number {
  const channel = (value: number) => {
    const normalized = value / 255;
    return normalized <= 0.03928 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

// Composição simples sobre fundo branco quando a cor de texto tem alfa (aproximação suficiente
// para o aviso de desenvolvimento).
function flattenOverWhite(rgba: readonly [number, number, number, number]): readonly [number, number, number, number] {
  const [r, g, b, a] = rgba;
  if (a >= 1) return rgba;
  return [r * a + 255 * (1 - a), g * a + 255 * (1 - a), b * a + 255 * (1 - a), 1];
}

// `null` quando alguma das cores não pôde ser interpretada (hsl, nome CSS): a checagem é pulada.
export function contrastRatio(foreground: string, background: string): number | null {
  const fg = parseColorToRgb(foreground);
  const bg = parseColorToRgb(background);
  if (fg === null || bg === null) return null;
  const l1 = relativeLuminance(flattenOverWhite(fg));
  const l2 = relativeLuminance(flattenOverWhite(bg));
  const [lighter, darker] = l1 >= l2 ? [l1, l2] : [l2, l1];
  return (lighter + 0.05) / (darker + 0.05);
}

interface ContrastPair {
  readonly label: string;
  readonly foreground: string;
  readonly background: string;
}

// Os pares de texto que a UI padrão de fato desenha: rótulo sobre fundo, sobre superfície, e
// texto de botão sobre a própria cor do botão.
// `disabledText`/`disabled` fica de fora: o WCAG isenta texto de componente desabilitado do
// contraste mínimo (é conteúdo inativo, não uma leitura normal).
function relevantPairs(tokens: PitacoThemeTokens): readonly ContrastPair[] {
  const { colors } = tokens;
  return [
    { label: 'colors.textPrimary sobre colors.background', foreground: colors.textPrimary, background: colors.background },
    { label: 'colors.textPrimary sobre colors.surface', foreground: colors.textPrimary, background: colors.surface },
    { label: 'colors.textSecondary sobre colors.background', foreground: colors.textSecondary, background: colors.background },
    { label: 'colors.primaryText sobre colors.primary', foreground: colors.primaryText, background: colors.primary },
    { label: 'colors.dangerText sobre colors.danger', foreground: colors.dangerText, background: colors.danger },
  ];
}

export function checkThemeContrast(logger: Logger, scheme: string, tokens: PitacoThemeTokens): void {
  if (!logger.isDev) return;
  for (const pair of relevantPairs(tokens)) {
    const ratio = contrastRatio(pair.foreground, pair.background);
    if (ratio === null) continue;
    if (ratio < MIN_CONTRAST_NORMAL_TEXT) {
      logger.warnOnce(
        `theme-contrast-${scheme}-${pair.label}`,
        `contraste insuficiente no tema "${scheme}": ${pair.label} tem razão ${ratio.toFixed(2)}:1, abaixo do mínimo de ${MIN_CONTRAST_NORMAL_TEXT}:1 do WCAG para texto normal.`,
      );
    }
  }
}
