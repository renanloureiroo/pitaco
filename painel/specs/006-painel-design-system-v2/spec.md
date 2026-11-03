# Feature Specification: Painel com Design System v2 — shell com barra lateral, tema claro e escuro, Visão geral da aplicação e celular

**Feature Branch**: `006-painel-design-system-v2`

**Created**: 2026-10-01

**Status**: Implemented

**Input**: User description: "o painel tá completo mas parece um monte de telas soltas. vamos aplicar o design system v2: barra lateral com troca rápida de aplicação, tema claro e escuro, uma visão geral da aplicação com os números que importam e funcionar direito no celular."

## Contexto

As entregas de `001` a `005` construíram o painel tela a tela: operação, coleta, autoria,
resultados, saúde e privacidade. Cada tela resolve o seu problema, mas o conjunto não se lê como
um produto: a navegação da aplicação era uma fileira de abas que crescia a cada entrega, trocar de
aplicação exigia voltar à listagem, a página da aplicação era só o cadastro, e as cores e a
tipografia variavam de tela para tela.

O Design System Pitaco v2 define a linguagem visual do painel — tokens de superfície, texto,
marca, status e gráfico nos dois temas, tipografia, espaço e forma, layout e componentes de
dashboard — e está resumido em `DESIGN.md` na raiz do painel, com os valores em
`src/app/globals.css`.

Esta feature aplica o v2 ao painel inteiro e acrescenta o que faltava para o painel funcionar como
um lugar de trabalho: um **shell** com barra lateral e troca rápida de aplicação, **tema claro e
escuro**, uma **Visão geral da aplicação** que responde "como estão as pesquisas deste app?" em
uma tela, e **uso no celular**.

A Visão geral não cria métrica nova: ela soma, no servidor do painel, o que o backend já apura por
pesquisa (`004-painel-resultados-comportamento`) e o que a saúde já expõe
(`005-painel-saude-privacidade`).

## Clarifications

### Session 2026-10-01

- Q: Qual tema é o padrão? → A: Escuro. O claro existe "de verdade", com os próprios passos de
  cor, não como inversão; os dois passam 4.5:1 em texto.
- Q: Como se calcula a taxa de resposta da aplicação? → A: Concluídas somadas ÷ exibidas somadas.
  Nunca média das taxas das pesquisas — uma pesquisa pequena não pode pesar como uma grande.
- Q: Contra o que a variação dos KPIs compara? → A: Contra o período anterior de mesma duração,
  que termina onde o atual começa. Sem base no período anterior, não há variação.
- Q: Que pesquisa alimenta o NPS consolidado e a análise por segmento? → A: O NPS consolidado soma
  os grupos de todas as pesquisas criadas pelo modelo NPS. A análise por segmento usa a pesquisa
  de NPS com mais respostas no período.
- Q: A cor da variação diz se é bom ou ruim? → A: A cor segue o sentido **bom** da métrica (menos
  abandono é bom), a seta segue o sentido do número — e o texto para leitor de tela diz "subiu" ou
  "caiu". A cor nunca carrega o significado sozinha.
- Q: Onde fica a navegação da aplicação no celular? → A: A barra lateral vira gaveta, aberta pelo
  botão de menu de uma barra superior.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Navegar pela barra lateral e trocar de aplicação (Priority: P1)

Em qualquer tela sob `/aplicacoes`, uma barra lateral mostra a marca, a troca de tema, o seletor
de aplicação e as seções. Dentro de uma aplicação, as seções aparecem em dois grupos — **Análise**
(Visão geral, Pesquisas, Respondentes) e **Operação** (Chaves, Saúde, Privacidade) — com o item
atual destacado. O seletor lista as aplicações com um ponto de situação (ativa ou inativa) e
oferece "Todas as aplicações" e "Nova aplicação". Fora de uma aplicação, a barra mostra só
"Aplicações".

**Why this priority**: é o esqueleto de todas as telas. Trocar de aplicação passa de três cliques
(voltar, achar, abrir) para dois, e as seções deixam de competir numa fileira de abas.

**Independent Test**: com duas aplicações, abrir a primeira, trocar pela segunda no seletor e
conferir que a Visão geral dela abre; seguir Saúde e Privacidade pela barra lateral.

**Acceptance Scenarios**:

1. **Given** a listagem de aplicações, **When** a abro, **Then** a barra lateral mostra a marca, o
   seletor ("Todas as aplicações") e o item Aplicações ativo.
