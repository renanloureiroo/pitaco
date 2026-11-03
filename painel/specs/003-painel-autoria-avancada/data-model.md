# Phase 1 — Modelo de dados do painel

O painel não tem banco. Aqui estão os tipos que atravessam a fronteira da API e viram domínio da
feature `surveys` (e, no catálogo, de `applications`), com as regras que a travessia aplica.
Todo tipo nasce de um schema Zod; campo ausente vira `undefined` pelo envoltório `absent()` de
`@/shared/api`, que aceita tanto a omissão quanto o `null` que o backend serializa.

Tipos de 001 que não mudam (`Trigger`, `SegmentationRule`, `PublicationImpediment`,
`SurveyVersion`, `VersionComparability`) estão no
[modelo de 001](../001-painel-operacao-pesquisas/data-model.md).

---

## Pesquisa — campos novos (`Survey`, `SurveyDetail`)

| Campo | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `templateKind` | `"nps" \| "csat" \| "ces"` | **não** | ausente ⇒ criada em branco |
| `priority` | `number` | sim (padrão `0`) | −100 a 100; maior vence o desempate |
| `responseQuota` | `number` | **não** | ausente ⇒ sem cota; nunca `0` |
| `ignoresQuietPeriod` | `boolean` | sim (padrão `false`) | |
| `freeTextNotice` | `FreeTextNotice` | **não** | ausente ⇒ painel não desenha o painel do aviso |

`SURVEY_TEMPLATES` e `SURVEY_STATES` são as opções dos enums, exportadas pela fronteira pública.

### `FreeTextNotice`

| Campo | Tipo | Observação |
|---|---|---|
| `enabled` | `boolean` | desligado ⇒ campos de texto livre sem aviso |
| `customText` | `string?` | texto personalizado; ausente ⇒ padrão |
| `text` | `string` | o que o respondente vê hoje |
| `defaultText` | `string` | sugestão exibida no campo quando não há personalizado |

---

## Pergunta — campos novos (`Question`)

| Campo | Tipo | Observação |
|---|---|---|
| `range.minLabel`, `range.maxLabel` | `string?` | rótulo dos extremos em avaliação, escala e NPS |
| `condition` | `Condition?` | ausente ⇒ sempre exibida |

### `Condition`

| Campo | Tipo | Observação |
|---|---|---|
| `sourceKey` | `string` | chave estável de uma pergunta **anterior** |
| `operator` | `"equals" \| "not_equals" \| "in" \| "between"` | |
| `values` | `string[]` | vazio em `between` |
| `min`, `max` | `number?` | só em `between` |

**Regras de forma** (painel, antes do envio) — a adequação à origem é do backend:

| Situação | Recusa | Campo do erro |
|---|---|---|
| origem escolhida sem operador | "Escolha como comparar a resposta." | `condition.operator` |
| `between` sem mínimo ou máximo | "Informe o mínimo e o máximo da faixa." | `condition.values` |
| outro operador sem valor | "Escolha ao menos um valor para comparar." | `condition.values` |
| `equals`/`not_equals` com mais de um valor | "Igual e diferente comparam com um valor só." | `condition.values` |

Os nomes de campo são os de `CONDITION_FIELDS`, os mesmos que o backend devolve em `field`.

### Origens e operadores (`lib/condition.ts`)

| Tipo da origem | Pode ser origem? | Operadores | Valores |
|---|---|---|---|
| `single_choice`, `multiple_choice` | sim | igual, diferente, um destes | opções, pelo rótulo |
| `rating`, `scale` | sim | igual, diferente, um destes, faixa | números da faixa |
| `nps` | sim | igual, diferente, um destes, faixa | 0 a 10 |
| `free_text` | **não** | — | — |

### Entrada do formulário (`readQuestionInput` → `questionFormSchema`)

Listas paralelas vindas de campos repetidos: `optionLabels[]`/`optionValues[]` e
`conditionValues[]`. Transformações relevantes:

- valor de opção em branco cai no rótulo;
- rótulos dos extremos só viajam em tipos que os aceitam, até 60 caracteres;
- NPS envia faixa apenas quando há rótulo, e sempre `{ min: 0, max: 10 }`;
- condição sem origem não é enviada.

