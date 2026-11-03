# Research — Painel de operação (001)

**Data**: 2026-09-09 · **Fonte primária**: `node_modules/next/dist/docs/` (Next.js 16.3.4, docs
versionadas junto do pacote) e o código do backend em `../backend`.

Todo item abaixo resolve uma incógnita do Technical Context do `plan.md`. Nenhum `NEEDS
CLARIFICATION` sobrou.

---

## R1 — Leitura de dados: Server Components com `fetch`, sem biblioteca de client

**Decisão**: toda leitura acontece em Server Component assíncrono, chamando `fetch` contra a API
do backend. Funções de leitura compartilhadas dentro da mesma requisição são embrulhadas em
`React.cache` para deduplicar (por exemplo, a aplicação lida no layout e na página).

**Rationale**: a constituição exige "server first" e o painel não tem estado de servidor no
navegador — cada tela é uma leitura direta da API. Em Next 16, `fetch` **não é cacheado por
padrão** e bloqueia a renderização até responder, que é exatamente a semântica desejada em um
painel administrativo: nunca mostrar dado velho. `React.cache` tem escopo de requisição, sem
vazamento entre usuários.

**Alternativas rejeitadas**:
- SWR/React Query: traria dependência de runtime, `"use client"` na árvore inteira e um segundo
  modelo de cache para conciliar. Sem ganho — não há revalidação em foco nem polling no escopo.
- `use cache` / Cache Components: cachear leitura administrativa criaria janela de dado obsoleto
  logo depois de uma mutação. O painel nunca marca leitura com `use cache`.

---

## R2 — Escrita de dados: Server Actions, `useActionState`, `refresh()`

**Decisão**: toda mutação é uma Server Action em `actions.ts` da feature (arquivo com `"use
server"` no topo). O formulário cliente usa `useActionState(action, initialState)`, que devolve
`[state, formAction, pending]`. Depois de mutar, a action chama `refresh()` de `next/cache` (ou
`revalidatePath` quando a mudança afeta outra rota) e, quando o fluxo termina em outra tela,
`redirect()`.

**Rationale**: é o caminho canônico do App Router em 16.3 e resolve de graça três requisitos da
spec: `pending` desabilita o submit (FR-006, envio duplicado), o retorno da action carrega a
recusa do backend sem perder o formulário (FR-004), e `refresh()` realinha a UI com o estado real
após a mutação (edge case de estado alterado por terceiros).

**Detalhe de versão**: `refresh()` de `next/cache` é a API de 16 para atualizar o router sem
revalidar tag. `revalidatePath`/`revalidateTag` continuam existindo e são usados só quando outra
rota depende do dado alterado (por exemplo, publicar afeta a listagem de pesquisas).

**Alternativas rejeitadas**: Route Handlers (`app/api/...`) como camada intermediária — só
acrescentaria um salto de rede e um contrato a manter, já que nada no navegador precisa falar
HTTP diretamente.

---

## R3 — Validação de fronteira: Zod (nova dependência de runtime)

**Decisão**: adicionar `zod` como dependência de runtime. Todo dado que cruza fronteira é
validado por schema antes de virar tipo de domínio: (a) resposta da API, (b) `FormData` dentro da
Server Action, (c) `searchParams` de paginação e filtro.

**Justificativa da dependência** (exigida pela constituição): a própria constituição obriga
validação por schema na fronteira; sem uma biblioteca, isso viraria validação manual espalhada e
divergente dos tipos TypeScript. Zod é a opção citada na documentação do próprio Next.js para
validação em Server Actions, infere o tipo estático a partir do schema (uma fonte só, sem
`interface` duplicada) e não tem dependência transitiva.

**Alternativas consideradas**: Valibot (menor, mas menos comum no ecossistema Next e sem ganho
relevante aqui, já que o bundle do cliente quase não carrega schema — a validação vive no
servidor); validação manual com type guards (rejeitada: repetitiva, e a divergência entre guard e
tipo é silenciosa).

---

