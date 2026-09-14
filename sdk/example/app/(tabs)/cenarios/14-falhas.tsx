// Cenário 14 — Falhas. Cada botão troca a configuração do Provider só neste cenário (chave inválida,
// endereço inalcançável ou API lenta) e dispara `track`. Nada aparece, o app segue navegável, e o
// que o SDK fez fica na tela e no painel de depuração.
import { useEffect, useState } from 'react';
import { resetNetworkConditions, setNetworkConditions } from '../../../src/debug/networkConditions';
import { seedSurvey } from '../../../src/pitaco/seed';
import { useScenario } from '../../../src/pitaco/useScenario';
import { NetworkFeed } from '../../../src/scenarios/11-15-comum/NetworkFeed';
import { SurveyStatusLine } from '../../../src/scenarios/11-15-comum/SurveyStatusLine';
import { usePolledDiagnostics } from '../../../src/scenarios/11-15-comum/usePolledDiagnostics';
import { FAILURE_CASES, FAILURE_SCENARIO_ID, type FailureMode, failureCase, NO_FAILURE_CONFIG } from '../../../src/scenarios/14-falhas/modes';
import { RunTrigger } from '../../../src/scenarios/14-falhas/RunTrigger';
import { TapCounter } from '../../../src/scenarios/14-falhas/TapCounter';
import { ActionButton } from '../../../src/ui/ActionButton';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';

interface FailureRun {
  readonly id: number;
  readonly mode: FailureMode;
  readonly startedAt: string;
}

export default function FailuresScenario() {
  const [run, setRun] = useState<FailureRun | null>(null);
  const current = run === null ? null : failureCase(run.mode);
  const config = current?.config ?? NO_FAILURE_CONFIG;
  useScenario(FAILURE_SCENARIO_ID, config);
  const diagnostics = usePolledDiagnostics();

  // A latência simulada nunca sobrevive à saída do cenário.
  useEffect(() => resetNetworkConditions, []);

  const start = (mode: FailureMode) => {
    setNetworkConditions({ latencyMs: failureCase(mode).latencyMs, offline: false });
    setRun((previous) => ({ id: (previous?.id ?? 0) + 1, mode, startedAt: new Date().toISOString() }));
  };

  return (
    <Screen testID="tela-14-falhas">
      <Paragraph>Cada botão troca a configuração do Provider só neste cenário e dispara o evento do seed.</Paragraph>
      {FAILURE_CASES.map((item) => (
        <ActionButton
          key={item.mode}
          testID={`cenario-14-${item.mode}`}
          variant={run?.mode === item.mode ? 'primary' : 'secondary'}
          label={item.label}
          disabled={seedSurvey === null}
          onPress={() => start(item.mode)}
        />
      ))}
      <ActionButton
        testID="cenario-14-normal"
        variant="secondary"
        label="Voltar à configuração normal"
        onPress={() => {
          resetNetworkConditions();
          setRun(null);
        }}
      />
      <RunTrigger runId={run?.id ?? null} config={config} />
      <Paragraph>
        {current === null ? 'Nenhuma falha aplicada.' : `${current.label} (disparo ${run?.id ?? 0}): ${current.expected}`}
      </Paragraph>
      <SurveyStatusLine testID="cenario-14-status" />
      <TapCounter />
      <Paragraph>
        Chave recusada: {diagnostics?.keyRejected === true ? 'sim' : 'não'} · fila local: {diagnostics?.queue.length ?? '…'}
      </Paragraph>
      <NetworkFeed testID="cenario-14-rede" title="Requisições desde o último disparo" since={run?.startedAt ?? null} />
      <Hint>
        Os avisos com a correção (chave recusada, endereço inalcançável) saem no console do Metro em desenvolvimento, uma
        vez por sessão. A aba Depuração mostra o mesmo log de rede e a fila. Navegue para outra aba no meio da espera: o app
        não trava.
      </Hint>
    </Screen>
  );
}