---

## Exposição

### `ExposureForm` (entrada)

| Campo da tela | Regra | Envio |
|---|---|---|
| `priority` | inteiro de −100 a 100; em branco = `0` | `priority` |
| `responseQuota` | inteiro ≥ 1; em branco = sem cota | `responseQuota` ou **`null`** |
| `ignoresQuietPeriod` | `"true"`/`"on"` ⇒ verdadeiro | `ignoresQuietPeriod` |

### `QuotaProgress`

| Campo | Tipo | Observação |
|---|---|---|
| `responseQuota` | `number?` | ausente ⇒ "Sem cota" |
| `completedResponses` | `number` | concluídas apuradas pela coleta |

---

## Aviso de texto livre — `FreeTextNoticeForm`

| Campo | Regra | Envio |
|---|---|---|
| `enabled` | `"true"`/`"on"` ⇒ verdadeiro | `freeTextNoticeEnabled` |
| `customText` | aparado; até 200 caracteres; em branco = padrão | `freeTextNoticeText` ou **`null`** |

---

## Modelo e duplicação

### `SurveyCreateForm`

| Campo | Regra |
|---|---|
| `name` | aparado, 1 a 160 caracteres |
| `template` | `nps`/`csat`/`ces`; `blank` ou vazio ⇒ ausente |

### `DuplicateSurveyForm`

| Campo | Regra | Envio |
|---|---|---|
| `targetApplicationId` | obrigatório | `targetApplicationId` |
| `name` | até 120 caracteres; em branco ⇒ ausente | `name` só quando presente |

A resposta é um `Survey` com o `applicationId` **da cópia** — é ele que decide o redirecionamento.

---

## Catálogo observado (`features/applications`)

| Tipo | Campos | Uso |
|---|---|---|
| `ObservedEvent` | `name`, `firstSeenAt`, `lastSeenAt` | sugestões do evento de disparo |
| `ObservedAttribute` | `name`, `firstSeenAt`, `lastSeenAt`, `values[{ value, lastSeenAt }]` | sugestões de atributo e valor |
| `ObservedAttributeSuggestion` (em `surveys`) | `name`, `values: string[]` | o que `RuleForm` recebe, montado em `app/` |

Catálogo, não perfil: nada liga um valor a quem o enviou.

---

## Avisos de publicação — `PublicationWarning`

| Campo | Tipo | Presente em |
|---|---|---|
| `code` | `string` (aberto) | todos |
| `ruleId` | `string?` | segmentação |
| `attribute` | `string?` | segmentação |
| `competingSurveys` | `{ surveyId, name, priority }[]` | concorrência (vazio nos demais; `null` vira `[]`) |
| `minRequiredVersion` | `string?` | compatibilidade |
| `unsupportedShare` | `number?` | compatibilidade (0..1) |

| Código conhecido | Mensagem (resumo) |
|---|---|
| `trigger.competing_surveys` | outras pesquisas no ar escutam o mesmo evento; só uma é entregue por vez |
| `segmentation.no_known_match` | a regra sobre o atributo exige algo que o app nunca enviou |
| `segmentation.contradictory` | as regras sobre o atributo se contradizem |
| `compatibility.unsupported_by_majority` | exige o SDK X ou mais novo, e Y% do tráfego recente vem de versões anteriores |
| qualquer outro | "Há um ponto de atenção nesta pesquisa." |

---

## Natureza da mudança — rótulos

| Valor | Rótulo exibido |
|---|---|
| `cosmetic` | Cosmética — respostas continuam comparáveis |
| `semantic` | Semântica — abre um novo grupo de comparabilidade |

---

## Ordem de perguntas

| Função | Entrada | Saída |
|---|---|---|
| `movedOrder` (001) | ids, id, `±1` | permutação, ou a mesma lista se não há para onde mover |
| `draggedOrder` | ids, id ativo, id sob o soltar | permutação completa, ou `undefined` quando não há o que enviar |

Invariante testada: a saída, quando existe, tem o mesmo comprimento e os mesmos identificadores
da entrada, e a entrada não é alterada.
