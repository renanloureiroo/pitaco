// Cenário 10 — Modal e inline. "Modal": o modal em tela cheia do SDK (`presentation="modal"`).
// "Inline": `<PitacoSurveyContent />` no meio de uma tela com rolagem, sem contêiner nenhum; o app
// monta o conteúdo quando a pesquisa chega e o tira no `onFinish`.
import { PitacoSurveyContent } from '@pitaco/react-native';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useScenario } from '../../../src/pitaco/useScenario';
import { INLINE_CONFIG, MODAL_CONFIG } from '../../../src/scenarios/shared/configs';
import { FlowModes } from '../../../src/scenarios/shared/FlowModes';
import { LocalPreview } from '../../../src/scenarios/shared/LocalPreview';
import { TriggerButton } from '../../../src/scenarios/shared/TriggerButton';
import { useSurveyArrival } from '../../../src/scenarios/shared/useSurveyArrival';
import { usePalette } from '../../../src/ui/palette';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';
import { Segmented } from '../../../src/ui/Segmented';

type Form = 'modal' | 'inline';

const FORM_OPTIONS = [
  { value: 'modal', label: 'Modal (tela cheia)' },
  { value: 'inline', label: 'Inline' },
] as const;

const ARTICLE = [
  'Notícias do bairro: a feira de sábado mudou de rua e agora fica perto da praça, com mais barracas de orgânicos.',
  'A biblioteca abre até mais tarde às quintas. Há clube de leitura, oficina de escrita e empréstimo de jogos.',
  'O parque ganhou uma pista de caminhada nova, com iluminação e bebedouros a cada quinhentos metros.',
];

export default function ModalInlineScenario() {
  const [form, setForm] = useState<Form>('modal');
  useScenario('10-modal-inline', form === 'modal' ? MODAL_CONFIG : INLINE_CONFIG);
  const { survey, arrivedId, active, finish } = useSurveyArrival();
  const palette = usePalette();

  return (
    <Screen testID="tela-10-modal-inline">
      <Paragraph>O modal em tela cheia do SDK, ou a pesquisa embutida no meio desta tela, sem contêiner.</Paragraph>
      <Segmented testIDPrefix="cenario-10-forma" options={FORM_OPTIONS} value={form} onChange={setForm} />
      <Hint>Escolha a forma antes de disparar: trocar a apresentação recria o runtime do SDK.</Hint>
      <FlowModes
        nn="10"
        real={<TriggerButton nn="10" />}
        preview={<LocalPreview scenarioId="10-modal-inline" nn="10" presentation={form} />}
      />
      {form === 'inline' && (
        <>
          {ARTICLE.slice(0, 2).map((text) => (
            <Paragraph key={text}>{text}</Paragraph>
          ))}
          <View testID="cenario-10-inline" style={[styles.slot, { borderColor: palette.border }]}>
            {active && survey.status !== 'idle' ? (
              <PitacoSurveyContent key={arrivedId} onFinish={finish} />
            ) : (
              <Hint>No fluxo real, a pesquisa aparece aqui, no meio do conteúdo.</Hint>
            )}
          </View>
          {ARTICLE.map((text) => (
            <Paragraph key={`fim-${text}`}>{text}</Paragraph>
          ))}
        </>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({ slot: { borderTopWidth: 1, borderBottomWidth: 1, paddingVertical: 8 } });
