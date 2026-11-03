import { createLogger } from '../../../core/logger';
import { checkThemeContrast, contrastRatio } from '../contrast';
import { DEFAULT_DARK_THEME, DEFAULT_LIGHT_THEME } from '../tokens';
import { mergeThemeTokens } from '../validate';
import { resolveColorScheme, resolvePitacoTheme } from '../useTheme';

describe('tema da UI padrão', () => {
  it('resolve o esquema pelo sistema quando não forçado', () => {
    expect(resolveColorScheme(undefined, 'dark')).toBe('dark');
    expect(resolveColorScheme(undefined, 'light')).toBe('light');
    expect(resolveColorScheme(undefined, null)).toBe('light');
    // React Native 0.86 tipa `useColorScheme()` com `'unspecified'`: vale como ausência de esquema.
    expect(resolveColorScheme(undefined, 'unspecified')).toBe('light');
    expect(resolvePitacoTheme(undefined, 'unspecified').scheme).toBe('light');
    expect(resolveColorScheme('system', 'dark')).toBe('dark');
  });

  it('um esquema forçado ignora o sistema', () => {
    expect(resolveColorScheme('dark', 'light')).toBe('dark');
    expect(resolveColorScheme('light', 'dark')).toBe('light');
  });

  it('merge parcial substitui só os tokens informados', () => {
    const logger = createLogger({ isDev: false });
    const merged = mergeThemeTokens(logger, DEFAULT_LIGHT_THEME, { colors: { primary: '#00FF00' } });
    expect(merged.colors.primary).toBe('#00FF00');
    expect(merged.colors.background).toBe(DEFAULT_LIGHT_THEME.colors.background);
    expect(merged.spacing).toEqual(DEFAULT_LIGHT_THEME.spacing);
  });

  it('um token inválido cai no padrão daquele token, com aviso em __DEV__', () => {
    const warn = jest.fn();
    const logger = createLogger({ isDev: true, sink: warn });

    const merged = mergeThemeTokens(logger, DEFAULT_LIGHT_THEME, {
      colors: { primary: 'não é uma cor' },
      spacing: { md: -4 },
      typography: { title: { fontWeight: '999' as never } },
    });

    expect(merged.colors.primary).toBe(DEFAULT_LIGHT_THEME.colors.primary);
    expect(merged.spacing.md).toBe(DEFAULT_LIGHT_THEME.spacing.md);
    expect(merged.typography.title.fontWeight).toBe(DEFAULT_LIGHT_THEME.typography.title.fontWeight);
    expect(warn).toHaveBeenCalled();
    expect(warn.mock.calls.some((call: unknown[]) => String(call[0]).includes('colors.primary'))).toBe(true);
  });

  it('em produção (fora de __DEV__) o valor inválido cai no padrão sem aviso', () => {
    const warn = jest.fn();
    const logger = createLogger({ isDev: false, sink: warn });
    const merged = mergeThemeTokens(logger, DEFAULT_LIGHT_THEME, { colors: { primary: 'xxx' } });
    expect(merged.colors.primary).toBe(DEFAULT_LIGHT_THEME.colors.primary);
    expect(warn).not.toHaveBeenCalled();
  });

  it('resolvePitacoTheme aplica o esquema, o merge e roda a checagem de contraste', () => {
    const light = resolvePitacoTheme(undefined, 'light');
    expect(light.scheme).toBe('light');
    expect(light.tokens).toEqual(DEFAULT_LIGHT_THEME);

    const dark = resolvePitacoTheme({ colorScheme: 'dark' }, 'light');
    expect(dark.scheme).toBe('dark');
    expect(dark.tokens).toEqual(DEFAULT_DARK_THEME);
  });

  it('contrastRatio calcula a razão WCAG entre duas cores', () => {
    expect(contrastRatio('#000000', '#FFFFFF')).toBeCloseTo(21, 0);
    expect(contrastRatio('#FFFFFF', '#FFFFFF')).toBeCloseTo(1, 0);
    expect(contrastRatio('hsl(0, 0%, 0%)', '#FFFFFF')).toBeNull();
  });

  it('avisa em __DEV__ quando um par de cores do tema fica abaixo de 4.5:1', () => {
    const warn = jest.fn();
    const logger = createLogger({ isDev: true, sink: warn });
    checkThemeContrast(logger, 'light', {
      ...DEFAULT_LIGHT_THEME,
      colors: { ...DEFAULT_LIGHT_THEME.colors, textPrimary: '#F0F0F0', background: '#FFFFFF' },
    });
    expect(warn.mock.calls.some((call: unknown[]) => String(call[0]).includes('contraste insuficiente'))).toBe(true);
  });

  it('não avisa quando o contraste está adequado', () => {
    const warn = jest.fn();
    const logger = createLogger({ isDev: true, sink: warn });
    checkThemeContrast(logger, 'light', DEFAULT_LIGHT_THEME);
    expect(warn).not.toHaveBeenCalled();
  });
});
