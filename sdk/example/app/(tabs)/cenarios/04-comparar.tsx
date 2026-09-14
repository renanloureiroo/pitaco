import type { InteractionEvent } from '@pitaco/react-native';
import { PitacoPreview, PitacoPreviewProvider } from '@pitaco/react-native/preview';
import { useRouter } from 'expo-router';
import { type ReactNode, useCallback, useState } from 'react';
import { Pressable, Text, View, ScrollView } from 'react-native';
import { usePublishEvent } from '../../../src/debug/eventLog';
import { seedSurveys } from '../../../src/pitaco/seed';
import { useScenario } from '../../../src/pitaco/useScenario';
import { GorhomSurveySheet } from '../../../src/scenarios/02-gorhom/GorhomSurveySheet';
import { COMPARE_THEMES, frameColors, THEME_OPTIONS, type ThemeChoice } from '../../../src/scenarios/04-comparar/themes';
import { ActionButton } from '../../../src/ui/ActionButton';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';
import { Segmented } from '../../../src/ui/Segmented';
import { usePalette } from '../../../src/ui/palette';

type OverlayForm = 'sheet' | 'gorhom';

export default function CompareScenario() {
  useScenario('04-comparar');
  const router = useRouter();
  const palette = usePalette();
  const publish = usePublishEvent('04-comparar');
  const [choice, setChoice] = useState<ThemeChoice>('claro');
  const [open, setOpen] = useState<OverlayForm | null>(null);
  const [run, setRun] = useState(0);
  const [selectedSurveyIndex, setSelectedSurveyIndex] = useState(0);

  const theme = COMPARE_THEMES[choice];
  const colors = frameColors(choice);

  const selectedSurvey = seedSurveys.length > 0 ? seedSurveys[selectedSurveyIndex] : null;

  const onSheetEvent = useCallback((event: InteractionEvent) => publish(event, { form: 'sheet' }), [publish]);
  const onGorhomEvent = useCallback((event: InteractionEvent) => publish(event, { form: 'gorhom' }), [publish]);
  const close = useCallback(() => setOpen(null), []);

  // O corpo do sheet do gorhom mora no portal dele: o preview entra por dentro do sheet.
  const wrapGorhom = useCallback(
    (body: ReactNode) =>
      selectedSurvey === null ? null : (
        <PitacoPreviewProvider
          schema={selectedSurvey.schema}
          presentation="inline"
          triggerEvent={selectedSurvey.triggerEvent}
          theme={theme}
          onEvent={onGorhomEvent}
        >
          {body}
        </PitacoPreviewProvider>
      ),
    [theme, onGorhomEvent, selectedSurvey],
  );

  const openOverlay = (form: OverlayForm) => {
    setRun((value) => value + 1);
    setOpen(form);
  };

  return (
    <>
      <Screen testID="tela-04-comparar">
        <Paragraph>A mesma pesquisa do seed nas três formas. Abra uma, percorra, feche e abra outra.</Paragraph>
        {seedSurveys.length === 0 ? (
          <Hint>Pesquisa do seed não encontrada: rode `npm run seed` com o backend no ar.</Hint>
        ) : (
          <>
            <View style={{ marginVertical: 8 }}>
              <Text style={{ fontSize: 14, fontWeight: '600', color: palette.text, marginBottom: 8 }}>Selecione a pesquisa:</Text>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
                {seedSurveys.map((survey, index) => {
                  const isSelected = index === selectedSurveyIndex;
                  return (
                    <Pressable
                      key={survey.surveyId}
                      onPress={() => setSelectedSurveyIndex(index)}
                      style={[
                        {
                          paddingHorizontal: 12,
                          paddingVertical: 8,
                          borderRadius: 8,
                          borderWidth: 1,
                          borderColor: isSelected ? palette.primary : palette.border,
                          backgroundColor: isSelected ? palette.primary : palette.surface,
                        }
                      ]}
                    >
                      <Text style={{ color: isSelected ? '#fff' : palette.text, fontSize: 13, fontWeight: '500' }}>
                        {survey.title}
                      </Text>
                    </Pressable>
                  );
                })}
              </ScrollView>
            </View>
            <Segmented testIDPrefix="cenario-04-tema" options={THEME_OPTIONS} value={choice} onChange={setChoice} />
            <ActionButton testID="cenario-04-sheet" label="Sheet do SDK" disabled={open !== null} onPress={() => openOverlay('sheet')} />
            <ActionButton testID="cenario-04-gorhom" label="Gorhom" disabled={open !== null} onPress={() => openOverlay('gorhom')} />
            <ActionButton
              testID="cenario-04-tela"
              label="Tela"
              disabled={open !== null}
              onPress={() => {
                // Pass the selected index so the next screen knows which one to use.
                // Wait, 04-comparar-tela uses seedSurvey from seed.ts currently. We might need to pass it as a param.
                router.push({ pathname: '/cenarios/04-comparar-tela', params: { tema: choice, surveyIndex: selectedSurveyIndex } })
              }}
            />
          </>
        )}
        <Hint>
          Preview local: nada vai para o backend e dá para repetir à vontade. No painel, agrupe por &quot;Cenário/forma&quot;:
          04-comparar · sheet, · gorhom e · tela. A sequência é a mesma; muda só o presentation do survey_presented.
        </Hint>
      </Screen>
      {open === 'sheet' && selectedSurvey !== null && (
        <PitacoPreview
          key={run}
          schema={selectedSurvey.schema}
          presentation="bottom-sheet"
          triggerEvent={selectedSurvey.triggerEvent}
          theme={theme}
          onEvent={onSheetEvent}
          onFinish={close}
        />
      )}
      {/* Um BottomSheetModal novo por abertura (ver o cenário 2): a mesma instância não reabria. */}
      <GorhomSurveySheet
        key={`gorhom-${run}`}
        open={open === 'gorhom'}
        onClosed={close}
        backgroundColor={colors.background}
        handleColor={colors.handle}
        wrap={wrapGorhom}
      />
    </>
  );
}
