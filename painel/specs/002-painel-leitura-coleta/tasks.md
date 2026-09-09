---

description: "Task list — Painel de leitura da coleta"
---

# Tasks: Painel de leitura da coleta — exibições, respostas e respondentes

**Input**: documentos de design em `/specs/002-painel-leitura-coleta/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/backend-api.md)

**Tests**: **incluídos e obrigatórios**. Não são opção aqui: o Princípio IV da constituição é
não negociável e o Princípio V exige Playwright nos fluxos críticos — SC-007 nomeia o fluxo.

**Organization**: tarefas agrupadas por história de usuário, para que cada uma seja
implementável e testável de forma independente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência pendente)
- **[Story]**: a que história a tarefa pertence (US1, US2, US3)
- Todo caminho de arquivo é explícito

## Path Conventions

- **Painel** (repositório atual, `painel/`): `src/`, `e2e/` na raiz
- **Backend** (repositório irmão, `../backend/`): caminhos prefixados por `backend/` e
  relativos à raiz do workspace `pitaco/` — é o único ponto desta entrega fora do painel
  ([R7](./research.md#r7))

## Desvios registrados em relação ao plano

| Item | Plano | Aqui | Motivo |
|---|---|---|---|
| `__tests__/components.test.tsx` | um arquivo | três (`displays-components`, `display-detail-components`, `respondents-components`) | um arquivo único seria escrito por três histórias diferentes, quebrando a independência e o `[P]` entre elas |
| `lib/empty-variants.ts` | não listado | acrescentado | [R6](./research.md#r6) exige "função pura testável" para os três vazios; não cabe em `collect-labels.ts`, que rotula valores de domínio |
| `schemas/optional.ts` | não listado | acrescentado | o backend **serializa o campo ausente como `null`**, não o omitindo; `.optional()` puro recusaria toda exibição ainda aberta. O envoltório `absent()` aceita as duas formas e produz `undefined` |
| `e2e/stub-api/routes/seed.ts` | não listado | acrescentado | a semeadura de coleta ([R9](./research.md#r9)) precisa de rota própria, e ela **não é do contrato**; enfiá-la em `displays.ts` misturaria simulação de contrato com ferramenta de teste |
| `DisplayFilters` / `DisplaySummary` como nome de componente | implícito nos nomes de arquivo | `DisplayFiltersForm` / `DisplaySummaryCard` | colidiam com os tipos de mesmo nome; o sufixo segue `SurveyForm`, `QuestionForm`, `RuleForm` do projeto |
| Detalhe da exibição: duas leituras **em paralelo** | plano e [contrato de rotas](./contracts/ui-routes.md#leituras-por-tela) | em **sequência** | impossível paralelizar: `getVersion` exige `surveyId` e `versionNumber`, que só existem depois que a exibição chega. A contagem de leituras continua 2, que é o que SC-009 verifica |
| `RespondentSummary` com o respondente lido | implícito em T046 e no contrato de rotas | identificação por `respondentId`, com vínculo para a listagem | a API **não tem** leitura de um respondente isolado — só a listagem paginada. Buscá-la para rotular um cabeçalho seria a consulta extra que SC-009 proíbe. O componente aceita o respondente por propriedade quando quem compõe já o conhece |
| `versionId` fora das respostas de versão | [R7](./research.md#r7) mantinha `SurveyVersionResponseDTO` sem identificador | `id` acrescentado na listagem e no detalhe de versão | pedido do solicitante. O **filtro continua por número** (a decisão de R7 não muda); o identificador passa a ser publicado porque a exibição o carrega e o SDK precisa dele ao abrir uma exibição |
| Painel na porta 3000 | padrão do Next | **3001**, em `package.json`, `Dockerfile`, `playwright.config.ts` e no `compose.yaml` da publicação | a stack local do backend publica o Grafana LGTM em 3000, e o `reuseExistingServer` do Playwright via a porta responder e nem subia o Next. O túnel em `~/apps/infra` precisa apontar para `pitaco-painel:3001` |

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: esqueleto da feature nova e a única alteração compartilhada fora dela

- [X] T001 Criar a árvore de diretórios da feature em `src/features/collect/` com `api/`, `schemas/`, `components/displays/`, `components/respondents/`, `lib/` e `__tests__/`, conforme a Project Structure de plan.md
- [X] T002 Criar `src/features/collect/api/paths.ts` com `surveyDisplaysPath`, `displayPath`, `respondentsPath` e `respondentDisplaysPath`, escopados por aplicação, espelhando o estilo de `src/features/surveys/api/paths.ts`
- [X] T003 [P] Exportar a constante de rótulo do fuso de referência (ex.: `TIMEZONE_NOTE = "Horários em Brasília (UTC−3)"`) em `src/shared/lib/format.ts`, sem criar função de formatação nova ([R3](./research.md#r3))
- [X] T004 [P] Cobrir o rótulo do fuso em `src/shared/lib/__tests__/format.test.ts`, garantindo que `formatDateTime` e a constante concordam quanto ao fuso

**Checkpoint**: feature vazia no lugar certo; nada mais depende de decisão de estrutura

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: contrato do backend corrigido, tipos de fronteira, clientes de leitura e simulador
de API — tudo que as três histórias consomem

**⚠️ CRITICAL**: nenhuma história pode começar antes desta fase

### Correção de contrato no backend ([R7](./research.md#r7))

- [X] T005 Trocar `versionId` (UUID) por `versionNumber` (inteiro, mínimo 1) em `backend/src/main/java/com/renanloureiroo/pitaco/modules/collect/infra/http/dtos/ListDisplaysQueryDTO.java`, ajustando a validação e o `toInput`
- [X] T006 Trocar `Optional<SurveyVersionId> versionId` por `Optional<Integer> versionNumber` no `Input` de `backend/src/main/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/ListSurveyDisplaysUseCase.java`
- [X] T007 Trocar o campo correspondente em `ListDisplaysQuery` de `backend/src/main/java/com/renanloureiroo/pitaco/modules/collect/application/repositories/SurveyDisplayRepository.java`
- [X] T008 Ajustar `findSummaryPage` em `backend/src/main/java/com/renanloureiroo/pitaco/modules/collect/infra/database/jpa/repositories/SurveyDisplayJpaRepository.java`: predicado `(:versionNumber is null or v.number = :versionNumber)` na consulta principal e **junção interna por igualdade de identificador** acrescentada à `countQuery`, que hoje não junta `SurveyVersionJpaEntity`
- [X] T009 Adequar o repositório em memória `backend/src/test/java/com/renanloureiroo/pitaco/testsupport/repositories/InMemorySurveyDisplayRepository.java` ao filtro por número de versão
- [X] T010 [P] Atualizar a documentação do parâmetro em `backend/src/main/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/SurveyDisplaySwagger.java`
- [X] T011 [P] Cobrir a validação do parâmetro em `backend/src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/dtos/ListDisplaysQueryDTOTest.java`: número malformado ou menor que 1 recusado apontando o campo; ausente não restringe
- [X] T012 [P] Cobrir o filtro no caso de uso em `backend/src/test/java/com/renanloureiroo/pitaco/modules/collect/application/usecases/ListSurveyDisplaysUseCaseTest.java`
- [X] T013 Cobrir em `backend/src/test/java/com/renanloureiroo/pitaco/modules/collect/infra/http/controllers/ListSurveyDisplaysE2ETest.java` as quatro verificações do quickstart: número existente restringe, número inexistente devolve **página vazia** (não `404`), sem filtro devolve todas, e **`total` com filtro igual ao tamanho do conjunto sem paginação** — é o teste que pega a junção esquecida na `countQuery` (T008)

### Fronteira de tipos do painel

- [X] T014 [P] Criar `src/features/collect/schemas/display.ts` com `displayOutcomeSchema` (`STARTED` | `COMPLETED` | `DISMISSED`), `displaySummarySchema`, `respondentDisplaySchema` (com `surveyId`) e `displayDetailSchema` (com `respondentId`, `surveyId`, `attributes`, `answers`), usando `.optional()` para `sdkVersion` e `closedAt` conforme [data-model](./data-model.md)
- [X] T015 [P] Criar `src/features/collect/schemas/answer.ts` com `answerStatusSchema` (`ANSWERED` | `SKIPPED` | `EXPIRED`) e `answerSchema` (`questionKey`, `status`, `text?`, `number?`, `options`)
- [X] T016 [P] Criar `src/features/collect/schemas/respondent.ts` com `respondentIdentityKindSchema` (`APP_REFERENCE` | `DEVICE`) e `respondentSchema`
- [X] T017 [P] Criar `src/features/collect/lib/collect-labels.ts` com `DISPLAY_OUTCOME_LABELS`, `displayOutcomeVariant`, `RESPONDENT_IDENTITY_KIND_LABELS` e os textos de situação de resposta (`Pulada`, `Texto expirado` + explicação de retenção), nunca expondo o código cru (FR-020)
- [X] T018 Criar `src/features/collect/schemas/filters.ts` com a leitura de `versao`, `desfecho`, `de` e `ate` de `searchParams` (valor inválido cai no padrão), a conversão data-hora do fuso de referência → ISO UTC, e a validação de coerência do par de período usada pelo formulário (FR-007) — as duas validações com propósitos distintos descritos em [data-model](./data-model.md#filtros-de-listagem)
- [X] T019 Criar `src/features/collect/api/displays.ts` com `listSurveyDisplays` (query `versionNumber`, `outcome`, `openedFrom`, `openedTo`, `page`, `size`) e `getDisplay`, usando `request` + `pageResponseSchema` de `@/shared/api`, com `404` mapeado para a tela de não encontrado
- [X] T020 Criar `src/features/collect/api/respondents.ts` com `listRespondents` e `listRespondentDisplays` (sem filtro de versão — FR-022)
- [X] T021 Criar a fronteira pública `src/features/collect/index.ts` exportando apenas as quatro leituras, os tipos de domínio, os rótulos e os componentes que `app/` compõe (Princípio I)
- [X] T022 [P] Testar a leitura e a recusa dos filtros em `src/features/collect/__tests__/filters-schema.test.ts`: par incoerente ignorado no servidor e recusado no formulário, valores desconhecidos caindo no padrão, conversão de fuso ida e volta
- [X] T023 [P] Testar os rótulos em `src/features/collect/__tests__/collect-labels.test.ts`, incluindo que `SKIPPED` e `EXPIRED` têm textos distintos entre si e de resposta em branco (SC-006)
- [X] T024 [P] Testar `listSurveyDisplays` e `getDisplay` em `src/features/collect/__tests__/displays-api.test.ts` com o cliente HTTP mockado: montagem da query, validação de corpo, `404`, e campos ausentes virando `undefined`
- [X] T025 [P] Testar `listRespondents` e `listRespondentDisplays` em `src/features/collect/__tests__/respondents-api.test.ts`, inclusive a ausência de `versionNumber` na query do histórico

### Simulador de API para o E2E ([R9](./research.md#r9))

- [X] T026 Acrescentar os tipos `StubDisplay`, `StubAnswer` e `StubRespondent` e seus mapas ao estado em memória de `e2e/stub-api/store.ts`, com semeadura direta — o painel é somente leitura e não sabe criar exibição
- [X] T027 [P] Criar `e2e/stub-api/routes/displays.ts` com as rotas de exibições da pesquisa (filtros e paginação) e de detalhe da exibição
- [X] T028 [P] Criar `e2e/stub-api/routes/respondents.ts` com respondentes da aplicação e exibições do respondente
- [X] T029 Registrar `displayRoutes` e `respondentRoutes` em `e2e/stub-api/routes/index.ts`, respeitando a precedência de segmento fixo sobre dinâmico

**Checkpoint**: contrato corrigido, leituras tipadas e simulador prontos — as três histórias podem começar em paralelo

---

## Phase 3: User Story 1 - Ver as exibições de uma pesquisa (Priority: P1) 🎯 MVP

**Goal**: dentro de uma pesquisa, uma seção lista as exibições da mais recente para a mais
antiga, com filtros combináveis por versão, desfecho e período, todos refletidos na URL.

**Independent Test**: com uma pesquisa publicada e exibições registradas, abrir a aba Exibições,
conferir a listagem, aplicar cada filtro isoladamente e confirmar que o recorte muda — sem sair
da pesquisa.

### Implementation for User Story 1

- [X] T030 [P] [US1] Criar `src/features/collect/lib/empty-variants.ts` com a função pura que decide entre os três vazios da listagem por pesquisa — nunca publicada, nenhuma exibição ainda, nenhuma no recorte ([R6](./research.md#r6))
- [X] T031 [P] [US1] Criar `src/features/collect/components/displays/displays-table.tsx` com colunas versão, grupo de comparabilidade, desfecho (badge), versão do SDK, abertura e fechamento, coluna de pesquisa condicionada por `showSurvey` ([R8](./research.md#r8)), linha ligando ao detalhe, e testids `displays-table`, `display-row`, `display-outcome-badge`, `display-survey-cell`
- [X] T032 [US1] Criar `src/features/collect/components/displays/display-filters.tsx` com `"use client"`: `Select` de versão (povoado por `getVersionComparability`), `Select` de desfecho com exatamente os três valores aceitos (FR-006), dois `Input type="datetime-local"` para o período, recusa no campo quando o início é posterior ao fim sem navegar (FR-007), volta à página 1 a cada troca, e testids `display-filters`, `filter-version`, `filter-outcome`, `filter-from`, `filter-to`, `apply-filters`, `clear-filters`, `field-error-periodo`
- [X] T033 [US1] Criar `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/exibicoes/page.tsx` como Server Component que lê `listSurveyDisplays`, `getSurvey` e `getVersionComparability` **em paralelo**, compõe filtros, tabela, `Pagination` e o rótulo do fuso (`timezone-note`), e escolhe o vazio por T030
- [X] T034 [P] [US1] Criar `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/exibicoes/loading.tsx` com esqueleto coerente com a tabela
- [X] T035 [US1] Acrescentar o item **Exibições** ao `SectionNav`, após Versões, em `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/layout.tsx`
- [X] T036 [US1] Testar em `src/features/collect/__tests__/displays-components.test.tsx`: os três vazios distinguíveis, fechamento ausente exibido como "ainda aberta" e nunca como data vazia ou zero, versão do SDK ausente com texto próprio, e a recusa do período no formulário preservando o digitado

**Checkpoint**: US1 completa e verificável sozinha — publicar deixa de ser um ato cego

---

## Phase 4: User Story 2 - Ler o que foi respondido em uma exibição (Priority: P1)

**Goal**: o detalhe da exibição mostra as respostas na ordem da versão exibida, cada uma com o
enunciado que a originou, mais o instantâneo de atributos daquela exibição.

**Independent Test**: abrir a rota de detalhe de uma exibição concluída e conferir respostas,
enunciados e atributos; repetir com uma dispensada e uma ainda aberta. A rota é própria — não
depende de navegar por US1.

### Implementation for User Story 2

- [X] T037 [P] [US2] Criar `src/features/collect/lib/answers.ts` com `matchAnswersToQuestions`, função pura que casa cada resposta com a pergunta da versão por `key`, tendo a **lista de respostas como fonte da ordem** ([R2](./research.md#r2))
- [X] T038 [P] [US2] Testar `matchAnswersToQuestions` em `src/features/collect/__tests__/answers.test.ts`, com a invariante `resultado.length === answers.length` em toda combinação: versão ausente, chave sem pergunta correspondente, ordem divergente
- [X] T039 [P] [US2] Criar `src/features/collect/components/displays/display-summary.tsx` com pesquisa e respondente como vínculos (`display-survey-link`, `display-respondent-link`), versão e grupo, desfecho, versão do SDK ("não informada" quando ausente) e instantes ("ainda aberta" quando `closedAt` ausente)
- [X] T040 [P] [US2] Criar `src/features/collect/components/displays/display-answers.tsx` renderizando cada resposta com enunciado, tipo (via `QUESTION_TYPE_LABELS`) e valor no formato do tipo; `Pulada` e `Texto expirado` com textos próprios (FR-012), aviso `version-unavailable-note` quando a versão não pôde ser lida, marcação de pergunta não encontrada, e `answers-empty` coerente com o desfecho (FR-015); testids `display-answers`, `answer-item`, `answer-question`, `answer-value`, `answer-skipped`, `answer-expired`
- [X] T041 [P] [US2] Criar `src/features/collect/components/displays/display-attributes.tsx` rotulando o instantâneo como pertencente **à exibição**, não ao respondente (FR-014), com `attributes-empty` para o mapa vazio; testids `display-attributes`, `attribute-item`
- [X] T042 [US2] Criar `src/app/aplicacoes/[applicationId]/exibicoes/[displayId]/page.tsx` como Server Component que lê `getDisplay` e `getVersion` **em paralelo** e ainda renderiza quando a leitura da versão falha, compondo os três blocos e o rótulo do fuso
- [X] T043 [P] [US2] Criar `loading.tsx`, `error.tsx` e `not-found.tsx` em `src/app/aplicacoes/[applicationId]/exibicoes/[displayId]/`, com nova tentativa no erro e caminho de volta no não encontrado (FR-017, FR-026)
- [X] T044 [US2] Testar em `src/features/collect/__tests__/display-detail-components.test.tsx`: número de `answer-item` igual ao de respostas em todo cenário de enriquecimento, `SKIPPED`/`EXPIRED`/em branco distinguíveis por quem lê, atributos vazios com texto próprio, e exibição aberta ou dispensada com ausência explícita

**Checkpoint**: US1 e US2 funcionam independentemente; o conteúdo pelo qual a pesquisa existe está legível

---

## Phase 5: User Story 3 - Ver os respondentes e o histórico de cada um (Priority: P2)

**Goal**: na aplicação, uma seção lista quem já foi visto; abrir um respondente mostra suas
exibições de qualquer pesquisa, com filtros de desfecho e período.

**Independent Test**: abrir a seção de respondentes de uma aplicação com coleta, conferir a
listagem paginada, abrir um respondente e conferir o histórico com os filtros aplicados.

### Implementation for User Story 3

- [X] T045 [P] [US3] Criar `src/features/collect/components/respondents/respondents-table.tsx` com forma de identificação em português, valor exibido como veio, primeiro e último contato, linha ligando ao histórico; testids `respondents-table`, `respondent-row`, `respondent-identity-kind`, `respondent-identity-value`
- [X] T046 [P] [US3] Criar `src/features/collect/components/respondents/respondent-summary.tsx` com o resumo do respondente e o testid `respondent-summary`
- [X] T047 [US3] Criar `src/app/aplicacoes/[applicationId]/respondentes/page.tsx` lendo `listRespondents`, com `Pagination` e o vazio "Esta aplicação ainda não recebeu contato" — nunca erro
- [X] T048 [P] [US3] Criar `loading.tsx` e `error.tsx` em `src/app/aplicacoes/[applicationId]/respondentes/`
- [X] T049 [US3] Criar `src/app/aplicacoes/[applicationId]/respondentes/[respondentId]/page.tsx` lendo `listRespondentDisplays`, reusando `DisplaysTable` com `showSurvey` e `DisplayFilters` **sem** o seletor de versão (FR-022), com os dois vazios da tela e o rótulo do fuso
- [X] T050 [P] [US3] Criar `loading.tsx` e `not-found.tsx` em `src/app/aplicacoes/[applicationId]/respondentes/[respondentId]/`
- [X] T051 [US3] Acrescentar o item **Respondentes** ao `SectionNav`, após Pesquisas, em `src/app/aplicacoes/[applicationId]/layout.tsx`
- [X] T052 [US3] Testar em `src/features/collect/__tests__/respondents-components.test.tsx`: rótulo de identificação em português e nunca o código cru, os dois vazios do histórico, a coluna de pesquisa visível e a ausência do filtro de versão

**Checkpoint**: as três histórias funcionam de forma independente

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T053 Criar `e2e/coleta.spec.ts` cobrindo os cinco cenários do [contrato de rotas](./contracts/ui-routes.md#fluxo-coberto-por-e2e-sc-007): listagem da aba Exibições, filtro por desfecho e recusa de período inválido sem navegar, detalhe com resposta pulada e expirada distinguíveis mais atributos, navegação até o histórico do respondente, e o vazio de recorte com limpar filtros (SC-007)
- [X] T054 Conferir a navegação fechada de SC-008 e FR-029: de pesquisa, exibição e respondente se alcançam os outros dois só por vínculos da interface
- [X] T055 Auditar as leituras por tela contra a [tabela de leituras](./contracts/ui-routes.md#leituras-por-tela) — 3, 2, 1, 1 — confirmando que nenhuma requisição existe só para produzir um número (FR-030, SC-009)
- [X] T056 Revisar cada ausência de valor nas quatro telas contra SC-005: nenhum zero, data vazia ou traço ambíguo; cada ausência com texto próprio
- [X] T057 Rodar o roteiro manual de [quickstart.md](./quickstart.md) contra a API real, com o backend já contendo a mudança de `versionNumber`
- [X] T058 Rodar `./mvnw test -Dtest='*Display*'` em `backend/` e `npm run verify` no painel — a constituição não admite suíte vermelha no merge

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependência — começa imediatamente
- **Foundational (Phase 2)**: depende da Phase 1 — **bloqueia todas as histórias**
- **User Stories (Phases 3–5)**: dependem da Phase 2; entre si são independentes
- **Polish (Phase 6)**: depende das três histórias

### User Story Dependencies

- **US1 (P1)**: começa após a Phase 2. Sem dependência de outra história
- **US2 (P1)**: começa após a Phase 2. Depende de US1 **apenas como caminho de navegação** — a rota de detalhe é própria e testável isoladamente
- **US3 (P2)**: começa após a Phase 2. Reusa `DisplaysTable` (T031) e `DisplayFilters` (T032) de US1; se US1 não estiver pronta, T049 espera por T031 e T032

### Dependências internas notáveis

- T008 depende de T007; T013 é o teste que valida T008 — não pule a verificação de `total`
- T019 e T020 dependem dos schemas T014–T016 e de T002
- T021 depende de T017, T019 e T020
- T033 depende de T030, T031, T032; T042 depende de T037, T039, T040, T041
- T049 depende de T031 e T032 (US1)
- T053 depende de T026–T029 e das três histórias

### Parallel Opportunities

- T003 e T004 em paralelo com T001/T002
- Backend: T010, T011 e T012 em paralelo após T005–T009
- Schemas e rótulos: T014, T015, T016, T017 todos em paralelo
- Testes de fronteira: T022, T023, T024, T025 em paralelo
- Simulador: T027 e T028 em paralelo após T026
- US2: T037, T039, T040, T041 em paralelo (arquivos distintos)
- Depois da Phase 2, as três histórias podem correr em paralelo, com a ressalva de T049

---

## Parallel Example: Phase 2

```bash
# Schemas e rótulos da feature, todos em arquivos distintos:
Task: "Criar src/features/collect/schemas/display.ts"
Task: "Criar src/features/collect/schemas/answer.ts"
Task: "Criar src/features/collect/schemas/respondent.ts"
Task: "Criar src/features/collect/lib/collect-labels.ts"

