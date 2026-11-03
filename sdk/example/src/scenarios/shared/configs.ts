// Configurações do `<PitacoProvider>` da raiz compartilhadas entre telas. Constantes de módulo:
// `useScenario` compara por referência, e a rota da pesquisa do cenário 3 precisa passar
// exatamente o mesmo objeto da tela de baixo para o runtime não ser recriado no meio da exibição.
import type { ScenarioProviderConfig } from '../../pitaco/ExampleContext';

export const INLINE_CONFIG: ScenarioProviderConfig = { presentation: 'inline' };
export const MODAL_CONFIG: ScenarioProviderConfig = { presentation: 'modal' };
