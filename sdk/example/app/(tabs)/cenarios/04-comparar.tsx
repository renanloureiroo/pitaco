// Cenário 4 — Comparar apresentações. A mesma pesquisa do seed nas três formas dos cenários 1, 2 e
// 3, reaberta localmente com o preview (`seedSurvey.schema`): sem elegibilidade, sem exibição
// aberta, sem resposta gravada. Os eventos de cada forma vão ao painel marcados com a forma
// (`sheet`, `gorhom`, `tela`); agrupe por "Cenário/forma" para ver as sequências lado a lado.
// O `BottomSheetModalProvider` do gorhom está na raiz (`app/_layout.tsx`), acima das abas.
import type { InteractionEvent } from '@pitaco/react-native';
import { PitacoPreview, PitacoPreviewProvider } from '@pitaco/react-native/preview';
import { useRouter } from 'expo-router';
import { type ReactNode, useCallback, useState } from 'react';
import { usePublishEvent } from '../../../src/debug/eventLog';
import { seedSurvey } from '../../../src/pitaco/seed';
import { useScenario } from '../../../src/pitaco/useScenario';
import { GorhomSurveySheet } from '../../../src/scenarios/02-gorhom/GorhomSurveySheet';
import { COMPARE_THEMES, frameColors, THEME_OPTIONS, type ThemeChoice } from '../../../src/scenarios/04-comparar/themes';
import { ActionButton } from '../../../src/ui/ActionButton';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';
import { Segmented } from '../../../src/ui/Segmented';

type OverlayForm = 'sheet' | 'gorhom';

export default function CompareScenario() {
  useScenario('04-comparar');
  const router = useRouter();
  const publish = usePublishEvent('04-comparar');
  const [choice, setChoice] = useState<ThemeChoice>('claro');
  const [open, setOpen] = useState<OverlayForm | null>(null);
  const [run, setRun] = useState(0);
  const theme = COMPARE_THEMES[choice];
  const colors = frameColors(choice);

  const onSheetEvent = useCallback((event: InteractionEvent) => publish(event, { form: 'sheet' }), [publish]);
  const onGorhomEvent = useCallback((event: InteractionEvent) => publish(event, { form: 'gorhom' }), [publish]);
  const close = useCallback(() => setOpen(null), []);

  // O corpo do sheet do gorhom mora no portal dele: o preview entra por dentro do sheet.
  const wrapGorhom = useCallback(
    (body: ReactNode) =>
      seedSurvey === null ? null : (
        <PitacoPreviewProvider
          schema={seedSurvey.schema}
          presentation="inline"
          triggerEvent={seedSurvey.triggerEvent}
          theme={theme}
          onEvent={onGorhomEvent}
        >
          {body}
        </PitacoPreviewProvider>
      ),
    [theme, onGorhomEvent],
  );

  const openOverlay = (form: OverlayForm) => {
    setRun((value) => value + 1);
    setOpen(form);
  };

  return (
    <>
      <Screen testID="tela-04-comparar">
        <Paragraph>A mesma pesquisa do seed nas três formas. Abra uma, percorra, feche e abra outra.</Paragraph>
        {seedSurvey === null ? (
          <Hint>Pesquisa do seed não encontrada: rode `npm run seed` com o backend no ar.</Hint>
        ) : (
          <>
            <Segmented testIDPrefix="cenario-04-tema" options={THEME_OPTIONS} value={choice} onChange={setChoice} />
            <ActionButton testID="cenario-04-sheet" label="Sheet do SDK" disabled={open !== null} onPress={() => openOverlay('sheet')} />
            <ActionButton testID="cenario-04-gorhom" label="Gorhom" disabled={open !== null} onPress={() => openOverlay('gorhom')} />
            <ActionButton
              testID="cenario-04-tela"
              label="Tela"
              disabled={open !== null}
              onPress={() => router.push({ pathname: '/cenarios/04-comparar-tela', params: { tema: choice } })}
            />
          </>
        )}
        <Hint>
          Preview local: nada vai para o backend e dá para repetir à vontade. No painel, agrupe por &quot;Cenário/forma&quot;:
          04-comparar · sheet, · gorhom e · tela. A sequência é a mesma; muda só o presentation do survey_presented.
        </Hint>
      </Screen>
      {open === 'sheet' && seedSurvey !== null && (
        <PitacoPreview
          key={run}
          schema={seedSurvey.schema}
          presentation="bottom-sheet"
          triggerEvent={seedSurvey.triggerEvent}
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
