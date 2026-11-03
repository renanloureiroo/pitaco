// Contêiner do `BottomSheetModal` no iOS. O portal do gorhom desenha o sheet dentro da hierarquia do
// React, e no iOS o contêiner nativo de telas (react-native-screens) fica por cima dele: a barra de
// abas cobria o pé do sheet. O `FullWindowOverlay` põe o sheet numa camada acima da janela inteira,
// que é a correção documentada pelo gorhom (`containerComponent`, issue #832).
//
// Essa camada é uma janela nativa à parte, fora do `GestureHandlerRootView` da raiz; sem uma raiz
// própria, os gestos do gorhom (arrastar e o toque no fundo) não chegariam ao sheet.
//
// No Android o `FullWindowOverlay` não faz nada e a barra de abas não cobre o portal, então o
// contêiner fica sem valor lá.
import type { PropsWithChildren } from 'react';
import { Platform, StyleSheet } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { FullWindowOverlay } from 'react-native-screens';

function IosSheetContainer({ children }: PropsWithChildren) {
  return (
    <FullWindowOverlay>
      <GestureHandlerRootView style={styles.fill}>{children}</GestureHandlerRootView>
    </FullWindowOverlay>
  );
}

export const sheetContainer = Platform.OS === 'ios' ? IosSheetContainer : undefined;

const styles = StyleSheet.create({ fill: { flex: 1 } });
