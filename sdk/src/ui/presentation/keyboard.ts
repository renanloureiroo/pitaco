// Como os contêineres do SDK (bottom sheet e modal em tela cheia) evitam o teclado. É o padrão da
// documentação do React Native: `padding` no iOS e `height` no Android.
//
// No Android ponta a ponta (Android 15+), o `adjustResize` não encolhe a janela de um `Modal`: sem
// `behavior`, a folha ficava atrás do teclado, com só a alça à vista, e o campo de texto livre, o
// contador e o "Enviar" sumiam. O `KeyboardAvoidingView` não repassa `behavior` ao `View` que
// renderiza, então a regra fica aqui, testável, e os dois contêineres a usam.

import { Platform, type KeyboardAvoidingViewProps } from 'react-native';

export function keyboardAvoidingBehavior(os: string = Platform.OS): NonNullable<KeyboardAvoidingViewProps['behavior']> {
  return os === 'ios' ? 'padding' : 'height';
}
