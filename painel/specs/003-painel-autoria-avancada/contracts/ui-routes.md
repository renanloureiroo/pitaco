# Contrato exposto — rotas, estados de tela e testids

Continuação do [contrato de rotas de 001](../../001-painel-operacao-pesquisas/contracts/ui-routes.md),
cujas convenções valem integralmente: segmentos em português sem acento, `params` e
`searchParams` como `Promise`, quatro estados por tela de leitura, seletores por papel acessível
e `data-testid` onde o papel não desambigua.

Nenhuma rota nova. Quatro rotas mudam de conteúdo.

---

## Rotas alteradas

| Rota | Mudança |
|---|---|
| `/aplicacoes/[applicationId]/pesquisas/nova` | escolha de modelo no formulário |
| `/aplicacoes/[applicationId]/pesquisas/[surveyId]` | lista arrastável, condição, edição no lugar, pré-visualização |
| `/aplicacoes/[applicationId]/pesquisas/[surveyId]/disparo` | sugestões do catálogo, avisos, exposição, aviso de texto livre |
| `/aplicacoes/[applicationId]/pesquisas/[surveyId]/publicacao` | avisos de publicação |
| cabeçalho da pesquisa (layout de `[surveyId]`) | ação Duplicar; Renomear oculto na encerrada |

---

## `…/pesquisas/nova`

**Conteúdo**: nome; grupo "Começar de" com quatro opções (`role="radiogroup"`, cada uma
`role="radio"`), cada uma com rótulo e descrição.

**Testids**: `survey-form`, `template-option-blank`, `template-option-nps`,
`template-option-csat`, `template-option-ces`, `submit-button`.

---

## `…/pesquisas/[surveyId]` — montagem

**Layout**: duas colunas a partir de `lg` — à esquerda a lista (ou o formulário em edição), à
direita a pré-visualização fixa ao rolar. Abaixo de `lg`, uma coluna.

**Estados da coluna da esquerda**:

| Estado | O que aparece |
|---|---|
| sem perguntas, editável | vazio "Nenhuma pergunta ainda" com Adicionar pergunta |
| lista | `SortableQuestionsList` + Adicionar pergunta (+ "Ver todas no preview" quando uma está isolada) |
| adicionando | `QuestionForm` vazio num cartão |
| editando | `QuestionForm` preenchido num cartão, no lugar da lista |
| somente leitura | `QuestionsList` sem alça nem ações |

**Ações por pergunta**: setas (só com 2+ perguntas), Visualizar, Editar, Remover; alça de
arrastar à esquerda do cartão.

**Testids**:
- lista: `questions-list`, `question-item` (com `data-question-key`), `drag-question-handle`,
  `move-question-up`, `move-question-down`, `edit-question-button`, `remove-question-button`,
  `add-question-button`, `question-condition`
- formulário: `question-form`, `question-type-select`, `question-options`, `add-option-button`,
  `question-range`, `question-labels`, `submit-button`
- condição: `question-condition-section`, `condition-toggle`, `condition-fields`,
  `condition-source-select`, `condition-operator-select`, `condition-value-select`,
  `condition-values`, `condition-min`, `condition-max`, `field-error-condition.values`
- pré-visualização: `survey-preview-section`, `survey-preview-events`

**Rótulos acessíveis usados pelos testes**: textbox "Enunciado", "Rótulo da opção N", "Valor da
opção N", botão "Arrastar pergunta \"…\"", "Mover para cima", "Mover para baixo".

---

## `…/pesquisas/[surveyId]/disparo`

**Ordem dos blocos**: Disparo e segmentação (formulário de disparo com sugestões de evento; regras
com sugestões de atributo e valor) → Avisos (só quando há) → Exposição → Aviso de dado pessoal.

**Estados**:

| Situação | O que aparece |
|---|---|
| nenhum evento visto | `observed-events-empty` explicando que o campo aceita qualquer nome |
| nenhum atributo visto | `observed-attributes-empty` |
| sem cota | `quota-progress` = "Sem cota" |
| sem intervalo de descanso na aplicação | `quiet-period-policy` = "Sem intervalo de descanso nesta aplicação." |
| sem pergunta de texto livre | `free-text-notice-unused` |
| aviso desligado | `free-text-notice-current` = "Desligado: …" |
| pesquisa encerrada | exposição e aviso sem formulário |
| alguma leitura falha | `error.tsx` do segmento, com nova tentativa |

