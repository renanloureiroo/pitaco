# Phase 0 — Pesquisa e decisões

As decisões das entregas anteriores continuam valendo. As que esta entrega revê estão marcadas.

---

## R1 — Tokens em `globals.css`, regras em `DESIGN.md`

**Decisão**: `src/app/globals.css` declara os valores dos dois temas em `:root` (claro) e `.dark`
(escuro) e os expõe ao Tailwind 4 por `@theme inline`, com duas famílias de nomes:

- os nomes do shadcn (`background`, `card`, `primary`, `muted`, `border`, `ring`…), que os
  primitivos de `src/components/ui` já leem;
- os papéis do Pitaco: superfícies (`surface`, `surface-raised`, `surface-sunken`, `sidebar`),
  tinta (`ink`, `ink-secondary`, `ink-muted`), marca (`primary-ink`, `primary-soft`), status
  (`success`/`danger`/`warning`/`info` com `-fill` e `-soft`), gráfico (`chart-1`…`chart-8`,
  `chart-grid`, `neutral-fill`) e bordas (`border-strong`).

O modo escuro é uma variante por classe (`@custom-variant dark (&:is(.dark *))`). Raios de 2 a
12px. `DESIGN.md`, na raiz do painel, é o resumo das regras (princípios, tabela de tokens,
tipografia, espaço, layout, componentes e gráficos).

**Rationale**: o Princípio III exige uma única folha global restrita a tokens. Manter os nomes do
shadcn evita reescrever os primitivos; acrescentar os papéis do Pitaco dá vocabulário para o que o
shadcn não nomeia (três planos de superfície, tinta secundária, cor de gráfico por entidade).

**Alternativas consideradas**:
- *Só os nomes do shadcn*: rejeitada. Não há como dizer "superfície rebaixada" ou "série 3" sem
  inventar classe.
- *Tokens em arquivo TypeScript*: rejeitada. Duplicaria a fonte; o Tailwind 4 lê do CSS.

---

## R2 — Tema: `next-themes`, escuro por padrão

**Decisão**: `ThemeProvider` (`attribute="class"`, `defaultTheme="dark"`, `enableSystem={false}`,
`disableTransitionOnChange`) envolve o `<body>`; o `<html>` nasce com a classe `dark` e
`suppressHydrationWarning`. `ThemeToggle` alterna entre `dark` e `light` pelo `resolvedTheme`; os
dois ícones existem no DOM e a classe do tema decide qual aparece, o que evita divergência de
hidratação.

**Rationale**: escuro é o padrão do Design System. Desligar o tema do sistema torna a primeira
visita previsível. `next-themes` já estava instalado (o `sonner` de 001 o usa) e guarda a escolha
no navegador.

**Consequências**: gráficos usam variáveis CSS (`var(--chart-n)`) e mudam com o tema sem
re-renderizar; a pré-visualização da pesquisa (`003-painel-autoria-avancada`) lê o
`resolvedTheme` e passa o esquema de cor ao SDK.

---

## R3 — Shell: barra lateral no layout de `/aplicacoes`, gaveta no celular

**Decisão** (revê a navegação de 001 e 002): o layout de `/aplicacoes` renderiza `AppSidebar` e o
conteúdo lado a lado a partir de `lg`. A lista de aplicações do seletor vem de
`listApplications({ page: 0, size: 100 })` num componente sob `<Suspense>`, com a barra sem
aplicações como fallback — o corpo do layout não espera leitura (R6 de 001).

A aplicação atual é deduzida do caminho (`/aplicacoes/{id}`), ignorando o segmento reservado
`nova`. Os itens são vínculos; o ativo usa `aria-current="page"` (Visão geral só na raiz da
aplicação; os demais também nas subtelas).

Abaixo de `lg`, uma barra superior fixa mostra o botão "Abrir menu" e a marca; a gaveta é um
`div` com `role="dialog"` e `aria-modal`, fundo clicável, botão de fechar, Escape, e fecha ao
navegar.

O layout de `[applicationId]` deixa de ter `SectionNav`: fica só a trilha, também sob
`<Suspense>`. As abas da pesquisa continuam como `SectionNav`, agora com rolagem horizontal.

**Alternativas consideradas**:
- *Componente `Sidebar` do shadcn*: rejeitada nesta entrega. Traz provedor, estado de recolhido e
  cookies para uma barra que não recolhe; a composição com `DropdownMenu` e `Button` basta.
- *Manter as abas da aplicação*: rejeitada. Com seis seções, a fileira estourava a largura e
  competia com as abas da pesquisa logo abaixo.

---

## R4 — Visão geral somada no servidor do painel

**Decisão**: `getApplicationOverview` lê a lista de pesquisas (até 100) e, em paralelo, o resultado
de cada pesquisa **publicada** no período atual e no anterior (`getSurveyResults` com `from`, e
com `from`/`to` no anterior), o total de respondentes (`listRespondents` com página de 1, lendo
`total`), as versões do SDK e o total de erros do SDK. Depois soma:

