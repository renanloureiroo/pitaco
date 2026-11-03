# Data Model: SDK React Native — core headless

**Feature**: `001-sdk-core` · **Date**: 2026-09-12

O SDK não tem banco: o "modelo" é o estado em memória do runtime e o que ele persiste no storage
do app. Tudo abaixo existe em `src/core/`.

---

## 1. Configuração resolvida — `core/config.ts`

`resolveConfig(input, logger)` devolve `ResolvedConfig` ou `null` (SDK desligado).

| Campo | Tipo | Origem / regra |
| --- | --- | --- |
| `baseUrl` | `string` | obrigatório; http(s), sem query nem fragmento, barra final removida |
| `apiKey` | `string` | obrigatório; aparado |
| `presentation` | `'bottom-sheet' \| 'modal' \| 'inline'` | padrão `'bottom-sheet'`; inválido cai no padrão com aviso |
| `errorReporting` | `boolean` | padrão `true`; só `false` desliga |
| `debug` | `boolean` | padrão `false`; liga os avisos de depuração em `__DEV__` |
| `eligibilityTimeoutMs` | `number` | padrão 3000; 250–30000 |
| `deferTimeoutMs` | `number` | padrão 300000; 1000–3600000 (usado pela `004`) |
| `requestTimeoutMs` | `number` | fixo 10000 |

### Limites de entrada (`LIMITS`)

| Entrada | Limite |
| --- | --- |
| nome do evento de `track` | 80 caracteres |
| `respondent.reference` | 200 caracteres |
| atributos | 50 pares; nome até 80; valor texto, número finito ou booleano, até 200 depois de convertido em texto |
| motivo de bloqueio | 80 caracteres (usado pela `004`) |

---

## 2. Pesquisa normalizada — `core/survey/schema.ts`

```ts
interface Survey {
  surveyId: string; versionId: string; versionNumber: number;
  freeTextNotice: { enabled: boolean; text: string | null };
  questions: SurveyQuestion[];        // só as renderizáveis, por position
  questionCount: number;              // total da versão no servidor, inclusive descartadas
  unknownQuestionTypes: string[];     // para a supressão (até 20, 40 caracteres cada)
  malformedQuestionCount: number;     // tipo conhecido com forma quebrada
}

interface SurveyQuestion {
  key: string; position: number; statement: string;
  type: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'RATING' | 'SCALE' | 'NPS' | 'FREE_TEXT';
  required: boolean;
  options: { label: string; value: string; position: number }[];
  range: { min: number; max: number; minLabel: string | null; maxLabel: string | null } | null;
  condition: { sourceKey: string; operator: 'equals' | 'not_equals' | 'in' | 'between';
               values: string[]; min: number | null; max: number | null } | null;
}
```

`parseEligibilityResponse(body)` → `{ kind: 'none' }` | `{ kind: 'survey', survey }` |
`{ kind: 'malformed', reason }`.

### Valor de resposta — `core/survey/answers.ts`

| Tipo | Valor em memória (`AnswerValue`) | Valor no envio (`AnswerValueWire`) |
| --- | --- | --- |
| `SINGLE_CHOICE` | `string` (o `value` da opção) | `string` |
| `MULTIPLE_CHOICE` | `readonly string[]` | `string[]` sem repetição |
| `RATING`, `SCALE`, `NPS` | `number` inteiro na faixa | `number` |
| `FREE_TEXT` | `string` até 2000 (`MAX_FREE_TEXT_LENGTH`) | `string` |

---

## 3. Estado da máquina — `core/machine/machine.ts`

```ts
type SessionStatus = 'ready' | 'presented' | 'completed' | 'dismissed' | 'discarded';
```

| Situação | Significado | Transições |
| --- | --- | --- |
| `ready` | recebida, contêiner ainda não visível | `present` → `presented`; `dismiss` → `discarded` |
| `presented` | visível, exibição aberta | `next` na última aplicável ou `complete` → `completed`; `dismiss` → `dismissed` |
| `completed` | desfecho de conclusão | terminal |
| `dismissed` | desfecho de dispensa | terminal |
| `discarded` | dispensada antes de ficar visível; nunca virou exibição | terminal |

Campos principais de `MachineState`: `survey`, `displayId`, `triggerEvent`, `presentation`,
`index` (pergunta atual), `trail` (índices percorridos), `answers`, `visits` (por chave),
`passedOver` (puladas pela condição), `validationError`, `focusedKey`, `pendingEdit`,
`dismissedVia`, e os tempos usados pelos eventos da `003` (`presentedAt`, `visitStartedAt`,
`visitBackgroundMs`, `backgroundedAt`, `totalBackgroundMs`, `seq`, `lastElapsedMs`).

