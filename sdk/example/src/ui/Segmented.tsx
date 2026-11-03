import { Pressable, StyleSheet, Text, View } from 'react-native';
import { usePalette } from './palette';

export interface SegmentedOption<T extends string> {
  readonly value: T;
  readonly label: string;
}

export interface SegmentedProps<T extends string> {
  readonly options: readonly SegmentedOption<T>[];
  readonly value: T;
  readonly onChange: (value: T) => void;
  // Cada opção recebe `${testIDPrefix}-${value}`.
  readonly testIDPrefix: string;
}

export function Segmented<T extends string>({ options, value, onChange, testIDPrefix }: SegmentedProps<T>) {
  const palette = usePalette();
  return (
    <View style={[styles.row, { borderColor: palette.border, backgroundColor: palette.surface }]}>
      {options.map((option) => {
        const selected = option.value === value;
        return (
          <Pressable
            key={option.value}
            testID={`${testIDPrefix}-${option.value}`}
            accessibilityRole="button"
            accessibilityState={{ selected }}
            onPress={() => onChange(option.value)}
            style={[styles.item, selected && { backgroundColor: palette.accent }]}
          >
            <Text style={[styles.label, { color: selected ? palette.onAccent : palette.text }]}>{option.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', borderWidth: 1, borderRadius: 10, padding: 3, gap: 3 },
  item: { flex: 1, minHeight: 36, borderRadius: 8, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 6 },
  label: { fontSize: 13, fontWeight: '600' },
});
