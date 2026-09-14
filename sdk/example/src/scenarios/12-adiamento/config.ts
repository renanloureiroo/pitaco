// Cenário 12 — Adiamento. As duas telas usam o mesmo id e a mesma configuração do prazo: um
// `deferTimeoutMs` diferente recriaria o runtime e a pesquisa adiada se perderia no caminho. A tela
// de liberação recebe o prazo escolhido pelo parâmetro `prazo` da rota.
import type { ScenarioProviderConfig } from '../../pitaco/ExampleContext';

export const DEFER_SCENARIO_ID = '12-adiamento';

export type DeferDeadline = '10' | '60';

export const DEFER_DEADLINE_OPTIONS = [
  { value: '10', label: 'Prazo de 10 s' },
  { value: '60', label: 'Prazo de 60 s' },
] as const;

// Constantes de módulo: `useScenario` compara por referência.
export const DEFER_CONFIGS: Readonly<Record<DeferDeadline, ScenarioProviderConfig>> = {
  '10': { deferTimeoutMs: 10_000 },
  '60': { deferTimeoutMs: 60_000 },
};

export const deferTimeoutOf = (deadline: DeferDeadline): number => (deadline === '60' ? 60_000 : 10_000);

export function parseDeadline(value: unknown): DeferDeadline {
  return value === '60' ? '60' : '10';
}

export const releaseRoute = (deadline: DeferDeadline) => `/cenarios/12-adiamento-liberar?prazo=${deadline}` as const;