2. **Given** uma aplicação aberta, **When** vejo a barra, **Then** vejo os grupos Análise e
   Operação com as seis seções, e o seletor com o nome da aplicação.
3. **Given** uma subtela (ex.: uma pesquisa), **When** vejo a barra, **Then** a seção que a contém
   continua destacada; Visão geral só fica destacada na própria Visão geral.
4. **Given** o seletor aberto, **When** escolho outra aplicação, **Then** chego à Visão geral dela.
5. **Given** a lista de aplicações ainda carregando, **When** a barra aparece, **Then** ela já é
   navegável, só sem aplicações no seletor.
6. **Given** a rota de nova aplicação, **When** a abro, **Then** a barra não a trata como uma
   aplicação.

---

### User Story 2 - Ver como estão as pesquisas da aplicação (Priority: P1)

A página da aplicação vira a **Visão geral**: título com o número de pesquisas, quantas no ar e
quantos respondentes conhecidos; seletor de período (7, 30 ou 90 dias, na URL, 30 por padrão);
quatro KPIs — exibições, taxa de resposta, NPS e abandono — cada um com a variação contra o
período anterior; a tendência diária de exibições e conclusões, com a taxa do dia no detalhe; para
onde vão as exibições (concluídas, dispensadas, abandonadas, em andamento, em barra 100%); o NPS
consolidado das pesquisas de NPS; respondentes, versões do SDK e erros do SDK com vínculo para as
telas correspondentes; e, no fim, a configuração da aplicação.

**Why this priority**: é a primeira tela depois de escolher a aplicação. Sem ela, saber se algo
mudou exige abrir pesquisa por pesquisa.

**Independent Test**: com uma aplicação com pesquisas publicadas e coleta, trocar o período entre
7, 30 e 90 dias e conferir que os KPIs, a tendência e a URL mudam; numa aplicação sem pesquisa,
conferir o convite a criar a primeira.

**Acceptance Scenarios**:

1. **Given** uma aplicação com pesquisas, **When** abro a Visão geral, **Then** vejo os quatro
   KPIs, a tendência, os desfechos, o NPS consolidado, o desempenho por pesquisa e a configuração.
2. **Given** o período padrão, **When** abro a Visão geral, **Then** ele é 30 dias; **When**
   escolho 7 ou 90, **Then** a URL guarda `periodo` e todos os números mudam juntos.
3. **Given** dados no período anterior, **When** vejo um KPI, **Then** a variação aparece com
   seta, valor (relativo nas exibições, em pontos na taxa e no abandono, em pontos de NPS) e cor
   pelo sentido bom; **Given** sem base anterior, **Then** não há variação.
4. **Given** a taxa de resposta, **When** a leio, **Then** ela é concluídas somadas sobre exibidas
   somadas de todas as pesquisas publicadas.
5. **Given** dias sem exibição no período, **When** vejo a tendência, **Then** eles aparecem com
   zero, e o detalhe de cada dia mostra exibidas, concluídas e a taxa do dia.
6. **Given** nenhuma pesquisa de NPS, **When** vejo o KPI de NPS, **Then** ele mostra "—" e
   "nenhuma pesquisa de NPS".
7. **Given** uma aplicação sem pesquisas, **When** abro a Visão geral, **Then** vejo "Nenhuma
   pesquisa ainda" com "Nova pesquisa", e a configuração continua visível.
8. **Given** `?periodo=15`, **When** abro a Visão geral, **Then** ela usa 30 dias.
9. **Given** a leitura de respondentes ou do SDK indisponível, **When** abro a Visão geral,
   **Then** só aquele bloco fica vazio; sem a lista de pesquisas, a tela cai no estado de falha.

---

### User Story 3 - Comparar segmentos (Priority: P2)

Na Visão geral, a **análise por segmento** mostra, para a pesquisa de NPS com mais respostas no
período, o NPS e a taxa de resposta por valor de um atributo (plano, plataforma, país…). O NPS é
uma barra divergente — zero no meio, verde para a direita, vermelho para a esquerda — e a taxa,
uma barra simples. O atributo é escolhido entre os vistos, fica na URL e sobrevive à troca de
período.

**Why this priority**: o número consolidado esconde o segmento que está puxando para baixo. É a
pergunta seguinte, não a primeira.

**Independent Test**: com respostas de NPS com atributo `plano` em dois valores, abrir a Visão
geral, conferir uma linha por valor com NPS e taxa, trocar o atributo e o período.

**Acceptance Scenarios**:

