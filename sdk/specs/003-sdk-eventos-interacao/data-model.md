# Data Model: SDK React Native — eventos de interação

**Feature**: `003-sdk-eventos-interacao` · **Date**: 2026-09-13

---

## 1. Envelope — `src/catalog/events.ts`

| Campo | Tipo | Regra |
| --- | --- | --- |
| `catalogVersion` | `1` | `CATALOG_VERSION` |
| `type` | `InteractionEventType` | um dos 18 |
| `displayId` | `string` | UUID gerado no dispositivo para a exibição |
| `seq` | `number` | monotônico por exibição, a partir de 1; chave de idempotência com `displayId` |
| `occurredAt` | `string` | ISO 8601 do relógio do dispositivo; informativo |
| `elapsedMs` | `number` | monotônico desde `survey_presented`, arredondado, nunca diminui |
| `questionKey` | `string` | presente se e somente se o tipo é de pergunta |
| `data` | `InteractionEventDataMap[type]` | fechado por tipo, somente leitura |

## 2. Tipos e `data`

| Tipo | Pergunta? | `data` |
| --- | --- | --- |
| `survey_presented` | não | `presentation`, `questionCount`, `renderableCount`, `triggerEvent` |
| `question_viewed` | sim | `position`, `visit`, `from: 'start' \| 'next' \| 'back'` |
| `answer_selected` | sim | `value` |
| `answer_changed` | sim | `from`, `to` |
| `answer_deselected` | sim | `value` |
| `text_focused` | sim | — |
| `text_edited` | sim | `length` |
| `text_blurred` | sim | `length` |
| `validation_blocked` | sim | `reason: 'required_missing'` |
| `question_skipped` | sim | — |
| `question_not_applicable` | sim | `sourceKey` |
| `navigated_next` | sim | `toKey` |
| `navigated_back` | sim | `toKey` |
| `question_left` | sim | `visit`, `to: 'next' \| 'back' \| 'dismiss' \| 'complete'`, `durationMs`, `activeMs`, `answered` |
| `survey_backgrounded` | não | — |
| `survey_foregrounded` | não | `backgroundMs` |
| `survey_dismissed` | não | `via`, `position`, `answeredCount` |
| `survey_completed` | não | `answeredCount`, `skippedCount`, `notApplicableCount`, `activeMs` |

`value`, `from`, `to` de resposta: `string | number` (`AnswerEventValue`) — o `value` da opção ou o
número.

`via`: `close_button`, `swipe`, `backdrop`, `hardware_back`, `navigation`, `programmatic`.

`presentation`: `bottom-sheet`, `modal`, `inline`.

## 3. Estado da máquina usado pela emissão

| Campo de `MachineState` | Papel |
| --- | --- |
| `seq` | último `seq` emitido |
| `presentedAt` | base de `elapsedMs` (monotônico) |
| `lastElapsedMs` | garante `elapsedMs` não decrescente |
| `visits` | número da visita por pergunta |
| `visitStartedAt`, `visitBackgroundMs` | tempos da visita atual |
| `backgroundedAt`, `totalBackgroundMs` | segundo plano em curso e acumulado |
| `focusedKey` | pergunta com o campo de texto em foco |
| `pendingEdit` | `{ questionKey, dueAt }` do `text_edited` com debounce de 1 s |

## 4. Na fila — `core/queue/queue.ts`

```ts
{ kind: 'events'; displayId: string; events: InteractionEvent[];
  id; createdAt; attempts; nextAttemptAt }
```

- um item por exibição; eventos acrescentados por `seq`, sem repetir;
- até 500 por exibição (`maxEventsPerDisplay`); lote de até 100 (`batchSize`) em ordem de `seq`;
- só sai quando não há `open_display` da mesma exibição na fila;
- `202` remove os `seq` enviados; o restante sai na mesma passada.

## 5. Assinatura — `src/__tests__/support/script.ts`

`signature(events)` → uma linha por evento, `"<seq> <type>[@questionKey] <data sem os campos de
tempo>"`. `elapsedMs` e `occurredAt` não entram, e os campos de tempo do `data` (`durationMs`,
`activeMs`, `backgroundMs`) são retirados. `seq`, tipo, pergunta e o resto do `data` entram. É a
unidade de comparação do teste de mesma sequência.
