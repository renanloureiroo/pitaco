# Implementation Plan: Painel de leitura da coleta — exibições, respostas e respondentes

**Branch**: `002-painel-leitura-coleta` | **Date**: 2026-09-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-painel-leitura-coleta/spec.md`

## Summary

O painel ganha o eixo de leitura da coleta, que a entrega 001 deixou de fora por ausência de
superfície no backend. São quatro consultas novas e quatro telas: exibições de uma pesquisa (com
filtros), detalhe de uma exibição (respostas e instantâneo de atributos), respondentes de uma
aplicação e histórico de um respondente.

A abordagem técnica não inventa nada: uma feature nova `src/features/collect/`, seguindo
exatamente os padrões que 001 estabeleceu — leitura em Server Components, validação de fronteira
com Zod, resultado tipado em vez de exceção, estado de listagem em `searchParams`, e os
componentes compartilhados de vazio, falha e paginação. A feature é **somente leitura**: não há
Server Action nenhuma nesta entrega, o que remove metade da superfície de risco de 001.

Duas decisões carregam o peso do design: o detalhe da exibição lê **também** a versão exibida,
para dar enunciado legível a cada resposta ([R2](./research.md#r2)); e a rota de detalhe fica
plana sob a aplicação, porque a exibição é alcançada por dois eixos ([R5](./research.md#r5)).

Uma lacuna de contrato foi encontrada durante o planejamento: a API de exibições filtra por
**identificador** de versão, e a API de versões não expõe identificador algum — só o número. O
painel não teria como traduzir a escolha da pessoa. Com a autorização para mexer no backend, a
correção entra no escopo desta entrega como uma mudança pequena e bem delimitada: o filtro passa
a aceitar o **número** da versão, que é o que o próprio backend já documenta como "o que
identifica a versão no painel" ([R7](./research.md#r7)).

Esta entrega, portanto, atravessa os dois repositórios: uma mudança cirúrgica no backend e a
feature nova no painel. As duas são independentes salvo pelo filtro de versão.

## Technical Context

**Language/Version**: TypeScript 5 em modo estrito, React 19.2, Node 20+

**Primary Dependencies**: Next.js 16.3 (App Router), Zod 4, Tailwind CSS 4, shadcn/ui sobre
Radix. **Nenhuma dependência de runtime nova** — ver [R4](./research.md#r4)

**Storage**: N/A — o painel não persiste nada; todo estado de tela vive na URL

**Testing**: Vitest 4 + Testing Library (unidade e integração), Playwright 1.63 (E2E) contra o
servidor de API simulado em `e2e/stub-api/`

**Target Platform**: navegadores modernos; aplicação renderizada no servidor (Node)

**Project Type**: aplicação web (Next.js App Router), consumindo a API administrativa do Pitaco.
Uma alteração pontual acompanha no backend (Java 21 / Spring Boot, módulo `collect`) — ver
[R7](./research.md#r7)

**Performance Goals**: nenhuma leitura cacheada (`cache: "no-store"`, herdado de 001 — painel
administrativo nunca mostra dado velho); leituras independentes de uma mesma tela em paralelo

**Constraints**:
- Somente leitura: nenhuma Server Action, nenhum verbo além de `GET`
- Nenhum número agregado que a API não devolva (FR-030), e nenhuma consulta cuja única
  finalidade seja produzir um número (SC-009)
- `"use client"` apenas nos formulários de filtro, que escrevem na URL
- Sem cabeçalho de chave de aplicação: as rotas são superfície administrativa

**Scale/Scope**: 4 telas novas, 1 feature nova, 4 consultas de API, ~20 páginas por listagem
(o mesmo recorte das demais listagens do painel)

## Constitution Check

*GATE: verificado antes da Phase 0 e reavaliado após a Phase 1.*

| Princípio | Como esta feature adere | Veredito |
|---|---|---|
| **I. Arquitetura Feature-Based** | Todo o domínio novo vive em `src/features/collect/`, com `api/`, `schemas/`, `components/`, `lib/` e `__tests__/` próprios, e uma única fronteira em `index.ts`. A única dependência entre features é `collect → surveys`, e passa pelo `index.ts` público de `surveys` (`getVersion`, `QUESTION_TYPE_LABELS`). `surveys` não conhece `collect` — não há ciclo ([R1](./research.md#r1)). As páginas em `app/` só validam parâmetro e compõem. | ✅ |
| **II. shadcn/ui** | Nenhum primitivo novo é criado. Todos já estão instalados: `Table`, `Badge`, `Select`, `Input`, `Button`, `Card`, `Skeleton`, `Tooltip`. O filtro de período usa `Input type="datetime-local"`, não um date-picker novo ([R4](./research.md#r4)). | ✅ |
| **III. Tailwind** | Só utilitários em `className`, tokens de tema, `cn()` para composição condicional. Nenhuma folha nova, nenhum valor arbitrário previsto. | ✅ |
| **IV. Vitest (não negociável)** | A lógica não óbvia desta feature é testável e será testada antes de concluída: casamento de resposta com pergunta da versão ([R2](./research.md#r2)), leitura e recusa do par de período ([R3](./research.md#r3)), rótulos de desfecho e de forma de identificação, e as três variantes de estado vazio ([R6](./research.md#r6)). Nenhuma rede real: o cliente HTTP é isolado por mock, como em 001. | ✅ |
| **V. Playwright nos fluxos críticos** | Um `e2e/coleta.spec.ts` cobre SC-007: ver exibições de uma pesquisa, filtrar, abrir o conteúdo respondido e navegar até o histórico do respondente. O `e2e/stub-api/` ganha as rotas de coleta. Cada teste cria seus próprios dados. | ✅ |
| **Server first** | As quatro telas são Server Components. `"use client"` aparece só nos dois formulários de filtro, que existem para escrever na URL — a menor superfície possível. | ✅ |
| **Validação de fronteira** | Todo corpo de resposta passa por schema Zod antes de virar tipo de domínio; todo `searchParams` é validado, e valor inválido na URL cai no padrão em vez de quebrar a tela. | ✅ |
| **Sem dependência nova** | Nenhuma adicionada ([R4](./research.md#r4)). | ✅ |

**Resultado do portão (pré-Phase 0)**: aprovado, sem violação a justificar.

**Resultado do portão (pós-Phase 1)**: aprovado. O design da Phase 1 não introduziu abstração
especulativa: nenhum componente genérico de listagem foi criado (R14 de 001 continua valendo), e
a única peça compartilhada entre as duas listagens de exibição é a tabela de exibições, que já
nasce com dois usos concretos ([R8](./research.md#r8)).

## Ajustes de spec e de contrato registrados no planejamento

Dois pontos da spec encontraram a realidade durante o desenho. Nenhum é violação de portão;
ambos precisam de decisão consciente antes da implementação.

| Item | Situação | Encaminhamento |
|---|---|---|
| **FR-005** — filtro por versão | A API de exibições filtra por `versionId` (UUID); a API de versões só devolve `number`. O painel não tem como traduzir a escolha da pessoa em um identificador. | **Resolvido no backend**, autorizado pelo solicitante: o filtro passa a aceitar `versionNumber`. Mudança de cinco pontos, detalhada em [R7](./research.md#r7). O `versionId` permanece nas **respostas** — é identidade legítima e já publicada; o que sai é apenas o parâmetro de filtro, que não tem consumidor. |
| **FR-027** — fuso horário | O painel já formata instantes em fuso fixo (`America/São_Paulo`) desde 001. Formatar no fuso do navegador exigiria `"use client"` em toda célula de data, ou aceitar divergência de hidratação. | **Resolvido na spec**: manter o fuso fixo, **rotulá-lo visivelmente** nas telas de coleta, e interpretar o filtro de período no mesmo fuso. FR-027 já foi reescrito de "fuso local de quem lê" para "fuso de referência do painel, indicado na tela" — ver [R3](./research.md#r3). |

## Project Structure

### Documentation (this feature)

```text
specs/002-painel-leitura-coleta/
├── plan.md              # Este arquivo
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── backend-api.md   # As quatro consultas consumidas
│   └── ui-routes.md     # Rotas, estados de tela e testids
├── checklists/
│   └── requirements.md
├── spec.md
└── tasks.md             # Phase 2 (/speckit-tasks — NÃO criado aqui)
```

### Source Code (repository root)

```text
src/
├── app/
│   └── aplicacoes/[applicationId]/
│       ├── layout.tsx                          # ALTERADO: aba "Respondentes"
│       ├── exibicoes/[displayId]/
│       │   ├── page.tsx                        # NOVO: detalhe da exibição
│       │   ├── loading.tsx
│       │   ├── error.tsx
│       │   └── not-found.tsx
│       ├── respondentes/
│       │   ├── page.tsx                        # NOVO: listagem de respondentes
│       │   ├── loading.tsx
│       │   ├── error.tsx
│       │   └── [respondentId]/
│       │       ├── page.tsx                    # NOVO: histórico do respondente
│       │       ├── loading.tsx
│       │       └── not-found.tsx
│       └── pesquisas/[surveyId]/
│           ├── layout.tsx                      # ALTERADO: aba "Exibições"
│           └── exibicoes/
│               ├── page.tsx                    # NOVO: exibições da pesquisa
│               └── loading.tsx
│
├── features/
│   └── collect/                                # NOVA FEATURE
│       ├── index.ts                            # fronteira pública (Princípio I)
│       ├── api/
│       │   ├── paths.ts
│       │   ├── displays.ts                     # listSurveyDisplays, getDisplay
│       │   └── respondents.ts                  # listRespondents, listRespondentDisplays
│       ├── schemas/
│       │   ├── display.ts                      # exibição, desfecho, detalhe
│       │   ├── answer.ts                       # resposta e sua situação
│       │   ├── respondent.ts
│       │   └── filters.ts                      # leitura dos searchParams de filtro
│       ├── components/
│       │   ├── displays/
│       │   │   ├── displays-table.tsx          # usada pelos dois eixos
│       │   │   ├── display-filters.tsx         # "use client"
│       │   │   ├── display-summary.tsx
│       │   │   ├── display-answers.tsx
│       │   │   └── display-attributes.tsx
│       │   └── respondents/
│       │       ├── respondents-table.tsx
│       │       └── respondent-summary.tsx
│       ├── lib/
│       │   ├── collect-labels.ts               # desfecho, identificação, situação de resposta
│       │   └── answers.ts                      # casamento resposta ↔ pergunta da versão
│       └── __tests__/
│           ├── displays-api.test.ts
│           ├── respondents-api.test.ts
│           ├── filters-schema.test.ts
│           ├── answers.test.ts
│           ├── collect-labels.test.ts
│           └── components.test.tsx
│
└── shared/lib/format.ts                        # ALTERADO: rótulo do fuso de referência