1. **Given** uma pesquisa de NPS com respostas e atributos, **When** abro a Visão geral, **Then**
   vejo uma linha por valor do atributo, até seis, do mais frequente para o menos, com exibições,
   NPS divergente e taxa.
2. **Given** nenhum atributo escolhido, **When** abro, **Then** o atributo inicial é o que tem
   menos valores entre os que têm ao menos dois.
3. **Given** a lista de atributos, **When** escolho outro, **Then** a URL guarda `segmento` e o
   período é mantido; **When** troco o período, **Then** o segmento é mantido.
4. **Given** nenhuma pesquisa de NPS com respostas no período, **When** vejo o bloco, **Then** ele
   diz que precisa de uma.
5. **Given** nenhum atributo recebido, **When** vejo o bloco, **Then** ele explica que o app envia
   atributos junto do evento.

---

### User Story 4 - Ver o desempenho de cada pesquisa (Priority: P2)

Na Visão geral, uma tabela lista as pesquisas da aplicação, da mais exibida para a menos, com
nome (e modelo, quando houver), estado, uma miniatura da tendência de exibições, exibições,
concluídas, taxa com a variação contra o período anterior e NPS. O nome leva aos resultados da
pesquisa.

**Why this priority**: aponta qual pesquisa olhar primeiro; depende da Visão geral existir.

**Independent Test**: com três pesquisas de volumes diferentes, conferir a ordem, a miniatura e o
vínculo para os resultados.

**Acceptance Scenarios**:

1. **Given** pesquisas com exibições, **When** vejo a tabela, **Then** elas vêm da mais exibida
   para a menos.
2. **Given** uma pesquisa, **When** clico no nome, **Then** chego aos resultados dela.
3. **Given** variação de taxa de ao menos meio ponto, **When** a vejo, **Then** aparece com seta e
   texto para leitor de tela; abaixo disso, não aparece.
4. **Given** uma pesquisa com menos de dois dias de dados, **When** vejo a miniatura, **Then** ela
   mostra "—".
5. **Given** taxa ou NPS ausentes, **When** os vejo, **Then** aparecem como "—".

---

### User Story 5 - Ativar e desativar a aplicação pela Visão geral (Priority: P3)

O cabeçalho da Visão geral oferece Editar (de `005-painel-saude-privacidade`) e Desativar — ou
Reativar, quando inativa. Desativar pede confirmação dizendo que o SDK deixa de receber pesquisas
na hora e que o histórico continua acessível; reativar não pede.

**Why this priority**: operação rara, mas antes só era possível pela API.

**Independent Test**: desativar com confirmação, conferir o estado no detalhe e na listagem,
reativar; cancelar a confirmação e conferir que continua ativa.

**Acceptance Scenarios**:

1. **Given** uma aplicação ativa, **When** peço para desativar, **Then** um diálogo explica o
   efeito; **When** cancelo, **Then** ela continua ativa.
2. **Given** a confirmação, **When** confirmo, **Then** a aplicação aparece inativa na Visão geral
   e na listagem, e a ação passa a ser Reativar.
3. **Given** uma aplicação inativa, **When** reativo, **Then** ela volta a ativa sem confirmação.

---

### User Story 6 - Alternar entre tema claro e escuro (Priority: P2)

O painel abre no tema escuro. Um botão na barra lateral alterna para o claro e de volta, e a
escolha vale para todas as telas, inclusive gráficos e a pré-visualização da pesquisa.

**Why this priority**: o escuro atende quem passa o dia no painel; o claro atende projeção e
impressão de tela. Nenhum dos dois bloqueia trabalho.

**Independent Test**: alternar o tema, navegar por Visão geral, resultados e montagem, e conferir
contraste e legibilidade nos dois.

**Acceptance Scenarios**:

1. **Given** a primeira visita, **When** abro o painel, **Then** ele está no tema escuro.
2. **Given** o botão de tema, **When** o aciono, **Then** o painel inteiro troca de tema sem
   recarregar, e o rótulo acessível diz "Alternar tema claro e escuro".
3. **Given** um tema escolhido, **When** navego, **Then** ele se mantém.
4. **Given** os dois temas, **When** leio qualquer texto, **Then** o contraste é de ao menos 4.5:1.

---

### User Story 7 - Usar o painel no celular (Priority: P2)

