// Duas abas fixas: os cenários (com a própria pilha de navegação) e o painel de depuração. Trocar
// de aba não desmonta o cenário aberto, então dá para olhar os eventos e voltar.
import { Tabs } from 'expo-router';

export default function TabsLayout() {
  return (
    <Tabs screenOptions={{ tabBarIconStyle: { display: 'none' }, tabBarLabelStyle: { fontSize: 14 } }}>
      <Tabs.Screen name="cenarios" options={{ title: 'Cenários', headerShown: false, tabBarButtonTestID: 'tab-cenarios' }} />
      <Tabs.Screen name="depuracao" options={{ title: 'Depuração', tabBarButtonTestID: 'tab-depuracao' }} />
    </Tabs>
  );
}
