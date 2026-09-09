# Feature Specification: Painel de leitura da coleta — exibições, respostas e respondentes

**Feature Branch**: `002-painel-leitura-coleta`

**Created**: 2026-09-09

**Status**: Draft

**Input**: User description: "adicionamos mais endpoints no backend, precisamos continuar implementando o painel."

## Contexto

A entrega anterior (`001-painel-operacao-pesquisas`) fechou o ciclo de autoria: quem pesquisa
cadastra a aplicação, emite chaves, monta perguntas, define disparo, publica versões e controla o
que está no ar. Aquela spec deixou uma fronteira explicitamente de fora, e disse por quê: **a
leitura de respostas e resultados**, porque o backend ainda não tinha de onde lê-las.

Agora tem. O backend passou a expor o eixo de coleta: as exibições de uma pesquisa (com filtro por
versão, desfecho e período), o detalhe de uma exibição (com o instantâneo de atributos e as
respostas dadas), a lista de respondentes vistos pela aplicação e o histórico de exibições de cada
respondente.

Esta feature entrega essa superfície no painel — e só ela. Continua fora do escopo tudo que a API
não oferece: agregações estatísticas prontas, exportação de dados e comparação entre versões além
do grupo de comparabilidade que a própria exibição já carrega.

O valor entregue é verificável sem tocar em código: depois de publicar uma pesquisa e receber
coleta, quem pesquisa consegue ver quem respondeu, o que respondeu e quando — sem pedir a ninguém
que consulte a API.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver as exibições de uma pesquisa (Priority: P1)

Quem publicou uma pesquisa precisa saber se ela está sendo exibida e como está terminando. Na
pesquisa, uma seção nova lista as exibições — a mais recente primeiro — mostrando de qual versão
cada uma veio, em que desfecho parou, quando abriu e quando fechou. Filtros permitem restringir a
uma versão, a um desfecho e a um período de abertura.

**Why this priority**: é o primeiro sinal de vida de uma pesquisa publicada. Sem ele, publicar é um
ato cego — não há como distinguir "ninguém viu" de "viram e abandonaram". Entrega valor sozinha,
mesmo que nenhuma outra história seja construída.

**Independent Test**: com uma pesquisa publicada e exibições registradas, abrir a seção de
exibições, conferir a listagem, aplicar cada filtro isoladamente e confirmar que o recorte muda de
acordo — tudo sem sair da pesquisa.

**Acceptance Scenarios**:

1. **Given** uma pesquisa com exibições registradas, **When** abro a seção de exibições, **Then**
   vejo a listagem paginada com versão, grupo de comparabilidade, desfecho, abertura e fechamento
   de cada exibição.
2. **Given** a listagem aberta, **When** filtro por uma versão específica, **Then** só aparecem
   exibições daquela versão, e o filtro permanece ao paginar e ao voltar de um detalhe.
3. **Given** a listagem aberta, **When** filtro por desfecho, **Then** só aparecem exibições
   daquele desfecho.
4. **Given** a listagem aberta, **When** informo um período de abertura, **Then** só aparecem
   exibições abertas dentro dele, limites inclusive.
5. **Given** um período com início posterior ao fim, **When** aplico o filtro, **Then** vejo a
   recusa apontando o campo, e a listagem anterior permanece visível.
6. **Given** uma pesquisa sem nenhuma exibição, **When** abro a seção, **Then** vejo um estado
   vazio explicando que ainda não houve exibição — nunca um erro.
7. **Given** uma exibição ainda aberta, **When** a vejo na listagem, **Then** o fechamento aparece
   como ausente, e não como data vazia ou zero.

---

### User Story 2 - Ler o que foi respondido em uma exibição (Priority: P1)

Da listagem, abrir uma exibição mostra o que ela de fato produziu: as respostas na ordem das
perguntas da versão exibida, cada uma junto do enunciado que a originou, e o instantâneo dos
atributos que a aplicação informou no momento da elegibilidade.

**Why this priority**: é o conteúdo pelo qual a pesquisa existe. A listagem sem o detalhe mostra
que houve resposta, mas não qual. Depende da Story 1 apenas como caminho de navegação — o detalhe
é acessível por rota própria e testável isoladamente.

**Independent Test**: abrir a rota de detalhe de uma exibição concluída e conferir respostas,
enunciados e atributos; repetir com uma exibição dispensada e uma ainda aberta.

**Acceptance Scenarios**:

1. **Given** uma exibição concluída, **When** abro seu detalhe, **Then** vejo cada resposta ao lado
   do enunciado da pergunta correspondente na versão exibida, na ordem daquela versão.
