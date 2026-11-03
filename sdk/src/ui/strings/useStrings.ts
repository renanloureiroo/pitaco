import { useContext, useMemo } from 'react';
import { PitacoContext } from '../../react/context';
import { DEFAULT_STRINGS, mergeStrings, type PitacoStrings } from './strings';

// Funciona fora do Provider também (headless, preview, teste isolado de primitivo): sem
// contexto, os rótulos padrão em pt-BR.
export function usePitacoStrings(): PitacoStrings {
  const context = useContext(PitacoContext);
  const override = context?.ui.strings;
  return useMemo(() => (context === null ? DEFAULT_STRINGS : mergeStrings(override)), [context, override]);
}
