// Resumo de um pagamento de mentira: só a moldura da tela em que nada pode aparecer.
import { StyleSheet, Text, View } from 'react-native';
import { usePalette } from '../../ui/palette';

export function PaymentSummary() {
  const palette = usePalette();
  return (
    <View style={[styles.card, { backgroundColor: palette.surface, borderColor: palette.border }]}>
      <Text style={[styles.label, { color: palette.muted }]}>Plano Pro · mensal</Text>
      <Text style={[styles.amount, { color: palette.text }]}>R$ 49,90</Text>
      <Text style={[styles.label, { color: palette.muted }]}>Cartão de crédito final 4242</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderWidth: 1, borderRadius: 12, padding: 16, gap: 4 },
  label: { fontSize: 13 },
  amount: { fontSize: 28, fontWeight: '700' },
});
