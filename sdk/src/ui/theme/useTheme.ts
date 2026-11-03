// `usePitacoTheme()`: resolve o tema efetivo (claro ou escuro) a partir da configuração do
// Provider (`theme`), acompanhando `useColorScheme()` do sistema a menos que a app force um
// esquema. Funciona também fora do Provider (headless total, preview, testes de componente
// isolado), caindo nos padrões.

import { useContext, useMemo } from 'react';
import { useColorScheme } from 'react-native';
import { createLogger } from '../../core/logger';
import { PitacoContext } from '../../react/context';
import { checkThemeContrast } from './contrast';
import { DEFAULT_DARK_THEME, DEFAULT_LIGHT_THEME, type PitacoResolvedTheme, type PitacoThemeConfig } from './tokens';
import { mergeThemeTokens } from './validate';

const logger = createLogger();

// O que `useColorScheme()` devolve. Nas versões do React Native em que o tipo inclui `'unspecified'`
// (0.86), ele vale como ausência de esquema: cai no claro.
export type SystemColorScheme = 'light' | 'dark' | 'unspecified' | null | undefined;

export function resolveColorScheme(
  preference: PitacoThemeConfig['colorScheme'],
  systemScheme: SystemColorScheme,
): 'light' | 'dark' {
  if (preference === 'light' || preference === 'dark') return preference;
  return systemScheme === 'dark' ? 'dark' : 'light';
}

export function resolvePitacoTheme(
  config: PitacoThemeConfig | undefined,
  systemScheme: SystemColorScheme,
): PitacoResolvedTheme {
  const scheme = resolveColorScheme(config?.colorScheme, systemScheme);
  const base = scheme === 'dark' ? DEFAULT_DARK_THEME : DEFAULT_LIGHT_THEME;
  const override = scheme === 'dark' ? config?.dark : config?.light;
  const tokens = mergeThemeTokens(logger, base, override);
  checkThemeContrast(logger, scheme, tokens);
  return { scheme, tokens };
}

export function usePitacoTheme(): PitacoResolvedTheme {
  const context = useContext(PitacoContext);
  const config = context?.ui.theme;
  const systemScheme = useColorScheme();

  return useMemo(() => resolvePitacoTheme(config, systemScheme), [config, systemScheme]);
}
