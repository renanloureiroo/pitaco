# Phase 1 — Modelo de dados do painel

Tipos que atravessam a fronteira da API e viram domínio da feature `results`. Todo tipo nasce de
um schema Zod em `src/features/results/schemas/`. Campo ausente vira `undefined` via `absent()`.

---

## Resultado da pesquisa — `SurveyResults`

| Campo | Tipo | Observação |
|---|---|---|
| `everPublished` | `boolean` | falso ⇒ vazio "nunca publicada" |
| `responseRate` | `ResponseRate` | |
| `questions` | `QuestionResult[]` | na ordem da versão |
| `sampleSize` | `number` | |
| `smallSample` | `boolean` | o backend decide o limiar |
| `filter` | `AppliedFilter` | o recorte que o backend aplicou |
| `attributes` | `AttributeCatalog[]` | atributos vistos nas exibições da pesquisa, com valores e contagens |
| `nps` | `NpsSummary?` | só em pesquisa criada pelo modelo NPS |
| `retention` | `Retention?` | só quando a retenção já descartou respostas (ver 005) |

### `ResponseRate`

| Campo | Tipo | Observação |
|---|---|---|
| `displayed`, `completed`, `dismissed`, `abandoned`, `inProgress` | `number` | |
| `rate` | `number?` | 0..1; **ausente sem exibição** — exibido "—" |
| `definition` | `string` | frase do cálculo, exibida junto dos números |
| `timeline` | `{ day, displayed, completed }[]` | só dias com exibição |

### `QuestionResult`

| Campo | Tipo | Observação |
|---|---|---|
| `key`, `statement`, `type`, `position` | | |
| `answered`, `skipped` | `number` | |
| `notApplicable` | `number` | quem não viu por condição; fora das proporções |
| `aggregate` | `QuestionAggregate?` | **ausente ⇒ ninguém respondeu** |
| `comparability` | `{ comparable, versions[] }?` | só no consolidado |

### `QuestionAggregate`

| `kind` | Campos presentes | Forma na tela |
|---|---|---|
| `choice` | `options[{ value, label, count, share }]` | barras, cor pela posição |
| `numeric` | `average`, `distribution[{ value, count, share }]` | média + histograma |
| `nps` | `promoters`, `passives`, `detractors`, `score`, `distribution` | pontuação + grupos + histograma 0–10 |
| `text` | — | aponta para as respostas abertas |

### `NpsSummary`

`questionKey`, `respondents`, `promoters`, `passives`, `detractors`, `score?` (ausente ⇒ sem
resposta, nunca zero).

---

## Recorte

### Na URL — `ResultsFilters`

| Param | Campo | Regra |
|---|---|---|
| `periodo` | `period` | `7` \| `30` \| `90`; vence `de`/`ate` |
| `de`, `ate` | `from`, `to` | data-hora local no fuso de referência; par invertido descartado |
| `atributo` | `attribute` | até 80 caracteres; só vale com `valor` ou `ausente=1` |
| `valor` | `attributeValue` | até 200 caracteres |
| `ausente` | `attributeAbsent` | `1` ⇒ sem o atributo |
| `versao` | `versionNumber` | inteiro ≥ 1; senão consolidado |
| `q` | (busca) | até 200 caracteres; só nas respostas abertas |
| `page`, `size` | | paginação das respostas abertas |

### Para o backend — `ResultsQuery`

| Campo | Origem |
|---|---|
| `from` | `agora − período` ou `de` convertido para UTC |
| `to` | `ate` convertido para UTC (nunca com atalho) |
| `attribute` | `atributo` |
| `attributeValue` | `valor` (ausente quando `ausente=1`) |
| `version` | `versao` |

### Texto do recorte ativo — `describeFilters`

| Recorte | Texto |
|---|---|
| atalho | "Últimos 7 dias" / "Últimos 30 dias" / "Últimos 90 dias" |
| personalizado | "De 2026-09-01 00:00 até agora" |
| atributo com valor | `plano = pro` |
| atributo ausente | `Sem o atributo "plano"` |
| versão | "Versão 2" |

---

## Resposta aberta — `OpenAnswer`

| Campo | Tipo | Observação |
|---|---|---|
| `displayId`, `questionKey` | `string` | chave da linha |
| `statement`, `text`, `answeredAt` | `string` | |
| `context` | `{ questionKey, statement, value }[]` | outras respostas da mesma exibição |

Paginada por `PageResponse<OpenAnswer>`.

---

## Comportamento — `SurveyBehavior`

| Campo | Tipo | Observação |
|---|---|---|
| `everPublished` | `boolean` | |
| `displayed` | `number` | exibições no recorte |
| `instrumented` | `number` | com ao menos um evento; base das taxas |
| `questions` | `QuestionBehavior[]` | |
| `dismissals` | `{ total, byVia[{ via, count, share? }], unspecified }` | |
| `filter` | `AppliedFilter` | |
| `definitions` | `{ metric, definition }[]` | exibidas em "Definições" |

### `QuestionBehavior`

| Campo | Observação |
|---|---|
| `viewed`, `answered`, `skipped`, `abandoned` | funil |
| `activeTime { samples, medianMs?, p90Ms? }?` | tempo ativo sem segundo plano |
| `revisited`, `revisitRate?` | voltas à pergunta |
| `selected?`, `changed?`, `answerChangeRate?` (ou `changeRate?`) | troca de resposta |
| `validationBlocks?` (ou `validationFailures?`), `validationBlockedDisplays?`, `failureRate?` | bloqueios de validação |

### Vias de dispensa

| `via` | Rótulo |
|---|---|
| `close_button` | Botão fechar |
| `swipe` | Arrastar para baixo |
| `backdrop` | Toque fora |
| `hardware_back` | Voltar do Android |
| `navigation` | Navegação do app |
| `programmatic` | Pelo app (código) |

---

## Vazios — `resultsEmptyVariant`

| Condição | Variante | Título | Ação |
|---|---|---|---|
| `!everPublished` | `never_published` | "Esta pesquisa ainda não foi publicada" | Ir para a publicação |
| `displayed = 0`, sem recorte | `no_displays` | "Nenhuma exibição ainda" | Ver o disparo |
| `displayed = 0`, com recorte | `no_matches` | "Nenhuma exibição neste recorte" | Limpar recorte |
| `displayed > 0` | — | conteúdo | — |