## R4 — Formulários sem `react-hook-form`

**Decisão**: não adotar `react-hook-form` nem o primitivo `form` do shadcn/ui, que depende dele.
Os formulários usam `<form action={formAction}>` com `useActionState`, e os erros por campo vêm
do estado devolvido pela action. Listas dinâmicas (opções de pergunta, regras de segmentação) são
controladas por `useState` local no componente cliente do formulário.

**Rationale**: duas dependências de runtime a menos, e o modelo bate com "server first": a
verdade da validação é o backend, e o schema do servidor é o mesmo que produz a mensagem de erro.
O painel não tem formulário com validação cruzada complexa nem revalidação por campo em tempo
real.

**Consequência assumida**: a validação de forma antes do envio (FR-022) é feita por atributos
HTML (`required`, `type`, `min`) mais uma checagem no submit; não há feedback por campo enquanto
se digita. Aceitável para o público do painel.

**Não é violação do Princípio II**: o `Form` do shadcn é um adaptador para react-hook-form, não um
primitivo visual. Os primitivos visuais (`label`, `input`, `field`, `select`, `textarea`)
continuam vindo do shadcn.

---

## R5 — Modelo de erro: RFC 9457 traduzido em resultado tipado

**Decisão**: o cliente HTTP nunca lança para erro esperado. Ele devolve um resultado
discriminado:

- `{ ok: true, data }`
- `{ ok: false, kind: "validation", code, detail, errors: Record<campo, mensagem> }` — 400 com
  corpo `ApiValidationError`
- `{ ok: false, kind: "not_found" | "conflict" | "forbidden" | "unknown", code, detail, traceId }`
- `{ ok: false, kind: "unreachable" }` — falha de rede/timeout

Nas **leituras**: `not_found` vira `notFound()` do `next/navigation`; `unreachable` e `unknown`
são lançados para o `error.tsx` do segmento, que oferece `retry()`. Nas **ações**: o resultado é
devolvido como estado do `useActionState` e renderizado no formulário.

**Detalhe de versão importante**: em Next 16.3 o componente de `error.tsx` recebe `{ error,
retry }` — **não** `reset`, como em versões anteriores. Documentado em
`01-getting-started/10-error-handling.md`.

**Sobre `code`**: o backend garante um `code` estável por erro (`application.not_found`,
`api_key.forbidden_surface`, `survey.publication_blocked`, …). O painel decide o tratamento pelo
`code`, e exibe o `detail` como texto ao usuário — nunca reescreve a mensagem do backend.

**`api_key.forbidden_surface`** é tratado como defeito do painel (o painel jamais envia o header
de chave), não como algo a pedir ao usuário: cai no `error.tsx` genérico.

---

## R6 — Estados de carregamento e streaming

**Decisão**: cada rota de listagem tem `loading.tsx` com esqueleto; dentro das páginas de detalhe
que fazem mais de uma leitura, cada bloco independente fica sob `<Suspense>` com seu esqueleto.
Layouts não fazem leitura bloqueante de dado incerto — só a página faz — para que o `loading.tsx`
do segmento realmente cubra a navegação.

**Rationale**: `loading.js` embrulha a página em `<Suspense>`; um layout que lê dado sem cache
bloqueia a navegação em vez de cair no `loading` do mesmo segmento (documentado em
`06-fetching-data.md`). O breadcrumb precisa do nome da aplicação, então essa leitura fica em um
componente sob `<Suspense>` dentro do layout, não no corpo do layout.

---

## R7 — Configuração de ambiente

**Decisão**: uma variável de servidor, `PITACO_API_URL` (ex.: `http://localhost:8080/api`),
lida apenas no módulo de cliente HTTP. **Nunca** `NEXT_PUBLIC_*` — nada no navegador fala com o
backend. O módulo falha rápido, na primeira chamada, se a variável estiver ausente.

**Rationale**: todo acesso é server-side; expor a URL ao cliente só ampliaria superfície. O
`context-path` do backend é `/api`, então a variável já inclui o prefixo e as rotas do painel
usam caminhos relativos a ele (`/applications`, `/collect` não é usado).