2. **Given** uma resposta que a pessoa pulou, **When** vejo o detalhe, **Then** ela aparece
   marcada como pulada, distinta de uma resposta em branco.
3. **Given** uma resposta de texto livre cujo prazo de retenção da aplicação já venceu, **When**
   vejo o detalhe, **Then** ela aparece marcada como expirada, explicando que o texto foi
   descartado por retenção — não como resposta vazia.
4. **Given** uma exibição ainda aberta ou dispensada, **When** abro seu detalhe, **Then** vejo o
   desfecho e os atributos, e um estado explícito de que não há respostas.
5. **Given** uma exibição com atributos informados, **When** abro seu detalhe, **Then** vejo o
   instantâneo de atributos identificado como pertencente àquela exibição, não ao respondente.
6. **Given** um identificador de exibição inexistente na aplicação, **When** acesso a rota,
   **Then** vejo a tela de não encontrado com caminho de volta.
7. **Given** o detalhe aberto, **When** sigo o vínculo do respondente, **Then** chego ao histórico
   daquele respondente.

---

### User Story 3 - Ver os respondentes da aplicação e o histórico de cada um (Priority: P2)

Na aplicação, ao lado de chaves e pesquisas, uma seção lista quem a aplicação já viu: como cada
respondente foi identificado, com que valor, quando apareceu pela primeira vez e quando foi visto
por último. Abrir um respondente mostra todas as exibições que ele recebeu, de qualquer pesquisa,
com os mesmos filtros de desfecho e período.

**Why this priority**: responde a perguntas que o eixo da pesquisa não responde — com que
frequência a mesma pessoa é abordada, e se alguém está sendo exposto demais. É valioso, mas só
depois que existe leitura por pesquisa.

**Independent Test**: abrir a seção de respondentes de uma aplicação com coleta, conferir a
listagem paginada, abrir um respondente e conferir seu histórico com os filtros aplicados.

**Acceptance Scenarios**:

1. **Given** uma aplicação com respondentes registrados, **When** abro a seção, **Then** vejo a
   listagem paginada com forma de identificação, valor, primeiro e último contato.
2. **Given** a listagem, **When** abro um respondente, **Then** vejo suas exibições com a pesquisa
   e a versão de cada uma, da mais recente para a mais antiga.
3. **Given** o histórico de um respondente, **When** filtro por desfecho ou por período, **Then** o
   recorte muda de acordo, e o filtro por versão não é oferecido aqui — o histórico atravessa
   pesquisas diferentes.
4. **Given** uma aplicação sem nenhum respondente, **When** abro a seção, **Then** vejo um estado
   vazio explicando que a aplicação ainda não recebeu contato — nunca um erro.
5. **Given** uma exibição no histórico, **When** a abro, **Then** chego ao mesmo detalhe de exibição
   da Story 2.

---

### Edge Cases

- **Filtro sem resultado**: recorte que não casa com nada mostra estado vazio explicando que o
  filtro não encontrou exibições, com ação de limpar o filtro — distinto do vazio de "nunca houve
  exibição", que convida a publicar.
- **Falha de leitura**: qualquer listagem ou detalhe que falhe mostra o estado de falha com nova
  tentativa, sem derrubar o restante da tela.
- **Versão do SDK ausente**: exibição sem versão informada mostra a ausência explicitamente, nunca
  um traço vazio ambíguo.
- **Pesquisa nunca publicada**: a seção de exibições existe, mas o vazio explica que a pesquisa
  ainda não foi publicada, em vez de sugerir que ninguém a viu.
- **Página fora do intervalo**: navegar para uma página além do total devolve ao recorte válido em
  vez de erro.
- **Pergunta removida em versão posterior**: o detalhe da exibição usa a versão exibida, não a
  atual — uma resposta a pergunta que já não existe continua legível.
- **Instante em fuso**: abertura e fechamento chegam em UTC e são exibidos no fuso de referência
  do painel, indicado na tela; os filtros de período usam a mesma convenção, de modo que filtrar
  por um dia case com o dia que a tela mostra.
- **Atributos vazios**: exibição sem atributos informados mostra estado explícito de ausência.
- **Aplicação inexistente**: rotas de respondentes sob uma aplicação inexistente levam à tela de
  não encontrado.

## Requirements *(mandatory)*

### Functional Requirements

**Exibições de uma pesquisa**

- **FR-001**: O painel MUST oferecer, dentro de uma pesquisa, uma seção de exibições, ao lado de
  montagem, disparo, publicação e versões.
- **FR-002**: A listagem de exibições MUST apresentar, para cada exibição, a versão exibida (por
  número), o grupo de comparabilidade, o desfecho, a versão do SDK, o instante de abertura e o de
  fechamento.
