# Painel Pitaco

Superfície administrativa do Pitaco: aplicações, chaves de acesso e pesquisas ponta a ponta
(montagem, disparo e exposição, publicação, versões, ciclo de vida, exibições e resultados com
export), mais a saúde do SDK e as operações de privacidade de cada aplicação.

Next.js 16 (App Router) + React 19 + TypeScript estrito, shadcn/ui sobre Tailwind CSS v4.
Toda leitura acontece em Server Component; toda escrita, em Server Action. Nada no navegador
fala com o backend.

## Configuração

Copie `.env.example` para `.env.local`:

```bash
cp .env.example .env.local
```

| Variável | Obrigatória | Descrição |
|---|---|---|
| `PITACO_API_URL` | sim | URL base da API do Pitaco, **já incluindo** o `context-path` `/api` do backend (ex.: `http://localhost:8080/api`). |

É a única variável. Ela é lida **somente no servidor** — não há equivalente `NEXT_PUBLIC_*`,
porque nenhuma requisição ao backend parte do navegador. Sem ela, a primeira leitura falha com
mensagem explícita em vez de tentar uma URL relativa.

## Rodar

```bash
npm install
npm run dev     # http://localhost:3001 → redireciona para /aplicacoes
```

Para desenvolver contra dados reais, suba o backend em `../backend` respondendo em
`http://localhost:8080/api`.

## Scripts

| Script | O que faz |
|---|---|
| `npm run dev` | servidor de desenvolvimento |
| `npm run build` / `npm start` | build de produção e execução |
| `npm run lint` | ESLint |
| `npm run typecheck` | `tsc --noEmit`, modo estrito |
| `npm run test` | Vitest (schemas, cliente HTTP, apresentadores, componentes cliente) |
| `npm run test:e2e` | Playwright (os fluxos de cada tela, contra o simulador de API) |
| `npm run verify` | tudo acima — portão de merge da constituição |

## Testes

**O E2E não precisa do backend.** O `fetch` do painel roda no servidor Node, fora do alcance da
interceptação de rota do Playwright, então o `playwright.config.ts` sobe dois servidores: o
simulador de API em `e2e/stub-api/` e o Next apontando para ele.

```bash
npm run test:e2e                 # contra o simulador (padrão)
E2E_API=real npm run test:e2e    # contra o backend real em PITACO_API_URL
```

Cada teste E2E cria sua própria aplicação e trabalha só dentro dela — é o que dá isolamento com
`fullyParallel: true`, sem reset global.

### Fluxos cobertos pelo E2E

| Spec | Fluxo |
| --- | --- |
| `smoke` | a raiz leva à listagem de aplicações |
| `aplicacoes` | cadastrar, listar, editar, desativar e reativar aplicação |
| `chaves` | emitir, listar e revogar chave de acesso |
| `pesquisas` | criar pesquisa, montar e reordenar perguntas, inclusive arrastando |
| `disparo` | evento com sugestões observadas, janela, amostragem, segmentação e exposição |
| `publicacao` | impedimentos, avisos de competição e compatibilidade, e publicar |
| `ciclo-de-vida` | pausar, retomar, encerrar, e o histórico com o encerramento por cota |
| `autoria-avancada` | modelos NPS/CSAT/CES, duplicação, condição entre perguntas e comparabilidade |
| `coleta` | exibições e respondentes, com filtros e detalhe |
| `resultados` | agregados, taxa de resposta, respostas abertas, recortes e export CSV |
| `saude` | versões do SDK, erros reportados, supressão e evento nunca recebido |
| `privacidade` | exclusão de respondente com confirmação, auditoria, aviso de descarte e aviso de texto livre |

Server Components assíncronos não são testados em unidade (limitação documentada pelo próprio
Next.js); por isso cada `page.tsx` é uma casca fina e a lógica vive em módulo puro coberto por
Vitest. Rede real é proibida no Vitest: `fetch` é substituído por `vi.stubGlobal`.

## Estrutura

```text
src/
├── app/         # só roteamento, layout e composição
├── features/    # domínio, com a fronteira em index.ts de cada um:
│                #   applications, api-keys, surveys, collect, results, health, privacy
├── shared/      # cliente HTTP, componentes de estado, formatação
└── components/ui/  # primitivos shadcn (CLI)
e2e/             # Playwright + simulador de API em stub-api/
```

As regras que governam essa estrutura estão em `.specify/memory/constitution.md`.
