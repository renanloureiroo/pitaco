// Cores da moldura do exemplo (não do SDK: a pesquisa usa o tema do Pitaco).
import { useColorScheme } from 'react-native';

export interface Palette {
  readonly background: string;
  readonly surface: string;
  readonly border: string;
  readonly text: string;
  readonly muted: string;
  readonly accent: string;
  readonly onAccent: string;
  readonly danger: string;
  readonly code: string;
}

const LIGHT: Palette = {
  background: '#F6F7F9',
  surface: '#FFFFFF',
  border: '#DDE1E6',
  text: '#16181D',
  muted: '#5F6773',
  accent: '#3B5BDB',
  onAccent: '#FFFFFF',
  danger: '#C92A2A',
  code: '#EEF1F5',
};

const DARK: Palette = {
  background: '#0F1115',
  surface: '#1A1D23',
  border: '#2C313A',
  text: '#ECEEF2',
  muted: '#9AA3AF',
  accent: '#748FFC',
  onAccent: '#0F1115',
  danger: '#FF8787',
  code: '#23272F',
};

export function usePalette(): Palette {
  return useColorScheme() === 'dark' ? DARK : LIGHT;
}
