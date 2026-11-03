---

description: "Task list — Painel de saúde do SDK e privacidade"
---

# Tasks: Painel de saúde do SDK e privacidade — versões, erros, supressões, retenção e exclusão de respondente

**Input**: documentos de design em `/specs/005-painel-saude-privacidade/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/backend-api.md)

**Tests**: **incluídos e obrigatórios** (Princípios IV e V). Exclusão de respondente é fluxo
crítico de conformidade. Testes antes da implementação em cada história.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo
- **[Story]**: US1 … US6
- Caminhos relativos à raiz do painel

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Criar as árvores `src/features/health/` e `src/features/privacy/` com `api/`, `schemas/`, `components/`, `lib/` e `__tests__/`
- [X] T002 [P] Criar `src/features/health/api/paths.ts` (`sdkVersionsPath`, `sdkErrorsPath`, `surveyHealthPath`) e `src/features/privacy/api/paths.ts` (`respondentsPath`, `deletionAuditsPath`, `retentionPreviewPath`)
- [X] T003 [P] Registrar `healthRoutes` e `privacyRoutes` em `e2e/stub-api/routes/index.ts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: nenhuma história começa antes desta fase

- [X] T004 [P] Testar os schemas de saúde em `src/features/health/__tests__/health-schemas.test.ts`: fatia ausente sem consulta recente; tipo desconhecido vira `unknown` e contexto ausente vira vazio; saúde sem publicação sem versão mínima nem evento
- [X] T005 [P] Testar os schemas de privacidade em `src/features/privacy/__tests__/privacy-schemas.test.ts`: prazo ausente como sem prazo, nunca zero
- [X] T006 [P] Testar as leituras de saúde em `src/features/health/__tests__/health-api.test.ts`: versões, erros com página e filtros só quando presentes, saúde da pesquisa
- [X] T007 [P] Testar as chamadas de privacidade em `src/features/privacy/__tests__/privacy-api.test.ts`: exclusão por referência e por dispositivo com `DELETE` e a identidade na query; registro paginado; prévia
- [X] T008 [P] Criar `src/features/health/schemas/health.ts` (`SDK_ERROR_KINDS`, `SUPPRESSION_REASONS`, `sdkVersionUsageSchema`, `sdkVersionsSchema`, `sdkErrorReportSchema` com `.catch`, `surveyHealthSchema`)
- [X] T009 [P] Criar `src/features/privacy/schemas/privacy.ts` (`respondentErasureSchema`, `deletionAuditSchema`, `retentionForecastSchema`, `retentionPreviewSchema`) e `src/features/privacy/schemas/forms.ts` (`IDENTITY_KINDS`, `IDENTITY_MAX`, `eraseRespondentFormSchema`)
- [X] T010 Criar `src/features/health/api/health.ts` (`getSdkVersions`, `listSdkErrors`, `getSurveyHealth`) e `src/features/privacy/api/privacy.ts` (`eraseRespondent`, `listDeletionAudits`, `getRetentionPreview`)
- [X] T011 Criar as fronteiras `src/features/health/index.ts` e `src/features/privacy/index.ts`
- [X] T012 [P] Criar `e2e/stub-api/routes/health.ts` com versões, erros, saúde da pesquisa e as semeaduras `/stub/sdk-versions`, `/stub/sdk-errors`, `/stub/suppressions`
- [X] T013 [P] Criar `e2e/stub-api/routes/privacy.ts` com exclusão, registro, prévia e `/stub/retention-snapshots`, e os auxiliares `seedSdkVersions`, `seedSdkErrors`, `seedSuppressions`, `seedRetentionSnapshot` e `setApplicationRetention` em `e2e/support/helpers.ts`

**Checkpoint**: leituras e escrita tipadas, simulador pronto

---

## Phase 3: User Story 1 - Ver as versões do SDK em uso (Priority: P1) 🎯 MVP

**Goal**: tabela de versões com fatia recente e a marca de versão fora do tráfego.

