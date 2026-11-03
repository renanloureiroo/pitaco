<!--
Sync Impact Report
- Version change: (template não ratificado) → 1.0.0
- Bump rationale: MAJOR inicial — primeira ratificação com definição completa dos
  princípios; nenhum princípio anterior existia (arquivo continha apenas placeholders).
- Modified principles:
  - [PRINCIPLE_1_NAME] → I. Arquitetura Feature-Based
  - [PRINCIPLE_2_NAME] → II. shadcn/ui como Camada de Componentes
  - [PRINCIPLE_3_NAME] → III. Tailwind CSS como Única Fonte de Estilo
  - [PRINCIPLE_4_NAME] → IV. Testes com Vitest (NÃO NEGOCIÁVEL)
  - [PRINCIPLE_5_NAME] → V. E2E com Playwright nos Fluxos Críticos
- Added sections:
  - Stack e Restrições Técnicas (era [SECTION_2_NAME])
  - Fluxo de Desenvolvimento e Portões de Qualidade (era [SECTION_3_NAME])
- Removed sections: nenhuma
- Deferred TODOs: nenhum
-->

# Painel Pitaco Constitution

## Core Principles

### I. Arquitetura Feature-Based

O código de domínio MUST residir em `src/features/<feature>/`, e cada feature MUST ser
autocontida com sua própria fronteira pública. Uma feature organiza internamente seus
`components/`, `hooks/`, `api/`, `schemas/` e `__tests__/`, e expõe apenas o que estiver
declarado em seu `index.ts`.

Regras não negociáveis:

- Imports entre features MUST passar pelo `index.ts` da feature de destino; imports de
  caminhos internos (`features/x/components/Foo`) são proibidos.
- Features MUST NOT depender ciclicamente umas das outras. Código compartilhado por duas ou
  mais features é promovido para `src/shared/` (ou `src/components/ui/`, no caso de
  primitivos de UI).
- O diretório `app/` MUST conter apenas roteamento, layouts e composição: ele orquestra
  features, nunca implementa regra de negócio.

Rationale: a fronteira explícita mantém o custo de mudança local à feature, permite excluir
ou substituir uma feature inteira sem arqueologia, e torna óbvio para humanos e agentes onde
cada mudança pertence.

### II. shadcn/ui como Camada de Componentes

Primitivos de interface MUST vir do shadcn/ui, instalados via CLI em `src/components/ui/`.
Esses arquivos são código do projeto e podem ser editados, mas com disciplina.

Regras não negociáveis:

- Antes de criar qualquer componente visual novo, o autor MUST verificar se o shadcn/ui já
  oferece o primitivo. Reimplementar um primitivo existente é violação.
- Componentes em `src/components/ui/` MUST permanecer agnósticos de domínio: sem chamadas de
  API, sem regra de negócio, sem tipos de feature.
- Variações de aparência MUST ser expressas como variantes (`cva`) do primitivo, não como
  cópias divergentes do componente.
- Nenhuma outra biblioteca de componentes de UI pode ser adicionada sem emenda a esta
  constituição.

Rationale: uma única fonte de primitivos evita a divergência visual que aparece quando cada
feature inventa seu próprio botão, e mantém acessibilidade concentrada em um lugar auditável.

### III. Tailwind CSS como Única Fonte de Estilo

Toda estilização MUST ser feita com utilitários Tailwind CSS aplicados em `className`.

Regras não negociáveis:

- CSS-in-JS, CSS Modules e folhas `.css` por componente são proibidos. A única folha global
  permitida é `app/globals.css`, restrita a diretivas do Tailwind, tokens de tema e resets.
- Cores, espaçamentos, raios e tipografia MUST usar os tokens de tema; valores arbitrários
  (`w-[437px]`, `text-[#3a3a3a]`) são permitidos apenas com justificativa registrada no PR.
- Composição condicional de classes MUST usar o helper `cn()`; concatenação manual de
  strings de classe é proibida.
- Estilo inline (`style={{ ... }}`) é permitido apenas para valores dinâmicos em tempo de
  execução que não podem ser expressos como classe.

Rationale: um único mecanismo de estilo elimina a pergunta "onde está definido este
espaçamento?" e garante que o tema seja realmente central em vez de decorativo.

### IV. Testes com Vitest (NÃO NEGOCIÁVEL)

Vitest é o runner de testes de unidade e integração do projeto. Toda lógica de negócio MUST
ter teste automatizado antes de ser considerada concluída.

Regras não negociáveis:

- Toda regra de negócio não óbvia MUST ser expressa como teste. Um comportamento sem teste é
  tratado como acidental e pode ser alterado sem aviso.
- Correção de bug MUST começar por um teste que falha reproduzindo o bug.
- Testes MUST ficar em `__tests__/` dentro da própria feature, ou como `*.test.ts(x)` ao lado
  do arquivo testado. Testes MUST NOT importar caminhos internos de outra feature.
