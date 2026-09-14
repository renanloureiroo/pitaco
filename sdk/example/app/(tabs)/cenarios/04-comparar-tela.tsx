// A forma "Tela" do cenário 4: a mesma rota empilhada do cenário 3, com o preview do seed no
// lugar da pesquisa real. Sair pela navegação com a pesquisa aberta vira `via: "navigation"`.
import type { InteractionEvent } from '@pitaco/react-native';
import { PitacoPreview } from '@pitaco/react-native/preview';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback } from 'react';
import { ScrollView, StyleSheet } from 'react-native';
import { usePublishEvent } from '../../../src/debug/eventLog';
import { seedSurvey } from '../../../src/pitaco/seed';
import { useScenario } from '../../../src/pitaco/useScenario';
import { COMPARE_THEMES, frameColors, parseThemeChoice } from '../../../src/scenarios/04-comparar/themes';
import { useLeavingRef } from '../../../src/scenarios/shared/useLeavingRef';

export default function CompareScreenForm() {
  useScenario('04-comparar');
  const router = useRouter();
  const { tema } = useLocalSearchParams<{ tema?: string }>();
  const choice = parseThemeChoice(tema);
  const leavingRef = useLeavingRef();
  const publish = usePublishEvent('04-comparar');
  const onEvent = useCallback((event: InteractionEvent) => publish(event, { form: 'tela' }), [publish]);

  return (
    <ScrollView
      testID="tela-04-comparar-tela"
      style={{ backgroundColor: frameColors(choice).background }}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      automaticallyAdjustKeyboardInsets
    >
      <Stack.Screen options={{ title: 'Comparar — Tela' }} />
      {seedSurvey !== null && (
        <PitacoPreview
          schema={seedSurvey.schema}
          presentation="inline"
          triggerEvent={seedSurvey.triggerEvent}
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
