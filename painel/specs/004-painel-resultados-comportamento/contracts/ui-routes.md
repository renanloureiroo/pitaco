# Contrato exposto — rotas, estados de tela e testids

Continuação dos contratos de rotas de [001](../../001-painel-operacao-pesquisas/contracts/ui-routes.md)
e [002](../../002-painel-leitura-coleta/contracts/ui-routes.md), cujas convenções valem aqui.

---

## Rotas novas

| Rota | Params | SearchParams | Leituras |
|---|---|---|---|
| `/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados` | `applicationId`, `surveyId` | `periodo?`, `de?`, `ate?`, `atributo?`, `valor?`, `ausente?`, `versao?`, `q?`, `page?`, `size?` | `getSurveyResults`, `listOpenAnswers`, `getVersionComparability`, `getSurveyHealth`, `getSurveyBehavior` |
| `/aplicacoes/[applicationId]/pesquisas/[surveyId]/comportamento` | idem | `periodo?`, `de?`, `ate?`, `atributo?`, `valor?`, `ausente?`, `versao?` | `getSurveyBehavior`, `getSurveyResults`, `getVersionComparability` |
| `/api/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados/export` | idem | `from?`, `to?`, `attribute?`, `attributeValue?`, `version?` (formato do backend) | repasse do export |

## Rota alterada

| Rota | Mudança |
|---|---|
| `…/pesquisas/[surveyId]/layout.tsx` | `SectionNav` ganha **Resultados** e **Comportamento**, após Exibições |

---

## `…/resultados`

**Ordem**: título + rótulo do fuso + Atualizar + Exportar CSV (os dois últimos só em pesquisa
publicada) → avisos de saúde (de 005) → recorte e recorte ativo (não na nunca publicada) → vazio
**ou** conteúdo.

**Conteúdo**: NPS no topo (se houver) → taxa de resposta (contagens, taxa, definição, aviso de
amostra, linha do tempo) → cartão de comportamento → nota de retenção (de 005) → uma carta por
pergunta → respostas abertas (busca, lembrete, lista, paginação).

**Estados**:

| Situação | O que aparece |
|---|---|
| nunca publicada | `empty-state` "Esta pesquisa ainda não foi publicada" + "Ir para a publicação"; sem `results-filters` |
| sem exibição | `empty-state` "Nenhuma exibição ainda" + "Ver o disparo" (omitido quando há aviso de supressão) |
| sem exibição no recorte | `empty-state` "Nenhuma exibição neste recorte" + "Limpar recorte" |
| pergunta sem resposta | `question-no-answers` |
| taxa ausente | `response-rate-value` = "—" |
| sem resposta aberta | `open-answers-empty` com ou sem o termo |
| falha de leitura | `error.tsx` do segmento da pesquisa, com nova tentativa |

**Testids**:
- topo: `timezone-note`, `refresh-button`, `refreshed-at`, `export-button`
- recorte: `results-filters`, `filter-period`, `filter-from`, `filter-to`, `filter-attribute`,
  `filter-value`, `filter-version`, `apply-filters`, `clear-filters`, `active-filters`,
  `active-filter`
- taxa: `response-rate`, `response-rate-value`, `response-rate-definition`, `small-sample-badge`,
  `stat-displayed`, `stat-completed`, `stat-dismissed`, `stat-abandoned`, `stat-in-progress`,
  `timeline`, `timeline-point`, `trend-chart`
- NPS: `nps-summary`, `nps-score`, `nps-promoters`, `nps-passives`, `nps-detractors`
- perguntas: `question-results`, `question-result` (com `data-question-key`),
  `question-statement`, `question-counts`, `question-not-applicable-note`, `question-no-answers`,
  `aggregate-row`, `aggregate-count`, `question-average`, `comparability-warning`,
  `comparability-safe`
- respostas abertas: `open-answers`, `open-answers-search`, `search-input`, `search-button`,
  `clear-search`, `open-answers-list`, `open-answer`, `open-answer-text`, `open-answer-context`,
  `context-value`, `open-answers-empty`
- comportamento (resumo): `behavior-card`
- export: `confirm-dialog`, `confirm-button`, `cancel-button` (do `ConfirmDialog` compartilhado)

---

## `…/comportamento`

**Ordem**: título e explicação → recorte e recorte ativo → vazio **ou** indicadores (4) → funil
→ tempo por pergunta e vias de dispensa lado a lado → sinais de atrito → definições.

**Estados**:

| Situação | O que aparece |
|---|---|
| nunca publicada | `empty-state` igual ao de Resultados, com vínculo para a publicação |
| nenhuma instrumentada | `empty-state` "Nenhuma exibição instrumentada neste recorte" |
| tempo ou taxa ausente | "—" |

**Testids**: `behavior-page`, `behavior-funnel`, `funnel-row`, `behavior-time`,
`behavior-dismissals`, `behavior-friction`, `behavior-definitions`.

---

## Route Handler de export

| Aspecto | Comportamento |
|---|---|
| Entrada | os parâmetros da URL do painel, já no formato do backend (montados por `exportHref`) |
| Chamada | `GET` ao export do backend com `Accept: text/csv, application/problem+json`, `cache: "no-store"` |
| Saída | o corpo do backend em fluxo, com `content-type` e `content-disposition` preservados e o mesmo status |
| Falha de rede | `502` com texto "Não foi possível falar com a API do Pitaco." |

---

## Fluxos cobertos por E2E

`e2e/resultados.spec.ts`, com coleta semeada pelo servidor simulado:

1. Números conferem com o semeado (4/2/1/1, 50%), definição da taxa visível, amostra pequena,
   cartão de comportamento, NPS 1/1/0, duas respostas abertas e o contexto da mesma exibição.
2. Nunca publicada e sem exibição têm vazios próprios; a nunca publicada não mostra o recorte.
3. Período personalizado muda todos os números e fica na URL; "Sem o atributo" leva `ausente=1`;
   limpar devolve a tela inteira.
4. Busca por termo encontra a resposta, fica na URL e não muda os agregados; limpar a busca
   devolve a lista.
5. Exportar pede confirmação sobre dado pessoal e baixa `resultados-{id}.csv`, com BOM, cabeçalho
   e só o recorte de 90 dias.

`e2e/autoria-avancada.spec.ts` cobre o aviso de comparabilidade no consolidado.
