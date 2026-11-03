# Contrato — catálogo de eventos de interação, versão 1

**Feature**: `003-sdk-eventos-interacao`

O que o `onEvent` do app recebe e o que vai ao servidor. Fonte: `src/catalog/events.ts`. O mesmo
catálogo está publicado pelo backend no OpenAPI (`InteractionEventType`, `InteractionEvent`,
`<Tipo>Event`, `<Tipo>Data`) e em `backend/docs/backend/contrato-sdk.md`.

## Exportado por `@pitaco/react-native`

| Export | O que é |
| --- | --- |
| `CATALOG_VERSION` | `1` |
| `INTERACTION_EVENT_TYPES` | os 18 tipos, em ordem |
| `QUESTION_EVENT_TYPES` | os 13 tipos que levam `questionKey` |
| `INTERACTION_EVENT_DATA_FIELDS` | os campos de `data` por tipo, na ordem do contrato |
| `DISMISS_VIAS` | `close_button`, `swipe`, `backdrop`, `hardware_back`, `navigation`, `programmatic` |
| `PRESENTATIONS` | `bottom-sheet`, `modal`, `inline` |
| `isInteractionEvent(value)` | detector |
| `isQuestionEventType(value)` | detector |
| tipos | `InteractionEvent` (= `PitacoEvent`), `InteractionEventOf<T>`, `InteractionEventType`, `InteractionEventDataMap`, `QuestionEventType`, `SurveyEventType`, `AnswerEventValue`, `DismissVia`, `Presentation`, `QuestionViewedFrom`, `QuestionLeftTo`, `CatalogVersion` |

O `onEvent` recebe `PitacoListenerEvent = InteractionEvent | PlacementEvent`; os `placement_` estão
em `specs/004-sdk-controle-onde-quando/contracts/placement-api.md`.

## Envelope

```ts
type InteractionEvent = {
  readonly catalogVersion: 1;
  readonly type: InteractionEventType;
  readonly displayId: string;
  readonly seq: number;          // 1, 2, 3… por exibição
  readonly occurredAt: string;   // ISO 8601, relógio do dispositivo
  readonly elapsedMs: number;    // monotônico desde survey_presented
  readonly questionKey?: string; // só nos eventos de pergunta
  readonly data: Readonly<InteractionEventDataMap[type]>;
};
```

## Tipos

| Tipo | Quando | `data` |
| --- | --- | --- |
| `survey_presented` | o contêiner ficou visível | `presentation`, `questionCount`, `renderableCount`, `triggerEvent` |
| `question_viewed` | uma pergunta passou a ser a atual | `position`, `visit`, `from` |
| `answer_selected` | primeira escolha, ou opção adicionada em múltipla | `value` |
| `answer_changed` | escolha trocada numa pergunta que já tinha valor | `from`, `to` |
| `answer_deselected` | opção removida, ou valor limpo | `value` |
| `text_focused` | campo de texto livre recebeu foco | — |
| `text_edited` | texto alterado, 1 s depois da última edição | `length` |
| `text_blurred` | o campo perdeu o foco (ou a pessoa saiu da pergunta com ele em foco) | `length` |
| `validation_blocked` | tentou avançar ou concluir com obrigatória em branco | `reason: 'required_missing'` |
| `question_skipped` | avançou de pergunta opcional sem responder | — |
| `question_not_applicable` | a condição pulou a pergunta | `sourceKey` |
| `navigated_next` | avançou | `toKey` |
| `navigated_back` | voltou | `toKey` |
| `question_left` | a pergunta deixou de ser a atual, por qualquer motivo | `visit`, `to`, `durationMs`, `activeMs`, `answered` |
| `survey_backgrounded` | o app foi para o segundo plano com a pesquisa aberta | — |
| `survey_foregrounded` | o app voltou com a pesquisa aberta | `backgroundMs` |
| `survey_dismissed` | a pessoa fechou | `via`, `position`, `answeredCount` |
| `survey_completed` | enviou a última pergunta | `answeredCount`, `skippedCount`, `notApplicableCount`, `activeMs` |

## Ordem por transição

```
next:      text_edited? → question_skipped? → text_blurred? → navigated_next → question_left
           → question_not_applicable* → question_viewed
back:      text_edited? → text_blurred? → navigated_back → question_left → question_viewed
concluir:  text_edited? → question_skipped? → text_blurred? → question_left
           → question_not_applicable* → survey_completed
dispensa:  text_edited? → text_blurred? → question_left → survey_dismissed
bloqueio:  text_edited? → validation_blocked
```

## Exemplo

```json
{
  "catalogVersion": 1,
  "type": "question_left",
  "displayId": "3b1f0a2c-6c9a-4a1e-9d0b-2c1f7a3e5d90",
  "seq": 7,
  "occurredAt": "2026-09-13T13:45:03.120Z",
  "elapsedMs": 8120,
  "questionKey": "5f0c2b1e-9a4d-4c3e-8b7a-1d2e3f4a5b6c",
  "data": { "visit": 1, "to": "next", "durationMs": 4200, "activeMs": 4200, "answered": true }
}
```

## Regras

- `value` é o `value` da opção ou o número, nunca o rótulo; texto livre só como `length`.
- Cada visita tem os próprios tempos; o painel soma as visitas.
- Tipo, campo ou valor novo exige `CATALOG_VERSION` nova; o servidor aceita as anteriores.
- O app MAY ouvir; MUST NOT criar tipo nem alterar payload. Não há API de emissão.
