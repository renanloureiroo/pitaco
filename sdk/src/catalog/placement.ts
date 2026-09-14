// Eventos de posicionamento: acontecem antes de existir exibição (pesquisa disponível, suprimida,
// bloqueada, adiada, liberada, adiamento vencido, limite da sessão). Não fazem parte do catálogo
// enviado ao servidor. Vão só para o `onEvent` do app, e por isso têm tipos próprios, prefixados
// com `placement_`, e envelope próprio, sem `displayId` nem `seq`.
//
// `surveyId`/`versionId` identificam a pesquisa candidata quando existe uma. Dois tipos não têm
// candidata para identificar e por isso levam `null`: `placement_blocked` (o bloqueio é aplicado
// por `block()`/`<PitacoBlock />`, independente de qualquer pesquisa) e `placement_session_limited`
// (a sessão de app já barra antes de consultar o servidor). `triggerEvent` — o nome do evento de
// `track()` — some pelo mesmo motivo em `placement_blocked`, que nunca nasce de um `track()`.
//
// Onde e quando cada um nasce:
//   placement_available        — o portão ofereceu a pesquisa na hora (oferta imediata).
//   placement_suppressed       — a pesquisa não tinha pergunta renderizável.
//   placement_session_limited  — `track()` foi ignorado: já houve pesquisa exibida nesta sessão
//                                 de app (feature 6). Nenhuma consulta de elegibilidade é feita.
//   placement_blocked          — `block()` (ou `<PitacoBlock />` montado) tornou o portão
//                                 bloqueado (de nenhum motivo ativo para pelo menos um).
//   placement_survey_held      — uma pesquisa chegou com o portão bloqueado: fica retida.
//   placement_survey_discarded — a pesquisa retida por bloqueio estourou o prazo (`deferTimeoutMs`)
//                                 sem desbloquear: descartada sem abrir exibição.
//   placement_deferred         — `defer()` reteve a pesquisa que chegou (ou a próxima a chegar).
//   placement_released         — `release()` liberou a pesquisa retida por adiamento (mostrada
//                                 assim que nenhum bloqueio também estiver ativo).
//   placement_expired          — a pesquisa retida por adiamento estourou o prazo sem `release()`:
//                                 descartada sem abrir exibição.

import type { EmptyData } from './events';

export const PLACEMENT_EVENT_TYPES = [
  'placement_available',
  'placement_suppressed',
  'placement_session_limited',
  'placement_blocked',
  'placement_survey_held',
  'placement_survey_discarded',
  'placement_deferred',
  'placement_released',
  'placement_expired',
] as const;

export type PlacementEventType = (typeof PLACEMENT_EVENT_TYPES)[number];

export type SuppressionReason = 'unknown_question_type' | 'unsupported_feature';

export interface PlacementEventDataMap {
  placement_available: EmptyData;
  placement_suppressed: { reason: SuppressionReason; questionTypes: readonly string[] };
  placement_session_limited: EmptyData;
  placement_blocked: { reasons: readonly string[] };
  placement_survey_held: EmptyData;
  placement_survey_discarded: { heldMs: number };
  placement_deferred: EmptyData;
  placement_released: { heldMs: number };
  placement_expired: { heldMs: number };
}

export type PlacementEventOf<T extends PlacementEventType> = {
  readonly type: T;
  readonly occurredAt: string;
  readonly surveyId: string | null;
  readonly versionId: string | null;
  readonly triggerEvent: string | null;
  readonly data: Readonly<PlacementEventDataMap[T]>;
};

export type PlacementEvent = {
  [T in PlacementEventType]: PlacementEventOf<T>;
}[PlacementEventType];

const placementTypes: ReadonlySet<string> = new Set(PLACEMENT_EVENT_TYPES);

export function isPlacementEvent(value: unknown): value is PlacementEvent {
  return (
    typeof value === 'object' &&
    value !== null &&
    'type' in value &&
    typeof value.type === 'string' &&
    placementTypes.has(value.type)
  );
}