**Alternativa rejeitada**: pacote `server-only` como guarda de importação — dependência extra para
um invariante que a revisão e a ausência de `"use client"` nesses módulos já sustentam. Adotar se
algum vazamento aparecer.

---

## R8 — Testes de unidade: o que o Vitest cobre e o que ele não cobre

**Decisão**: Vitest cobre (a) schemas e mapeamento de erro, (b) o cliente HTTP, com `fetch`
substituído por `vi.stubGlobal`, (c) apresentadores puros (rótulo de estado, formatação de data,
construção de query string de paginação), (d) componentes cliente via Testing Library. Server
Components assíncronos **não** são testados em unidade — ficam por conta do Playwright.

**Rationale**: a documentação de testes do Next.js é explícita: "Vitest currently does not
support async Server Components... we recommend using E2E tests for async components"
(`02-guides/testing/vitest.md`). Tentar contornar isso produziria teste frágil que não prova nada.

**Consequência arquitetural**: manter lógica testável **fora** do componente assíncrono. O Server
Component deve ser uma casca fina que chama a função de leitura da feature e passa o resultado a
um componente de apresentação.

**Alternativa rejeitada**: MSW para simular a rede nos testes de unidade. Uma dependência de
desenvolvimento a mais para substituir três linhas de `vi.stubGlobal` — o cliente HTTP é o único
ponto do código que toca `fetch`.

---

## R9 — Testes E2E: servidor de API simulado

**Problema**: o `fetch` do painel roda no **servidor** Node do Next, não no navegador. A
interceptação de rota do Playwright (`page.route`) só enxerga requisição do navegador, e portanto
**não** consegue simular a API do painel. Sem decisão, o E2E dependeria de um backend real com
banco.

**Decisão**: subir, junto do app, um **servidor de API simulado** em `e2e/stub-api/` — um servidor
HTTP Node, sem dependências, com estado em memória, implementando fielmente os contratos que o
painel consome. O `playwright.config.ts` passa a declarar dois `webServer`: o stub e o Next, com
`PITACO_API_URL` apontando para o stub. Uma variável (`E2E_API=real`) permite rodar a mesma suíte
contra o backend de verdade.

**Isolamento entre testes** (a constituição exige que cada teste crie e limpe seus próprios
dados): cada teste cria **sua própria aplicação** pela UI ou por uma chamada de preparação ao
stub, e trabalha apenas dentro dela. Como aplicação é a raiz de todo o resto, isso dá isolamento
natural mesmo com `fullyParallel: true`, sem precisar de reset global.

**Rationale**: o E2E passa a rodar em CI sem banco, container ou seed, e continua exercitando o
código do painel de ponta a ponta — inclusive a camada de servidor, que é onde mora a maior parte
da lógica. O stub é código de teste: quando ele e o backend divergirem, o contrato em
`contracts/backend-api.md` é o árbitro.

**Alternativas rejeitadas**:
- Backend real via docker compose no CI: mais fiel, muito mais lento e frágil (banco, migração,
  seed, limpeza). Continua disponível pela variável de ambiente para uma verificação manual.
- Rodar o E2E só contra telas estáticas: não provaria nada dos fluxos exigidos pela spec.

---

## R10 — Primitivos de UI a instalar

**Decisão**: instalar via CLI do shadcn, em `src/components/ui/`: `table`, `badge`,
`alert-dialog`, `dialog`, `select`, `textarea`, `checkbox`, `skeleton`, `separator`, `breadcrumb`,
`dropdown-menu`, `tooltip`, `sonner`, `empty`, `field`, `spinner`. Já existem `button`, `card`,
`input`, `label`.

**Rationale**: o Princípio II obriga a verificar o shadcn antes de criar componente visual. Esses
cobrem tudo que as telas pedem: tabela de listagem, estado de vazio, confirmação destrutiva,
aviso de segredo, seleção de tipo de pergunta e de operação de regra, esqueleto de carregamento,
notificação de sucesso.

