// Tokens de tema da UI padrão: cor, tipografia, raio e espaçamento. Dois conjuntos prontos
// (claro e escuro); o app substitui só o que quiser, token a token.

export type ColorSchemeName = 'light' | 'dark';
export type ColorSchemePreference = ColorSchemeName | 'system';

export interface PitacoColorTokens {
  readonly background: string;
  readonly surface: string;
  readonly overlay: string;
  readonly border: string;
  readonly textPrimary: string;
  readonly textSecondary: string;
  readonly textInverse: string;
  readonly primary: string;
  readonly primaryText: string;
  readonly danger: string;
  readonly dangerText: string;
  readonly disabled: string;
  readonly disabledText: string;
  readonly focusRing: string;
}

export type PitacoFontWeight = '400' | '500' | '600' | '700';

export interface PitacoTypographyToken {
  readonly fontSize: number;
  readonly lineHeight: number;
  readonly fontWeight: PitacoFontWeight;
}

export interface PitacoTypographyTokens {
  readonly title: PitacoTypographyToken;
  readonly body: PitacoTypographyToken;
  readonly label: PitacoTypographyToken;
  readonly caption: PitacoTypographyToken;
  readonly button: PitacoTypographyToken;
}

export interface PitacoRadiusTokens {
  readonly sm: number;
  readonly md: number;
  readonly lg: number;
  readonly pill: number;
}

export interface PitacoSpacingTokens {
  readonly xs: number;
  readonly sm: number;
  readonly md: number;
  readonly lg: number;
  readonly xl: number;
}

export interface PitacoThemeTokens {
  readonly colors: PitacoColorTokens;
  readonly typography: PitacoTypographyTokens;
  readonly radius: PitacoRadiusTokens;
  readonly spacing: PitacoSpacingTokens;
}

// `DeepPartial` porque o app substitui só o token que quiser, em qualquer profundidade.
export type DeepPartial<T> = T extends object ? { readonly [K in keyof T]?: DeepPartial<T[K]> } : T;

export type PitacoThemeTokensOverride = DeepPartial<PitacoThemeTokens>;

// Configuração aceita em `theme` no Provider. `colorScheme` decide entre acompanhar o sistema
// (`useColorScheme`) ou um valor forçado pelo app; `light`/`dark` são substituições parciais.
export interface PitacoThemeConfig {
  readonly colorScheme?: ColorSchemePreference;
  readonly light?: PitacoThemeTokensOverride;
  readonly dark?: PitacoThemeTokensOverride;
}

export interface PitacoResolvedTheme {
  readonly scheme: ColorSchemeName;
  readonly tokens: PitacoThemeTokens;
}

export const DEFAULT_LIGHT_THEME: PitacoThemeTokens = {
  colors: {
    background: '#FFFFFF',
    surface: '#F5F6F8',
    overlay: 'rgba(15, 17, 21, 0.4)',
    border: '#E1E4E8',
    textPrimary: '#12151A',
    textSecondary: '#535B66',
    textInverse: '#FFFFFF',
    primary: '#3454D1',
    primaryText: '#FFFFFF',
    danger: '#B3261E',
    dangerText: '#FFFFFF',
    disabled: '#E1E4E8',
    disabledText: '#8A909B',
    focusRing: '#3454D1',
  },
  typography: {
    title: { fontSize: 18, lineHeight: 24, fontWeight: '700' },
    body: { fontSize: 16, lineHeight: 22, fontWeight: '400' },
    label: { fontSize: 14, lineHeight: 20, fontWeight: '500' },
    caption: { fontSize: 12, lineHeight: 16, fontWeight: '400' },
    button: { fontSize: 16, lineHeight: 20, fontWeight: '600' },
  },
  radius: { sm: 6, md: 12, lg: 20, pill: 999 },
  spacing: { xs: 4, sm: 8, md: 16, lg: 24, xl: 32 },
};

export const DEFAULT_DARK_THEME: PitacoThemeTokens = {
  colors: {
    background: '#15171C',
    surface: '#1E2126',
    overlay: 'rgba(0, 0, 0, 0.6)',
    border: '#33373F',
    textPrimary: '#F4F5F7',
    textSecondary: '#B4BAC4',
    textInverse: '#12151A',
    primary: '#7C93FF',
    primaryText: '#12151A',
    danger: '#F2B8B5',
    dangerText: '#12151A',
    disabled: '#33373F',
    disabledText: '#6B7180',
    focusRing: '#7C93FF',
  },
  typography: DEFAULT_LIGHT_THEME.typography,
  radius: DEFAULT_LIGHT_THEME.radius,
  spacing: DEFAULT_LIGHT_THEME.spacing,
};
