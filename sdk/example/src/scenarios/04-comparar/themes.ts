// Os dois temas do cenário 4 (constantes de módulo: o preview compara por referência) e as cores
// da moldura que não é do SDK (o fundo do sheet do gorhom e da rota "Tela").
import { DEFAULT_DARK_THEME, DEFAULT_LIGHT_THEME, type PitacoThemeConfig } from '@pitaco/react-native';

export type ThemeChoice = 'claro' | 'escuro';

export const THEME_OPTIONS = [
  { value: 'claro', label: 'Claro' },
  { value: 'escuro', label: 'Escuro' },
] as const;

export const COMPARE_THEMES: Readonly<Record<ThemeChoice, PitacoThemeConfig>> = {
  claro: { colorScheme: 'light' },
  escuro: { colorScheme: 'dark' },
};

export function frameColors(choice: ThemeChoice) {
  const tokens = choice === 'escuro' ? DEFAULT_DARK_THEME : DEFAULT_LIGHT_THEME;
  return { background: tokens.colors.background, handle: tokens.colors.border };
}

export function parseThemeChoice(value: unknown): ThemeChoice {
  return value === 'escuro' ? 'escuro' : 'claro';
}
