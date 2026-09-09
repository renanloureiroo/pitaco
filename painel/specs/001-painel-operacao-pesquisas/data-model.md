# Data Model — Painel de operação (001)

O painel não persiste nada. O que este documento descreve são os **tipos de fronteira**: o que o
schema Zod valida ao ler a API, o que o formulário produz ao escrever, e as regras de forma que o
painel conhece. A verdade do domínio é do backend; aqui está apenas o recorte que a interface
precisa entender para renderizar e para recusar cedo o que já se sabe inválido.

Convenção: todo campo marcado `opcional` chega **ausente** (não `null`, não `0`) quando não
configurado — o backend é explícito quanto a isso, e o painel deve exibir "não configurado", nunca
zero.

---

## Tipos compartilhados (`src/shared/api`)

### `PageResponse<T>`

| Campo | Tipo | Nota |
|---|---|---|
| `items` | `T[]` | vazio quando nada atende ao filtro — nunca erro |
| `page` | `number` | base 0 |
| `size` | `number` | tamanho aplicado |
| `total` | `number` | total do conjunto filtrado inteiro, não da página |
| `totalPages` | `number` | 0 quando não há item |

### `ApiError` — resultado de fronteira

O cliente HTTP nunca lança para erro esperado. Devolve:

```
Result<T> =
  | { ok: true;  data: T }
  | { ok: false; kind: "validation"; code: string; detail: string; errors: Record<string,string> }
  | { ok: false; kind: "not_found" | "conflict" | "forbidden" | "unknown";
      code: string; detail: string; traceId?: string }
  | { ok: false; kind: "unreachable" }
```

Origem dos campos: corpo RFC 9457 do backend (`type`, `title`, `status`, `detail`, `instance`,
`code`, `traceId`), mais `errors` no caso de `ApiValidationError` (400).

Mapeamento de `status` → `kind`: 400 → `validation` · 403 → `forbidden` · 404 → `not_found` ·
409 → `conflict` · demais → `unknown` · falha de rede/timeout → `unreachable`.

**Regra de tratamento**: em leitura, `not_found` → `notFound()`; `unreachable`/`unknown` → lança
para o `error.tsx` do segmento. Em ação, o resultado vira estado do `useActionState`.

### `PaginationParams` — validado a partir de `searchParams`

| Campo | Tipo | Padrão | Regra |
|---|---|---|---|
| `page` | `number` | `0` | `>= 0`; valor inválido cai no padrão em vez de quebrar a tela |
| `size` | `number` | `20` | `1..100` |

---

## Aplicação (`features/applications`)

### `ApplicationSummary` — item da listagem

| Campo | Tipo | Obrig. |
|---|---|---|
| `id` | `string` | sim |
| `slug` | `string` | sim |
| `name` | `string` | sim |
| `status` | `"active" \| "inactive"` | sim |
| `createdAt` | `string` (ISO UTC) | sim |

### `Application` — detalhe

Os cinco campos acima, mais:

| Campo | Tipo | Obrig. |
|---|---|---|
| `quietPeriodDays` | `number` | opcional |
| `retentionDays` | `number` | opcional |
| `openTextRetentionDays` | `number` | opcional |
| `updatedAt` | `string` (ISO UTC) | sim |

### `CreateApplicationForm` — entrada

| Campo | Tipo | Regra de forma conhecida pelo painel |
|---|---|---|
| `name` | `string` | obrigatório, até 120 caracteres |
| `slug` | `string` | opcional; `^[a-z0-9]+(-[a-z0-9]+)*$`, até 50; ausente ⇒ derivado do nome pelo backend |
| `quietPeriodDays` | `number` | opcional, `>= 1` |
| `retentionDays` | `number` | opcional, `>= 1` |
| `openTextRetentionDays` | `number` | opcional, `>= 1` |

A relação "texto livre nunca maior que o prazo geral" é invariante do **backend**; o painel não a
replica — exibe a recusa se ela vier.

### Filtro da listagem

`status`: `"active" | "inactive"` ou ausente (todas).

---

## Chave de acesso (`features/api-keys`)

### `ApiKey`

| Campo | Tipo | Obrig. |
|---|---|---|
| `id` | `string` | sim |
| `applicationId` | `string` | sim |
| `label` | `string` | sim |
| `prefix` | `string` | sim — público, não sensível |
| `status` | `"active" \| "revoked"` | sim |
| `createdAt` | `string` (ISO UTC) | sim |
| `revokedAt` | `string` (ISO UTC) | opcional — ausente enquanto válida |

**Não existe campo de segredo neste tipo.** A ausência é estrutural no backend e deve ser
estrutural no painel: nenhum tipo de leitura tem onde carregar um segredo.

### `IssuedApiKey` — devolvido **apenas** pela emissão

`id`, `applicationId`, `label`, `prefix`, `createdAt` e `secret: string`.

**Ciclo de vida do `secret` no painel**: existe no retorno da Server Action → é guardado em
`useState` do componente cliente → é exibido no diálogo → é descartado ao fechar. Nunca entra em
URL, `searchParams`, `localStorage`, cookie ou log. Nenhuma leitura o recupera.

### `IssueApiKeyForm`

| Campo | Regra |
|---|---|
| `label` | obrigatório, até 80 caracteres |

---

## Pesquisa (`features/surveys`)

### `Survey` — item de listagem e retorno de mutação

| Campo | Tipo | Obrig. |
|---|---|---|
| `id` | `string` | sim |
| `applicationId` | `string` | sim |
| `name` | `string` | sim |
| `state` | `SurveyState` | sim |
| `publishedVersionNumber` | `number` | opcional — ausente enquanto rascunho |
| `draftVersionNumber` | `number` | opcional |
| `createdAt` | `string` (ISO UTC) | sim |