- **FR-003**: A listagem MUST ser paginada, com o recorte de página refletido na URL.
- **FR-004**: A listagem MUST ordenar da exibição mais recente para a mais antiga.
- **FR-005**: A listagem MUST oferecer filtro por versão, por desfecho e por período de abertura,
  todos combináveis e todos refletidos na URL.
- **FR-006**: O filtro de desfecho MUST oferecer exatamente os desfechos que o backend aceita
  filtrar, sem inventar opções que sempre devolveriam vazio.
- **FR-007**: Um período com início posterior ao fim MUST ser recusado com mensagem apontando o
  campo, preservando o que foi digitado e a listagem anterior.
- **FR-008**: Um valor de fechamento ausente MUST ser exibido como exibição ainda aberta, nunca
  como data vazia ou zero.

**Detalhe de uma exibição**

- **FR-009**: O painel MUST oferecer uma tela de detalhe de exibição, alcançável a partir da
  listagem por pesquisa e do histórico de respondente.
- **FR-010**: O detalhe MUST apresentar respondente, pesquisa, versão exibida, grupo de
  comparabilidade, desfecho, versão do SDK, abertura e fechamento.
- **FR-011**: O detalhe MUST apresentar as respostas na ordem das perguntas da versão exibida, cada
  uma acompanhada do enunciado da pergunta que a originou naquela versão.
- **FR-012**: Uma resposta pulada MUST ser exibida como pulada, e uma resposta de texto livre
  descartada por retenção MUST ser exibida como expirada, com explicação — nenhuma das duas pode
  aparecer como resposta em branco.
- **FR-013**: O detalhe MUST apresentar cada resposta no formato do tipo da pergunta: texto,
  número ou opções escolhidas.
- **FR-014**: O detalhe MUST apresentar o instantâneo de atributos identificado como pertencente à
  exibição, e MUST mostrar ausência explícita quando não houver atributo.
- **FR-015**: Uma exibição sem respostas MUST mostrar estado explícito de ausência, coerente com o
  desfecho, em vez de lista vazia sem explicação.
- **FR-016**: O detalhe MUST oferecer vínculo para o respondente e para a pesquisa da exibição.
- **FR-017**: Identificador de exibição desconhecido na aplicação MUST levar à tela de não
  encontrado, com caminho de volta.

**Respondentes**

- **FR-018**: O painel MUST oferecer, dentro de uma aplicação, uma seção de respondentes, ao lado
  de chaves e pesquisas.
- **FR-019**: A listagem de respondentes MUST apresentar a forma de identificação, o valor
  informado, o primeiro contato e o último contato, paginada e com o recorte na URL.
- **FR-020**: A forma de identificação MUST ser exibida em linguagem de quem opera o painel, não
  como o código cru do backend.
- **FR-021**: O painel MUST oferecer uma tela de histórico do respondente com suas exibições, de
  qualquer pesquisa, identificando pesquisa e versão de cada uma.
- **FR-022**: O histórico MUST oferecer filtro por desfecho e por período, e MUST NOT oferecer
  filtro por versão, por atravessar pesquisas diferentes.
- **FR-023**: O histórico MUST ordenar da exibição mais recente para a mais antiga e MUST ser
  paginado.

**Estados, leitura e limites**

- **FR-024**: Toda listagem e todo detalhe desta feature MUST distinguir quatro estados:
  carregando, conteúdo, vazio e falha.
- **FR-025**: O estado vazio MUST distinguir "nunca houve coleta" de "o filtro não encontrou nada",
  e o segundo MUST oferecer ação de limpar o filtro.
- **FR-026**: O estado de falha MUST oferecer nova tentativa sem exigir recarregar a página inteira.
- **FR-027**: Instantes MUST ser exibidos no fuso de referência do painel, a partir dos valores em
  UTC recebidos, e o fuso MUST estar indicado na tela. Os filtros de período MUST ser
  interpretados no mesmo fuso, para que o recorte case com o que a tela mostra.
- **FR-028**: Toda tela desta feature MUST ser somente leitura: o painel MUST NOT oferecer editar,
  apagar ou anonimizar exibições, respostas ou respondentes.
- **FR-029**: Os vínculos entre pesquisa, exibição e respondente MUST formar navegação fechada:
  de qualquer um dos três se alcança os outros dois.
- **FR-030**: A leitura desta feature MUST ser item a item: o painel MUST NOT exibir número
  agregado algum que a API não devolva diretamente, e MUST NOT emitir consulta cuja única
  finalidade seja produzir um número — nem contagem por desfecho, nem taxa, nem série temporal.
