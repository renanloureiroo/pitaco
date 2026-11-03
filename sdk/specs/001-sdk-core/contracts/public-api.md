# Contrato — superfície pública do core

**Feature**: `001-sdk-core`

O que esta fatia exporta. Tudo o que não está aqui (ou nas features seguintes) é interno e pode
mudar sem aviso. Ponto de entrada: `@pitaco/react-native` (`src/index.ts`); subpaths declarados em
`package.json#exports`.

---

## `<PitacoProvider>`

```tsx
<PitacoProvider
  baseUrl="https://pitaco.exemplo.com/api"      // obrigatório: prefixo que serve /collect
  apiKey="pk_..."                               // obrigatório
  respondent={{ reference: userIdHash }}        // opcional; sem ele vale o deviceId
  attributes={{ plano: 'pro' }}                 // opcional; nunca dado pessoal
  storage={createAsyncStorageAdapter(AsyncStorage)}  // opcional; sem ele, memória
  presentation="bottom-sheet"                   // ver 002
  onEvent={(event) => analytics.track(event.type, event)}  // só leitura; ver 003 e 004
  errorReporting                                // padrão true
  debug={__DEV__}                               // padrão false
  eligibilityTimeoutMs={3000}                   // 250–30000
  deferTimeoutMs={300000}                       // ver 004
>
```

| Prop | Tipo | Padrão | Recria o runtime |
| --- | --- | --- | --- |
| `baseUrl` | `string` | — | sim |
| `apiKey` | `string` | — | sim |
| `respondent` | `{ reference?: string \| null } \| null` | `null` | não (setter) |
| `attributes` | `Readonly<Record<string, string \| number \| boolean>>` | `{}` | não (setter, comparado por conteúdo) |
| `storage` | `PitacoStorage` | memória | sim (por referência) |
| `presentation` | `'bottom-sheet' \| 'modal' \| 'inline'` | `'bottom-sheet'` | sim |
| `onEvent` | `(event: PitacoListenerEvent) => void` | — | não (setter) |
| `errorReporting` | `boolean` | `true` | sim |
| `debug` | `boolean` | `false` | sim |
| `eligibilityTimeoutMs` | `number` | `3000` | sim |
| `deferTimeoutMs` | `number` | `300000` | sim |
| `theme`, `strings`, `renderers`, `slots`, `insets`, `getInsets` | ver `002` | — | não |
| `children` | `ReactNode` | — | — |

Configuração inválida nunca lança: o Provider renderiza os filhos e os hooks ficam inertes.

---

## `usePitaco(): PitacoActions`

| Método | Assinatura | Comportamento |
| --- | --- | --- |
| `track` | `(eventName: string, attributes?: Attributes) => Promise<void>` | consulta a elegibilidade; nunca rejeita |
| `setRespondent` | `(respondent: Respondent \| null) => void` | troca a referência sem recriar o runtime |
| `setAttributes` | `(attributes: Attributes) => void` | troca os atributos base |
| `reset` | `() => Promise<void>` | logout: dispensa `programmatic`, esquece referência e atributos, gira o `deviceId`, mantém a fila |
| `block`, `unblock`, `defer`, `release` | ver `004` | — |
| `diagnostics` | `() => RuntimeDiagnostics \| null` | estado interno para ferramentas de diagnóstico; `null` sem runtime |
| `simulateAppReopen` | `() => void` | ver `004`; ferramenta de diagnóstico |

Fora do Provider (ou com configuração inválida) todas as ações são inertes e o hook avisa uma vez
em desenvolvimento.

---

## `usePitacoSurvey(): PitacoSurveyState & PitacoSurveyActions`

### Estado

| Campo | Tipo |
| --- | --- |
| `available` | `boolean` — `ready` ou `presented` |
| `status` | `'idle' \| 'ready' \| 'presented' \| 'completed' \| 'dismissed' \| 'discarded'` |
| `displayId` | `string \| null` |
| `survey` | `SurveySummary \| null` |
| `questions` | `readonly SurveyQuestion[]` |
| `question` | `SurveyQuestion \| null` |
| `answers` | `Readonly<Record<string, AnswerValue>>` |
| `value` | `AnswerValue \| undefined` — da pergunta atual |
| `progress` | `{ position: number; total: number }` |
| `error` | `{ questionKey: string; reason: 'required_missing' } \| null` |
| `focusedQuestionKey` | `string \| null` |
| `canGoBack`, `canGoNext`, `isLast` | `boolean` |

### Ações

| Ação | Assinatura |
| --- | --- |
| `present` | `(presentation?: Presentation) => void` — o contêiner ficou visível: abre a exibição |
| `select` | `(value: string \| number, questionKey?: string) => void` |
| `deselect` | `(value?: string \| number, questionKey?: string) => void` |
| `setText` | `(text: string, questionKey?: string) => void` |
| `focusText`, `blurText` | `(questionKey?: string) => void` |
| `next`, `back`, `complete` | `() => void` |
| `dismiss` | `(via?: DismissVia) => void` — padrão `'programmatic'` |

Nenhuma ação lança. `questionKey` diferente da pergunta atual faz a ação ser ignorada.

---

## Storage

```ts
interface PitacoStorage {
  getItem(key: string): Promise<string | null | undefined> | string | null | undefined;
  setItem(key: string, value: string): Promise<void> | void;
  removeItem(key: string): Promise<void> | void;
}
```

| Export | Onde | Uso |
| --- | --- | --- |
| `createMemoryStorage(initial?)` | `@pitaco/react-native` | padrão; não persiste |
| `createAsyncStorageAdapter(AsyncStorage)` | `@pitaco/react-native/storage/async-storage` | recebe a instância do app |
| `createMmkvAdapter(mmkv)` | `@pitaco/react-native/storage/mmkv` | MMKV 2/3 (`delete`) e 4 (`remove`) |

Instância que não tem a forma esperada devolve memória e avisa em desenvolvimento.

---

## Tipos exportados por esta fatia

`PitacoProviderProps`, `PitacoActions`, `PitacoSurveyActions`, `PitacoSurveyState`,
`UsePitacoSurveyResult`, `PitacoListenerEvent`, `RuntimeDiagnostics`, `QueueItem`,
`QueueItemKind`, `Attributes`, `AttributeValue`, `Respondent`, `PitacoStorage`, `SessionStatus`,
`ValidationError`, `SurveyProgress`, `SurveySummary`, `AnswerValue`, `ChoiceOption`,
`ConditionOperator`, `FreeTextNotice`, `NumericRange`, `QuestionCondition`, `QuestionType`,
`SurveyQuestion`, e a constante `SDK_VERSION` (igual ao `version` do `package.json`).

### `RuntimeDiagnostics`

```ts
{
  deviceId: string | null;
  queue: readonly QueueItem[];
  keyRejected: boolean;
  rateLimitedUntil: number;       // epoch ms; 0 sem 429
  sessionSurveyShown: boolean;    // 004
  blockedReasons: readonly string[]; // 004
  deferred: boolean;              // 004
  held: boolean;                  // 004
}
```

Não é recurso de produto: existe para o painel de depuração do app de exemplo e para os testes.
