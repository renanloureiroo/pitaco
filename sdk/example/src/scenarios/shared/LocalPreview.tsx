// Modo "preview local" dos cenários 5 a 10: a mesma customização sobre o schema do seed, com o
// `<PitacoPreview />` (sem elegibilidade, sem exibição aberta, sem envio), para repetir à vontade
// sem limpar a identidade. Os eventos vão ao painel pela forma `preview-local`.
//
// Sem `children`, usa `<PitacoPreview />` (inclusive `presentation="modal"`/`"bottom-sheet"`, que
// abrem o contêiner do SDK). Com `children`, monta a UI do app dentro do `PitacoPreviewProvider`
// (o headless do cenário 9), e ela recebe `onFinish` para fechar.
import type { PartialPitacoStrings, PitacoRendererMap, PitacoSlotMap, PitacoThemeConfig, Presentation } from '@pitaco/react-native';
import { PitacoPreview, PitacoPreviewProvider } from '@pitaco/react-native/preview';
import { type ReactNode, useCallback, useState } from 'react';
import { usePublishEvent } from '../../debug/eventLog';
import { seedSurvey } from '../../pitaco/seed';
import { ActionButton } from '../../ui/ActionButton';
import { Hint } from '../../ui/Screen';

export const LOCAL_PREVIEW_FORM = 'preview-local';

export interface LocalPreviewProps {
  readonly scenarioId: string;
  readonly nn: string;
  readonly presentation?: Presentation;
  readonly theme?: PitacoThemeConfig;
  readonly strings?: PartialPitacoStrings;
  readonly renderers?: Partial<PitacoRendererMap>;
  readonly slots?: Partial<PitacoSlotMap>;
  readonly children?: (onFinish: () => void) => ReactNode;
}

export function LocalPreview(props: LocalPreviewProps) {
  const { scenarioId, nn, presentation = 'inline', theme, strings, renderers, slots, children } = props;
  const publish = usePublishEvent(scenarioId);
  const [run, setRun] = useState(0);
  const [open, setOpen] = useState(false);
  const onEvent = useCallback((event: Parameters<typeof publish>[0]) => publish(event, { form: LOCAL_PREVIEW_FORM }), [publish]);
  const close = useCallback(() => setOpen(false), []);

  if (seedSurvey === null) return <Hint>Pesquisa do seed não encontrada: rode `npm run seed`.</Hint>;

  const shared = {
    schema: seedSurvey.schema,
    presentation,
    triggerEvent: seedSurvey.triggerEvent,
    onEvent,
    ...(theme === undefined ? {} : { theme }),
    ...(strings === undefined ? {} : { strings }),
    ...(renderers === undefined ? {} : { renderers }),
    ...(slots === undefined ? {} : { slots }),
  };

  return (
    <>
      <ActionButton
        testID={`cenario-${nn}-abrir-preview`}
        label={open ? 'Recomeçar preview local' : 'Abrir preview local'}
        variant="secondary"
        onPress={() => {
          setRun((value) => value + 1);
          setOpen(true);
        }}
      />
      <Hint>Preview local: sem elegibilidade e sem gravar nada no backend. Pode repetir quantas vezes quiser.</Hint>
      {open &&
        (children === undefined ? (
          <PitacoPreview key={run} {...shared} onFinish={close} />
        ) : (
          <PitacoPreviewProvider key={run} {...shared}>
            {children(close)}
          </PitacoPreviewProvider>
        ))}
    </>
  );
}
