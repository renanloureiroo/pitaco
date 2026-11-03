---

description: "Task list — Painel de resultados e comportamento"
---

# Tasks: Painel de resultados e comportamento — números por pergunta, recorte, respostas abertas, export e interação

**Input**: documentos de design em `/specs/004-painel-resultados-comportamento/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/backend-api.md)

**Tests**: **incluídos e obrigatórios** (Princípios IV e V). Testes antes da implementação em cada
história.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo
- **[Story]**: US1 … US5
- Caminhos relativos à raiz do painel

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Criar a árvore da feature em `src/features/results/` com `api/`, `schemas/`, `components/`, `lib/` e `__tests__/`
- [X] T002 Criar `src/features/results/api/paths.ts` com `surveyResultsPath`, `openAnswersPath`, `exportPath`, `behaviorPath` e `exportRoutePath` (rota do painel)
- [X] T003 [P] Registrar `resultRoutes` em `e2e/stub-api/routes/index.ts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: fronteira de tipos, recorte e leituras que todas as histórias consomem

**⚠️ CRITICAL**: nenhuma história começa antes desta fase

- [X] T004 [P] Testar o recorte em `src/features/results/__tests__/filters.test.ts`: atalho vence o personalizado e vira `from` relativo a agora; personalizado invertido descartado; atributo com valor e ausente são recortes distintos; atributo sem valor e sem `ausente` não é recorte; versão inválida e busca longa ignoradas; recorte descrito em português
- [X] T005 [P] Testar as leituras em `src/features/results/__tests__/results-api.test.ts`: query no formato do backend e resposta validada; respostas abertas com busca e página; endereço do export na rota do painel com o mesmo recorte
- [X] T006 [P] Criar `src/features/results/schemas/results.ts` com `surveyResultsSchema`, `responseRateSchema`, `questionResultSchema`, `questionAggregateSchema`, `comparabilitySchema`, `npsSummarySchema`, `retentionSchema`, `attributeCatalogSchema`, `appliedFilterSchema` e `openAnswerSchema`
- [X] T007 [P] Criar `src/features/results/schemas/behavior.ts` com `surveyBehaviorSchema`, `questionBehaviorSchema`, `dismissalsSchema`, `activeTimeSchema`, `metricDefinitionSchema`, `changeRateOf` e `validationBlocksOf`
- [X] T008 Criar `src/features/results/schemas/filters.ts` com os nomes de parâmetro, `PERIOD_SHORTCUTS`, `PERIOD_LABELS`, `periodError`, `parseResultsFilters`, `parseTerm`, `toResultsQuery`, `hasAnyFilter` e `describeFilters` ([R2](./research.md#r2))
- [X] T009 Criar `src/features/results/api/results.ts` com `getSurveyResults`, `listOpenAnswers`, `getSurveyBehavior`, `backendExportUrl` e `exportHref`
- [X] T010 Criar `src/features/results/lib/results-labels.ts` com formatos (`formatShare`, `formatRate`, `formatAverage`, `formatScore`, `formatCount`), `RESULT_TYPE_LABELS`, textos de vazio, amostra, export, respostas abertas, múltipla escolha, não aplicável, `formatVersionList`, `incomparableMessage`, `comparableMessage` e `resultsEmptyVariant`
- [X] T011 Criar a fronteira `src/features/results/index.ts`
- [X] T012 Criar `e2e/stub-api/routes/results.ts` apurando resultado, respostas abertas, export e comportamento a partir das exibições semeadas, com o mesmo recorte ([R9](./research.md#r9))
- [X] T013 Acrescentar **Resultados** e **Comportamento** ao `SectionNav` em `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/layout.tsx`

**Checkpoint**: tipos, recorte e leituras prontos

---

## Phase 3: User Story 1 - Ler o resultado de uma pesquisa (Priority: P1) 🎯 MVP

**Goal**: taxa de resposta com definição, NPS, cada pergunta na forma do seu tipo, três vazios.

**Independent Test**: com coleta semeada, conferir 4/2/1/1, 50%, definição, amostra pequena e NPS 1/1/0.

### Tests for User Story 1

- [X] T014 [P] [US1] Testar em `src/features/results/__tests__/results-components.test.tsx`: as quatro contagens, a taxa e a definição juntas; sem exibição não há taxa nem aviso de amostra; escolha com contagem e proporção inclusive zero; pergunta sem resposta não é zero; NPS com os três grupos e a pontuação; avaliação com média; os três vazios distinguíveis
- [X] T015 [P] [US1] Testar em `src/features/results/__tests__/advanced-authoring-components.test.tsx`: não aplicáveis à parte das puladas com a explicação; `NpsSummaryCard` com grupos e ausência que não vira zero

### Implementation for User Story 1

- [X] T016 [P] [US1] Criar `src/features/results/components/response-rate-card.tsx` com as cinco contagens, a taxa, a definição, o aviso de amostra pequena e a linha do tempo (`timeline`, `timeline-point`)
- [X] T017 [P] [US1] Criar `src/features/results/components/nps-summary-card.tsx`
- [X] T018 [P] [US1] Criar `src/features/results/components/question-result-card.tsx` com barras de escolha (cor pela posição, `CHOICE_COLORS`), média e histograma, NPS com cores semânticas, texto apontando para as respostas abertas e o estado sem resposta ([R4](./research.md#r4))
- [X] T019 [P] [US1] Criar `src/features/results/components/refresh-control.tsx` com `router.refresh()` manual e a cada 30 s com a aba visível ([R7](./research.md#r7))
- [X] T020 [US1] Criar `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados/page.tsx` como Server Component que lê em paralelo e compõe topo, vazios (`resultsEmptyVariant`) e conteúdo
- [X] T021 [US1] Cobrir em `e2e/resultados.spec.ts` os números conferidos com o semeado e os vazios de nunca publicada e sem exibição

**Checkpoint**: o resultado de uma pesquisa é legível no painel

---

## Phase 4: User Story 2 - Recortar por período, atributo e versão (Priority: P1)

**Goal**: um recorte na URL para todas as leituras, com o recorte ativo escrito e a comparabilidade por pergunta.

**Independent Test**: período personalizado, "Sem o atributo" e limpar, conferindo números e URL.

### Tests for User Story 2

- [X] T022 [P] [US2] Testar em `src/features/results/__tests__/results-components.test.tsx`: atalho de período e atributo ausente vão para a URL e limpar remove tudo; período personalizado invertido recusado sem navegar
- [X] T023 [P] [US2] Testar em `src/features/results/__tests__/advanced-authoring-components.test.tsx`: aviso quando a pergunta mudou entre as versões somadas; "é seguro somar" quando nada mudou; uma versão só, ou visão por versão, sem marca; `formatVersionList`

### Implementation for User Story 2

- [X] T024 [US2] Criar `src/features/results/components/results-filters.tsx` (`"use client"`): período com atalhos e personalizado, atributo com valores e "Sem o atributo", visão consolidada ou por versão, recusa de período invertido, aplicar e limpar sem `page`
- [X] T025 [P] [US2] Criar `src/features/results/components/active-filters.tsx` com `describeFilters`
- [X] T026 [US2] Acrescentar a comparabilidade (`comparability-warning`, `comparability-safe`) a `src/features/results/components/question-result-card.tsx` ([R5](./research.md#r5))
- [X] T027 [US2] Compor recorte, recorte ativo e o vazio "sem exibição no recorte" em `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados/page.tsx`, com as versões de `getVersionComparability`
- [X] T028 [US2] Cobrir em `e2e/resultados.spec.ts` o período personalizado na URL mudando todos os números, "Sem o atributo" com `ausente=1` e limpar

**Checkpoint**: o número responde "e no plano pro?"

---

## Phase 5: User Story 3 - Ler as respostas abertas (Priority: P2)

**Goal**: lista paginada com busca e contexto da mesma exibição.

**Independent Test**: buscar "ÓTIMO", conferir a resposta, a URL e os agregados; limpar.

### Tests for User Story 3

- [X] T029 [P] [US3] Testar em `src/features/results/__tests__/results-components.test.tsx`: cada resposta traz momento e contexto; a busca vai para a URL e zera a página

### Implementation for User Story 3

- [X] T030 [P] [US3] Criar `src/features/results/components/open-answers-list.tsx` com o contexto recolhido em `details`
- [X] T031 [P] [US3] Criar `src/features/results/components/open-answers-search.tsx` (`"use client"`) com busca e limpar
- [X] T032 [US3] Compor a seção `open-answers` com lembrete, vazio por termo e `Pagination` em `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados/page.tsx`
- [X] T033 [US3] Cobrir em `e2e/resultados.spec.ts` a busca por termo e a limpeza

**Checkpoint**: o texto explica o número

---

## Phase 6: User Story 4 - Exportar o recorte em CSV (Priority: P2)

**Goal**: download do recorte, depois de confirmação, sem o navegador falar com o backend.

**Independent Test**: exportar com `?periodo=90`, confirmar e conferir nome, BOM, cabeçalho e conteúdo.

### Tests for User Story 4

- [X] T034 [P] [US4] Testar em `src/features/results/__tests__/results-components.test.tsx` que o export só navega depois da confirmação com o lembrete de dado pessoal

### Implementation for User Story 4

- [X] T035 [P] [US4] Criar `src/features/results/components/export-button.tsx` sobre o `ConfirmDialog` compartilhado
- [X] T036 [US4] Criar o Route Handler `src/app/api/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados/export/route.ts` que repassa o corpo do backend em fluxo, preserva `content-type`, `content-disposition` e status, e devolve `502` em falha de rede ([R3](./research.md#r3))
- [X] T037 [US4] Mostrar Atualizar e Exportar CSV só em pesquisa publicada, com `exportHref` do recorte, em `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados/page.tsx`
- [X] T038 [US4] Cobrir em `e2e/resultados.spec.ts` o export: confirmação, nome `resultados-{id}.csv`, BOM, cabeçalho e só o recorte

**Checkpoint**: análise fora do painel sem esquecer o dado pessoal

---

## Phase 7: User Story 5 - Ver o que acontece dentro da pesquisa (Priority: P2)

**Goal**: aba Comportamento com indicadores, funil, tempo, vias de dispensa, atrito e definições; resumo em Resultados.

**Independent Test**: com eventos semeados, conferir o funil e as vias; sem eventos, o vazio próprio.

### Tests for User Story 5

- [X] T039 [US5] Cobrir em `e2e/resultados.spec.ts` que o cartão `behavior-card` aparece nos resultados com "Taxa de troca de resposta"

### Implementation for User Story 5

- [X] T040 [US5] Criar `src/features/results/components/behavior-panels.tsx` com `QuestionFunnel`, `QuestionTimeChart`, `DismissalChart` (rótulos de via em português e "sem via informada"), `FrictionTable`, `BehaviorDefinitions` e `BehaviorSummary` ([R6](./research.md#r6))
- [X] T041 [US5] Criar `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/comportamento/page.tsx` lendo comportamento, resultado (catálogo de atributos) e comparabilidade em paralelo, com os vazios de nunca publicada e sem instrumentada e os quatro indicadores
- [X] T042 [US5] Compor `BehaviorSummary` com vínculo para a aba em `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados/page.tsx`
- [X] T043 [P] [US5] Simular `GET …/results/behavior` em `e2e/stub-api/routes/results.ts`

**Checkpoint**: onde as pessoas desistem, e por quê

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T044 Revisar cada tela contra SC-002: nenhuma proporção sem contagem, nenhuma ausência como zero
- [X] T045 Conferir que o recorte vale para todas as leituras de cada tela (um único `toResultsQuery` por requisição)
- [X] T046 Conferir na aba de rede que o download vem da rota do painel, nunca do backend
- [X] T047 Rodar o roteiro manual de [quickstart.md](./quickstart.md) contra a API real
- [X] T048 Rodar `npm run verify`

---

## Dependencies & Execution Order

- **Setup → Foundational → histórias → Polish**
- **US1** é a base da página de resultados; **US2**, **US3**, **US4** e **US5** estendem a mesma
  `resultados/page.tsx` e podem ser feitas em qualquer ordem depois de T020, uma de cada vez
  nesse arquivo
- **US5** tem página própria (`comportamento/page.tsx`) e reusa `ResultsFiltersForm` de US2: T041
  espera T024
- Componentes de cada história, marcados [P], podem ser feitos em paralelo

## Implementation Strategy

1. Setup + Foundational
2. US1 → **PARE E VALIDE**: números conferem com a coleta
3. US2 → recorte
4. US3, US4 → texto e export
5. US5 → comportamento
6. Polish

## Notes

- Nenhuma Server Action; o único efeito é o download, por Route Handler
- `"use client"` em: `results-filters.tsx`, `open-answers-search.tsx`, `export-button.tsx`,
  `refresh-control.tsx`
- `behavior-panels.tsx` não tem teste de componente próprio — registrado em Complexity Tracking
