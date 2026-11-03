// O corpo do sheet do gorhom: `<PitacoSurveyContent presentDeferred />` numa `BottomSheetScrollView`.
// Fica dentro do contexto do Pitaco que vale no sheet (o da raiz, ou o preview do cenário 4), e
// entrega ao `GorhomSurveySheet` as duas ações que ele dispara de fora: "terminou de abrir" e
// "o gorhom fechou, por esta via".
import { BottomSheetScrollView, useBottomSheet } from '@gorhom/bottom-sheet';
import { type DismissVia, PitacoSurveyContent, usePitacoSurvey } from '@pitaco/react-native';
import { type RefObject, useEffect } from 'react';

export interface SheetBridge {
  readonly opened: () => void;
  readonly dismissed: (via: DismissVia) => void;
}

export interface SheetBodyProps {
  readonly bridgeRef: RefObject<SheetBridge | null>;
  readonly requestClose: () => void;
  readonly bottomInset: number;
}

const TOP_SNAP_INDEX = 1;

export function SheetBody({ bridgeRef, requestClose, bottomInset }: SheetBodyProps) {
  const survey = usePitacoSurvey();
  const { snapToIndex } = useBottomSheet();
  const { present, dismiss, focusedQuestionKey } = survey;

  useEffect(() => {
    bridgeRef.current = {
      opened: () => present('inline'),
      // Depois de um desfecho (concluir, o botão de fechar do SDK), o core ignora a dispensa.
      dismissed: (via) => dismiss(via),
    };
  }, [bridgeRef, present, dismiss]);

  // O texto livre do SDK ganhou foco (o core sabe: `focusedQuestionKey`): sobe o sheet para o
  // ponto mais alto, para o campo ficar acima do teclado.
  useEffect(() => {
    if (focusedQuestionKey !== null) snapToIndex(TOP_SNAP_INDEX);
  }, [focusedQuestionKey, snapToIndex]);

  return (
    <BottomSheetScrollView
      testID="cenario-02-sheet"
      keyboardShouldPersistTaps="handled"
      contentContainerStyle={{ paddingBottom: bottomInset + 16 }}
    >
      <PitacoSurveyContent presentDeferred onFinish={requestClose} />
    </BottomSheetScrollView>
  );
}
