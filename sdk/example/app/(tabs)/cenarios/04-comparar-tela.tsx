// A forma "Tela" do cenário 4: a mesma rota empilhada do cenário 3, com o preview do seed no
// lugar da pesquisa real. Sair pela navegação com a pesquisa aberta vira `via: "navigation"`.
import type { InteractionEvent } from '@pitaco/react-native';
import { PitacoPreview } from '@pitaco/react-native/preview';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { ScrollView, StyleSheet } from 'react-native';
import { usePublishEvent } from '../../../src/debug/eventLog';
import { seedSurveys } from '../../../src/pitaco/seed';
import { useScenario } from '../../../src/pitaco/useScenario';
import { COMPARE_THEMES, frameColors, parseThemeChoice } from '../../../src/scenarios/04-comparar/themes';
import { useLeavingRef } from '../../../src/scenarios/shared/useLeavingRef';

export default function CompareScreenForm() {
  useScenario('04-comparar');
  const router = useRouter();
  const params = useLocalSearchParams<{ tema?: string; surveyIndex?: string }>();
  const tema = params.tema;
  const choice = parseThemeChoice(tema);
  const leavingRef = useLeavingRef();
  const publish = usePublishEvent('04-comparar');
  const onEvent = useCallback((event: InteractionEvent) => publish(event, { form: 'tela' }), [publish]);

  const selectedSurveyIndex = typeof params.surveyIndex === 'string' ? parseInt(params.surveyIndex, 10) : 0;
  const selectedSurvey = seedSurveys.length > 0 ? (seedSurveys[selectedSurveyIndex] ?? null) : null;

  return (
    <ScrollView
      testID="tela-04-comparar-tela"
      style={{ backgroundColor: frameColors(choice).background }}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      automaticallyAdjustKeyboardInsets
    >
      <Stack.Screen options={{ title: 'Comparar — Tela' }} />
      {selectedSurvey !== null && (
        <PitacoPreview
          schema={selectedSurvey.schema}
          presentation="inline"
          triggerEvent={selectedSurvey.triggerEvent}
          theme={COMPARE_THEMES[choice]}
          onEvent={onEvent}
          onFinish={() => {
            if (!leavingRef.current) router.back();
          }}
        />
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({ content: { flexGrow: 1, paddingBottom: 24 } });
