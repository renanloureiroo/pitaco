---

description: "Task list — Painel de autoria avançada"
---

# Tasks: Painel de autoria avançada — modelos, condição, reordenação, pré-visualização e exposição

**Input**: documentos de design em `/specs/003-painel-autoria-avancada/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/backend-api.md)

**Tests**: **incluídos e obrigatórios** — Princípio IV (Vitest, não negociável) e Princípio V
(Playwright nos fluxos críticos; SC-007 nomeia os fluxos). Dentro de cada história, os testes vêm
antes da implementação.

**Organization**: tarefas agrupadas por história de usuário, para que cada uma seja
implementável e testável de forma independente.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: pode rodar em paralelo (arquivos diferentes, sem dependência pendente)
- **[Story]**: a que história a tarefa pertence (US1 … US8)
- Caminhos relativos à raiz do painel

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dependências novas e a configuração de build que a pré-visualização exige

- [X] T001 Acrescentar `@dnd-kit/core`, `@dnd-kit/sortable`, `@dnd-kit/utilities`, `@pitaco/react-native` (`file:../sdk`), `react-native` e `react-native-web` em `package.json`, com a justificativa registrada em Complexity Tracking
- [X] T002 Configurar `next.config.ts` com `transpilePackages: ["@pitaco/react-native"]` e o alias `react-native → react-native-web` (Turbopack e webpack), com as extensões `.web.*` resolvidas primeiro ([R4](./research.md#r4))
- [X] T003 [P] Registrar `authoringRoutes` e `exposureRoutes` em `e2e/stub-api/routes/index.ts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: a pesquisa passa a carregar os campos novos, e o simulador sabe semeá-los

**⚠️ CRITICAL**: nenhuma história começa antes desta fase

- [X] T004 Estender `surveySchema` e `surveyDetailSchema` em `src/features/surveys/schemas/survey.ts` com `templateKind`, `priority` (padrão 0), `responseQuota`, `ignoresQuietPeriod` (padrão falso) e `freeTextNotice`, todos via `absent()`/`default()` conforme [data-model](./data-model.md)
- [X] T005 [P] Testar a leitura dos campos novos em `src/features/surveys/__tests__/survey-template-schema.test.ts` (modelo presente e ausente) e `src/features/surveys/__tests__/exposure-schema.test.ts` (pesquisa sem campos de exposição assume o padrão)
- [X] T006 [P] Estender `e2e/stub-api/routes/surveys.ts` e `e2e/stub-api/store.ts` para aceitar modelo, condição, rótulos dos extremos, exposição e aviso de texto livre, e devolvê-los na leitura
- [X] T007 [P] Criar as rotas de semeadura `/stub/observed-events` e `/stub/observed-attributes` em `e2e/stub-api/routes/seed.ts` e os auxiliares `seedObservedEvents`, `seedObservedAttributes` e `questionKeysOf` em `e2e/support/helpers.ts`

**Checkpoint**: a leitura da pesquisa conhece tudo que as histórias exibem

---

## Phase 3: User Story 1 - Criar a pesquisa a partir de um modelo (Priority: P1) 🎯 MVP

**Goal**: a criação oferece Em branco, NPS, CSAT e CES, e o modelo escolhido vai ao backend.

**Independent Test**: criar pelo modelo NPS e conferir a pergunta de 0 a 10 com rótulos.

### Tests for User Story 1

- [X] T008 [P] [US1] Testar `surveyCreateFormSchema` em `src/features/surveys/__tests__/survey-template-schema.test.ts`: em branco não envia modelo; aceita os três modelos e recusa o desconhecido
- [X] T009 [P] [US1] Testar `createSurveyAction` com modelo em `src/features/surveys/__tests__/advanced-authoring-actions.test.ts`: envia o modelo escolhido; em branco envia só o nome

### Implementation for User Story 1

- [X] T010 [P] [US1] Acrescentar `surveyTemplateSchema`, `SURVEY_TEMPLATES` e `surveyCreateFormSchema` em `src/features/surveys/schemas/survey.ts`
- [X] T011 [P] [US1] Criar `src/features/surveys/lib/survey-templates.ts` com `SURVEY_TEMPLATE_OPTIONS` (rótulo e descrição de uma linha) e `SURVEY_TEMPLATE_LABELS`
- [X] T012 [US1] Aceitar `template` opcional em `createSurvey` em `src/features/surveys/api/surveys.ts`
- [X] T013 [US1] Ler o modelo em `createSurveyAction` em `src/features/surveys/actions.ts`
- [X] T014 [US1] Acrescentar o grupo "Começar de" (`role="radiogroup"`, `template-option-*`, campo oculto `template`) em `src/features/surveys/components/survey/survey-form.tsx` e compor em `src/app/aplicacoes/[applicationId]/pesquisas/nova/page.tsx`
- [X] T015 [US1] Cobrir em `e2e/autoria-avancada.spec.ts` a criação pelo modelo NPS: uma pergunta, "0 a 10" e "Nada provável … Extremamente provável"