### `SurveyDetail`

`id`, `applicationId`, `name`, `state`, `createdAt`, mais `content` (opcional — ausente quando não
há versão nenhuma):

| Campo de `content` | Tipo |
|---|---|
| `source` | `"draft" \| "published"` |
| `versionNumber` | `number` |
| `questions` | `Question[]` |
| `trigger` | `Trigger` (opcional) |

`content.source` é o que a tela de montagem usa para decidir se está editando rascunho ou
mostrando o publicado em leitura.

### `SurveyState` e máquina de estados

Estados: `draft` · `scheduled` · `active` · `paused` · `ended`.

```
draft ──publicação──> scheduled ──janela abre──> active
                          │                        │
                          │                    manual_pause
                          │                        ↓
                          └────────────────────> paused ──manual_resume──> active
                                                   │                         │
                                                   └──── manual_end ─────────┤
                                                                             ↓
                                                  janela fecha ──────────> ended
```

**Regra de ouro da interface**: o painel **não** deriva as transições permitidas a partir do
estado. Ele lê `GET .../transitions` e oferece apenas o que vier de lá (FR-035). O diagrama acima
existe para orientar o desenho das telas e dos testes, não para virar lógica condicional.

Transições registradas trazem `reason`: `publication` · `manual_pause` · `manual_resume` ·
`manual_end` · `window_opened` · `window_closed`.

### `Question`

| Campo | Tipo | Nota |
|---|---|---|
| `id` | `string` | identificador da pergunta na versão |
| `key` | `string` | chave estável — atravessa versões e nunca muda |
| `statement` | `string` | enunciado |
| `type` | `QuestionType` | ver abaixo |
| `position` | `number` | ordem de exibição |
| `required` | `boolean` | |
| `options` | `QuestionOption[]` | só nos tipos de escolha |
| `range` | `{ min: number; max: number }` | opcional, tipos de escala |

`QuestionOption`: `{ label: string; value: string }`.

### `QuestionType` e regras de forma

| Tipo | Exige `options` | Aceita `range` |
|---|---|---|
| `single_choice` | sim | não |
| `multiple_choice` | sim | não |
| `rating` | não | sim |
| `scale` | não | sim |
| `nps` | não | não |
| `free_text` | não | não |

Essas são as regras de **forma** que o painel valida antes de enviar (FR-022): tipo de escolha sem
nenhuma opção não sai do formulário. Qualquer outra invariante (quantidade mínima de opções,
duplicidade de `value`, faixa válida) é decidida pelo backend e apenas exibida.

### `Trigger`

| Campo | Tipo | Regra de forma |
|---|---|---|
| `eventName` | `string` | `^[a-z][a-z0-9_.]{1,79}$` |
| `windowStart` | `string` (ISO UTC) | obrigatório |
| `windowEnd` | `string` (ISO UTC) | opcional — ausente = janela aberta |
| `samplingRate` | `number` | `0.0..1.0`; ausente no envio ⇒ `0.0` |
| `rules` | `SegmentationRule[]` | |

`windowEnd` posterior a `windowStart` é invariante do backend (aparece como impedimento
`trigger.window_invalid`); o painel pode alertar, mas não bloqueia por conta própria.

### `SegmentationRule`

| Campo | Tipo | Regra |
|---|---|---|
| `id` | `string` | |
| `attribute` | `string` | obrigatório |
| `operation` | `"equals" \| "not_equals" \| "present" \| "absent"` | |
| `value` | `string` | exigido em `equals` e `not_equals`; recusado em `present` e `absent` |

A dependência entre `operation` e `value` é regra de forma: o campo de valor aparece e é exigido
só nas duas primeiras operações.

### `PublicationImpediment`

| Campo | Tipo |
|---|---|
| `code` | `"survey.no_questions" \| "question.statement_missing" \| "question.options_missing" \| "trigger.missing" \| "trigger.window_invalid"` |
| `field` | `string` (opcional) |
| `questionKey` | `string` (opcional) |

O painel traduz cada `code` em uma frase acionável e, quando há `questionKey`, liga o impedimento
à pergunta correspondente na tela de montagem. Lista vazia ⇒ publicação liberada (FR-030).

### `SurveyVersion`

| Campo | Tipo | Nota |
|---|---|---|
| `number` | `number` | |
| `status` | `"draft" \| "published"` | |
| `publishedAt` | `string` (ISO UTC) | ausente no rascunho |
| `changeKind` | `"cosmetic" \| "semantic"` | ausente na versão 1 |
| `changeSummary` | `string` | ausente na versão 1 |
| `comparabilityGroup` | `number` | versões no mesmo grupo têm respostas somáveis |

`SurveyVersionDetail` acrescenta `questions` e `trigger` — o conteúdo congelado.

### `PublishSurveyForm`

| Campo | Regra |
|---|---|
| `changeKind` | `"cosmetic" \| "semantic"` — obrigatório a partir da versão 2, ignorado na versão 1 |
| `changeSummary` | texto livre, opcional |

O painel decide exigir ou não pelo `publishedVersionNumber` da pesquisa: ausente ⇒ é a versão 1 e
os dois campos não aparecem.

### `VersionComparability`

`{ groups: { group: number; versions: number[] }[] }` — versões dentro do mesmo grupo são
comparáveis entre si.

---

## Relações

```
Application 1 ──── N ApiKey
Application 1 ──── N Survey
Survey      1 ──── N SurveyVersion
Survey      1 ──── 1 Trigger (da versão corrente) ──── N SegmentationRule
Survey      1 ──── N Question (da versão corrente, ordenadas por position)
```

Toda rota do painel carrega o `applicationId` no caminho porque toda leitura da API é escopada
pela aplicação — não existe listagem global de pesquisa ou de chave.
