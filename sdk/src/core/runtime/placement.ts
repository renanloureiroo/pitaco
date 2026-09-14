// Ponto de extensão do controle de onde e quando exibir.
//
// O runtime entrega cada pesquisa renderizável ao `PlacementGate`, que decide: oferecer agora,
// segurar para depois (adiamento) ou descartar sem abrir exibição (bloqueio, prazo do adiamento
// vencido). O limite de uma pesquisa por sessão de app é resolvido antes disso, em `track()` —
// nem chega a consultar o servidor —, então não passa por aqui.
//
// O portão padrão do runtime (feature 4, em `runtime.ts`) já implementa bloqueio e adiamento sobre
// esta mesma interface; `setPlacementGate` troca por outra implementação inteira, sem mexer na
// máquina nem na fila. `immediatePlacement` fica disponível para quem troca o portão e quer voltar
// ao comportamento mais simples (oferece sempre, sem bloqueio nem adiamento próprios).

import type { PlacementEventDataMap, PlacementEventType } from '../../catalog/placement';
import type { Survey } from '../survey/schema';

export interface SurveyCandidate {
  readonly survey: Survey;
  readonly triggerEvent: string;
}

export interface PlacementControls {
  // Torna a pesquisa disponível (`usePitacoSurvey().available`). Chamar mais de uma vez não faz nada.
  offer(): void;
  // Descarta sem abrir exibição. O motivo vai só para o log de depuração.
  discard(reason: string): void;
  // Emite um evento `placement_` para o `onEvent` do app.
  notify<T extends PlacementEventType>(type: T, data: PlacementEventDataMap[T]): void;
}

export interface PlacementGate {
  consider(candidate: SurveyCandidate, controls: PlacementControls): void;
  // Chamado quando a exibição oferecida termina (concluída, dispensada ou descartada).
  sessionFinished?(candidate: SurveyCandidate): void;
  dispose?(): void;
}

export const immediatePlacement: PlacementGate = {
  consider: (_candidate, controls) => controls.offer(),
};
