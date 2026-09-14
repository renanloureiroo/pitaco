// Uma linha dizendo se há pesquisa na tela, lida de `usePitacoSurvey().status` (só leitura).
import { usePitacoSurvey, type PitacoSurveyState } from '@pitaco/react-native';
import { StyleSheet, Text } from 'react-native';
import { usePalette } from '../../ui/palette';

const LABELS: Record<PitacoSurveyState['status'], string> = {
  idle: 'nenhuma na tela',
  ready: 'pronta para abrir',
  presented: 'na tela',
  completed: 'concluída',
  dismissed: 'dispensada',
  discarded: 'descartada sem exibição',
};

export function SurveyStatusLine({ testID }: { testID: string }) {
  const palette = usePalette();
  const { status } = usePitacoSurvey();
  return (
    <Text testID={testID} style={[styles.text, { color: palette.text }]}>
      Pesquisa: {LABELS[status]}
    </Text>
  );
}

const styles = StyleSheet.create({ text: { fontSize: 14, fontWeight: '600' } });
