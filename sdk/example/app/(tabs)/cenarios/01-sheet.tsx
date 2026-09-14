// Cenário 1 — Sheet do SDK. A integração mínima: o `<PitacoProvider>` já está na raiz do app
// (`src/pitaco/PitacoRoot.tsx`), e esta tela só chama `track` com o evento que dispara a pesquisa.
// O SDK busca a pesquisa e a abre no bottom sheet próprio dele.
import { usePitaco } from '@pitaco/react-native';
import { seedSurvey } from '../../../src/pitaco/seed';
import { useScenario } from '../../../src/pitaco/useScenario';
import { ActionButton } from '../../../src/ui/ActionButton';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';

export default function SheetScenario() {
  useScenario('01-sheet');
  const { track } = usePitaco();

  return (
    <Screen testID="tela-01-sheet">
      <Paragraph>Toque no botão para disparar o evento do seed. A pesquisa abre no bottom sheet do SDK.</Paragraph>
      <ActionButton
        testID="cenario-01-disparar"
        label="Disparar pesquisa"
        disabled={seedSurvey === null}
        onPress={() => {
          if (seedSurvey !== null) void track(seedSurvey.triggerEvent);
        }}
      />
      <Hint>
        Experimente: arrastar o sheet para baixo, tocar no fundo, o voltar do Android, o teclado no texto livre
        e o movimento reduzido ligado no sistema. Uma pesquisa por sessão: para ver de novo, use
        &quot;Simular app reaberto&quot; na aba Depuração.
      </Hint>
    </Screen>
  );
}
