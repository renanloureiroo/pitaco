// Cenário 15 — Proxy. O mesmo fluxo do cenário 1, pela topologia com gateway: esta tela troca o
// `baseUrl` do Provider para o perfil Proxy só enquanto estiver montada. O SDK não sabe que há um
// proxy; só o endereço muda, e as requisições passam pelo prefixo `/pitaco`.
import { usePitaco } from '@pitaco/react-native';
import { Text } from 'react-native';
import { useNetworkLog } from '../../../src/debug/networkLog';
import { profileById } from '../../../src/pitaco/config';
import { type ScenarioProviderConfig, useExampleState } from '../../../src/pitaco/ExampleContext';
import { seedSurvey } from '../../../src/pitaco/seed';
import { useScenario } from '../../../src/pitaco/useScenario';
import { NetworkFeed } from '../../../src/scenarios/11-15-comum/NetworkFeed';
import { ScenarioPrep } from '../../../src/scenarios/11-15-comum/ScenarioPrep';
import { SurveyStatusLine } from '../../../src/scenarios/11-15-comum/SurveyStatusLine';
import { ProxyProbe } from '../../../src/scenarios/15-proxy/ProxyProbe';
import { ActionButton } from '../../../src/ui/ActionButton';
import { usePalette } from '../../../src/ui/palette';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';

const PROXY = profileById('proxy');
const DIRECT = profileById('direto');
const PROXY_CONFIG: ScenarioProviderConfig = { baseUrl: PROXY.baseUrl };
const NO_CONFIG: ScenarioProviderConfig = {};

export default function ProxyScenario() {
  useScenario('15-proxy', PROXY.available ? PROXY_CONFIG : NO_CONFIG);
  const { track } = usePitaco();
  const { profile, activeScenario } = useExampleState();
  const palette = usePalette();
  const log = useNetworkLog();

  const effectiveUrl = activeScenario?.config.baseUrl ?? profile.baseUrl;
  const viaProxy = log.filter((entry) => PROXY.baseUrl !== '' && entry.url.startsWith(`${PROXY.baseUrl}/collect/`)).length;
  const direct = log.filter((entry) => DIRECT.baseUrl !== '' && entry.url.startsWith(`${DIRECT.baseUrl}/collect/`)).length;

  return (
    <Screen testID="tela-15-proxy">
      <Paragraph>
        O mesmo botão do cenário 1, com o Provider apontando para o gateway local. Suba o proxy antes: `npm run proxy`
        em sdk/example (porta 8787, prefixo /pitaco).
      </Paragraph>
      {!PROXY.available && (
        <Hint>Perfil Proxy sem configuração: defina EXPO_PUBLIC_PITACO_PROXY_BASE_URL no .env (npm run seed) e reinicie o Metro.</Hint>
      )}
      <Text testID="cenario-15-endereco" style={{ color: palette.text, fontWeight: '600' }}>
        URL efetiva do SDK: {effectiveUrl || '(sem endereço)'}
      </Text>
      <ProxyProbe proxyBaseUrl={PROXY.baseUrl} />
      <ActionButton
        testID="cenario-15-disparar"
        label="Disparar pesquisa pelo proxy"
        disabled={seedSurvey === null || !PROXY.available}
        onPress={() => {
          if (seedSurvey !== null) void track(seedSurvey.triggerEvent);
        }}
      />
      <SurveyStatusLine testID="cenario-15-status" />
      <Text testID="cenario-15-prefixo" style={{ color: palette.text }}>
        Requisições pelo gateway (/pitaco): {viaProxy} · direto ao backend (/api): {direct}
      </Text>
      <NetworkFeed testID="cenario-15-rede" title="Últimas requisições ao Pitaco" since={null} />
      <ScenarioPrep prefix="cenario-15" />
      <Hint>
        Trocar o baseUrl recria o runtime: a fila e o limite de sessão deste perfil são separados dos do perfil Direto. Para
        usar o proxy no app inteiro, escolha o perfil Proxy na tela inicial. O proxy repassa só POST /pitaco/collect/* e
        registra cada requisição no terminal onde roda.
      </Hint>
    </Screen>
  );
}