**Independent Test**: três versões semeadas; conferir ordem, 75% e a marca.

### Tests for User Story 1

- [X] T014 [P] [US1] Testar em `src/features/health/__tests__/health-components.test.tsx` que a tabela mostra a fatia recente e marca só a versão que sumiu do tráfego

### Implementation for User Story 1

- [X] T015 [P] [US1] Criar `src/features/health/lib/health-labels.ts` com `formatShare`, `formatCount`, `SDK_ERROR_KIND_LABELS`, `STALE_NOTE` e `SDK_ERRORS_NOTE`
- [X] T016 [P] [US1] Criar `src/features/health/components/sdk-versions-table.tsx` com fatia "—" quando ausente e a marca `sdk-version-stale`
- [X] T017 [US1] Criar `src/app/aplicacoes/[applicationId]/saude/page.tsx` lendo versões e erros em paralelo, com rótulo do fuso e o vazio de versões
- [X] T018 [US1] Cobrir em `e2e/saude.spec.ts` a distribuição de versões e a marca da que sumiu do tráfego

**Checkpoint**: o lado do app aparece no painel

---

## Phase 4: User Story 2 - Investigar os erros do SDK (Priority: P1)

**Goal**: lista filtrável por tipo e versão, com contexto recolhido.

**Independent Test**: filtrar por Renderização e abrir o contexto.

### Tests for User Story 2

- [X] T019 [P] [US2] Testar em `src/features/health/__tests__/health-schemas.test.ts` a leitura de `tipo` e `versao` da URL, ignorando tipo inválido e versão vazia e usando o primeiro valor repetido
- [X] T020 [P] [US2] Testar em `src/features/health/__tests__/health-components.test.tsx`: tipo, versão e contexto, com texto próprio sem mensagem; filtrar por versão vai para a URL e volta à primeira página; limpar tira os filtros

### Implementation for User Story 2

- [X] T021 [P] [US2] Criar `src/features/health/schemas/filters.ts` (`KIND_PARAM`, `SDK_VERSION_PARAM`, `parseSdkErrorFilters`)
- [X] T022 [P] [US2] Criar `src/features/health/components/sdk-errors-list.tsx` com contexto em `details` e textos para versão e mensagem ausentes
- [X] T023 [P] [US2] Criar `src/features/health/components/sdk-error-filters.tsx` (`"use client"`) com tipo, versão, aplicar e limpar
- [X] T024 [US2] Compor filtros, lista, vazios com e sem filtro e `Pagination` em `src/app/aplicacoes/[applicationId]/saude/page.tsx`
- [X] T025 [US2] Cobrir em `e2e/saude.spec.ts` os erros listados, filtrados por tipo na URL, com contexto

**Checkpoint**: o erro do app chega antes da reclamação

---

## Phase 5: User Story 3 - Saber por que uma pesquisa não está sendo respondida (Priority: P2)

**Goal**: avisos de supressão e de evento nunca visto nos resultados.

**Independent Test**: supressões semeadas e evento ausente, conferidos nos resultados.

### Tests for User Story 3

- [X] T026 [P] [US3] Testar `surveyHealthNotices` em `src/features/health/__tests__/health-schemas.test.ts`: supressão relevante com proporção, motivo dominante e versão mínima; abaixo do limiar sem aviso; evento que nunca chegou; nunca publicada sem aviso
- [X] T027 [P] [US3] Testar em `src/features/health/__tests__/health-components.test.tsx` que cada aviso tem a sua marca e que, sem aviso, nada é desenhado

### Implementation for User Story 3

