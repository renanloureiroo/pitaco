// Painel de depuração: o que o SDK está fazendo, ao vivo.
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { DebugActions } from '../../src/debug/panel/DebugActions';
import { EventsView } from '../../src/debug/panel/EventsView';
import { NetworkView } from '../../src/debug/panel/NetworkView';
import { StateView } from '../../src/debug/panel/StateView';
import { usePalette } from '../../src/ui/palette';
import { Segmented } from '../../src/ui/Segmented';

type Section = 'eventos' | 'rede' | 'estado';

const SECTIONS = [
  { value: 'eventos', label: 'Eventos' },
  { value: 'rede', label: 'Rede' },
  { value: 'estado', label: 'Fila e identidade' },
] as const;

export default function DebugPanel() {
  const palette = usePalette();
  const [section, setSection] = useState<Section>('eventos');

  return (
    <View testID="tela-depuracao" style={[styles.container, { backgroundColor: palette.background }]}>
      <DebugActions />
      <Segmented testIDPrefix="secao" options={SECTIONS} value={section} onChange={setSection} />
      <View style={styles.body}>
        {section === 'eventos' && <EventsView />}
        {section === 'rede' && <NetworkView />}
        {section === 'estado' && <StateView />}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 12, gap: 10 },
  body: { flex: 1 },
});
