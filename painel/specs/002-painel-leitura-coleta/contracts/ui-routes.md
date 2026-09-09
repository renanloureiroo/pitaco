# Contrato exposto — rotas e estados de tela

Continuação do [contrato de rotas de 001](../../001-painel-operacao-pesquisas/contracts/ui-routes.md),
cujas convenções valem integralmente aqui: segmentos em português sem acento, `params` e
`searchParams` como `Promise` em Next 16, quatro estados por tela de leitura, e seletores por
papel acessível com `data-testid` só onde o papel não desambigua.

---

## Rotas novas

| Rota | Params | SearchParams | Leituras |
|---|---|---|---|
| `.../pesquisas/[surveyId]/exibicoes` | `applicationId`, `surveyId` | `versao?`, `desfecho?`, `de?`, `ate?`, `page?`, `size?` | `listSurveyDisplays`, `getSurvey`, `getVersionComparability` |
| `/aplicacoes/[applicationId]/exibicoes/[displayId]` | `applicationId`, `displayId` | — | `getDisplay`, `getVersion` |
| `/aplicacoes/[applicationId]/respondentes` | `applicationId` | `page?`, `size?` | `listRespondents` |
| `/aplicacoes/[applicationId]/respondentes/[respondentId]` | + `respondentId` | `desfecho?`, `de?`, `ate?`, `page?`, `size?` | `listRespondentDisplays` |

## Rotas alteradas

| Rota | Mudança |
|---|---|
| `aplicacoes/[applicationId]/layout.tsx` | `SectionNav` ganha **Respondentes** após Pesquisas |
| `.../pesquisas/[surveyId]/layout.tsx` | `SectionNav` ganha **Exibições** após Versões |

