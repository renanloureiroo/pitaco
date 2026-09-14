// Rodapé próprio do cenário 8. As ações são as do core: `onNext` na última pergunta aplicável
// conclui sozinho, e quem valida a obrigatória em branco é o core (`validation_blocked`), por isso
// o botão de avançar nunca fica desabilitado.
import type { FooterSlotProps } from '@pitaco/react-native';
import { Pressable, StyleSheet, Text, View } from 'react-native';

export function BrandFooter({ canGoBack, isLast, onBack, onNext }: FooterSlotProps) {
  return (
    <View style={styles.row}>
      {canGoBack && (
        <Pressable testID="cenario-08-voltar" accessibilityRole="button" onPress={onBack} style={[styles.button, styles.secondary]}>
          <Text style={styles.secondaryLabel}>← Voltar</Text>
        </Pressable>
      )}
      <Pressable testID="cenario-08-avancar" accessibilityRole="button" onPress={onNext} style={[styles.button, styles.primary]}>
        <Text style={styles.primaryLabel}>{isLast ? 'Enviar respostas' : 'Continuar →'}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 10, paddingHorizontal: 20, paddingVertical: 16 },
  button: { flex: 1, minHeight: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  primary: { backgroundColor: '#2B8A3E' },
  secondary: { borderWidth: 2, borderColor: '#2B8A3E' },
  primaryLabel: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  secondaryLabel: { color: '#2B8A3E', fontSize: 16, fontWeight: '700' },
});
