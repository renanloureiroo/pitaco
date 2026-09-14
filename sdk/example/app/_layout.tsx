import { BottomSheetModalProvider } from '@gorhom/bottom-sheet';
import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { LogBox, StyleSheet, useColorScheme } from 'react-native';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { installNetworkLogger } from '../src/debug/networkLog';
import { ExampleProvider } from '../src/pitaco/ExampleContext';
import { PitacoRoot } from '../src/pitaco/PitacoRoot';
import { FloatingResetButton } from '../src/ui/FloatingResetButton';

// O `BottomSheetModalProvider` do gorhom (cenários 2 e 4) fica aqui, acima das abas e dentro do
// `PitacoRoot`: o portal dele desenha o sheet por cima da barra de abas (dentro da tela, a barra
// nativa cobria o pé do sheet), continua dentro do `GestureHandlerRootView` e enxerga o contexto do
// Pitaco da raiz.
//
// Antes do Provider montar: o SDK lê o `fetch` global a cada chamada, e o painel de depuração
// mostra as requisições ao Pitaco.
installNetworkLogger();

// Aviso de desenvolvimento do próprio Expo Router ao abrir por deep link: a URL inicial resolve e
// faz setState no navegador antes de ele montar (expo-router/build/fork/useLinking.native.js). Não
// vem do exemplo nem do SDK e não quebra nada, mas a notificação do LogBox cobre a barra de abas
// no Android e engole o toque em "Depuração". Continua no console do Metro. O texto é o do React e
// não cita o componente, então esconde também esse aviso vindo de outro lugar (só do LogBox).
LogBox.ignoreLogs(["Can't perform a React state update on a component that hasn't mounted yet"]);

// O tema da navegação (cabeçalho, barra de abas, fundo das telas) segue o esquema do sistema, como
// a paleta do exemplo e o tema padrão do SDK. Sem isto, no escuro o conteúdo ficava escuro e o
// cabeçalho e as abas continuavam brancos (Expo Router 57 exporta o ThemeProvider e os temas).
export default function RootLayout() {
  const colorScheme = useColorScheme();
  return (
    <GestureHandlerRootView style={styles.root}>
      <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
        <ExampleProvider>
          <PitacoRoot>
            <BottomSheetModalProvider>
              <StatusBar style="auto" />
              <Stack screenOptions={{ headerShown: false }} />
              <FloatingResetButton />
            </BottomSheetModalProvider>
          </PitacoRoot>
        </ExampleProvider>
      </ThemeProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({ root: { flex: 1 } });
