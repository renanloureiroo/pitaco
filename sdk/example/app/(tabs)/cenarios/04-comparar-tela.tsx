// A forma "Tela" do cenário 4: `<PitacoSurveyContent />` em tela cheia, sem contêiner do SDK, com a
// pesquisa real que a tela de baixo disparou. Sair pela navegação com a pesquisa aberta vira
// `via: "navigation"`; o `onFinish` que vem junto não volta de novo (`useLeavingRef`).
//
// Chama `useScenario` com a mesma configuração da tela de baixo (o mesmo objeto): o runtime não é
// recriado e a pesquisa continua a mesma.
import { PitacoSurveyContent, usePitacoSurvey } from '@pitaco/react-native';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { ScrollView, StyleSheet } from 'react-native';
import { useScenario } from '../../../src/pitaco/useScenario';
import { compareConfig } from '../../../src/scenarios/04-comparar/configs';
import { frameColors, parseThemeChoice } from '../../../src/scenarios/04-comparar/themes';
import { useLeavingRef } from '../../../src/scenarios/shared/useLeavingRef';
import { Hint } from '../../../src/ui/Screen';

export default function CompareScreenForm() {
  const params = useLocalSearchParams<{ tema?: string }>();
  const choice = parseThemeChoice(params.tema);
  useScenario('04-comparar', compareConfig('tela', choice));
  const router = useRouter();
  const leavingRef = useLeavingRef();
  const survey = usePitacoSurvey();

  return (
    <ScrollView
      testID="tela-04-comparar-tela"
      style={{ backgroundColor: frameColors(choice).background }}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      automaticallyAdjustKeyboardInsets
    >
      <Stack.Screen options={{ title: 'Pesquisa' }} />
      <PitacoSurveyContent
        onFinish={() => {
          if (!leavingRef.current) router.back();
        }}
      />
      {survey.status === 'idle' && <Hint>Nenhuma pesquisa em andamento. Volte e abra pela tela anterior.</Hint>}
    </ScrollView>
  );
}

const styles = StyleSheet.create({ content: { flexGrow: 1, paddingBottom: 24 } });