e2e/
├── coleta.spec.ts                              # NOVO: fluxo crítico (SC-007)
└── stub-api/routes/
    ├── index.ts                                # ALTERADO: registra as rotas novas
    ├── displays.ts                             # NOVO
    └── respondents.ts                          # NOVO
```

**No repositório `backend/`** (mudança pontual, módulo `collect` — ver [R7](./research.md#r7)):

```text
modules/collect/
├── infra/http/dtos/ListDisplaysQueryDTO.java              # versionId → versionNumber
├── infra/http/controllers/SurveyDisplaySwagger.java       # documentação do parâmetro
├── application/usecases/ListSurveyDisplaysUseCase.java    # Input.versionNumber
├── application/repositories/SurveyDisplayRepository.java  # ListDisplaysQuery.versionNumber
└── infra/database/jpa/repositories/
    └── SurveyDisplayJpaRepository.java                    # filtro por v.number nas duas consultas
```

**Structure Decision**: uma feature nova `src/features/collect/`, espelhando o módulo `collect`
do backend, em vez de estender `surveys`. A justificativa está em [R1](./research.md#r1): o
respondente pertence à **aplicação**, não à pesquisa, e seu histórico atravessa pesquisas
diferentes — enfiar isso em `surveys` criaria uma feature que lê fora do próprio agregado. A
dependência `collect → surveys` é unidirecional e passa pela fronteira pública, como o
Princípio I exige.

## Complexity Tracking

> Preenchido apenas quando o portão constitucional tem violação a justificar.

Nenhuma violação. Nenhuma dependência nova, nenhum primitivo novo, nenhuma abstração
especulativa: a única peça reaproveitada entre telas (`DisplaysTable`) nasce com dois usos
concretos, o que a cláusula de simplicidade da constituição autoriza explicitamente.