Abaixo da largura de desktop, a barra lateral some e uma barra superior mostra o botão de menu e a
marca. O menu abre a barra lateral como gaveta sobre o conteúdo; Escape, o fundo ou o botão de
fechar a fecham, e navegar por ela também. As grades de KPIs e gráficos viram uma coluna, as
tabelas rolam na horizontal sem quebrar a página e as abas da pesquisa rolam de lado.

**Why this priority**: quem pesquisa confere números fora da mesa; a autoria continua sendo de
desktop.

**Independent Test**: numa largura de celular, abrir o menu, ir para a Visão geral de outra
aplicação, conferir que a gaveta fecha ao navegar e que a página não tem rolagem horizontal.

**Acceptance Scenarios**:

1. **Given** uma tela estreita, **When** abro o painel, **Then** vejo a barra superior com menu e
   marca, e não a barra lateral fixa.
2. **Given** o menu, **When** o abro, **Then** a gaveta aparece como diálogo modal; **When** aperto
   Escape, toco no fundo ou no botão de fechar, **Then** ela fecha.
3. **Given** a gaveta aberta, **When** sigo um item, **Then** ela fecha e a tela muda.
4. **Given** uma tabela larga, **When** a vejo no celular, **Then** só a tabela rola na horizontal.

---

### Edge Cases

- **Período inválido na URL**: cai em 30 dias.
- **Segmento inexistente na URL**: cai no atributo inicial.
- **Taxa sem exibição, NPS sem resposta, abandono sem exibição**: "—", nunca zero.
- **Variação sem base anterior**: não aparece; variação nula aparece sem seta, como estável.
- **Pesquisa nunca publicada**: entra na tabela de desempenho com zeros de contagem e estado, mas
  não é lida para somar.
- **Lista de aplicações indisponível**: a barra lateral aparece sem aplicações no seletor; o resto
  do painel continua.
- **Aplicação inexistente**: tela de não encontrado.
- **Pré-visualização e gráficos**: seguem o tema corrente.

## Requirements *(mandatory)*

### Functional Requirements

**Design System**

- **FR-001**: Todas as telas MUST usar os tokens do Design System v2 para superfícies, texto,
  marca, status e gráficos, nos dois temas.
- **FR-002**: O amarelo da marca MUST marcar apenas o CTA principal, o item ativo da navegação e no
  máximo um destaque por tela, e MUST NOT ser usado como série de gráfico.
- **FR-003**: Status MUST NOT ser comunicado só por cor: seta, ícone, rótulo ou texto para leitor
  de tela acompanham.
- **FR-004**: Valores ausentes MUST aparecer como "—"; números em coluna MUST ter largura fixa de
  dígito.
- **FR-005**: NPS MUST usar cores semânticas (promotores, neutros, detratores) com a faixa
  rotulada; desfechos MUST usar cores fixas por desfecho.

**Shell**

- **FR-006**: As telas sob `/aplicacoes` MUST ter uma barra lateral com marca, troca de tema,
  seletor de aplicação e seções.
- **FR-007**: Dentro de uma aplicação, a barra MUST agrupar as seções em Análise (Visão geral,
  Pesquisas, Respondentes) e Operação (Chaves, Saúde, Privacidade), destacando a seção atual.
- **FR-008**: O seletor MUST listar as aplicações com o ponto de situação e oferecer "Todas as
  aplicações" e "Nova aplicação"; escolher uma aplicação MUST levar à sua Visão geral.
- **FR-009**: A barra MUST ser navegável enquanto a lista de aplicações carrega.

**Tema**

- **FR-010**: O painel MUST abrir no tema escuro e oferecer alternância para o claro, mantida entre
  telas.

**Visão geral**

- **FR-011**: A página da aplicação MUST ser a Visão geral, com o período (7, 30, 90 dias) na URL,
  30 por padrão e valor inválido caindo no padrão.
- **FR-012**: A Visão geral MUST exibir os KPIs de exibições, taxa de resposta, NPS e abandono, com
  variação contra o período anterior de mesma duração quando houver base.
- **FR-013**: A taxa de resposta e o abandono da aplicação MUST ser calculados sobre as contagens
  somadas, nunca como média de taxas; o NPS consolidado MUST ser recalculado sobre os grupos
  somados das pesquisas de NPS.
- **FR-014**: A Visão geral MUST exibir a tendência diária de exibições e conclusões do período,
  com dias sem exibição como zero e a taxa do dia no detalhe.
- **FR-015**: A Visão geral MUST exibir os desfechos em barra 100% com contagem e proporção por
  desfecho, e o NPS consolidado com os três grupos.