| Número | Regra |
|---|---|
| totais | soma das contagens por desfecho; taxa = concluídas ÷ exibidas **sobre a soma** |
| NPS | soma de promotores, neutros, detratores e respondentes; pontuação recalculada |
| linha do tempo | soma por dia; dias do período sem exibição preenchidos com zero |
| variação relativa | (atual − anterior) ÷ anterior; ausente sem base |
| variação em pontos | atual − anterior; ausente se um dos lados falta |
| período anterior | mesma duração, terminando onde o atual começa |

Falha na lista de pesquisas derruba a tela; falha numa leitura complementar só tira o bloco.

**Rationale**: não há endpoint consolidado da aplicação. Somar contagens que o backend já apurou
não cria métrica nova; média de taxas criaria, e erraria (uma pesquisa com 3 exibições pesaria
como uma com 3.000).

**Nota sobre a regra de 002** ("nenhuma consulta só para produzir um número"): ela vale para as
telas de coleta item a item. A Visão geral existe para mostrar números; a leitura de respondentes
com página de 1 é a forma mais barata de obter o total que a paginação já informa.

**Alternativas consideradas**:
- *Endpoint consolidado no backend*: preferível em escala, mas fora do escopo desta entrega;
  registrado em Complexity Tracking.
- *Média das taxas*: rejeitada pelo motivo acima.

---

## R5 — Peças de dashboard em `shared`

**Decisão**: `KpiCard` (um número, variação com seta, cor pelo sentido bom e texto para leitor de
tela), `ChartCard` (moldura com título, descrição e ações), `TrendChart` (área de exibidas e
concluídas, legenda, tooltip com a taxa do dia, sem segundo eixo), `OutcomeBreakdown` (barra 100%
dos quatro desfechos com contagem, proporção e explicação de cada um) e `NpsBreakdown` (pontuação
com sinal e grupos com faixa) ficam em `src/shared/components/dashboard`. Peças com domínio ficam
nas features: `PeriodTabs`, `SegmentBreakdown`, `SurveyPerformanceTable`, `Sparkline` e
`SdkAdoption` em `analytics`.

**Rationale**: `results` (taxa de resposta, linha do tempo) e `analytics` usam as mesmas peças; o
Princípio I manda promover o que duas features compartilham. Elas recebem números e rótulos, sem
tipo de feature.

---

## R6 — `recharts` pelo primitivo `chart` do shadcn

**Decisão**: instalar o primitivo `chart` do shadcn (`src/components/ui/chart.tsx`), que traz
`recharts`, e usá-lo só no `TrendChart`. O primitivo aplica os tokens à grade, aos eixos e ao
cursor do tooltip. Barras, histogramas, funil, barra divergente e miniatura continuam em `div` ou
SVG simples com tokens.

**Rationale**: série temporal com tooltip acessível e legenda é o caso em que a biblioteca se
paga; barras horizontais não precisam dela. O Princípio II pede o primitivo do shadcn quando ele
existe — e para gráfico ele existe.

**Alternativas consideradas**:
- *SVG à mão também para a tendência*: rejeitada pelo custo do tooltip e da acessibilidade.
- *Outra biblioteca de gráficos*: rejeitada; o shadcn já escolheu.

---

## R7 — Análise por segmento sobre a pesquisa de NPS em destaque

**Decisão**: a pesquisa em destaque é a de NPS com mais respondentes no período atual. Os
atributos vêm do catálogo do resultado dela. O atributo é o da URL (`segmento`), ou o de menos
valores entre os que têm ao menos dois. `getSegmentBreakdown` lê o resultado da pesquisa uma vez
por valor (os seis mais frequentes), com o mesmo período, e soma como a Visão geral.

**Forma**: NPS em barra divergente de −100 a +100 (zero no meio, `success-fill` à direita,
`danger-fill` à esquerda) e taxa em barra simples; a coluna de NPS só aparece se algum valor tiver
pontuação.

**Rationale**: atributo com poucos valores compara melhor (plano, plataforma), e a pesquisa com
mais respostas dá a base mais firme.

---

## R8 — Período e segmento na URL, por vínculos

**Decisão**: `PeriodTabs` e a lista de atributos são `Link`s que reescrevem `periodo` e
`segmento`, cada um preservando o outro. `parseOverviewPeriod` cai em 30 dias para qualquer valor
fora de 7/30/90.

**Rationale**: compartilhável, "voltar" funciona, e nenhum estado de cliente na página (Server
Component inteiro). Mesma decisão de R12 de 001.

---

## R9 — Responsividade

**Decisão**: conteúdo até 1280px com `px-4 sm:px-8`; KPIs em 1, 2 e 4 colunas; gráficos em 1 e 3
colunas; tabelas dentro de contêiner com `overflow-x-auto`; `SectionNav` com rolagem horizontal
sem barra; grade da análise por segmento que esconde a barra de taxa em telas estreitas e mantém
o número.

**Rationale**: no celular a tarefa é ler números. A tabela rola sozinha para a página não rolar.
