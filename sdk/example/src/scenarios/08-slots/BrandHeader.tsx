// Cabeçalho próprio do cenário 8: faixa com a marca do app, a posição e uma barra de avanço. Recebe
// o resumo da pesquisa e o progresso do core, como o cabeçalho padrão.
import type { HeaderSlotProps } from '@pitaco/react-native';
import { StyleSheet, Text, View } from 'react-native';

export function BrandHeader({ progress }: HeaderSlotProps) {
  const ratio = progress.total > 0 ? Math.min(1, progress.position / progress.total) : 0;
  return (
    <View testID="cenario-08-header" style={styles.band}>
      <Text style={styles.brand}>Pitaco Café · pesquisa rápida</Text>
      <Text style={styles.position}>
        {progress.position} de {progress.total}
      </Text>
      <View style={styles.track}>
        <View style={[styles.fill, { width: `${ratio * 100}%` }]} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  band: { backgroundColor: '#2B8A3E', paddingHorizontal: 20, paddingVertical: 12, gap: 6 },
  brand: { color: '#FFFFFF', fontSize: 15, fontWeight: '700' },
  position: { color: '#E6FCF5', fontSize: 13 },
  track: { height: 4, borderRadius: 2, backgroundColor: 'rgba(255, 255, 255, 0.3)' },
  fill: { height: 4, borderRadius: 2, backgroundColor: '#FFFFFF' },
});
