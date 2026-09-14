// Cenário 13 — Sem rede. Responder sem rede, fechar o app, reabrir com rede e ver a resposta chegar
// uma vez. O simulador iOS não tem modo avião: o interruptor "Simular sem rede" faz as requisições
// do SDK falharem como falha de rede (no embrulho de `fetch` do exemplo) e fica só em memória, então
// o app reaberto volta com rede. A fila do SDK está no AsyncStorage e sobrevive ao fechamento.
import { type QueueItem, usePitaco } from '@pitaco/react-native';
import { useEffect, useState } from 'react';
import { resetNetworkConditions, setNetworkConditions, useNetworkConditions } from '../../../src/debug/networkConditions';
import { seedSurvey } from '../../../src/pitaco/seed';
import { useScenario } from '../../../src/pitaco/useScenario';
import { NetworkFeed } from '../../../src/scenarios/11-15-comum/NetworkFeed';
import { ScenarioPrep } from '../../../src/scenarios/11-15-comum/ScenarioPrep';
import { SurveyStatusLine } from '../../../src/scenarios/11-15-comum/SurveyStatusLine';
import { usePolledDiagnostics } from '../../../src/scenarios/11-15-comum/usePolledDiagnostics';
import { DeliveryCheck } from '../../../src/scenarios/13-sem-rede/DeliveryCheck';
import { OfflineSwitch } from '../../../src/scenarios/13-sem-rede/OfflineSwitch';
import { clearOfflineRun, loadOfflineRun, type OfflineRun, saveOfflineRun } from '../../../src/scenarios/13-sem-rede/offlineRun';
import { QueueList } from '../../../src/scenarios/13-sem-rede/QueueList';
import { ActionButton } from '../../../src/ui/ActionButton';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';

type SubmissionItem = Extract<QueueItem, { kind: 'submission' }>;

export default function OfflineScenario() {
  useScenario('13-sem-rede');
  const { track } = usePitaco();
  const diagnostics = usePolledDiagnostics();
  const { offline } = useNetworkConditions();
  const [run, setRun] = useState<OfflineRun | null>(null);
  const [loaded, setLoaded] = useState(false);

  // Sair do cenário sem fechar o app devolve a rede.
  useEffect(() => resetNetworkConditions, []);

  useEffect(() => {
    let active = true;
    void loadOfflineRun().then((saved) => {
      if (!active) return;
      setRun(saved);
      setLoaded(true);
    });
    return () => {
      active = false;
    };
  }, []);

  // Assim que a resposta entra na fila sem rede, grava qual exibição conferir depois de reabrir.
  const pending = offline ? diagnostics?.queue.find((item): item is SubmissionItem => item.kind === 'submission') : undefined;
  const pendingDisplayId = pending?.displayId ?? null;
  const pendingCreatedAt = pending?.createdAt ?? null;
  const savedDisplayId = run?.displayId ?? null;
  useEffect(() => {
    if (pendingDisplayId === null || pendingCreatedAt === null || seedSurvey === null) return;
    if (pendingDisplayId === savedDisplayId) return;
    const next: OfflineRun = {
      displayId: pendingDisplayId,
      surveyId: seedSurvey.surveyId,
      queuedAt: new Date(pendingCreatedAt).toISOString(),
    };
    void saveOfflineRun(next).then(() => setRun(next));
  }, [pendingDisplayId, pendingCreatedAt, savedDisplayId]);

  return (
    <Screen testID="tela-13-sem-rede">
      <Paragraph>
        &quot;Disparar e cortar a rede&quot; busca a pesquisa com rede e liga &quot;Simular sem rede&quot; logo depois.
        Responda até o fim: abertura, eventos e resposta ficam na fila. Feche o app (sem voltar de tela), abra de novo
        e volte a este cenário.
      </Paragraph>
      <OfflineSwitch />
      <ActionButton
        testID="cenario-13-disparar-sem-rede"
        label="Disparar e cortar a rede"
        disabled={seedSurvey === null}
        onPress={() => {
          if (seedSurvey === null) return;
          setNetworkConditions({ offline: false });
          void track(seedSurvey.triggerEvent).then(() => setNetworkConditions({ offline: true }));
        }}
      />
      <SurveyStatusLine testID="cenario-13-status" />
      <QueueList queue={diagnostics?.queue ?? null} />
      {loaded && run !== null && (
        <DeliveryCheck
          run={run}
          queue={diagnostics?.queue ?? null}
          onForget={() => {
            void clearOfflineRun().then(() => setRun(null));
          }}
        />
      )}
      {loaded && run === null && <Hint>Nenhuma resposta gravada sem rede ainda.</Hint>}
      <NetworkFeed testID="cenario-13-requisicoes" title="Requisições ao Pitaco nesta abertura do app" since={null} limit={10} />
      <ScenarioPrep prefix="cenario-13" />
      <Hint>
        Em aparelho físico, use o modo avião de verdade: dispare com rede, ligue o modo avião com a pesquisa aberta,
        responda, feche o app, desligue o modo avião e reabra. Use o perfil Direto: a fila é separada por endereço e chave.
        Sem fechar o app, desligar o interruptor também entrega, na próxima tentativa do SDK (backoff de 2 s em diante).
      </Hint>
    </Screen>
  );
}
