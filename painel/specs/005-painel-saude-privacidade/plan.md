# Implementation Plan: Painel de saúde do SDK e privacidade — versões, erros, supressões, retenção e exclusão de respondente

**Branch**: `005-painel-saude-privacidade` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-painel-saude-privacidade/spec.md`

## Summary

Duas features novas, cada uma espelhando um eixo do backend: `src/features/health/` (versões do
SDK, erros do SDK, saúde da pesquisa) e `src/features/privacy/` (prévia de retenção, exclusão de
respondente, registro de exclusões). Duas telas novas sob a aplicação — `/saude` e
`/privacidade` — e dois pontos de contato com telas existentes: os avisos de saúde e a nota de
retenção nos resultados de `004-painel-resultados-comportamento`, e o diálogo de edição da
aplicação, onde os prazos de retenção são ajustados.

Saúde é somente leitura. Privacidade tem uma única escrita — a exclusão — feita por Server
Action com confirmação explícita, que termina em `refresh()` para o registro novo aparecer na
mesma tela ([R4](./research.md#r4)).

A decisão que mais pesa: a saúde da pesquisa vira **avisos** com linguagem própria e entra nos
resultados como leitura complementar — se ela falhar, os resultados continuam de pé
([R3](./research.md#r3)).

## Technical Context

**Language/Version**: TypeScript 5 em modo estrito, React 19.2, Node 20+

**Primary Dependencies**: Next.js 16.3 (App Router), Zod 4, Tailwind CSS 4, shadcn/ui. Nenhuma
dependência nova.

**Storage**: N/A — filtros e página na URL

**Testing**: Vitest 4 + Testing Library; Playwright 1.63 contra o servidor de API simulado

**Target Platform**: navegadores modernos; renderização no servidor

**Project Type**: aplicação web (Next.js App Router) consumindo a API administrativa

**Performance Goals**: leituras de cada tela em paralelo (2 em Saúde, 2 em Privacidade); a saúde
da pesquisa em paralelo com as demais leituras de resultados

**Constraints**:
- Nenhuma tela exibe a identificação de quem foi excluído
- A exclusão exige confirmação; o botão fica desabilitado sem identificação e durante o envio
- Ausência nunca como zero (fatia recente, versão, mensagem)
- `"use client"` só no formulário de filtros de erros, no formulário de exclusão e no diálogo de
  edição da aplicação; o contexto recolhível dos erros é `details` nativo, sem estado de cliente

**Scale/Scope**: 2 rotas novas, 2 features novas, 6 leituras e 1 escrita de API

## Constitution Check

*GATE: verificado antes da Phase 0 e reavaliado após a Phase 1.*

| Princípio | Como esta feature adere | Veredito |
|---|---|---|
| **I. Arquitetura Feature-Based** | `health` e `privacy` são features próprias, cada uma com `api/`, `schemas/`, `components/`, `lib/`, `__tests__/` e `index.ts`. Nenhuma importa outra feature. A nota de retenção é componente de `results` (o texto vem do resultado); os avisos de saúde são compostos na página de resultados em `app/`. O diálogo de edição é de `applications`. | ✅ |
| **II. shadcn/ui** | Nenhum primitivo novo: `Table`, `Badge`, `Select`, `Input`, `Button`, `Card`, `Dialog` e o `ConfirmDialog` compartilhado. | ✅ |
| **III. Tailwind** | Utilitários e tokens. | ✅ (ver Complexity Tracking) |
| **IV. Vitest (não negociável)** | Schemas (fatia ausente, tipo desconhecido, contexto ausente, prazo ausente), filtros da URL, `surveyHealthNotices`, `describeRetentionPolicy`, `retentionWarning`, `lastRunMessage`, `erasureResultMessage`, componentes (marca de versão sumida, contexto, filtros na URL, confirmação da exclusão, registro sem identificação, cartão de política) e `RetentionNote`. | ✅ |
| **V. Playwright nos fluxos críticos** | `e2e/saude.spec.ts` e `e2e/privacidade.spec.ts`; exclusão de respondente é fluxo crítico de conformidade. | ✅ |
| **Server first** | Duas páginas Server Component; cliente só onde há interação. | ✅ |
| **Validação de fronteira** | Todo corpo por Zod; `searchParams` por `parseSdkErrorFilters`; formulário de exclusão por `eraseRespondentFormSchema`. | ✅ |
| **Sem dependência nova** | Nenhuma. | ✅ |

**Resultado do portão (pré-Phase 0 e pós-Phase 1)**: aprovado.

## Project Structure

### Documentation (this feature)

```text
specs/005-painel-saude-privacidade/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── backend-api.md
│   └── ui-routes.md
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md
```

### Source Code (repository root)

```text
src/
├── app/aplicacoes/[applicationId]/
│   ├── saude/page.tsx                         # NOVO
│   ├── privacidade/page.tsx                   # NOVO
│   └── pesquisas/[surveyId]/resultados/page.tsx   # ALTERADO: avisos de saúde e nota de retenção
│
├── features/health/                           # NOVA FEATURE
│   ├── index.ts
│   ├── api/
│   │   ├── paths.ts
│   │   └── health.ts                          # getSdkVersions, listSdkErrors, getSurveyHealth
│   ├── schemas/
│   │   ├── health.ts                          # versões, erro, saúde da pesquisa
│   │   └── filters.ts                         # tipo e versão na URL
│   ├── lib/
│   │   └── health-labels.ts                   # rótulos, notas, surveyHealthNotices
│   ├── components/
│   │   ├── sdk-versions-table.tsx
│   │   ├── sdk-errors-list.tsx                # contexto em `details` nativo
│   │   ├── sdk-error-filters.tsx              # "use client"
│   │   └── survey-health-notices.tsx
│   └── __tests__/
│       ├── health-api.test.ts
│       ├── health-schemas.test.ts
│       └── health-components.test.tsx
│
├── features/privacy/                          # NOVA FEATURE
│   ├── index.ts
│   ├── actions.ts                             # eraseRespondentAction
│   ├── api/
│   │   ├── paths.ts
│   │   └── privacy.ts                         # eraseRespondent, listDeletionAudits, getRetentionPreview
│   ├── schemas/
│   │   ├── privacy.ts                         # exclusão, registro, prévia
│   │   └── forms.ts                           # formulário de exclusão
│   ├── lib/
│   │   └── privacy-labels.ts                  # política, aviso, último descarte, resultado
│   ├── components/
│   │   ├── retention-policy-card.tsx
│   │   ├── erase-respondent-form.tsx          # "use client"
│   │   └── deletion-audits-list.tsx
│   └── __tests__/
│       ├── privacy-api.test.ts
│       ├── privacy-schemas.test.ts
│       └── privacy-components.test.tsx
│
├── features/results/
│   ├── components/retention-note.tsx          # NOVO
│   ├── lib/results-labels.ts                  # ALTERADO: retentionNoteText
│   └── __tests__/retention-note.test.tsx      # NOVO
│
└── features/applications/
    ├── actions.ts                             # ALTERADO: updateApplicationAction
    ├── api/index.ts                           # ALTERADO: updateApplication (PATCH com null)
    ├── schemas/forms.ts                       # ALTERADO: updateApplicationFormSchema
    └── components/edit-application-dialog.tsx # NOVO

e2e/
├── saude.spec.ts                              # NOVO
├── privacidade.spec.ts                        # NOVO
├── aplicacoes.spec.ts                         # ALTERADO: edição de nome e prazos
├── support/helpers.ts                         # ALTERADO: seedSdkVersions, seedSdkErrors, seedSuppressions, seedRetentionSnapshot, setApplicationRetention
└── stub-api/routes/
    ├── health.ts                              # NOVO
    ├── privacy.ts                             # NOVO
    ├── applications.ts                        # ALTERADO: PATCH
    └── index.ts                               # ALTERADO
```

**Structure Decision**: duas features, porque são dois agregados com leitores diferentes — saúde
é sobre o SDK e a entrega, privacidade é sobre o dado do respondente — e o backend os separa da
mesma forma ([R1](./research.md#r1)).

## Complexity Tracking

| Ressalva | Por que existe | Encaminhamento |
|---|---|---|
| Os avisos de saúde (`survey-health-notices.tsx`) nasceram com a paleta crua `amber-500` | Vieram antes dos tokens `warning`/`warning-soft` do Design System v2 | Resolvido na `006-painel-design-system-v2`: migrados para `border-warning`, `bg-warning-soft` e `text-warning` |
