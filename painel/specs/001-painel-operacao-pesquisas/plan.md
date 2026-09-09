# Implementation Plan: Painel de operação — aplicações, chaves e pesquisas ponta a ponta

**Branch**: `001-painel-operacao-pesquisas` | **Date**: 2026-09-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-painel-operacao-pesquisas/spec.md`

## Summary

Entregar a superfície administrativa do Pitaco cobrindo **tudo** que a API já expõe: aplicações,
chaves de acesso, autoria de pesquisa (perguntas, disparo, regras), publicação, versões e ciclo
de vida. Fora do escopo: SDK, aplicação de teste e leitura de resultados (esta última por
ausência de endpoint no backend).

A abordagem técnica é server-first: cada tela é um Server Component assíncrono que lê da API por
`fetch`, e cada mutação é uma Server Action com `useActionState` no formulário cliente. O único
ponto do código que fala HTTP com o backend é um cliente compartilhado que traduz respostas RFC
9457 em um resultado tipado — nada de biblioteca de data-fetching no navegador. A lógica testável
(schemas, mapeamento de erro, apresentadores) fica fora dos componentes assíncronos, porque o
Vitest não renderiza Server Component assíncrono; esses fluxos são cobertos por Playwright contra
um servidor de API simulado.

## Technical Context

**Language/Version**: TypeScript 5 em modo estrito (`any` proibido) · React 19.2 · Node ≥ 20

**Framework**: Next.js 16.3.4, App Router. Convenções desta versão conferidas em
`node_modules/next/dist/docs/` — com destaque para `error.tsx` receber `{ error, retry }` (não
`reset`), `params`/`searchParams` serem `Promise`, os helpers globais `PageProps<'/rota'>` e
`LayoutProps<'/rota'>`, e `refresh()` de `next/cache` para realinhar o router após mutação.

**Primary Dependencies**: shadcn/ui sobre Tailwind CSS v4 (`style: radix-nova`), `lucide-react`,
`radix-ui`. **Nova dependência de runtime**: `zod`, justificada em [research.md](./research.md#r3)
e registrada em Complexity Tracking.

**Storage**: nenhuma. O painel não persiste nada — sem banco, sem cache de servidor, sem
`localStorage`. Toda verdade vem da API do backend a cada requisição.

**Backend**: API Pitaco em `PITACO_API_URL` (inclui o `context-path` `/api`, ex.:
`http://localhost:8080/api`). Superfície administrativa sob `/applications/**`, sem autenticação
de usuário hoje; o painel nunca envia o header de chave de aplicação.

**Testing**: Vitest + Testing Library (unidade e integração de componentes cliente e módulos
puros); Playwright (E2E) contra um servidor de API simulado em `e2e/stub-api/`, com opção de
apontar para o backend real.

**Target Platform**: navegador de desktop moderno; aplicação servida por processo Node
(`next start`).

**Project Type**: aplicação web (frontend Next.js consumindo API externa já existente).

**Performance Goals**: navegação entre telas com resposta perceptível imediata — esqueleto
renderizado antes do dado; nenhuma tela bloqueia a navegação esperando leitura de layout.

**Constraints**: server-first (`"use client"` no menor componente possível e justificado);
estilização só por utilitários Tailwind com `cn()`; nenhuma leitura administrativa cacheada;
segredo de chave nunca persistido em lugar nenhum do painel.

**Scale/Scope**: ~16 rotas, 3 features, 8 recursos de API, 5 fluxos críticos com cobertura E2E.

## Constitution Check

*GATE: avaliado antes da Phase 0 e reavaliado após a Phase 1.*

