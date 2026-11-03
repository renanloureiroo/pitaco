# Contrato — controle de onde e quando

**Feature**: `004-sdk-controle-onde-quando`

Exportado por `@pitaco/react-native`.

---

## `usePitaco()` — ações novas

| Ação | Assinatura | Comportamento |
| --- | --- | --- |
| `block` | `(reason?: string) => void` | acrescenta uma referência ao motivo; primeiro bloqueio ativo emite `placement_blocked` |
| `unblock` | `(reason?: string) => void` | remove uma referência; o último libera a retida; sem `block` correspondente: aviso e nada |
| `defer` | `() => void` | segura a próxima pesquisa; não empilha |
| `release` | `() => void` | libera a retida por adiamento; sem `defer` correspondente: aviso e nada |
| `diagnostics` | `() => RuntimeDiagnostics \| null` | inclui `sessionSurveyShown`, `blockedReasons`, `deferred`, `held` |
| `simulateAppReopen` | `() => void` | zera só o limite de uma pesquisa por sessão |

`diagnostics` e `simulateAppReopen` são ferramentas de diagnóstico, não recurso de produto.

## `<PitacoBlock />`

```tsx
function TelaDePagamento() {
  return (
    <>
      <PitacoBlock reason="pagamento" />
      {/* … */}
    </>
  );
}
```

| Prop | Tipo | Padrão |
| --- | --- | --- |
| `reason` | `string` | motivo padrão do SDK |

Bloqueia ao montar, desbloqueia ao desmontar (inclusive em saída por erro ou troca abrupta de
rota). Renderiza `null`.

## `<PitacoProvider deferTimeoutMs>`

| Prop | Tipo | Padrão | Faixa |
| --- | --- | --- | --- |
| `deferTimeoutMs` | `number` | `300000` | `1000`–`3600000`; inválido cai no padrão com aviso |

Trocar recria o runtime (e zera o limite de sessão).

---

## Eventos `placement_`

Entregues só ao `onEvent`, junto dos eventos de interação
(`PitacoListenerEvent = InteractionEvent | PlacementEvent`). Nunca vão ao servidor.

```ts
type PlacementEvent = {
  readonly type: PlacementEventType;
  readonly occurredAt: string;
  readonly surveyId: string | null;
  readonly versionId: string | null;
  readonly triggerEvent: string | null;
  readonly data: Readonly<PlacementEventDataMap[type]>;
};
```

| Tipo | Quando | `data` | Candidata |
| --- | --- | --- | --- |
| `placement_available` | o portão ofereceu a pesquisa | — | sim |
| `placement_suppressed` | a pesquisa não tinha pergunta renderizável | `reason`, `questionTypes` | sim |
| `placement_session_limited` | `track()` ignorado: já houve exibição nesta sessão | — | não (`triggerEvent` = o evento do `track`) |
| `placement_blocked` | o portão passou de livre a bloqueado | `reasons` | não (tudo `null`) |
| `placement_survey_held` | pesquisa chegou com bloqueio ativo | — | sim |
| `placement_survey_discarded` | retida por bloqueio venceu o prazo | `heldMs` | sim |
| `placement_deferred` | pesquisa chegou com adiamento pedido | — | sim |
| `placement_released` | `release()` liberou a retida por adiamento | `heldMs` | sim |
| `placement_expired` | retida por adiamento venceu o prazo | `heldMs` | sim |

`SuppressionReason`: `'unknown_question_type' | 'unsupported_feature'`.

### Exports

`PLACEMENT_EVENT_TYPES`, `isPlacementEvent`, `PlacementEvent`, `PlacementEventOf<T>`,
`PlacementEventType`, `PlacementEventDataMap`, `SuppressionReason`, `PitacoBlock`,
`PitacoBlockProps`.

---

## Ponto de extensão — `PlacementGate` (`src/core/runtime/placement.ts`)

```ts
interface PlacementGate {
  consider(candidate: SurveyCandidate, controls: PlacementControls): void;
  sessionFinished?(candidate: SurveyCandidate): void;
  dispose?(): void;
}
interface PlacementControls {
  offer(): void;                         // torna a pesquisa disponível; idempotente
  discard(reason: string): void;         // descarta sem exibição
  notify<T extends PlacementEventType>(type: T, data: PlacementEventDataMap[T]): void;
}
```

Instalado por `PitacoRuntime.setPlacementGate(gate)`, que substitui bloqueio e adiamento inteiros e
descarta a candidata retida pelo portão anterior. `immediatePlacement` oferece sempre. Não é
acessível pelos hooks: é extensão do runtime.