# Testes de fronteira, depois que api/ e schemas/ existem:
Task: "Testar filtros em __tests__/filters-schema.test.ts"
Task: "Testar rótulos em __tests__/collect-labels.test.ts"
Task: "Testar displays-api em __tests__/displays-api.test.ts"
Task: "Testar respondents-api em __tests__/respondents-api.test.ts"
```

## Parallel Example: User Story 2

```bash
Task: "Criar lib/answers.ts com matchAnswersToQuestions"
Task: "Criar components/displays/display-summary.tsx"
Task: "Criar components/displays/display-answers.tsx"
Task: "Criar components/displays/display-attributes.tsx"
```

---

## Implementation Strategy

### MVP primeiro (US1)

1. Phase 1: Setup
2. Phase 2: Foundational — crítica, bloqueia tudo. A correção do backend (T005–T013) pode
   correr em paralelo com a fronteira de tipos do painel (T014–T025)
3. Phase 3: US1
4. **PARE E VALIDE**: aba Exibições com listagem e os três filtros, testada isoladamente
5. Demonstrável: publicar deixa de ser um ato cego

### Entrega incremental

1. Setup + Foundational → fundação pronta, contrato corrigido
2. + US1 → ver exibições (MVP)
3. + US2 → ler o que foi respondido
4. + US3 → respondentes e histórico
5. + Polish → E2E, auditorias de leitura e de ausência, portão de qualidade

### Estratégia com mais de uma pessoa

Depois da Phase 2: uma pessoa em US1, outra em US2 (independentes de verdade), e US3 quando
`DisplaysTable` e `DisplayFilters` existirem.

---

## Notes

- A feature é **somente leitura**: nenhuma Server Action, nenhum verbo além de `GET` (FR-028)
- Nenhuma dependência nova e nenhum primitivo shadcn/ui novo ([R4](./research.md#r4))
- `"use client"` só em `display-filters.tsx`
- Campo ausente no backend vira `undefined`, nunca `null` nem valor de preenchimento
- Imports entre features passam pelo `index.ts` público; `surveys` não conhece `collect`
- A aba **Exibições** é composta em `app/`, não dentro de `surveys` — é o que evita o ciclo
- Commit por tarefa ou por grupo lógico; pare em qualquer checkpoint para validar a história
