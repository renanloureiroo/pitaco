import { useContext, useEffect, useMemo } from 'react';
import type { Attributes, Respondent } from '../core/config';
import { createLogger } from '../core/logger';
import type { RuntimeDiagnostics } from '../core/runtime/runtime';
import { PitacoContext } from './context';

// Controle de onde e quando exibir (feature 4), sobre o portão padrão do runtime.
export interface PitacoActions {
  // Dispara a consulta de elegibilidade. Nunca rejeita; falha é silêncio.
  track(eventName: string, attributes?: Attributes): Promise<void>;
  setRespondent(respondent: Respondent | null): void;
  setAttributes(attributes: Attributes): void;
  // Logout: dispensa a pesquisa aberta, esquece respondente e atributos e troca o deviceId.
  reset(): Promise<void>;
  // Bloqueia a exibição enquanto o motivo estiver ativo (telas em que nada pode aparecer, como
  // pagamento). Motivos acumulam por contagem de referência: use `<PitacoBlock />` quando possível
  // — ele já pareia `block`/`unblock` com a montagem do componente.
  block(reason?: string): void;
  // Desfaz um `block(reason)`. Sem o `block(reason)` correspondente, é ignorado com aviso em
  // desenvolvimento.
  unblock(reason?: string): void;
  // Represa a próxima pesquisa disponível (ou a que já estiver retida) sem exibir.
  defer(): void;
  // Libera uma pesquisa represada por `defer()`. Sem o `defer()` correspondente, é ignorado com
  // aviso em desenvolvimento.
  release(): void;
  // Estado interno do runtime (identidade, fila local, bloqueios, chave recusada). Não é um
  // recurso de produto: existe para ferramentas de diagnóstico, como o painel de depuração do
  // exemplo. `null` fora do `<PitacoProvider>` (ou com configuração inválida).
  diagnostics(): RuntimeDiagnostics | null;
  // Zera só o limite de uma pesquisa por sessão de app (feature 6), sem recriar o runtime.
  // Ferramenta de diagnóstico (o painel de depuração do exemplo simula "app reaberto"); não afeta
  // bloqueios nem adiamentos pendentes, que são escopo de tela.
  simulateAppReopen(): void;
}

const inert: PitacoActions = {
  track: () => Promise.resolve(),
  setRespondent: () => undefined,
  setAttributes: () => undefined,
  reset: () => Promise.resolve(),
  block: () => undefined,
  unblock: () => undefined,
  defer: () => undefined,
  release: () => undefined,
  diagnostics: () => null,
  simulateAppReopen: () => undefined,
};

const logger = createLogger();

export function warnOutsideProvider(hook: string): void {
  logger.warnOnce(
    `outside-${hook}`,
    `${hook}() foi chamado fora do <PitacoProvider>. Envolva a raiz do app com o Provider; até lá as chamadas não fazem nada.`,
  );
}

export function usePitaco(): PitacoActions {
  const context = useContext(PitacoContext);
  const runtime = context?.runtime ?? null;

  useEffect(() => {
    if (context === null) warnOutsideProvider('usePitaco');
  }, [context]);

  return useMemo<PitacoActions>(() => {
    if (runtime === null) return inert;
    return {
      track: (eventName, attributes) => runtime.track(eventName, attributes),
      setRespondent: (respondent) => runtime.setRespondent(respondent),
      setAttributes: (attributes) => runtime.setAttributes(attributes),
      reset: () => runtime.reset(),
      block: (reason) => runtime.block(reason),
      unblock: (reason) => runtime.unblock(reason),
      defer: () => runtime.defer(),
      release: () => runtime.release(),
      diagnostics: () => runtime.diagnostics(),
      simulateAppReopen: () => runtime.simulateAppReopen(),
    };
  }, [runtime]);
}