**Checkpoint**: NPS, CSAT e CES em uma interação

---

## Phase 4: User Story 2 - Exibir uma pergunta só quando a resposta anterior pede (Priority: P1)

**Goal**: condição de exibição montada no formulário, validada na forma, com erro do backend no campo e resumo legível na lista.

**Independent Test**: condicionar "O que faltou?" a "Não" e ver o resumo; tentar a faixa 0–11 sobre NPS e ver o erro no campo.

### Tests for User Story 2

- [X] T016 [P] [US2] Testar `conditionSources`, `operatorsFor`, `scaleOf` e `describeCondition` em `src/features/surveys/__tests__/condition.test.ts`: só anteriores, sem texto livre e sem a própria; faixa só nas escalas; NPS 0–10; rótulo da opção; "incluir" em múltipla escolha; origem que sumiu
- [X] T017 [P] [US2] Testar a condição e os rótulos em `src/features/surveys/__tests__/question-condition-schema.test.ts`: sem origem não há condição; igual com um valor; faixa leva mínimo e máximo; cada falta apontada no campo; valor fora da escala **não** é recusado aqui; rótulos até 60 caracteres; NPS só envia faixa com rótulo
- [X] T018 [P] [US2] Testar o formulário em `src/features/surveys/__tests__/condition-components.test.tsx`: sem origem explica; ligar mostra os campos e desligar os tira do envio; ao editar a condição vem montada; escalas oferecem rótulos; `QuestionsList` mostra o resumo
- [X] T019 [P] [US2] Testar em `src/features/surveys/__tests__/advanced-authoring-actions.test.ts` que `addQuestionAction` envia a condição montada e mostra a recusa de regra junto do campo

### Implementation for User Story 2

