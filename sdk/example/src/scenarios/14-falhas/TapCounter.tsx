// Prova de que o app segue respondendo enquanto o SDK falha: um contador que a pessoa incrementa.
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { ActionButton } from '../../ui/ActionButton';
import { usePalette } from '../../ui/palette';

export function TapCounter() {
  const palette = usePalette();
  const [count, setCount] = useState(0);

  return (
    <View style={styles.row}>
      <View style={styles.cell}>
        <ActionButton testID="cenario-14-incrementar" variant="secondary" label="O app responde? Toque aqui" onPress={() => setCount((value) => value + 1)} />
      </View>
      <Text testID="cenario-14-contagem" style={[styles.count, { color: palette.text }]}>
        {count} toque(s)
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  cell: { flex: 1 },
  count: { fontSize: 16, fontWeight: '700', fontVariant: ['tabular-nums'] },
});
