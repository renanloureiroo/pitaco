// Liga uma tela de cenário ao `<PitacoProvider>` da raiz enquanto ela estiver montada.
//
//   const THEME = { mode: 'dark' } as const;             // constante de módulo, ou useMemo
//   const CONFIG = { theme: THEME, presentation: 'modal' } as const;
//   export default function Screen() {
//     useScenario('10-modal-inline', CONFIG);
//     ...
//   }
//
// - Os eventos do Provider passam a sair no painel marcados com `scenarioId`.
// - `config` sobrepõe as props do Provider. Vale o cenário montado por último: uma rota empilhada
//   por cima (a pesquisa como tela, no cenário 3) que também chame `useScenario` assume; ao
//   desmontar, volta a valer a de baixo. Trocar para a aba de depuração não desmonta o cenário.
// - Os valores de `config` são comparados por referência: passe objetos estáveis. Mudar
//   `baseUrl`, `apiKey`, `presentation`, `errorReporting`, `debug` ou os prazos recria o runtime
//   (zera o limite de uma pesquisa por sessão); tema, textos, renderizadores e slots não.
import { useEffect, useId } from 'react';
import { type ScenarioProviderConfig, useExampleActions } from './ExampleContext';

const NO_CONFIG: ScenarioProviderConfig = {};

export function useScenario(scenarioId: string, config: ScenarioProviderConfig = NO_CONFIG): void {
  const token = useId();
  const { mountScenario, configureScenario } = useExampleActions();

  useEffect(() => mountScenario(token, scenarioId), [token, scenarioId, mountScenario]);

  useEffect(() => {
    configureScenario(token, config);
  }, [token, config, configureScenario]);
}