- **FR-016**: A Visão geral MUST exibir respondentes conhecidos, adoção das versões do SDK e
  contagem de erros do SDK, com vínculo para Respondentes e Saúde.
- **FR-017**: A Visão geral MUST exibir a análise por segmento: para a pesquisa de NPS com mais
  respostas no período, NPS (barra divergente) e taxa por valor do atributo escolhido, até seis
  valores, com o atributo na URL (`segmento`) e preservado ao trocar de período.
- **FR-018**: A Visão geral MUST exibir o desempenho por pesquisa, da mais exibida para a menos,
  com estado, miniatura da tendência, exibições, concluídas, taxa com variação e NPS, e vínculo
  para os resultados.
- **FR-019**: Sem pesquisas, a Visão geral MUST convidar a criar a primeira, mantendo a
  configuração visível.
- **FR-020**: Falha na lista de pesquisas MUST levar ao estado de falha; falha em leitura
  complementar (respondentes, SDK, erros) MUST apenas esvaziar o bloco correspondente.
- **FR-021**: A Visão geral MUST exibir a configuração da aplicação e oferecer editar, desativar
  (com confirmação que explica o efeito) e reativar (sem confirmação).

**Celular**

- **FR-022**: Abaixo da largura de desktop, a barra lateral MUST virar gaveta modal aberta por um
  botão de menu, fechável por Escape, pelo fundo, pelo botão de fechar e ao navegar.
- **FR-023**: O conteúdo MUST caber na largura do celular: grades em uma coluna, tabelas e abas com
  rolagem horizontal própria, sem rolagem horizontal da página.

### Key Entities

- **Token de design**: papel visual (superfície, texto, marca, status, gráfico, borda) com valor por
  tema.
- **Visão geral da aplicação**: período, totais e totais anteriores, NPS e NPS anterior, linha do
  tempo, desempenho por pesquisa, contagem por estado, respondentes, versões do SDK, erros do SDK e
  pesquisa de NPS em destaque com seus atributos.
- **Totais**: exibidas, concluídas, dispensadas, abandonadas, em andamento e taxa (ausente sem
  exibição).
- **Linha de segmento**: valor do atributo, exibidas, concluídas, taxa e NPS.
- **Desempenho por pesquisa**: identificação, estado, modelo, totais, taxa anterior, NPS e linha do
  tempo.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A partir de qualquer tela de uma aplicação, a Visão geral de outra aplicação está a
  no máximo 2 cliques.
- **SC-002**: Quem opera responde "a taxa de resposta deste app subiu ou caiu neste mês?" sem sair
  da Visão geral.
- **SC-003**: A taxa de resposta da aplicação é sempre igual a concluídas ÷ exibidas sobre o total
  das pesquisas, verificado por teste automatizado.
- **SC-004**: 100% dos textos passam 4.5:1 de contraste nos dois temas.
- **SC-005**: Nenhuma tela do painel tem rolagem horizontal de página numa largura de 360px.
- **SC-006**: Nenhum indicador de status ou variação depende só de cor — verificável em revisão de
  tela.
- **SC-007**: Os testes ponta a ponta alcançam Respondentes, Saúde e Privacidade pela barra
  lateral, e a raiz do painel leva à listagem de aplicações com título de página visível.

## Assumptions

- Os valores dos tokens vêm do Design System Pitaco; `DESIGN.md` é o resumo para quem escreve código
  e `globals.css` a tradução para Tailwind.
- A Visão geral soma o que o backend apura por pesquisa; não há endpoint de visão consolidada da
  aplicação, e o painel não cria métrica nova.
- Uma aplicação tem até 100 pesquisas para a Visão geral (uma página da listagem).
- A linha do tempo da Visão geral é por dia de abertura em UTC, como o backend agrupa, e o gráfico
  diz isso.
- A autoria continua sendo uso de desktop; no celular o foco é leitura.
- A escolha de tema é guardada no navegador de quem usa.

## Dependencies

- `001-painel-operacao-pesquisas` a `005-painel-saude-privacidade`: todas as telas que o shell e o
  Design System passam a envolver.
- `004-painel-resultados-comportamento`: o resultado por pesquisa que a Visão geral soma.
- `005-painel-saude-privacidade`: versões e erros do SDK; diálogo de edição da aplicação.
- `002-painel-leitura-coleta`: total de respondentes.
- API administrativa do backend, em particular `backend 005-application-read-list` (aplicações,
  ativar e desativar).
