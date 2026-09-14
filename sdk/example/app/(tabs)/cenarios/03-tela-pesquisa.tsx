// A rota da pesquisa do cenário 3. `<PitacoSurveyContent />` em tela cheia, sem contêiner do SDK.
// - Concluir: o agradecimento aparece e o `onFinish('completed')` volta a navegação.
// - O X do SDK: `onFinish('dismissed')` também volta.
// - Seta do cabeçalho, gesto do iOS, voltar do Android: a rota desmonta com a pesquisa em andamento
//   e o próprio `<PitacoSurveyContent />` dispensa com `via: "navigation"`. O `onFinish` que vem
//   junto não volta de novo, porque a rota já está saindo (`useLeavingRef`).
//
// Chama `useScenario` com a mesma configuração da tela de baixo (o mesmo objeto): o runtime não é
// recriado e a pesquisa continua a mesma.
import { PitacoSurveyContent, usePitacoSurvey, usePitacoTheme } from '@pitaco/react-native';
import { Stack, useRouter } from 'expo-router';
import { ScrollView, StyleSheet } from 'react-native';
import { useScenario } from '../../../src/pitaco/useScenario';
import { INLINE_CONFIG } from '../../../src/scenarios/shared/configs';
import { useLeavingRef } from '../../../src/scenarios/shared/useLeavingRef';
import { Hint } from '../../../src/ui/Screen';

export default function SurveyRoute() {
  useScenario('03-tela', INLINE_CONFIG);
  const router = useRouter();
  const leavingRef = useLeavingRef();
  const survey = usePitacoSurvey();
  const theme = usePitacoTheme();

  return (
    <ScrollView
      testID="tela-03-tela-pesquisa"
      style={{ backgroundColor: theme.tokens.colors.background }}
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
      {survey.status === 'idle' && <Hint>Nenhuma pesquisa em andamento. Volte e dispare pelo cenário 3.</Hint>}
    </ScrollView>
  );
}

const styles = StyleSheet.create({ content: { flexGrow: 1, paddingBottom: 24 } });
