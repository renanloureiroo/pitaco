// NPS desenhado pelo app (cenário 7): cores por faixa (detrator, neutro, promotor). Recebe as ações
// do core já vinculadas à pergunta; validação, eventos e envio continuam com o SDK.
import type { QuestionRendererProps } from '@pitaco/react-native';
import { Pressable, StyleSheet, Text, View } from 'react-native';

const SCORES = Array.from({ length: 11 }, (_, index) => index);

function tone(score: number): string {
  if (score <= 6) return '#E03131';
  if (score <= 8) return '#F08C00';
  return '#2F9E44';
}

export function AppNps({ question, value, error, actions, theme }: QuestionRendererProps) {
  const colors = theme.tokens.colors;
  return (
    <View testID="cenario-07-nps" style={[styles.box, { borderColor: error === null ? colors.border : colors.danger }]}>
      <Text style={[styles.badge, { color: colors.textSecondary }]}>NPS desenhado pelo app</Text>
      <View style={styles.row}>
        {SCORES.map((score) => {
          const selected = value === score;
          return (
            <Pressable
              key={score}
              testID={`cenario-07-nps-${score}`}
              accessibilityRole="radio"
              accessibilityState={{ selected }}
              accessibilityLabel={String(score)}
              onPress={() => actions.select(score)}
              style={[styles.cell, { borderColor: tone(score), backgroundColor: selected ? tone(score) : 'transparent' }]}
            >
              <Text style={[styles.score, { color: selected ? '#FFFFFF' : tone(score) }]}>{score}</Text>
            </Pressable>
          );
        })}
      </View>
      <View style={styles.labels}>
        <Text style={[styles.label, { color: colors.textSecondary }]}>{question.range?.minLabel ?? 'Nada provável'}</Text>
        <Text style={[styles.label, { color: colors.textSecondary }]}>{question.range?.maxLabel ?? 'Muito provável'}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  box: { borderWidth: 1, borderRadius: 14, padding: 12, gap: 10 },
  badge: { fontSize: 12, fontWeight: '700', textTransform: 'uppercase' },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, justifyContent: 'center' },
  cell: { width: 44, height: 44, borderRadius: 22, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
  score: { fontSize: 16, fontWeight: '700' },
  labels: { flexDirection: 'row', justifyContent: 'space-between' },
  label: { fontSize: 12 },
});
