// Safe area sem dependência (restrição 1: nada de `react-native-safe-area-context`). O app pode
// passar `insets` fixos, ou `getInsets` (por exemplo o próprio `useSafeAreaInsets()` de quem já
// usa aquele pacote) — `getInsets` tem prioridade quando os dois vêm, decisão já tomada e
// documentada pela fase 3a em `PitacoProvider`. Sem nenhum dos dois, cada apresentação usa um
// padrão conservador por plataforma.

import { Platform, StatusBar } from 'react-native';
import type { EdgeInsets } from '../types';

const ZERO_INSETS: EdgeInsets = { top: 0, right: 0, bottom: 0, left: 0 };

// iOS: aproxima um aparelho com notch/Dynamic Island (barra de status) e o indicador de início
// (rodapé). Conservador de propósito — um aparelho sem notch só perde alguns pontos de respiro.
const IOS_DEFAULT_INSETS: EdgeInsets = { top: 47, right: 0, bottom: 34, left: 0 };

function androidDefaultInsets(): EdgeInsets {
  const statusBarHeight = typeof StatusBar.currentHeight === 'number' ? StatusBar.currentHeight : 24;
  return { top: statusBarHeight, right: 0, bottom: 0, left: 0 };
}

export function platformDefaultInsets(): EdgeInsets {
  if (Platform.OS === 'ios') return IOS_DEFAULT_INSETS;
  if (Platform.OS === 'android') return androidDefaultInsets();
  return ZERO_INSETS;
}

// `getInsets` tem prioridade sobre `insets`; uma função que lança (configuração externa, fora do
// controle do SDK) nunca derruba a apresentação — cai no padrão da plataforma.
export function resolveInsets(insets: EdgeInsets | undefined, getInsets: (() => EdgeInsets) | undefined): EdgeInsets {
  if (typeof getInsets === 'function') {
    try {
      return getInsets();
    } catch {
      return insets ?? platformDefaultInsets();
    }
  }
  return insets ?? platformDefaultInsets();
}
