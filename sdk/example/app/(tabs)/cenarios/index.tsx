import { Link } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { ProfileSelector } from '../../../src/pitaco/ProfileSelector';
import { seedSurvey } from '../../../src/pitaco/seed';
import { SCENARIOS, scenarioHref } from '../../../src/scenarios/registry';
import { usePalette } from '../../../src/ui/palette';
import { Hint, Screen } from '../../../src/ui/Screen';

export default function ScenarioList() {
  const palette = usePalette();
  return (
    <Screen testID="tela-inicio">
      <ProfileSelector />
      {seedSurvey === null && (
        <Hint>Pesquisa do seed não encontrada: rode `npm run seed` com o backend no ar.</Hint>
      )}
      <View style={styles.list}>
        {SCENARIOS.map((scenario) => (
          <Link key={scenario.id} href={scenarioHref(scenario.id)} asChild>
            <Pressable
              testID={`cenario-${scenario.id}`}
              accessibilityRole="button"
              style={({ pressed }) => [
                styles.item,
                { backgroundColor: palette.surface, borderColor: palette.border },
                pressed && styles.pressed,
              ]}
            >
              <Text style={[styles.title, { color: palette.text }]}>{scenario.title}</Text>
              <Text style={[styles.description, { color: palette.muted }]}>{scenario.description}</Text>
            </Pressable>
          </Link>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  list: { gap: 8 },
  item: { padding: 14, borderRadius: 12, borderWidth: 1, gap: 4 },
  pressed: { opacity: 0.7 },
  title: { fontSize: 16, fontWeight: '600' },
  description: { fontSize: 13, lineHeight: 18 },
});