O detalhe da exibição fica **fora** do layout da pesquisa, por ser alcançado dos dois eixos
([R5](../research.md#r5)): herda breadcrumb e navegação da aplicação, e liga para a pesquisa
explicitamente no corpo.

---

## Leituras por tela

Tabela de verificação de SC-009: nenhuma leitura existe só para produzir um número.

| Tela | Leituras | Paralelas? | Justificativa de cada uma |
|---|---|---|---|
| Exibições da pesquisa | 3 | sim | listagem = conteúdo; pesquisa = qual dos três vazios ([R6](../research.md#r6)); comparabilidade = opções do seletor de versão |
| Detalhe da exibição | 2 | sim | exibição = conteúdo; versão = enunciados ([R2](../research.md#r2)) |
| Respondentes | 1 | — | |
| Histórico do respondente | 1 | — | |

---

## `.../pesquisas/[surveyId]/exibicoes`

**Conteúdo**: filtros (versão, desfecho, período) acima; tabela com versão, grupo de
comparabilidade, desfecho (badge), versão do SDK, abertura, fechamento; paginação abaixo. Cada
linha liga ao detalhe. Rótulo do fuso de referência visível uma vez ([R3](../research.md#r3)).

**Vazio — três variantes distinguíveis** (FR-025, [R6](../research.md#r6)):

| Condição | Título | Ação |
|---|---|---|
| pesquisa nunca publicada | "Esta pesquisa ainda não foi publicada" | vínculo para a publicação |
| sem filtro, zero itens | "Nenhuma exibição ainda" | nenhuma |
| com filtro, zero itens | "Nenhuma exibição neste recorte" | limpar filtros |

**Filtros**: um único formulário cliente. Trocar qualquer filtro volta para a página 1. Início
posterior ao fim é recusado no campo, sem navegar, preservando o digitado e a listagem
([data-model](../data-model.md#filtros-de-listagem)).

**Testids**: `displays-table`, `display-row`, `display-outcome-badge`, `display-filters`,
`filter-version`, `filter-outcome`, `filter-from`, `filter-to`, `apply-filters`, `clear-filters`,
`field-error-periodo`, `timezone-note`.

---

## `/aplicacoes/[applicationId]/exibicoes/[displayId]`

**Conteúdo, em três blocos**:

1. **Resumo** — pesquisa (vínculo), respondente (vínculo), versão e grupo, desfecho, versão do
   SDK, abertura, fechamento.
2. **Respostas** — na ordem vinda da API, cada uma com o enunciado da pergunta da versão exibida,
   seu tipo, e o valor no formato do tipo.
3. **Atributos** — o instantâneo, rotulado como pertencente **à exibição**, não ao respondente.

**Estados de ausência, cada um com texto próprio** (FR-012, FR-014, FR-015, SC-005):

| Situação | O que a tela diz |
|---|---|
| exibição aberta ou dispensada | "Sem respostas" + motivo coerente com o desfecho |
| resposta `SKIPPED` | "Pulada" |
| resposta `EXPIRED` | "Texto expirado" + explicação da retenção |
| `attributes` vazio | "Nenhum atributo informado" |
| `sdkVersion` ausente | "não informada" |
| `closedAt` ausente | "ainda aberta" |
| versão não pôde ser lida | aviso no bloco de respostas; respostas seguem visíveis pela chave |
| chave sem pergunta na versão | a resposta aparece marcada como pergunta não encontrada |

**Não encontrado**: `404` da API → `notFound()` → `not-found.tsx` do segmento.

**Testids**: `display-detail`, `display-summary`, `display-survey-link`, `display-respondent-link`,
`display-answers`, `answer-item`, `answer-question`, `answer-value`, `answer-skipped`,
`answer-expired`, `answers-empty`, `display-attributes`, `attribute-item`, `attributes-empty`,
`version-unavailable-note`.

**Invariante testada**: o número de `answer-item` é igual ao número de respostas do corpo, em
qualquer cenário de enriquecimento — nenhuma resposta some por falha de leitura da versão.

---

## `/aplicacoes/[applicationId]/respondentes`

**Conteúdo**: tabela com forma de identificação (rótulo em português, nunca o código cru),
valor, primeiro contato, último contato. Cada linha liga ao histórico.

**Vazio**: "Esta aplicação ainda não recebeu contato" — nunca erro.

**Testids**: `respondents-table`, `respondent-row`, `respondent-identity-kind`,
`respondent-identity-value`.

---

## `/aplicacoes/[applicationId]/respondentes/[respondentId]`

**Conteúdo**: resumo do respondente no topo; abaixo, suas exibições — a mesma tabela da listagem
por pesquisa, com a coluna de pesquisa visível ([R8](../research.md#r8)). Filtros de desfecho e
período; **sem** filtro de versão (FR-022).

**Vazio**: duas variantes — "Este respondente ainda não recebeu exibição" e "Nenhuma exibição
neste recorte" com ação de limpar.

**Testids**: `respondent-summary`, `respondent-displays`, e os mesmos da tabela de exibições, mais
`display-survey-cell`.

---

## Navegação fechada (SC-008, FR-029)

```
pesquisa ──── aba Exibições ────► listagem de exibições ──► detalhe da exibição
   ▲                                                            │   │
   └──────────── display-survey-link ◄──────────────────────────┘   │
                                                                    │
aplicação ─── aba Respondentes ──► listagem ──► histórico ◄─ display-respondent-link
                                                    │
                                                    └──► detalhe da exibição
```

Partindo de qualquer um dos três — pesquisa, exibição, respondente — os outros dois são
alcançáveis só por vínculos da interface.

---

## Componentes compartilhados reaproveitados

Nenhum componente compartilhado novo. `EmptyState`, `ErrorState`, `Pagination`, `PageHeader` e
`SectionNav` já existem e atendem, com os mesmos testids de 001.

`shared/lib/format.ts` ganha **uma** constante: o rótulo do fuso de referência
([R3](../research.md#r3)). Nenhuma função de formatação nova — `formatDateTime` já resolve.

---

## Fluxo coberto por E2E (SC-007)

`e2e/coleta.spec.ts`, com dados semeados pelo `stub-api` ([R9](../research.md#r9)), já que o
painel é somente leitura e não sabe criar exibição:

1. Abrir a aba Exibições de uma pesquisa publicada e conferir a listagem.
2. Filtrar por desfecho e conferir o recorte; filtrar por período inválido e conferir a recusa
   no campo, sem navegação.
3. Abrir uma exibição concluída e conferir respostas com enunciado, uma pulada e uma expirada
   distinguíveis, e o bloco de atributos.
4. Seguir para o respondente e conferir o histórico.
5. Conferir o vazio de "recorte sem resultado" e que limpar o filtro devolve a listagem.