### Ações (`SurveyAction`)

`present(presentation)`, `select(value, questionKey?)`, `deselect(value?, questionKey?)`,
`setText(text, questionKey?)`, `focusText(questionKey?)`, `blurText(questionKey?)`, `next`, `back`,
`dismiss(via)`, `complete`, e as internas `background`, `foreground`, `tick`. A UI recebe apenas
`SurveyUserAction` (sem `present`, que tem método próprio, e sem as internas).

### Caminho — `core/machine/path.ts`

`resolveStatuses(questions, answers)` → `'ANSWERED' | 'UNANSWERED' | 'NOT_APPLICABLE'` por
pergunta; `findNextApplicable(questions, answers, from)` → `{ index, passed }`.

---

## 4. Snapshot exposto à UI — `core/session/snapshot.ts`

| Campo | Regra |
| --- | --- |
| `status` | situação da máquina |
| `displayId` | gerado no dispositivo |
| `survey` | `SurveySummary`: ids, número da versão, `questionCount`, `renderableCount`, `freeTextNotice` |
| `question` | em `ready`, a primeira aplicável (para o contêiner montar antes); em `presented`, a atual; depois do desfecho, `null` |
| `progress` | `{ position, total }`; `total` é estimativa e diminui quando a condição pula perguntas |
| `validationError` | `{ questionKey, reason: 'required_missing' }` ou `null` |
| `canGoBack` | apresentada e com trilha |
| `canGoNext` | apresentada e (opcional ou respondida) |
| `isLast` | não há aplicável depois da atual |

`usePitacoSurvey()` acrescenta `available` (`ready` ou `presented`) e `status: 'idle'` sem sessão.

---

## 5. Fila persistente — `core/queue/queue.ts`

Chave: `@pitaco/v1:queue:<djb2 base36 de "baseUrl|apiKey">`.

```ts
interface PersistedQueue { version: 1; items: QueueItem[]; blockedUntil: number }

type QueueItem = ItemBase & (
  | { kind: 'open_display'; displayId: string; body: OpenDisplayRequest }
  | { kind: 'submission';   displayId: string; body: SubmissionRequest }
  | { kind: 'events';       displayId: string; events: InteractionEvent[] }
  | { kind: 'suppression';  body: SuppressionRequest });

interface ItemBase { id: string; createdAt: number; attempts: number; nextAttemptAt: number }
```

| Limite (`DEFAULT_QUEUE_LIMITS`) | Valor |
| --- | --- |
| `maxItems` | 200 |
| `maxAgeMs` | 3 dias |
| `maxEventsPerDisplay` | 500 |
| `batchSize` | 100 |
| `backoffBaseMs` / `backoffMaxMs` | 2000 / 300000 |

Invariantes: no máximo uma `open_display` e uma `submission` por `displayId`; `events` sem `seq`
repetido; `submission` e `events` nunca saem enquanto existir `open_display` da mesma exibição na
fila.

---

## 6. Identidade e storage

| Chave | Conteúdo |
| --- | --- |
| `@pitaco/v1:device-id` | UUID do dispositivo |
| `@pitaco/v1:queue:<hash>` | `PersistedQueue` em JSON |

`PitacoStorage`: `getItem(key)`, `setItem(key, value)`, `removeItem(key)`, cada um síncrono ou
assíncrono. `MemoryStorage` é marcado por `Symbol.for('pitaco.memory-storage')`, o que permite ao
runtime avisar que a fila não persiste.

---

## 7. Resultado de transporte — `core/transport/http.ts`

| `kind` | Quando | Quem trata |
| --- | --- | --- |
| `success` | 2xx (corpo lido só quando se espera JSON) | remove da fila / lê a elegibilidade |
| `malformed` | 2xx cujo corpo deveria ser JSON e não é | fila: entregue; elegibilidade: aviso + relatório |
| `retry` | rede, timeout, 5xx, 408, status 0 | fila: backoff; elegibilidade: aviso único |
| `rate_limited` | 429, com `retryAfterMs` | pausa global |
| `blocked` | ainda dentro do `Retry-After` | não saiu |
| `unauthorized` | 401, 403, com `code` | desliga a sessão |
| `rejected` | demais 4xx, com `code` e `problem` | descarta + relatório |

---

## 8. Relatório de erro — `core/errors/reporter.ts`

```ts
{ kind: 'render_error' | 'network_error' | 'malformed_response' | 'storage_error' | 'unknown';
  message: string;     // até 500, montada pelo SDK
  context: Record<string, string | number | boolean>;  // até 10 chaves, valores até 80
  occurredAt: string } // ISO do relógio do dispositivo
```

Deduplicação por `kind|message`; teto padrão de 10 por sessão.
