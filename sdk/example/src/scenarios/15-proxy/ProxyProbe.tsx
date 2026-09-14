// Confere se o proxy local está de pé sem gastar nada do Pitaco: uma rota fora de `/collect/*`
// recebe do próprio proxy um 404 `gateway.route_not_found`, sem ser repassada.
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { ActionButton } from '../../ui/ActionButton';
import { usePalette } from '../../ui/palette';

const TIMEOUT_MS = 3000;

async function probe(proxyBaseUrl: string): Promise<string> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const response = await fetch(`${proxyBaseUrl}/saude`, { signal: controller.signal });
    const body = await response.text();
    if (response.status === 404 && body.includes('gateway.route_not_found')) {
      return 'Proxy de pé: respondeu 404 gateway.route_not_found a uma rota fora de /collect (não repassou).';
    }
    return `Resposta inesperada (${response.status}). O endereço aponta mesmo para o npm run proxy?`;
  } catch {
    return 'Proxy fora do ar: rode `npm run proxy` em sdk/example.';
  } finally {
    clearTimeout(timer);
  }
}

export function ProxyProbe({ proxyBaseUrl }: { proxyBaseUrl: string }) {
  const palette = usePalette();
  const [result, setResult] = useState<string | null>(null);

  return (
    <View style={styles.box}>
      <ActionButton
        testID="cenario-15-conferir-proxy"
        variant="secondary"
        label="Conferir se o proxy está de pé"
        onPress={() => {
          setResult('Conferindo…');
          void probe(proxyBaseUrl).then(setResult);
        }}
      />
      {result !== null && (
        <Text testID="cenario-15-proxy-status" style={[styles.text, { color: palette.text }]}>
          {result}
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: 6 },
  text: { fontSize: 13, lineHeight: 18 },
});
