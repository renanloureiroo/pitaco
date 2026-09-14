import { Stack } from 'expo-router';
import { SCENARIOS } from '../../../src/scenarios/registry';

export const unstable_settings = { initialRouteName: 'index' };

export default function ScenariosLayout() {
  return (
    <Stack>
      <Stack.Screen name="index" options={{ title: 'Pitaco — exemplo' }} />
      {SCENARIOS.map((scenario) => (
        <Stack.Screen key={scenario.id} name={scenario.id} options={{ title: scenario.title }} />
      ))}
      <Stack.Screen name="04-comparar-tela" options={{ title: 'Pesquisa como tela' }} />
    </Stack>
  );
}
