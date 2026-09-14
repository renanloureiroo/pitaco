// Cenário 14 — as três falhas. Cada uma é uma configuração do Provider só para o cenário (constante
// de módulo: `useScenario` compara por referência) e, na API lenta, uma latência no embrulho de
// `fetch`. As três mudam a identidade do runtime (chave, endereço ou o prazo de elegibilidade
// explícito), então trocar de uma para outra sempre recria o runtime.
import type { ScenarioProviderConfig } from '../../pitaco/ExampleContext';

export const FAILURE_SCENARIO_ID = '14-falhas';

export type FailureMode = 'chave-invalida' | 'endereco-inalcancavel' | 'api-lenta';

export interface FailureCase {
  readonly mode: FailureMode;
  readonly label: string;
  readonly config: ScenarioProviderConfig;
  readonly latencyMs: number;
  readonly expected: string;
}

export const ELIGIBILITY_TIMEOUT_MS = 3000;
export const SLOW_LATENCY_MS = 5000;

export const FAILURE_CASES: readonly FailureCase[] = [
  {
    mode: 'chave-invalida',
    label: 'Chave inválida',
    config: { apiKey: 'pk_invalida' },
    latencyMs: 0,
    expected: 'A elegibilidade volta 401 e o SDK para de consultar até o fim da sessão (chave recusada: sim).',
  },
  {
    mode: 'endereco-inalcancavel',
    label: 'Endereço inalcançável',
    config: { baseUrl: 'http://10.255.255.1:9' },
    latencyMs: 0,
    expected: 'A conexão não completa; o SDK desiste da elegibilidade em 3 s (ou antes, se a rede recusar na hora).',
  },
  {
    mode: 'api-lenta',
    label: 'API lenta',
    config: { eligibilityTimeoutMs: ELIGIBILITY_TIMEOUT_MS },
    latencyMs: SLOW_LATENCY_MS,
    expected: 'A resposta levaria 5 s; o SDK desiste em 3 s (AbortError no painel) e nada aparece, nem depois.',
  },
];

export const NO_FAILURE_CONFIG: ScenarioProviderConfig = {};

export function failureCase(mode: FailureMode): FailureCase {
  return FAILURE_CASES.find((item) => item.mode === mode) ?? FAILURE_CASES[0]!;
}
