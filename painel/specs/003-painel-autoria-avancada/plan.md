# Implementation Plan: Painel de autoria avançada — modelos, condição, reordenação, pré-visualização e exposição

**Branch**: `003-painel-autoria-avancada` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-painel-autoria-avancada/spec.md`

## Summary

A autoria do painel alcança o que o backend passou a oferecer: modelo de origem na criação,
condição de exibição por pergunta, reordenação por arrastar e soltar, edição no lugar,
pré-visualização com o renderizador real do SDK, duplicação para outra aplicação, exposição
(prioridade, cota com progresso, isenção do descanso), aviso de texto livre, catálogo observado
para sugerir evento e atributo, avisos de publicação e classificação da mudança.

Nada disso cria feature nova. Tudo é pesquisa — mesmo agregado, mesmo identificador, mesmo
estado — e por isso cresce dentro de `src/features/surveys/`, seguindo R15 de 001. A única
leitura de fora é o catálogo observado, que pertence à aplicação e vive em
`src/features/applications/`; a página de disparo compõe as duas features em `app/`.

Três decisões carregam o desenho: a condição é validada só na forma, e o erro do backend volta
para o campo pelo nome que ele aponta ([R2](./research.md#r2)); a pré-visualização é o
`PitacoPreview` do SDK, carregado por `next/dynamic` com `ssr: false` sobre react-native-web
([R4](./research.md#r4)); e reordenar por arrastar usa dnd-kit, com a ordem pretendida exibida
até a resposta e a permutação completa enviada pela mesma ação das setas ([R3](./research.md#r3)).

## Technical Context

**Language/Version**: TypeScript 5 em modo estrito, React 19.2, Node 20+

**Primary Dependencies**: Next.js 16.3 (App Router), Zod 4, Tailwind CSS 4, shadcn/ui sobre Radix.
**Novas dependências de runtime**: `@dnd-kit/core`, `@dnd-kit/sortable`, `@dnd-kit/utilities`
([R3](./research.md#r3)); `@pitaco/react-native` (pacote local `file:../sdk`), `react-native` e
`react-native-web` ([R4](./research.md#r4)). Todas registradas em Complexity Tracking.

**Storage**: N/A — o painel não persiste nada; estado de tela na URL ou em estado de componente

**Testing**: Vitest 4 + Testing Library (unidade e integração), Playwright 1.63 (E2E) contra o
servidor de API simulado em `e2e/stub-api/`

**Target Platform**: navegadores modernos; aplicação renderizada no servidor (Node), com a
pré-visualização renderizada só no navegador

**Project Type**: aplicação web (Next.js App Router) consumindo a API administrativa do Pitaco

**Performance Goals**: nenhuma leitura cacheada entre requisições; leituras de uma mesma tela em
paralelo (a tela de disparo faz seis); `React.cache` só para deduplicar `getSurvey` entre layout
e página

**Constraints**:
- O navegador nunca fala com o backend: escrita por Server Action, leitura por Server Component
- `"use client"` apenas onde há estado de tela: lista arrastável, formulários, diálogo de
  duplicação, pré-visualização
- Nenhuma regra de domínio replicada: a validação de condição, exposição e publicação é de forma
- Exposição e aviso de texto livre não abrem versão

**Scale/Scope**: nenhuma rota nova; 4 rotas alteradas (nova pesquisa, montagem, disparo,
publicação), 7 Server Actions novas ou estendidas, 5 leituras novas consumidas

## Constitution Check

*GATE: verificado antes da Phase 0 e reavaliado após a Phase 1.*

| Princípio | Como esta feature adere | Veredito |
|---|---|---|
| **I. Arquitetura Feature-Based** | Tudo cresce em `src/features/surveys/` (schemas `condition`, `exposure`, `notice`, `warnings`; libs `condition`, `question-order`, `survey-templates`, `warning-messages`; componentes novos em `questions/`, `survey/`, `trigger/`, `publication/`). O catálogo observado é de `applications` e sai por `index.ts` (`listObservedEvents`, `listObservedAttributes`); `surveys` só exporta o tipo `ObservedAttributeSuggestion`, e a página em `app/` faz a ponte. Sem ciclo. | ✅ |
| **II. shadcn/ui** | Nenhum primitivo novo. Alça de arrastar é `Button`; diálogo de duplicação é `Dialog`; opções de modelo são `radiogroup` sobre `button`; sugestões do catálogo são `Button` pequenos. dnd-kit não é biblioteca de componentes visuais — ele só fornece sensores e transformações ([R3](./research.md#r3)). | ✅ |
| **III. Tailwind** | Utilitários e tokens. Um valor arbitrário na altura da moldura da pré-visualização (`h-[460px]`), necessário para o renderizador de altura flexível ter onde se apoiar; e `style` inline só para a transformação do item arrastado, valor dinâmico de execução. | ✅ |
| **IV. Vitest (não negociável)** | Regras não óbvias com teste próprio: `draggedOrder` e `reorderOnDrop` (sem simular ponteiro), `conditionSources`, `operatorsFor`, `describeCondition`, forma da condição e dos rótulos em `questionFormSchema`, `exposureFormSchema`, `freeTextNoticeFormSchema`, `warningMessage`, `impedimentMessage`, `publishSurveyFormSchema`, ações de modelo, duplicação e exposição. | ✅ |
| **V. Playwright nos fluxos críticos** | `e2e/autoria-avancada.spec.ts` (modelo, duplicação, condição, recusa de condição, aviso de comparabilidade), `e2e/disparo.spec.ts` (catálogo, exposição), `e2e/publicacao.spec.ts` (natureza da mudança, avisos), `e2e/ciclo-de-vida.spec.ts` (encerramento por cota), `e2e/pesquisas.spec.ts` (arrastar por teclado). | ✅ |
| **Server first** | Leitura em Server Components (`page.tsx` de montagem, disparo, publicação; `SurveyHeader` sob `<Suspense>`). Cliente só no que tem estado de tela. | ✅ |
| **Validação de fronteira** | Todo corpo novo passa por Zod (`surveyDetailSchema` com `templateKind`, `priority`, `responseQuota`, `ignoresQuietPeriod`, `freeTextNotice`; `quotaProgressSchema`; `publicationWarningSchema` com código aberto; `conditionSchema`). | ✅ |
| **Nova dependência justificada** | dnd-kit e a pilha do SDK web, justificadas em [R3](./research.md#r3) e [R4](./research.md#r4). | ✅ (ver Complexity Tracking) |

**Resultado do portão (pré-Phase 0)**: aprovado, com duas dependências novas a justificar.

**Resultado do portão (pós-Phase 1)**: aprovado. Nenhuma abstração especulativa: `QuestionCard`
é compartilhado pela lista fixa (somente leitura) e pela arrastável, dois usos concretos.

## Project Structure

### Documentation (this feature)

```text
specs/003-painel-autoria-avancada/
├── plan.md              # Este arquivo
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── backend-api.md   # Rotas do backend consumidas
│   └── ui-routes.md     # Rotas, estados de tela e testids
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md
```

### Source Code (repository root)

```text
src/
├── app/aplicacoes/[applicationId]/pesquisas/
│   ├── nova/page.tsx                          # ALTERADO: SurveyForm com modelos
│   └── [surveyId]/
│       ├── page.tsx                           # ALTERADO: QuestionsPanel com pré-visualização
│       ├── disparo/page.tsx                   # ALTERADO: catálogo, avisos, exposição, aviso de texto livre
│       └── publicacao/page.tsx                # ALTERADO: avisos de publicação
│
├── features/applications/
│   ├── api/index.ts                           # ALTERADO: listObservedEvents, listObservedAttributes
│   ├── schemas/application.ts                 # ALTERADO: observedEventSchema, observedAttributeSchema
│   └── index.ts                               # ALTERADO: exporta as leituras do catálogo
│
└── features/surveys/
    ├── index.ts                               # ALTERADO: novas ações, tipos e componentes
    ├── actions.ts                             # ALTERADO: modelo, duplicação, exposição, aviso, reordenar por arrastar
    ├── api/
    │   ├── surveys.ts                         # ALTERADO: createSurvey(template), duplicateSurvey, updateSurveyExposure, updateFreeTextNotice, getQuotaProgress
    │   ├── publication.ts                     # ALTERADO: getPublicationWarnings
    │   └── questions.ts                       # movedOrder (setas) — inalterado
    ├── schemas/
    │   ├── survey.ts                          # ALTERADO: templateKind, exposição, freeTextNotice, formulários de criação e duplicação
    │   ├── question.ts                        # ALTERADO: condição, rótulos dos extremos, readQuestionInput
    │   ├── condition.ts                       # NOVO
    │   ├── exposure.ts                        # NOVO
    │   ├── notice.ts                          # NOVO
    │   ├── warnings.ts                        # NOVO
    │   └── publication.ts                     # changeKind (de 001) — rótulos revistos
    ├── lib/
    │   ├── condition.ts                       # NOVO: origens, operadores, escala, resumo
    │   ├── question-order.ts                  # NOVO: draggedOrder
    │   ├── survey-templates.ts                # NOVO: opções e rótulos de modelo
    │   └── warning-messages.ts                # NOVO: TIEBREAK_RULE, warningMessage
    ├── components/
    │   ├── questions/
    │   │   ├── questions-panel.tsx            # ALTERADO: grade lista + pré-visualização, edição no lugar
    │   │   ├── sortable-questions-list.tsx    # NOVO: dnd-kit, reorderOnDrop
    │   │   ├── question-card.tsx              # NOVO: cartão compartilhado
    │   │   ├── question-form.tsx              # ALTERADO: condição, rótulos, onLiveUpdate
    │   │   ├── condition-fields.tsx           # NOVO
    │   │   ├── survey-preview.tsx             # NOVO: PitacoPreview + eventos
    │   │   ├── questions-list.tsx             # ALTERADO: resumo da condição
    │   │   └── move-question-buttons.tsx      # inalterado (setas)
    │   ├── survey/
    │   │   ├── survey-form.tsx                # ALTERADO: radiogroup de modelos
    │   │   ├── survey-header.tsx              # ALTERADO: Duplicar; renomear oculto na encerrada
    │   │   └── duplicate-survey-dialog.tsx    # NOVO
    │   ├── trigger/
    │   │   ├── trigger-form.tsx               # ALTERADO: sugestões de evento
    │   │   ├── rule-form.tsx                  # ALTERADO: sugestões de atributo e valor
    │   │   ├── trigger-panel.tsx              # ALTERADO: repassa o catálogo
    │   │   ├── exposure-panel.tsx             # NOVO
    │   │   ├── exposure-form.tsx              # NOVO
    │   │   ├── free-text-notice-panel.tsx     # NOVO
    │   │   └── free-text-notice-form.tsx      # NOVO
    │   ├── publication/
    │   │   ├── warnings-list.tsx              # NOVO
    │   │   └── publish-form.tsx               # ALTERADO: rótulos da natureza dizem a consequência
    │   └── versions/
    │       └── versions-table.tsx             # ALTERADO: natureza e grupo; rascunho abre a montagem
    └── __tests__/                             # ver tasks.md