- [X] T028 [US3] Acrescentar `surveyHealthNotices` e os textos de motivo em `src/features/health/lib/health-labels.ts` ([R3](./research.md#r3))
- [X] T029 [US3] Criar `src/features/health/components/survey-health-notices.tsx` com `survey-health-suppression` e `survey-health-event-missing`
- [X] T030 [US3] Ler `getSurveyHealth` em paralelo, tolerar sua falha e omitir o vazio "sem exibição" quando houver supressão em `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados/page.tsx`
- [X] T031 [US3] Cobrir em `e2e/saude.spec.ts` a supressão relevante sem vazio e o evento nunca visto que some quando o evento chega; e o aviso de compatibilidade do SDK na publicação

**Checkpoint**: "ninguém respondeu" deixa de ser ambíguo

---

## Phase 6: User Story 4 - Conhecer e ajustar a política de retenção (Priority: P1)

**Goal**: política em linguagem de gente, aviso antes do descarte, edição dos prazos.

**Independent Test**: retenção de 30 dias com resposta de 45 dias; conferir descrição, aviso e último descarte.

### Tests for User Story 4

- [X] T032 [P] [US4] Testar em `src/features/privacy/__tests__/privacy-schemas.test.ts`: sem política não avisa; descreve os dois prazos quando o de texto é mais curto; avisa a próxima execução com data e convite; sem nada na próxima, avisa a semana seguinte; descarte desligado; nada vence; diz se já houve descarte
- [X] T033 [P] [US4] Testar em `src/features/privacy/__tests__/privacy-components.test.tsx` que o cartão mostra a política e o aviso com o caminho para exportar, e que sem política não avisa nem oferece exportar
- [X] T034 [P] [US4] Testar em `src/features/applications/__tests__/actions.test.ts` e `src/features/applications/__tests__/components.test.tsx`: `updateApplicationAction` recusa nome em branco, mostra a recusa de regra preservando valores e realinha em sucesso; o diálogo abre preenchido com prazo ausente em branco, mostra a recusa por campo e fecha quando aceita

### Implementation for User Story 4

- [X] T035 [P] [US4] Criar `src/features/privacy/lib/privacy-labels.ts` com `describeRetentionPolicy`, `retentionWarning` e `lastRunMessage` ([R6](./research.md#r6))
- [X] T036 [P] [US4] Criar `src/features/privacy/components/retention-policy-card.tsx` com "Editar prazos" (para a aplicação) e "Exportar pelos resultados das pesquisas"
- [X] T037 [P] [US4] Acrescentar `updateApplicationFormSchema` em `src/features/applications/schemas/forms.ts`, `updateApplication` (PATCH com `null` para prazo em branco) em `src/features/applications/api/index.ts` e `updateApplicationAction` em `src/features/applications/actions.ts` ([R5](./research.md#r5))
- [X] T038 [US4] Criar `src/features/applications/components/edit-application-dialog.tsx` (`"use client"`) com nome, descanso, retenção e retenção de texto livre
- [X] T039 [US4] Criar `src/app/aplicacoes/[applicationId]/privacidade/page.tsx` lendo prévia e registro em paralelo, com rótulo do fuso e o cartão da política
- [X] T040 [P] [US4] Aceitar `PATCH /applications/:applicationId` em `e2e/stub-api/routes/applications.ts`, recusando texto livre maior que a retenção geral
- [X] T041 [US4] Cobrir em `e2e/privacidade.spec.ts` a prévia com prazo de 30 dias e em `e2e/aplicacoes.spec.ts` a edição de nome e prazos e a recusa sem fechar o diálogo

**Checkpoint**: o que vai sumir aparece antes de sumir

---

## Phase 7: User Story 5 - Atender a um pedido de exclusão (Priority: P1)

**Goal**: exclusão com confirmação, resultado e registro sem identificação.

**Independent Test**: cancelar e confirmar a exclusão de um respondente semeado; pedido sem alvo.

### Tests for User Story 5

- [X] T042 [P] [US5] Testar em `src/features/privacy/__tests__/privacy-schemas.test.ts` o formulário (identificação aparada, referência como padrão, dispositivo aceito, vazia recusada) e `erasureResultMessage` (singular e plural; pedido sem alvo não é erro)
- [X] T043 [P] [US5] Testar em `src/features/privacy/__tests__/privacy-components.test.tsx`: exclusão só habilitada com identificação; confirmação diz que é irreversível e cancelar não apaga; confirmar envia e mostra quanto saiu; registro mostra quando e quanto; sem exclusões diz isso

### Implementation for User Story 5

- [X] T044 [US5] Criar `eraseRespondentAction` em `src/features/privacy/actions.ts`, terminando em `refresh()` e devolvendo o resultado no estado ([R4](./research.md#r4))
- [X] T045 [US5] Acrescentar `ERASURE_TITLE`, `erasureDescription`, `AUDIT_NOTE` e `erasureResultMessage` em `src/features/privacy/lib/privacy-labels.ts`
- [X] T046 [P] [US5] Criar `src/features/privacy/components/erase-respondent-form.tsx` (`"use client"`) sobre o `ConfirmDialog` compartilhado
- [X] T047 [P] [US5] Criar `src/features/privacy/components/deletion-audits-list.tsx` com quando, exibições e respostas — sem identificação
- [X] T048 [US5] Compor exclusão, registro e `Pagination` em `src/app/aplicacoes/[applicationId]/privacidade/page.tsx`
- [X] T049 [US5] Cobrir em `e2e/privacidade.spec.ts` cancelar, confirmar (registro sem a referência e respondente fora da listagem) e o pedido sem alvo

**Checkpoint**: o pedido do titular é atendido pela tela, com prova

---

## Phase 8: User Story 6 - Ler resultados sabendo o que a retenção apagou (Priority: P2)

**Goal**: nota de retenção com os dois casos.

**Independent Test**: agregado semeado; conferir com e sem recorte de período.

### Tests for User Story 6

- [X] T050 [P] [US6] Testar em `src/features/results/__tests__/retention-note.test.tsx`: com o agregado, diz que inclui; com recorte, diz que ficou de fora; sem retenção, sem campo

### Implementation for User Story 6

- [X] T051 [US6] Acrescentar `retentionNoteText` em `src/features/results/lib/results-labels.ts` e criar `src/features/results/components/retention-note.tsx`, exportados por `src/features/results/index.ts` ([R7](./research.md#r7))
- [X] T052 [US6] Compor `RetentionNote` após a taxa de resposta em `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/resultados/page.tsx`
- [X] T053 [US6] Cobrir em `e2e/privacidade.spec.ts` a nota com o agregado e com `?periodo=7`

**Checkpoint**: as seis histórias funcionam de forma independente

---

## Phase 9: Polish & Cross-Cutting Concerns

- [X] T054 Procurar em todas as telas desta feature qualquer exibição da identificação de respondente excluído (SC-003)
- [X] T055 Revisar ausências contra a regra "nunca zero": fatia recente, prazos, versão e mensagem de erro
- [X] T056 Rodar o roteiro manual de [quickstart.md](./quickstart.md) contra a API real
- [X] T057 Rodar `npm run verify`

---

## Dependencies & Execution Order

- **Setup → Foundational → histórias → Polish**
- **US1** e **US2** compartilham `saude/page.tsx`: T024 depois de T017
- **US4** e **US5** compartilham `privacidade/page.tsx`: T048 depois de T039
- **US3** e **US6** alteram `resultados/page.tsx` de `004-painel-resultados-comportamento`: uma de
  cada vez nesse arquivo
- Fora isso, as histórias são independentes

## Implementation Strategy

1. Setup + Foundational
2. US1 + US2 → aba Saúde
3. US4 + US5 → aba Privacidade (**PARE E VALIDE** a exclusão)
4. US3 + US6 → avisos nos resultados
5. Polish

## Notes

- Uma única Server Action nesta feature (exclusão), mais a edição da aplicação em `applications`
- `"use client"` em: `sdk-error-filters.tsx`, `erase-respondent-form.tsx`,
  `edit-application-dialog.tsx`
- `performedBy` é lido mas não exibido
