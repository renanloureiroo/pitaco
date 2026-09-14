// A área de resposta da UI headless (cenário 9), para os seis tipos, só com as ações do core:
// `select`/`deselect` nas escolhas e números, `setText`/`focusText`/`blurText` no texto livre.
import type { UsePitacoSurveyResult } from '@pitaco/react-native';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { usePalette } from '../../ui/palette';

const DEFAULT_RANGES = { NPS: { min: 0, max: 10 }, RATING: { min: 1, max: 5 }, SCALE: { min: 1, max: 5 } } as const;

function Choice({ label, selected, onPress, testID }: { label: string; selected: boolean; onPress: () => void; testID: string }) {
  const palette = usePalette();
  return (
    <Pressable
      testID={testID}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={[styles.choice, { borderColor: palette.accent, backgroundColor: selected ? palette.accent : 'transparent' }]}
    >
      <Text style={[styles.choiceLabel, { color: selected ? palette.onAccent : palette.accent }]}>{label}</Text>
    </Pressable>
  );
}

export function HeadlessQuestion({ survey }: { survey: UsePitacoSurveyResult }) {
  const palette = usePalette();
  const question = survey.question;
  if (question === null) return null;
  const value = survey.value;

  if (question.type === 'FREE_TEXT') {
    const notice = survey.survey?.freeTextNotice;
    return (
      <View style={styles.gap}>
        {notice?.enabled === true && <Text style={[styles.notice, { color: palette.muted }]}>{notice.text}</Text>}
        <TextInput
          testID="cenario-09-texto"
          multiline
          maxLength={2000}
          value={typeof value === 'string' ? value : ''}
          onChangeText={(text) => survey.setText(text)}
          onFocus={() => survey.focusText()}
          onBlur={() => survey.blurText()}
          placeholder="Escreva aqui"
          placeholderTextColor={palette.muted}
          style={[styles.input, { borderColor: palette.border, color: palette.text }]}
        />
      </View>
    );
  }

  if (question.type === 'SINGLE_CHOICE' || question.type === 'MULTIPLE_CHOICE') {
    const multiple = question.type === 'MULTIPLE_CHOICE';
    const isSelected = (option: string) => (Array.isArray(value) ? value.includes(option) : value === option);
    return (
      <View style={styles.wrap}>
        {question.options.map((option) => {
          const selected = isSelected(option.value);
          return (
            <Choice
              key={option.value}
              testID={`cenario-09-opcao-${option.value}`}
              label={option.label}
              selected={selected}
              onPress={() => (multiple && selected ? survey.deselect(option.value) : survey.select(option.value))}
            />
          );
        })}
      </View>
    );
  }

  const range = question.range ?? DEFAULT_RANGES[question.type];
  const numbers = Array.from({ length: range.max - range.min + 1 }, (_, index) => range.min + index);
  return (
    <View style={styles.gap}>
      <View style={styles.wrap}>
        {numbers.map((number) => (
          <Choice
            key={number}
            testID={`cenario-09-opcao-${number}`}
            label={String(number)}
            selected={value === number}
            onPress={() => survey.select(number)}
          />
        ))}
      </View>
      {question.range !== null && (
        <Text style={[styles.notice, { color: palette.muted }]}>
          {range.min}: {question.range.minLabel ?? '—'} · {range.max}: {question.range.maxLabel ?? '—'}
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  gap: { gap: 8 },
  wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  choice: { minWidth: 44, minHeight: 44, paddingHorizontal: 12, borderRadius: 8, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  choiceLabel: { fontSize: 15, fontWeight: '600' },
  input: { minHeight: 96, borderWidth: 1, borderRadius: 8, padding: 10, fontSize: 15, textAlignVertical: 'top' },
  notice: { fontSize: 12, lineHeight: 17 },
});
