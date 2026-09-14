// Tela provisória dos cenários 2 a 15 (fase 5a). A 5c e a 5d substituem o conteúdo de cada rota.
import { useScenario } from '../pitaco/useScenario';
import { Hint, Paragraph, Screen } from '../ui/Screen';
import { type ScenarioId, scenarioById } from './registry';

export function ScenarioPlaceholder({ id }: { id: ScenarioId }) {
  useScenario(id);
  const meta = scenarioById(id);
  return (
    <Screen testID={`tela-${id}`}>
      <Paragraph>{meta.description}</Paragraph>
      <Hint>Cenário ainda não implementado.</Hint>
    </Screen>
  );
}