| Princípio | Avaliação | Como o desenho atende |
|---|---|---|
| **I. Arquitetura Feature-Based** | ✅ PASS | Três features (`applications`, `api-keys`, `surveys`), cada uma com `api/`, `schemas/`, `components/`, `actions.ts` e `index.ts` como fronteira. Perguntas, disparo, versões e ciclo de vida são submódulos internos de `surveys` — decisão de [R15](./research.md#r15), tomada justamente para não criar import cruzado entre features. `app/` só roteia e compõe. |
| **II. shadcn/ui como camada de componentes** | ✅ PASS | Primitivos instalados por CLI ([R10](./research.md#r10)); nenhuma outra biblioteca de UI. O `Form` do shadcn não é usado porque é adaptador de `react-hook-form`, não primitivo visual ([R4](./research.md#r4)) — os primitivos visuais continuam vindo do shadcn. |
| **III. Tailwind como única fonte de estilo** | ✅ PASS | Só utilitários em `className`, compostos com `cn()`. Única folha global é `src/app/globals.css`. Nenhum CSS-in-JS, nenhum CSS Module. |
| **IV. Testes com Vitest (não negociável)** | ✅ PASS com nota | Toda regra não óbvia vira teste: schemas, mapeamento de erro RFC 9457, cliente HTTP, apresentadores, componentes cliente. **Nota**: Server Component assíncrono não é testável em Vitest (limitação documentada pelo próprio Next.js, [R8](./research.md#r8)); a arquitetura responde mantendo esses componentes como cascas finas e empurrando a lógica para módulos puros testáveis. Rede real é proibida: `fetch` é substituído por `vi.stubGlobal`. |
| **V. E2E com Playwright nos fluxos críticos** | ✅ PASS | Cinco fluxos críticos cobertos (SC-006). Seletores por papel acessível ou `data-testid`. Cada teste cria sua própria aplicação e trabalha só dentro dela — isolamento natural sob `fullyParallel` ([R9](./research.md#r9)). |
| **Stack: server first** | ✅ PASS | Leitura em Server Component; `"use client"` só em formulário, diálogo de confirmação e controles interativos. |
| **Stack: validação de fronteira por schema** | ✅ PASS | Zod valida resposta da API, `FormData` e `searchParams`. Dependência nova, justificada abaixo. |
| **Portões de qualidade** | ✅ PASS | `npm run verify` (lint + typecheck + vitest + playwright) é o portão; nenhuma tarefa é dada por concluída com suíte vermelha. |

**Resultado do gate inicial**: aprovado, com uma dependência de runtime a justificar.

**Reavaliação pós-Phase 1**: aprovado sem mudança. O desenho de `data-model.md` e dos contratos
não introduziu nenhuma abstração especulativa; o único componente compartilhado criado
antecipadamente é o conjunto vazio/erro/confirmação/paginação, cuja forma é idêntica nas três
listagens ([R14](./research.md#r14)).

## Project Structure

### Documentation (this feature)

```text
specs/001-painel-operacao-pesquisas/
├── plan.md              # Este arquivo
├── research.md          # Phase 0 — 15 decisões técnicas
├── data-model.md        # Phase 1 — entidades, schemas e máquina de estados
├── quickstart.md        # Phase 1 — como rodar e validar
├── contracts/
│   ├── backend-api.md   # Contrato consumido: endpoints × funções do painel
│   └── ui-routes.md     # Contrato exposto: rotas, parâmetros, estados, testids
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 — gerado por /speckit-tasks
```

### Source Code (repository root)

```text
src/
├── app/                                  # só roteamento, layout e composição
│   ├── layout.tsx                        # já existe — ajustar lang/metadata para pt-BR
│   ├── page.tsx                          # redireciona para /aplicacoes
│   ├── error.tsx · not-found.tsx
│   └── aplicacoes/
│       ├── page.tsx · loading.tsx · error.tsx
│       ├── nova/page.tsx
│       └── [applicationId]/
│           ├── layout.tsx                # breadcrumb da aplicação (leitura sob Suspense)
│           ├── page.tsx · not-found.tsx
│           ├── chaves/page.tsx · loading.tsx
│           └── pesquisas/
│               ├── page.tsx · loading.tsx
│               ├── nova/page.tsx
│               └── [surveyId]/
│                   ├── layout.tsx        # cabeçalho, estado e transições da pesquisa
│                   ├── page.tsx          # montagem: perguntas
│                   ├── disparo/page.tsx
│                   ├── publicacao/page.tsx
│                   └── versoes/
│                       ├── page.tsx
│                       └── [number]/page.tsx
├── features/
│   ├── applications/
│   │   ├── api/           # leituras (list, get, create) sobre o cliente compartilhado
│   │   ├── schemas/       # zod: resposta, formulário, searchParams
│   │   ├── components/    # tabela, filtro, formulário, cartão de detalhe
│   │   ├── actions.ts     # "use server"
│   │   ├── __tests__/
│   │   └── index.ts       # fronteira pública
│   ├── api-keys/          # mesma forma; inclui o diálogo de segredo exibido uma vez
│   └── surveys/
│       ├── api/           # surveys, questions, trigger, versions, lifecycle, publication
│       ├── schemas/
│       ├── components/
│       │   ├── survey/    · questions/  · trigger/
│       │   ├── versions/  · lifecycle/  · publication/
│       ├── actions.ts
│       ├── __tests__/
│       └── index.ts
├── shared/
│   ├── api/               # cliente HTTP, PITACO_API_URL, erro RFC 9457, paginação
│   ├── components/        # EmptyState, ErrorState, ConfirmDialog, Pagination, PageHeader
│   ├── hooks/
│   └── lib/               # formatação de data, rótulos de estado, cn helpers
└── components/ui/         # primitivos shadcn (CLI)

e2e/
├── stub-api/              # servidor de API simulado para o E2E (Node, sem dependências)
├── aplicacoes.spec.ts · chaves.spec.ts · pesquisas.spec.ts
├── publicacao.spec.ts · ciclo-de-vida.spec.ts
└── support/               # helpers de criação de dados por teste
```

**Structure Decision**: estrutura de aplicação web única (não há backend neste repositório — ele
vive em `../backend`). O layout segue o Princípio I da constituição: domínio em
`src/features/<feature>/` com fronteira em `index.ts`, compartilhado em `src/shared/`, primitivos
em `src/components/ui/`, e `src/app/` restrito a roteamento e composição. Os diretórios
`src/features/` e `src/shared/` já existem no repositório, hoje apenas com `README.md` e
`.gitkeep`.

## Complexity Tracking

| Violação / desvio | Por que é necessário | Alternativa mais simples rejeitada porque |
|---|---|---|
| Nova dependência de runtime: `zod` | A constituição obriga validação por schema em toda fronteira externa (formulário, resposta de API, parâmetro de rota). Sem biblioteca, isso vira validação manual espalhada, divergente dos tipos TypeScript. | Type guards escritos à mão: repetitivos, e a divergência entre o guard e o tipo estático é silenciosa. Valibot: mesmo custo conceitual, sem ganho — a validação vive no servidor, então o tamanho do bundle não é o critério decisivo. |
| Servidor de API simulado em `e2e/stub-api/` (código novo, só de teste) | O `fetch` do painel roda no servidor Node, fora do alcance do `page.route` do Playwright. Sem ele, todo E2E exigiria backend real com banco, migração e seed — o que a constituição não pede e o CI não sustenta. | Backend real via compose no CI: mais fiel, muito mais lento e frágil; fica disponível por `E2E_API=real` para verificação manual. Rodar E2E só em telas estáticas: não provaria os fluxos críticos que a constituição exige cobrir. |
