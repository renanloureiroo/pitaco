// Botão "Disparar pesquisa" do fluxo real (elegibilidade, exibição e resposta gravadas no
// backend), com a orientação de sempre: o servidor não reexibe a pesquisa a quem já respondeu ou
// dispensou. testID `cenario-<NN>-disparar`.
import { usePitaco } from '@pitaco/react-native';
import { seedSurvey } from '../../pitaco/seed';
import { ActionButton } from '../../ui/ActionButton';
import { Hint } from '../../ui/Screen';

export function TriggerButton({ nn }: { nn: string }) {
  const { track } = usePitaco();
  return (
    <>
      <ActionButton
        testID={`cenario-${nn}-disparar`}
        label="Disparar pesquisa"
        disabled={seedSurvey === null}
        onPress={() => {
          if (seedSurvey !== null) void track(seedSurvey.triggerEvent);
        }}
      />
      {seedSurvey === null && <Hint>Pesquisa do seed não encontrada: rode `npm run seed` com o backend no ar.</Hint>}
      <ResetHint />
    </>
  );
}

export function ResetHint() {
  return (
    <Hint>
      O servidor não mostra a pesquisa de novo a quem já respondeu ou dispensou. Para ver de novo, use
      &quot;Limpar storage e identidade&quot; na aba Depuração.
    </Hint>
  );
}
