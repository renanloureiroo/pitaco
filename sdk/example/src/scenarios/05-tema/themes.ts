// Os três temas do cenário 5 e o aviso de desenvolvimento esperado para o inválido. Constantes de
// módulo: `useScenario` compara a configuração por referência.
import type { PitacoThemeConfig } from '@pitaco/react-native';
import type { ScenarioProviderConfig } from '../../pitaco/ExampleContext';

export type ThemeMode = 'claro' | 'escuro' | 'invalido';

export const THEME_MODE_OPTIONS = [
  { value: 'claro', label: 'Claro' },
  { value: 'escuro', label: 'Escuro' },
  { value: 'invalido', label: 'Inválido' },
] as const;

// Errado de propósito, token a token (por isso o `as unknown`): cada valor inválido cai no padrão
// daquele token, sozinho. O raio médio (4) é válido e vale: as opções aparecem mais quadradas.
const INVALID_THEME = {
  colorScheme: 'light',
  light: {
    colors: { primary: 'azul-pitaco', background: 42 },
    typography: { title: { fontWeight: 'bold' } },
    radius: { md: 4, lg: -8 },
    spacing: { lg: 'grande' },
  },
} as unknown as PitacoThemeConfig;

export const THEMES: Readonly<Record<ThemeMode, PitacoThemeConfig>> = {
  claro: { colorScheme: 'light' },
  escuro: { colorScheme: 'dark' },
  invalido: INVALID_THEME,
};

export const THEME_CONFIGS: Readonly<Record<ThemeMode, ScenarioProviderConfig>> = {
  claro: { theme: THEMES.claro },
  escuro: { theme: THEMES.escuro },
  invalido: { theme: THEMES.invalido },
};

// O que o SDK escreve no console em desenvolvimento (`__DEV__`), uma vez por token e por processo.
// Em produção, silêncio.
const fix = 'usando o padrão. Corrija o valor em theme.light/theme.dark.';
export const EXPECTED_WARNINGS: readonly string[] = [
  `[Pitaco] token de tema inválido em "colors.primary" (recebido "azul-pitaco"); ${fix}`,
  `[Pitaco] token de tema inválido em "colors.background" (recebido 42); ${fix}`,
  `[Pitaco] token de tema inválido em "typography.title.fontWeight" (recebido "bold"); ${fix}`,
  `[Pitaco] token de tema inválido em "radius.lg" (recebido -8); ${fix}`,
  `[Pitaco] token de tema inválido em "spacing.lg" (recebido "grande"); ${fix}`,
];
