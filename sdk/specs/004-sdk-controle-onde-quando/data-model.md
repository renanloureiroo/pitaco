# Data Model: SDK React Native — controle de onde e quando a pesquisa aparece

**Feature**: `004-sdk-controle-onde-quando` · **Date**: 2026-09-13

Tudo em memória, no `PitacoRuntime` (`src/core/runtime/runtime.ts`). Nada é persistido.

---

## 1. Estado do controle

| Campo | Tipo | Inicial | Regra |
| --- | --- | --- | --- |
| `sessionSurveyShown` | `boolean` | `false` | liga no `survey_presented`; só `simulateAppReopen()` ou um runtime novo desligam |
| `blockReasons` | `Map<string, number>` | vazio | contagem por motivo; bloqueado se não vazio |
| `deferRequested` | `boolean` | `false` | liga em `defer()`, desliga em `release()` |
| `held` | `HeldSurvey \| null` | `null` | no máximo uma |
| `holdTimer` | timer | `null` | vence em `deferTimeoutMs` a partir da retenção |
| `disposed` | `boolean` | `false` | liga em `dispose()`; nada mais sai |

```ts
interface HeldSurvey {
  candidate: { survey: Survey; triggerEvent: string };
  controls: PlacementControls;   // os do consider() que a reteve
  heldAtMono: number;            // relógio monotônico
  cause: 'block' | 'defer';
}
```

## 2. Decisão do portão padrão (`considerPlacement`)

```
bloqueado?            → retém (cause: block), notify placement_survey_held
senão, adiamento?     → retém (cause: defer), notify placement_deferred
senão                 → offer() → placement_available
```

## 3. Transições da retida

| De | Gatilho | Para | Evento(s) |
| --- | --- | --- | --- |
| retida (block) | último `unblock`, sem adiamento | oferecida | `placement_available` |
| retida (defer) | `release()`, sem bloqueio | oferecida | `placement_released {heldMs}`, `placement_available` |
| retida (qualquer) | `unblock`/`release` com a outra condição ativa | continua retida | — |
| retida (block) | prazo | descartada | `placement_survey_discarded {heldMs}` |
| retida (defer) | prazo | descartada | `placement_expired {heldMs}` |
| retida | `reset()` | descartada | — |
| retida | `setPlacementGate()` | descartada | — |
| retida | `dispose()` | descartada | — |

Descarte nunca chama `offer()`: não abre exibição e não consome o limite.

## 4. Configuração

| Campo | Padrão | Faixa válida |
| --- | --- | --- |
| `deferTimeoutMs` | 300000 | 1000 – 3600000 |
| motivo de bloqueio | `'default'` | texto não vazio até 80 caracteres |

## 5. Evento de posicionamento — `src/catalog/placement.ts`

| Campo | Tipo |
| --- | --- |
| `type` | um dos 9 `placement_` |
| `occurredAt` | ISO 8601, relógio do dispositivo |
| `surveyId`, `versionId` | `string \| null` (nulos em `blocked` e `session_limited`) |
| `triggerEvent` | `string \| null` (nulo em `blocked`) |
| `data` | por tipo (ver contrato) |

## 6. Diagnóstico

`RuntimeDiagnostics` ganha `sessionSurveyShown`, `blockedReasons` (os motivos ativos), `deferred` e
`held` (booleano).