- **FR-031**: Quem quiser contar MUST conseguir fazê-lo aplicando o filtro correspondente e lendo
  o total do recorte que a própria paginação já informa.

### Key Entities

- **Exibição**: um momento em que uma versão de pesquisa foi mostrada a um respondente. Carrega a
  versão exibida e seu grupo de comparabilidade, o desfecho, a versão do SDK, a abertura e o
  fechamento. É a unidade de leitura da coleta.
- **Desfecho**: em que a exibição parou — iniciada, concluída ou dispensada. Fechamento existe se e
  somente se o desfecho é final.
- **Resposta**: o que foi respondido a uma pergunta dentro de uma exibição. Tem uma situação
  (respondida, pulada, expirada) e um valor cujo formato o tipo da pergunta determina. Liga-se à
  pergunta pela chave estável que atravessa versões.
- **Instantâneo de atributos**: os atributos que a aplicação informou quando consultou
  elegibilidade. Pertence à exibição, não ao respondente — o mesmo respondente pode ter
  instantâneos diferentes em exibições diferentes.
- **Respondente**: quem a aplicação já viu, identificado por um par opaco (forma e valor) que a
  própria aplicação informou. Guarda primeiro e último contato.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A partir de uma pesquisa publicada com coleta, quem opera o painel encontra as
  exibições daquela pesquisa em no máximo 2 cliques a partir da tela da pesquisa.
- **SC-002**: A partir de uma exibição na listagem, o conteúdo respondido fica visível em 1 clique.
- **SC-003**: 100% das telas de leitura desta feature exibem os quatro estados (carregando,
  conteúdo, vazio, falha) de forma distinguível por quem observa a tela.
- **SC-004**: 100% dos filtros oferecidos sobrevivem a paginar, recarregar a página e voltar de uma
  tela de detalhe.
- **SC-005**: Nenhuma tela desta feature exibe valor ausente como zero, data vazia ou traço
  ambíguo — cada ausência tem texto próprio, verificável em revisão de tela.
- **SC-006**: Uma resposta descartada por retenção é distinguível de uma resposta pulada e de uma
  resposta em branco por quem lê, sem consultar documentação.
- **SC-007**: Os fluxos de ver exibições de uma pesquisa, ler o conteúdo de uma exibição e navegar
  até o histórico do respondente estão cobertos por teste ponta a ponta.
- **SC-008**: Partindo de qualquer uma das três telas (pesquisa, exibição, respondente), as outras
  duas são alcançáveis apenas por vínculos da interface.
- **SC-009**: Cada carga de tela desta feature faz exatamente as leituras que seu conteúdo exige —
  nenhuma leitura existe só para produzir um número na tela.

## Assumptions

- A leitura desta entrega cobre exatamente as quatro consultas que o backend passou a oferecer:
  exibições de uma pesquisa, detalhe de uma exibição, respondentes de uma aplicação e exibições de
  um respondente. Nada além disso é antecipado.
- Não há endpoint de agregação, exportação ou comparação de resultados entre versões; portanto
  painéis estatísticos, gráficos, contagens por desfecho e exportação de dados ficam fora do
  escopo desta feature (FR-030). A única quantidade exibida é o total do recorte, que a paginação
  já devolve junto com os itens.
- Os enunciados das perguntas vêm da versão exibida, que o painel já sabe ler — é assim que a
  chave de pergunta de cada resposta ganha texto legível.
- Os endpoints de coleta usados pelo SDK (elegibilidade, abertura de exibição e envio de respostas)
  não pertencem ao painel e ficam fora do escopo, como no escopo anterior.
- O painel continua sem autenticação própria nesta entrega, seguindo o que já vale para as demais
  telas.
- Nenhuma anonimização, exclusão ou retificação de dado de respondente é oferecida — a API não
  expõe essas operações, e a retenção é decidida pela configuração da aplicação.
- Os padrões de paginação, estados de tela, tratamento de recusa e convenções de rota em português
  seguem o que a entrega anterior já estabeleceu — inclusive o fuso de referência único, já usado
  em todas as telas do painel, que esta entrega passa a exibir explicitamente.
- O backend pode ser alterado quando o contrato atual impedir um requisito, como no filtro por
  versão das exibições; alterações desse tipo ficam registradas no plano.
- O volume esperado por página é o mesmo das demais listagens do painel; não há requisito de
  visualização de séries longas nesta entrega.

## Dependencies

- Depende da entrega `001-painel-operacao-pesquisas`: navegação da aplicação, tela da pesquisa,
  leitura de versões, componentes compartilhados de listagem, estado vazio, falha e paginação.
- Depende das quatro consultas de coleta já disponíveis no backend e da leitura de versão de
  pesquisa, usada para dar enunciado às respostas.
