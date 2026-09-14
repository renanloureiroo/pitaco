// Cenário 5 — Tema. Claro, escuro e um tema com valores inválidos que degrada para o padrão, token
// a token. O tema entra no `<PitacoProvider>` da raiz por `useScenario`; trocar de tema não recria
// o runtime, então dá para trocar com a pesquisa aberta.
import { useState } from 'react';
import { useScenario } from '../../../src/pitaco/useScenario';
import { EXPECTED_WARNINGS, THEME_CONFIGS, THEME_MODE_OPTIONS, THEMES, type ThemeMode } from '../../../src/scenarios/05-tema/themes';
import { FlowModes } from '../../../src/scenarios/shared/FlowModes';
import { LocalPreview } from '../../../src/scenarios/shared/LocalPreview';
import { TriggerButton } from '../../../src/scenarios/shared/TriggerButton';
import { JsonBlock } from '../../../src/ui/JsonBlock';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';
import { Segmented } from '../../../src/ui/Segmented';

export default function ThemeScenario() {
  const [mode, setMode] = useState<ThemeMode>('claro');
  useScenario('05-tema', THEME_CONFIGS[mode]);

  return (
    <Screen testID="tela-05-tema">
      <Paragraph>O tema do Pitaco num ponto único: cores, tipografia, raio e espaçamento, com esquema claro ou escuro forçado.</Paragraph>
      <Segmented testIDPrefix="cenario-05-tema" options={THEME_MODE_OPTIONS} value={mode} onChange={setMode} />
      {mode === 'invalido' && (
        <>
          <Hint>
            Cada token inválido cai no padrão sozinho (o raio médio de 4 é válido e vale). Aviso esperado no console, só em
            desenvolvimento e uma vez por token:
          </Hint>
          <JsonBlock value={EXPECTED_WARNINGS.join('\n')} />
        </>
      )}
      <FlowModes
        nn="05"
        real={<TriggerButton nn="05" />}
        preview={<LocalPreview scenarioId="05-tema" nn="05" theme={THEMES[mode]} />}
      />
    </Screen>
  );
}
