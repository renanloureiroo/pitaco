// Configurações do `<PitacoProvider>` da raiz no cenário 4, uma por forma e tema. Constantes de
// módulo: `useScenario` compara por referência, e a rota "Tela" precisa passar exatamente o objeto
// da tela de baixo para o runtime não ser recriado no meio da exibição.
import type { ScenarioProviderConfig } from '../../pitaco/ExampleContext';
import { COMPARE_THEMES, type ThemeChoice } from './themes';

export type CompareForm = 'sheet' | 'gorhom' | 'tela';

export const FORM_OPTIONS = [
  { value: 'sheet', label: 'Sheet do SDK' },
  { value: 'gorhom', label: 'Gorhom' },
  { value: 'tela', label: 'Tela' },
] as const;

function byTheme(presentation: ScenarioProviderConfig['presentation']): Readonly<Record<ThemeChoice, ScenarioProviderConfig>> {
  return {
    claro: { presentation, theme: COMPARE_THEMES.claro },
    escuro: { presentation, theme: COMPARE_THEMES.escuro },
  };
}

// Gorhom e Tela dividem os objetos: as duas são `inline`, e trocar entre elas não recria o runtime.
const SHEET = byTheme('bottom-sheet');
const INLINE = byTheme('inline');

export function compareConfig(form: CompareForm, choice: ThemeChoice): ScenarioProviderConfig {
  return (form === 'sheet' ? SHEET : INLINE)[choice];
}