- [X] T020 [P] [US2] Criar `src/features/surveys/schemas/condition.ts` com `conditionOperatorSchema`, `CONDITION_OPERATORS`, `conditionSchema` e `CONDITION_FIELDS`
- [X] T021 [US2] Estender `src/features/surveys/schemas/question.ts`: `condition` e `range.minLabel/maxLabel` em `questionSchema`; `readQuestionInput` com os campos repetidos da condição; transformação e `superRefine` de `questionFormSchema` conforme [data-model](./data-model.md)
- [X] T022 [P] [US2] Criar `src/features/surveys/lib/condition.ts` com `isNumericType`, `canBeConditionSource`, `conditionSources`, `operatorsFor`, `CONDITION_OPERATOR_LABELS`, `scaleOf` e `describeCondition`
- [X] T023 [US2] Criar `src/features/surveys/components/questions/condition-fields.tsx`: origem, operador, valor (opções pelo rótulo) ou faixa, campos ocultos com os nomes que a action lê, e `FieldMessage` por `CONDITION_FIELDS`
- [X] T024 [US2] Acrescentar a seção de condição (`condition-toggle`) e os rótulos dos extremos em `src/features/surveys/components/questions/question-form.tsx`, restaurando a condição enviada quando a action volta com erro ([R2](./research.md#r2))
- [X] T025 [US2] Mostrar o resumo `question-condition` em `src/features/surveys/components/questions/questions-list.tsx`
- [X] T026 [US2] Fazer `e2e/stub-api/routes/surveys.ts` recusar faixa fora da escala da origem com `400` e `field: "condition.values"`, como o backend
- [X] T027 [US2] Cobrir em `e2e/autoria-avancada.spec.ts` a condição com resumo `Exibida se P1 for "Não"` e a condição impossível com `field-error-condition.values` visível e nenhuma pergunta criada

**Checkpoint**: pergunta de motivo só para quem deu nota baixa

---

## Phase 5: User Story 3 - Reordenar arrastando e editar no lugar (Priority: P2)

**Goal**: alça de arrastar acessível por teclado, ordem pretendida até a resposta, edição no lugar da lista.

**Independent Test**: arrastar pela alça operada por teclado, recarregar e conferir; editar e conferir.

### Tests for User Story 3

- [X] T028 [P] [US3] Testar `draggedOrder` em `src/features/surveys/__tests__/question-order.test.ts`: descendo, subindo, permutação completa, nada a enviar fora da lista ou sobre si, ids desconhecidos ignorados, entrada não alterada
- [X] T029 [P] [US3] Testar `SortableQuestionsList` e `QuestionsPanel` em `src/features/surveys/__tests__/question-components.test.tsx`: uma alça por pergunta na ordem de `position`; soltar envia a permutação completa e realinha; soltar fora ou no lugar não envia; recusa volta à ordem do servidor; alça desabilitada durante a reordenação; somente leitura sem alça nem ações

### Implementation for User Story 3

- [X] T030 [P] [US3] Criar `src/features/surveys/lib/question-order.ts` com `draggedOrder`
- [X] T031 [P] [US3] Criar `src/features/surveys/components/questions/question-card.tsx`, o cartão compartilhado pela lista fixa e pela arrastável (enunciado, obrigatória, tipo, faixa e rótulos, condição, opções, alça e ações)
- [X] T032 [US3] Acrescentar `reorderQuestionsAction` em `src/features/surveys/actions.ts`, enviando a permutação completa recebida
- [X] T033 [US3] Criar `src/features/surveys/components/questions/sortable-questions-list.tsx` com `DndContext` de `id` estável, sensores de ponteiro (distância 4) e teclado, anúncios em português, `reorderOnDrop` exportada, estado pendente e `FormError`
- [X] T034 [US3] Reescrever `src/features/surveys/components/questions/questions-panel.tsx` para usar a lista arrastável e mostrar o `QuestionForm` preenchido no lugar da lista durante a edição ([R5](./research.md#r5))
- [X] T035 [US3] Cobrir em `e2e/pesquisas.spec.ts` a reordenação pela alça operada por teclado, persistida após recarregar

**Checkpoint**: reordenar é uma operação, por ponteiro ou teclado

---

## Phase 6: User Story 4 - Ver a pesquisa como o app vai mostrá-la (Priority: P2)

**Goal**: pré-visualização com o renderizador do SDK, rascunho ao vivo e lista de eventos.

**Independent Test**: interagir com a pré-visualização e conferir os eventos; editar e conferir o rascunho.

### Tests for User Story 4

- [X] T036 [US4] Cobrir em `e2e/pesquisas.spec.ts` que a pré-visualização não aparece numa pesquisa sem perguntas e aparece ao começar a adicionar (`survey-preview-section`)

### Implementation for User Story 4

- [X] T037 [US4] Criar `src/features/surveys/components/questions/survey-preview.tsx`: `PitacoPreview` por `next/dynamic` com `ssr: false`, mapeamento de `Question` para o formato de entrega, `resetKey`, tema de `next-themes`, lista de eventos (`survey-preview-events`) com Limpar ([R4](./research.md#r4))
- [X] T038 [US4] Acrescentar `onLiveUpdate` em `src/features/surveys/components/questions/question-form.tsx`, montando o rascunho a partir do `FormData` a cada `onChange` e a cada mudança de tipo, opções e condição
- [X] T039 [US4] Compor a grade lista + pré-visualização em `src/features/surveys/components/questions/questions-panel.tsx`, com o rascunho ao vivo, a ação Visualizar por pergunta e "Ver todas no preview"

**Checkpoint**: nada de publicar para ver como ficou

---

## Phase 7: User Story 5 - Duplicar, renomear e descartar pelo cabeçalho (Priority: P2)

**Goal**: duplicar para qualquer aplicação ativa, com a montagem da cópia em seguida.

**Independent Test**: duplicar para outra aplicação e conferir montagem, nome e estado da cópia.

### Tests for User Story 5

- [X] T040 [P] [US5] Testar `duplicateSurveyFormSchema` em `src/features/surveys/__tests__/survey-template-schema.test.ts`: destino exigido, nome em branco ausente, nome acima de 120 recusado
- [X] T041 [P] [US5] Testar `duplicateSurveyAction` em `src/features/surveys/__tests__/advanced-authoring-actions.test.ts`: leva à montagem da cópia sob o destino; recusa vira mensagem e não redireciona; sem destino recusa antes da API

### Implementation for User Story 5

- [X] T042 [P] [US5] Acrescentar `duplicateSurveyFormSchema` em `src/features/surveys/schemas/survey.ts` e `duplicateSurvey` em `src/features/surveys/api/surveys.ts`
- [X] T043 [US5] Acrescentar `duplicateSurveyAction` em `src/features/surveys/actions.ts`, revalidando a listagem do destino e redirecionando para a montagem da cópia
- [X] T044 [US5] Criar `src/features/surveys/components/survey/duplicate-survey-dialog.tsx` (destino com "(esta)", nome opcional, destino restaurado após recusa)
- [X] T045 [US5] Ler as aplicações ativas em paralelo e compor Duplicar em `src/features/surveys/components/survey/survey-header.tsx`, ocultando Renomear na encerrada
- [X] T046 [P] [US5] Criar a rota de duplicação em `e2e/stub-api/routes/authoring.ts`
- [X] T047 [US5] Cobrir em `e2e/autoria-avancada.spec.ts` a duplicação para outra aplicação: URL do destino, "Cópia de …", Rascunho e a mesma pergunta

**Checkpoint**: a pesquisa que deu certo roda em outro app sem ser refeita

---

## Phase 8: User Story 6 - Controlar a exposição e o aviso de texto livre (Priority: P2)

**Goal**: prioridade, cota com progresso, isenção do descanso e aviso de texto livre na tela de disparo.

**Independent Test**: salvar 7 / 50 / isenção, recarregar, limpar a cota; personalizar e desligar o aviso.

### Tests for User Story 6

- [X] T048 [P] [US6] Testar `exposureFormSchema` em `src/features/surveys/__tests__/exposure-schema.test.ts`: prioridade em branco é zero, cota em branco é ausência, números e isenção convertidos, progresso sem cota é ausente
- [X] T049 [P] [US6] Testar `updateSurveyExposure` (cota em branco como `null` explícito) e `getQuotaProgress` em `src/features/surveys/__tests__/exposure-api.test.ts`
- [X] T050 [P] [US6] Testar `updateExposureAction` em `src/features/surveys/__tests__/exposure-actions.test.ts`: prioridade fora da faixa sem chamar a API; grava e realinha; recusa sem realinhar
- [X] T051 [P] [US6] Testar `ExposurePanel` em `src/features/surveys/__tests__/exposure-components.test.tsx`: progresso e desempate; "Sem cota" e sem intervalo em vez de zero; explicação ao ligar a isenção; somente leitura sem formulário
- [X] T052 [P] [US6] Testar o aviso em `src/features/surveys/__tests__/free-text-notice.test.tsx`: texto em branco vira ausente; desligar chega como `false`; acima de 200 recusado; texto exibido; sem texto livre explica; encerrada sem edição

### Implementation for User Story 6

- [X] T053 [P] [US6] Criar `src/features/surveys/schemas/exposure.ts` (`PRIORITY_MIN`, `PRIORITY_MAX`, `quotaProgressSchema`, `exposureFormSchema`) e `src/features/surveys/schemas/notice.ts` (`FREE_TEXT_NOTICE_MAX`, `freeTextNoticeFormSchema`)
- [X] T054 [US6] Acrescentar `updateSurveyExposure`, `updateFreeTextNotice` e `getQuotaProgress` em `src/features/surveys/api/surveys.ts`, com `null` explícito para remover ([R6](./research.md#r6))
- [X] T055 [US6] Acrescentar `updateExposureAction` e `updateFreeTextNoticeAction` em `src/features/surveys/actions.ts`, terminando em `refresh()`
- [X] T056 [P] [US6] Criar `src/features/surveys/components/trigger/exposure-panel.tsx` (`describeQuota`, `describeQuietPeriod`, `TIEBREAK_RULE`) e `src/features/surveys/components/trigger/exposure-form.tsx` (isenção por campo oculto `true`/`false`)
- [X] T057 [P] [US6] Criar `src/features/surveys/components/trigger/free-text-notice-panel.tsx` e `src/features/surveys/components/trigger/free-text-notice-form.tsx`
- [X] T058 [US6] Compor exposição e aviso em `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/disparo/page.tsx`, lendo `getApplication` (descanso) e `getQuotaProgress` em paralelo e passando `readOnly` só na encerrada
- [X] T059 [P] [US6] Criar a rota `quota-progress` em `e2e/stub-api/routes/exposure.ts` e o encerramento por cota na semeadura de coleta de `e2e/stub-api/routes/seed.ts`
- [X] T060 [US6] Cobrir em `e2e/disparo.spec.ts` exposição salva, recarregada e limpa; em `e2e/ciclo-de-vida.spec.ts` o encerramento por cota com "Encerrada por cota"; em `e2e/privacidade.spec.ts` o aviso personalizado e desligado

**Checkpoint**: quem pesquisa decide quanto a pesquisa disputa a atenção

---

## Phase 9: User Story 7 - Escolher evento e atributo entre os que o app já enviou (Priority: P3)

**Goal**: sugestões do catálogo observado, sem restringir a digitação.

**Independent Test**: com catálogo semeado, preencher evento, atributo e valor por sugestão.

### Tests for User Story 7

- [X] T061 [P] [US7] Testar `listObservedAttributes` em `src/features/applications/__tests__/observed-attributes.test.ts` (caminho, página e valores embutidos)
- [X] T062 [P] [US7] Testar `RuleForm` com sugestões em `src/features/surveys/__tests__/exposure-components.test.tsx`: preenche atributo e valor; sem catálogo explica e aceita digitação

### Implementation for User Story 7

- [X] T063 [P] [US7] Acrescentar `observedEventSchema` e `observedAttributeSchema` em `src/features/applications/schemas/application.ts`, `listObservedEvents` e `listObservedAttributes` em `src/features/applications/api/index.ts`, e exportá-los em `src/features/applications/index.ts`
- [X] T064 [US7] Acrescentar sugestões de evento em `src/features/surveys/components/trigger/trigger-form.tsx` e de atributo e valor em `src/features/surveys/components/trigger/rule-form.tsx` (exportando `ObservedAttributeSuggestion`), repassadas por `src/features/surveys/components/trigger/trigger-panel.tsx`
- [X] T065 [US7] Ler os dois catálogos com `MAX_SIZE` na página de disparo `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/disparo/page.tsx` e montar as sugestões ([R7](./research.md#r7))
- [X] T066 [P] [US7] Criar a rota `GET /applications/:applicationId/attributes` em `e2e/stub-api/routes/exposure.ts` e `GET …/events` em `e2e/stub-api/routes/applications.ts`
- [X] T067 [US7] Cobrir em `e2e/disparo.spec.ts` as sugestões de evento (com digitação livre mantida) e de atributo e valor (regra `plano é igual a "pro"`)

**Checkpoint**: o nome do evento deixa de ser digitado de memória

---

## Phase 10: User Story 8 - Publicar sabendo o que muda (Priority: P2)

**Goal**: avisos que não bloqueiam, natureza da mudança que diz a consequência, versões legíveis, rascunho direto na montagem.

**Independent Test**: publicar v1, abrir rascunho, mudar, publicar como semântica e conferir Grupo 2; ver aviso de concorrência com Publicar habilitado.

### Tests for User Story 8

- [X] T068 [P] [US8] Testar `getPublicationWarnings` em `src/features/surveys/__tests__/exposure-api.test.ts` e o aviso sem concorrentes com lista vazia em `src/features/surveys/__tests__/exposure-schema.test.ts`
- [X] T069 [P] [US8] Testar `WarningsList` em `src/features/surveys/__tests__/exposure-components.test.tsx`: concorrentes com prioridade e desempate; regra sem alcance; sem aviso nada renderiza
- [X] T070 [P] [US8] Testar o aviso de compatibilidade em `src/features/surveys/__tests__/compatibility-warning.test.ts`: versão mínima e proporção na mensagem; avisos antigos sem os campos novos
- [X] T071 [P] [US8] Testar em `src/features/surveys/__tests__/publication-actions.test.ts` que abrir rascunho revalida o segmento e leva à montagem, e que o resumo digitado é preservado na recusa
- [X] T072 [P] [US8] Testar em `src/features/surveys/__tests__/publication-components.test.tsx` que `VersionsTable` mostra a natureza e que a linha de rascunho abre a montagem

### Implementation for User Story 8

- [X] T073 [P] [US8] Criar `src/features/surveys/schemas/warnings.ts` (código aberto, `competingSurveys` nulo vira lista vazia) e `src/features/surveys/lib/warning-messages.ts` (`TIEBREAK_RULE`, `warningMessage`) ([R8](./research.md#r8))
- [X] T074 [US8] Acrescentar `getPublicationWarnings` em `src/features/surveys/api/publication.ts`
- [X] T075 [US8] Criar `src/features/surveys/components/publication/warnings-list.tsx` com vínculo para o disparo de cada concorrente e a regra de desempate
- [X] T076 [US8] Compor os avisos em `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/publicacao/page.tsx` (com "Nenhum aviso impede a publicação") e em `src/app/aplicacoes/[applicationId]/pesquisas/[surveyId]/disparo/page.tsx`
- [X] T077 [US8] Reescrever `CHANGE_KIND_LABELS` em `src/features/surveys/schemas/publication.ts` com a consequência de cada natureza, usados por `src/features/surveys/components/publication/publish-form.tsx`
- [X] T078 [US8] Fazer `openDraftVersionAction` em `src/features/surveys/actions.ts` redirecionar para a montagem, e `src/features/surveys/components/versions/versions-table.tsx` mostrar natureza e grupo
- [X] T079 [P] [US8] Criar a rota `publication-warnings` em `e2e/stub-api/routes/exposure.ts` (concorrência, regra sem alcance, compatibilidade)
- [X] T080 [US8] Cobrir em `e2e/publicacao.spec.ts` a natureza exigida na v2 com Grupo 2, o aviso de concorrência e o de regra sem alcance sem bloquear; em `e2e/autoria-avancada.spec.ts` o aviso de comparabilidade nos resultados consolidados

**Checkpoint**: as oito histórias funcionam de forma independente

---

## Phase 11: Polish & Cross-Cutting Concerns

- [X] T081 Exportar pela fronteira pública `src/features/surveys/index.ts` as ações, tipos, constantes e componentes novos (`DuplicateSurveyDialog`, `ExposurePanel`, `FreeTextNoticePanel`, `WarningsList`, `SURVEY_TEMPLATE_*`, `describeCondition`, `CONDITION_OPERATORS`, `PRIORITY_*`, `WARNING_CODES`, `TIEBREAK_RULE`, `warningMessage`)
- [X] T082 Conferir a acessibilidade da alça de arrastar (rótulo, instruções e anúncios) e do `radiogroup` de modelos com leitor de tela
- [X] T083 Conferir que nenhuma tela desta feature exibe cota ausente como zero (SC-005)
- [X] T084 Rodar o roteiro manual de [quickstart.md](./quickstart.md) contra a API real
- [X] T085 Rodar `npm run verify` — a constituição não admite suíte vermelha no merge

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependência
- **Foundational (Phase 2)**: depende da Phase 1 — bloqueia todas as histórias
- **Histórias (Phases 3–10)**: dependem da Phase 2; entre si são independentes, com as ressalvas
  abaixo
- **Polish (Phase 11)**: depende das histórias

### User Story Dependencies

- **US1, US2, US5, US6, US7, US8**: independentes entre si
- **US3** reescreve `questions-panel.tsx`, que **US4** estende com a pré-visualização: T039
  espera T034
- **US2** e **US4** tocam `question-form.tsx` (condição; `onLiveUpdate`): T038 espera T024
- **US6** e **US7** compõem a mesma página de disparo: T058 e T065 em sequência
- **US8** também compõe a página de disparo (avisos): T076 depois de T058 e T065

### Parallel Opportunities

- T005, T006, T007 em paralelo
- Testes de cada história, marcados [P], em paralelo entre si
- US1, US2, US5 e US8 podem correr em paralelo depois da Phase 2

---

## Implementation Strategy

### MVP primeiro (US1 + US2)

1. Phase 1 e Phase 2
2. US1 — modelo
3. US2 — condição
4. **PARE E VALIDE**: uma pesquisa de NPS com pergunta de motivo condicionada à nota baixa

### Entrega incremental

1. + US3 → reordenar e editar no lugar
2. + US4 → pré-visualização
3. + US5 → duplicar
4. + US6, US7 → disparo completo
5. + US8 → publicação consciente
6. + Polish → acessibilidade, ausências, portão

---

## Notes

- Nenhuma rota nova; nenhum primitivo shadcn/ui novo
- `"use client"` em: `sortable-questions-list.tsx`, `questions-panel.tsx`, `question-form.tsx`,
  `condition-fields.tsx`, `survey-preview.tsx`, `duplicate-survey-dialog.tsx`, `exposure-form.tsx`,
  `free-text-notice-form.tsx`, `survey-form.tsx`, `move-question-buttons.tsx`
- A pré-visualização não tem teste Vitest próprio: o renderizador é do SDK, testado lá; aqui ela é
  coberta pelo E2E e pela revisão do mapeamento em `survey-preview.tsx`
- Validação de forma no painel, invariante no backend — em condição, exposição e publicação
