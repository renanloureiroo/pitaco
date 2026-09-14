// Cenário 11 — Bloqueio. As duas telas (a de entrada e a de pagamento) usam o mesmo id e a mesma
// configuração: trocar `deferTimeoutMs` recriaria o runtime e perderia a pesquisa retida.
import type { ScenarioProviderConfig } from '../../pitaco/ExampleContext';

export const BLOCK_SCENARIO_ID = '11-bloqueio';
export const BLOCK_REASON = 'pagamento';

// O prazo de retenção vale para o bloqueio e para o adiamento (fase 4). 60 s em vez dos 5 min
// padrão, para dar para ver também o descarte esperando na tela de pagamento.
export const BLOCK_HOLD_MS = 60_000;
export const BLOCK_CONFIG: ScenarioProviderConfig = { deferTimeoutMs: BLOCK_HOLD_MS };

export const PAYMENT_ROUTE = '/cenarios/11-bloqueio-pagamento';