next.config.ts                                 # ALTERADO: transpilePackages + alias react-native → react-native-web
package.json                                   # ALTERADO: dnd-kit, SDK local, react-native-web

e2e/
├── autoria-avancada.spec.ts                   # NOVO
├── disparo.spec.ts, publicacao.spec.ts,
│   ciclo-de-vida.spec.ts, pesquisas.spec.ts   # ALTERADOS
├── support/helpers.ts                         # ALTERADO: seedObservedEvents, seedObservedAttributes, questionKeysOf
└── stub-api/routes/
    ├── authoring.ts                           # NOVO: duplicação
    ├── exposure.ts                            # NOVO: quota-progress, publication-warnings, attributes
    ├── surveys.ts, publication.ts,
    │   applications.ts, seed.ts               # ALTERADOS
    └── index.ts                               # ALTERADO: registra as rotas novas
```

**Structure Decision**: nenhuma feature nova. Modelo, condição, exposição, aviso e avisos de
publicação são atributos da pesquisa ou de suas perguntas; criá-los fora de `surveys` produziria
o import cruzado que o Princípio I proíbe ([R1](./research.md#r1)). O catálogo observado é da
aplicação — nada nele liga um valor a uma pesquisa — e fica em `applications`.

## Complexity Tracking

| Violação / custo | Por que é necessário | Alternativa mais simples rejeitada porque |
|---|---|---|
| Dependência nova: `@dnd-kit/core`, `@dnd-kit/sortable`, `@dnd-kit/utilities` | Arrastar e soltar acessível por teclado, com anúncios para leitor de tela e sensores de ponteiro e teclado | HTML5 drag-and-drop nativo não é acessível por teclado nem funciona em toque; implementar sensores à mão é exatamente o que a biblioteca resolve ([R3](./research.md#r3)) |
| Dependências novas: `@pitaco/react-native` (local), `react-native`, `react-native-web` e alias no `next.config.ts` | A pré-visualização precisa ser o renderizador que o app usa | Desenhar a pesquisa em componentes do painel duplicaria o layout do SDK e divergiria na primeira mudança dele ([R4](./research.md#r4)) |