**Testids**:
- catálogo: `observed-events`, `observed-event-suggestion`, `observed-events-empty`,
  `observed-attributes`, `observed-attribute-suggestion`, `observed-value-suggestion`,
  `observed-attributes-empty`
- exposição: `exposure-panel`, `exposure-priority`, `quota-progress`, `exposure-quiet-period`,
  `quiet-period-policy`, `tiebreak-rule`, `exposure-form`, `priority-input`, `quota-input`,
  `ignore-quiet-period-checkbox`, `quiet-period-exemption-notice`, `save-exposure-button`
- aviso: `free-text-notice-panel`, `free-text-notice-current`, `free-text-notice-unused`,
  `free-text-notice-form`, `free-text-notice-checkbox`, `free-text-notice-input`,
  `save-free-text-notice-button`
- avisos: `warnings-list`, `warning-item`, `competing-survey`

---

## `…/pesquisas/[surveyId]/publicacao`

**Ordem dos blocos**: O que falta para publicar (impedimentos, de 001) → Avisos (só quando há,
com a frase "Nenhum aviso impede a publicação") → Publicar.

**Formulário**: a partir da versão 2, `change-kind-select` (com os rótulos que dizem a
consequência) e `change-summary-input`; erro `field-error-changeKind` quando falta.

**Testids**: `impediments-list`, `impediment-item`, `warnings-list`, `warning-item`,
`competing-survey`, `publish-form`, `change-kind-select`, `change-summary-input`,
`publish-button`.

---

## Cabeçalho da pesquisa

**Ações**: Renomear (não na encerrada), Duplicar, Descartar (só nunca publicada), transições.

**Diálogo de duplicação**: título "Duplicar pesquisa", descrição do que vai e do que fica,
seletor de aplicação de destino (a atual como "(esta)"), nome opcional com placeholder
"Cópia de …".

**Testids**: `survey-header`, `survey-state`, `rename-survey-button`, `duplicate-survey-button`,
`duplicate-survey-form`, `duplicate-target-select`, `duplicate-survey-submit`,
`discard-survey-button`.

---

## Versões

Sem rota nova. `versions-table` mostra natureza da mudança e grupo; `open-draft-version-button`
leva à montagem; `comparability-panel` lista "Grupo N: v1, v2".

---

## Fluxos cobertos por E2E

| Arquivo | Cenário |
|---|---|
| `e2e/autoria-avancada.spec.ts` | criar por modelo NPS com a pergunta oficial (0 a 10 e rótulos) |
| `e2e/autoria-avancada.spec.ts` | duplicar para outra aplicação: montagem do destino, "Cópia de …", Rascunho, mesma pergunta |
| `e2e/autoria-avancada.spec.ts` | montar condição e conferir o resumo `Exibida se P1 for "Não"` |
| `e2e/autoria-avancada.spec.ts` | condição impossível (faixa 0–11 sobre NPS) recusada com erro no campo |
| `e2e/autoria-avancada.spec.ts` | resultados consolidados avisam a pergunta que mudou entre versões |
| `e2e/pesquisas.spec.ts` | reordenar pela alça operada por teclado e persistir |
| `e2e/disparo.spec.ts` | sugestões de evento; sugestões de atributo e valor; exposição salva, recarregada e limpa |
| `e2e/publicacao.spec.ts` | natureza exigida na v2, versão semântica e Grupo 2; aviso de concorrência sem bloquear; aviso de regra sem alcance sem bloquear |
| `e2e/ciclo-de-vida.spec.ts` | encerramento por cota com "Encerrada por cota" no histórico |
| `e2e/privacidade.spec.ts` | aviso de texto livre personalizado e desligado pela tela de disparo |
| `e2e/saude.spec.ts` | aviso de compatibilidade do SDK na publicação, sem bloquear |
