// A pesquisa dentro de um `BottomSheetModal` do `@gorhom/bottom-sheet` (cenário 2, e a forma
// "Gorhom" do cenário 4). O SDK não sabe que o gorhom existe: o app abre o sheet, põe
// `<PitacoSurveyContent presentDeferred />` dentro e conta ao core o que aconteceu.
//
// - Abrir: `open` vira `true` → `present()` do gorhom. O `present()` do Pitaco (que abre a
//   exibição e emite `survey_presented`) só quando o gorhom avisa que terminou de abrir: o
//   `onChange` dele só dispara no fim da animação.
// - Fechar pelo gorhom (arrastar, fundo, voltar do Android): o `onDismiss` chama `dismiss(via)`.
//   O gorhom chama `onDismiss` antes de desmontar o conteúdo, então a via certa chega ao core antes
//   de o `<PitacoSurveyContent />` desmontar (e a dispensa por desmontagem não se repete).
// - Fechar pelo Pitaco (botão de fechar do SDK, concluir): `onFinish` fecha o sheet do gorhom.
//
// O conteúdo mora num portal do gorhom, renderizado junto do `BottomSheetModalProvider`: o
// contexto do Pitaco que vale lá dentro é o de onde o provider está. Quem precisa de outro (o
// preview do cenário 4) passa `wrap`, que envolve o corpo já dentro do sheet.
import { BottomSheetBackdrop, type BottomSheetBackdropProps, BottomSheetModal } from '@gorhom/bottom-sheet';
import type { DismissVia } from '@pitaco/react-native';
import { type ReactNode, useCallback, useEffect, useMemo, useRef } from 'react';
import { BackHandler } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { sheetContainer } from './SheetContainer';
import { type SheetBridge, SheetBody } from './SheetBody';

export interface GorhomSurveySheetProps {
  readonly open: boolean;
  // O sheet terminou de fechar (qualquer via). O app volta a poder abrir outro.
  readonly onClosed: () => void;
  readonly backgroundColor: string;
  readonly handleColor: string;
  readonly wrap?: (body: ReactNode) => ReactNode;
}

const SNAP_POINTS = ['60%', '92%'];

export function GorhomSurveySheet({ open, onClosed, backgroundColor, handleColor, wrap }: GorhomSurveySheetProps) {
  const modalRef = useRef<BottomSheetModal>(null);
  const bridgeRef = useRef<SheetBridge | null>(null);
  const openedRef = useRef(false);
  // Via da próxima dispensa pelo gorhom: arrastar é o padrão; o fundo e o voltar do Android trocam
  // antes de mandar fechar.
  const viaRef = useRef<DismissVia>('swipe');
  const insets = useSafeAreaInsets();

  useEffect(() => {
    if (open) modalRef.current?.present();
  }, [open]);

  // O gorhom não trata o voltar do Android: sem isto, o voltar sairia da tela com o sheet aberto.
  useEffect(() => {
    if (!open) return undefined;
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      viaRef.current = 'hardware_back';
      modalRef.current?.dismiss();
      return true;
    });
    return () => subscription.remove();
  }, [open]);

  const handleChange = useCallback((index: number) => {
    if (index >= 0 && !openedRef.current) {
      openedRef.current = true;
      bridgeRef.current?.opened();
    }
  }, []);

  const handleDismiss = useCallback(() => {
    bridgeRef.current?.dismissed(viaRef.current);
    openedRef.current = false;
    viaRef.current = 'swipe';
    onClosed();
  }, [onClosed]);

  const requestClose = useCallback(() => modalRef.current?.dismiss(), []);

  const renderBackdrop = useCallback(
    (props: BottomSheetBackdropProps) => (
      <BottomSheetBackdrop
        {...props}
        appearsOnIndex={0}
        disappearsOnIndex={-1}
        pressBehavior="close"
        onPress={() => {
          viaRef.current = 'backdrop';
        }}
      />
    ),
    [],
  );

  const body = useMemo(
    () => <SheetBody bridgeRef={bridgeRef} requestClose={requestClose} bottomInset={insets.bottom} />,
    [requestClose, insets.bottom],
  );

  return (
    <BottomSheetModal
      ref={modalRef}
      // No iOS, acima das abas e da pilha nativa (ver `SheetContainer`).
      containerComponent={sheetContainer}
      snapPoints={SNAP_POINTS}
      enableDynamicSizing={false}
      enablePanDownToClose
      backdropComponent={renderBackdrop}
      topInset={insets.top}
      // Teclado: o `keyboardBehavior` do gorhom só age para campos que ele conhece (o
      // `BottomSheetTextInput`); o texto livre do SDK é um `TextInput` comum. No Android o
      // `adjustResize` deixa a janela encolher (o Expo usa `resize`) e o sheet acompanha; nos dois
      // sistemas o `SheetBody` sobe o sheet para o ponto mais alto quando o campo ganha foco.
      keyboardBehavior="interactive"
      keyboardBlurBehavior="restore"
      android_keyboardInputMode="adjustResize"
      backgroundStyle={{ backgroundColor }}
      handleIndicatorStyle={{ backgroundColor: handleColor }}
      onChange={handleChange}
      onDismiss={handleDismiss}
    >
      {wrap === undefined ? body : wrap(body)}
    </BottomSheetModal>
  );
}