- Testes de componente MUST usar Testing Library e asserções sobre comportamento observável
  pelo usuário (papéis, textos, estados), nunca sobre detalhes de implementação
  (nomes de estado interno, estrutura de árvore de componentes).
- Rede real é proibida em testes Vitest; dependências externas MUST ser isoladas por mock ou
  servidor de mock local.

Rationale: o teste é o único registro executável da intenção; sem ele, cada refatoração vira
uma aposta e a regra de negócio se dissolve no código.

### V. E2E com Playwright nos Fluxos Críticos

Playwright cobre os fluxos que atravessam múltiplas features e o navegador real.

Regras não negociáveis:

- Todo fluxo crítico de usuário (autenticação, criação e resposta de pesquisa, e qualquer
  fluxo cuja falha impeça o uso do produto) MUST ter ao menos um teste Playwright.
- Testes E2E MUST ficar em `e2e/` na raiz do projeto e rodar contra um build da aplicação,
  nunca contra componentes isolados.
- Seletores MUST ser baseados em papel acessível ou `data-testid` explícito; seletores por
  classe CSS ou estrutura do DOM são proibidos.
- Cada teste E2E MUST criar e limpar seus próprios dados; testes MUST NOT depender da ordem
  de execução nem de estado deixado por outro teste.
- Um teste E2E instável (flaky) MUST ser corrigido ou removido dentro do mesmo ciclo; marcar
  como `skip` indefinidamente é proibido.

Rationale: o E2E é o único teste que prova que as features se encaixam de verdade; mantê-lo
pequeno, determinístico e focado no crítico é o que impede que ele seja ignorado.

## Stack e Restrições Técnicas

- **Framework**: Next.js 16 com App Router e React 19. As convenções desta versão diferem de
  versões anteriores; antes de escrever código, consultar os guias em
  `node_modules/next/dist/docs/` conforme `AGENTS.md`.
- **Linguagem**: TypeScript em modo estrito. `any` é proibido; use `unknown` com
  estreitamento de tipo. Supressões (`@ts-expect-error`) MUST vir com comentário explicando
  o motivo.
- **UI**: shadcn/ui sobre Tailwind CSS v4, com tokens de tema em `app/globals.css`.
- **Testes**: Vitest + Testing Library (unidade e integração); Playwright (E2E).
- **Server first**: componentes são Server Components por padrão. `"use client"` MUST ser
  aplicado no menor componente possível e justificado por interatividade, estado de browser
  ou uso de efeito.
- **Validação de fronteira**: toda entrada externa (formulário, resposta de API, parâmetro de
  rota) MUST ser validada por schema antes de virar tipo de domínio.
- Adicionar uma nova dependência de runtime MUST ser justificado no PR: qual problema
  resolve e por que a stack existente não o resolve.

## Fluxo de Desenvolvimento e Portões de Qualidade

- **Portão obrigatório**: `tsc --noEmit`, lint, `vitest run` e `playwright test` MUST passar
  antes do merge. Uma suíte vermelha bloqueia o merge; não há exceção "arrumo depois".
- **Escopo de PR**: um PR entrega uma mudança coerente. Refatoração ampla MUST vir em PR
  separado da mudança de comportamento.
- **Revisão**: o revisor MUST verificar explicitamente a conformidade com os cinco princípios
  acima; qualquer desvio precisa estar justificado no corpo do PR.
- **Commits**: mensagens descrevem a mudança, em português, seguindo o padrão já usado no
  repositório (`feat:`, `fix:`, `chore:`).
- **Simplicidade**: abstração só é introduzida quando há caso de uso concreto, ou quando a
  forma é óbvia e a superfície mínima. Complexidade especulativa MUST ser rejeitada na
  revisão.

## Governance

Esta constituição prevalece sobre qualquer outra prática, convenção herdada ou preferência
individual. Onde um documento de apoio conflitar com ela, a constituição vence.

- **Emendas**: qualquer mudança MUST ser proposta em PR que altere este arquivo, descrevendo
  a motivação, o impacto sobre o código existente e o plano de migração quando houver
  código em desacordo.
- **Versionamento**: MAJOR para remoção ou redefinição incompatível de princípio; MINOR para
  novo princípio ou seção, ou expansão material de regra existente; PATCH para
  esclarecimento, redação ou correção sem mudança de semântica.
- **Conformidade**: toda revisão de PR MUST verificar aderência aos princípios. Violação
  aceita conscientemente MUST ser registrada no PR com a justificativa e, quando aplicável,
  o prazo de correção.
- **Guia de runtime**: `AGENTS.md` (referenciado por `CLAUDE.md`) permanece a fonte de
  orientação operacional do dia a dia e MUST ser mantido consistente com esta constituição.

**Version**: 1.0.0 | **Ratified**: 2026-09-08 | **Last Amended**: 2026-09-08
