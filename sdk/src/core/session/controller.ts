import type { Presentation } from '../../catalog/events';
import type { SurveyAction } from '../machine/machine';
import type { SurveySnapshot } from './snapshot';

// As ações que a UI pode pedir. `present` tem método próprio; segundo plano e `tick` são do core.
export type SurveyUserAction = Exclude<
  SurveyAction,
  { readonly type: 'present' | 'background' | 'foreground' | 'tick' }
>;

// O que os hooks enxergam: o runtime de verdade (com transporte) e o preview (sem transporte)
// implementam a mesma interface, e a UI não sabe qual dos dois está por baixo.
// Propriedades, e não métodos: `getSnapshot` e `subscribe` vão soltos para o
// `useSyncExternalStore` e precisam funcionar sem `this`.
export interface SurveyController {
  readonly getSnapshot: () => SurveySnapshot | null;
  readonly subscribe: (listener: () => void) => () => void;
  // A UI chama quando o contêiner ficou visível de fato. É isso que abre a exibição.
  readonly present: (presentation?: Presentation) => void;
  readonly dispatch: (action: SurveyUserAction) => void;
}
