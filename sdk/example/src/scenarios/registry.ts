// Registro dos cenários do exemplo. Rotas fixas, em `app/(tabs)/cenarios/<id>.tsx`.
//
// testIDs estáveis para o Maestro:
// - item da lista na tela inicial: `cenario-<id>` (por exemplo `cenario-04-comparar`);
// - raiz da tela do cenário: `tela-<id>` (por exemplo `tela-04-comparar`).
export type ScenarioId = '04-comparar';

export interface ScenarioMeta {
  readonly id: ScenarioId;
  readonly title: string;
  readonly description: string;
}

export const SCENARIOS: readonly ScenarioMeta[] = [
  { id: '04-comparar', title: 'Testar Pesquisas', description: 'Testar os diversos cenários gerados de pesquisas.' },
];

export const scenarioHref = (id: ScenarioId) => `/cenarios/${id}` as const;

export function scenarioById(id: ScenarioId): ScenarioMeta {
  const meta = SCENARIOS.find((scenario) => scenario.id === id);
  if (meta === undefined) throw new Error(`Cenário desconhecido: ${id}`);
  return meta;
}