**Confirmação em tarefa**: a disponibilidade de `empty`, `field` e `spinner` no registro
configurado (`style: radix-nova`) é verificada na primeira tarefa de setup; o que não existir vira
composição de primitivos existentes, nunca uma segunda biblioteca.

---

## R11 — Nomes de rota em português

**Decisão**: os segmentos de rota são em português — `/aplicacoes`, `/chaves`, `/pesquisas`,
`/perguntas`, `/disparo`, `/versoes` — sem acento e sem cedilha, com os identificadores como
segmentos dinâmicos.

**Rationale**: a interface é pt-BR e a URL é parte da interface. Sem acento para evitar
percent-encoding no meio de link copiado e colado.

---

## R12 — Paginação e filtro por `searchParams`

**Decisão**: página, tamanho e filtro de situação vivem em `searchParams` (`?page=0&status=active`)
e são lidos pelo Server Component, validados por schema, e repassados à API. Links de navegação
preservam os parâmetros vigentes.

**Rationale**: torna o estado da listagem endereçável e compartilhável, e faz o "voltar" do
navegador funcionar sem código. `searchParams` é uma `Promise` em Next 16 e opta a página por
renderização dinâmica — que é o que se quer aqui.

---

## R13 — Exibição única do segredo da chave

**Decisão**: a Server Action de emissão devolve o segredo no estado da action. O componente
cliente o guarda em `useState` e o exibe em um `Dialog`. Ao fechar o diálogo, o estado é
descartado. O segredo **nunca** entra em URL, `searchParams`, `localStorage`, cookie ou log.
Depois disso, nenhuma leitura o traz de volta — o `ApiKeyResponseDTO` do backend não tem o campo.

**Rationale**: o backend já garante estruturalmente a impossibilidade de recuperar; a
responsabilidade do painel é não criar uma cópia que sobreviva à tela. Um teste E2E recarrega a
página depois da emissão e afirma a ausência do segredo (SC-004).

---

## R14 — Sem `DataTable` genérico

**Decisão**: cada listagem compõe os primitivos de `table` do shadcn diretamente. Um componente de
tabela genérico só nasce se três listagens pedirem o mesmo comportamento não trivial.

**Rationale**: o Princípio de simplicidade da constituição — abstração com caso de uso concreto,
ou forma óbvia e superfície mínima. As três listagens (aplicações, chaves, pesquisas) têm colunas,
ações e estados vazios diferentes. O que **é** compartilhado e nasce pronto: paginação,
estado vazio, estado de erro e diálogo de confirmação, porque a forma é idêntica em todas.

---

## R15 — Fronteira entre features

**Decisão**: três features — `applications`, `api-keys`, `surveys`. Perguntas, disparo, regras,
versões, publicação e ciclo de vida são **submódulos internos** de `surveys`, não features
próprias.

**Rationale**: essas partes compartilham o mesmo agregado (a pesquisa), o mesmo identificador e o
mesmo estado; separá-las em features criaria justamente o import cruzado que o Princípio I
proíbe. `api-keys` é feature própria porque nada além da aplicação a liga ao resto.

O que é de fato compartilhado sobe para `src/shared/`: cliente HTTP, tipos de erro, schema de
paginação, componentes de estado (vazio, erro, confirmação, paginação, cabeçalho de página).

---

## R10 — Confirmação de disponibilidade (execução da T002)

Os dezesseis primitivos foram instalados pela CLI do shadcn no registro `radix-nova` sem
exceção: `table`, `badge`, `alert-dialog`, `dialog`, `select`, `textarea`, `checkbox`,
`skeleton`, `separator`, `breadcrumb`, `dropdown-menu`, `tooltip`, `sonner`, `empty`, `field` e
`spinner`. **Nenhum precisou ser composto a partir de outros** — a contingência prevista em R10
não foi acionada, e nenhuma segunda biblioteca de UI entrou no projeto.
