# Feature Specification: Aplicações — listagem e consulta

**Feature Branch**: `005-application-read-list`

**Created**: 2026-09-08

**Status**: Draft

**Input**: User description: "Vamos criar um endpoint para listar aplicações e buscar uma aplicação."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver todas as aplicações cadastradas (Priority: P1)

Quem opera o painel precisa saber quais aplicações existem: quantas são, como se chamam,
qual o identificador público de cada uma, se estão ativas ou inativas e desde quando
existem. Hoje esse conhecimento só existe na resposta da criação — quem não anotou o
identificador perdeu o caminho para tudo o que pende da aplicação: emitir chave, listar
chaves, publicar pesquisa. A listagem devolve o conjunto de aplicações com identificador,
slug, nome, estado e instante de criação, paginado e com total.

**Why this priority**: é a porta de entrada de todo o resto do produto. Sem ela o painel
não tem primeira tela: não há como chegar a uma aplicação sem já saber o identificador
dela. Entrega valor sozinha e não depende da consulta individual existir.

**Independent Test**: criar três aplicações, desativar uma, listar e conferir que as três
aparecem com o estado correto, com identificador, slug, nome e instante de criação, e que
a paginação informa o total.

**Acceptance Scenarios**:

1. **Given** três aplicações cadastradas, **When** listo as aplicações, **Then** recebo as
   três, cada uma com identificador, slug, nome, estado e instante de criação.
2. **Given** uma aplicação ativa e uma inativa, **When** listo as aplicações, **Then** as
   duas aparecem, distinguíveis pelo estado — inatividade não esconde a aplicação.
3. **Given** nenhuma aplicação cadastrada, **When** listo as aplicações, **Then** recebo um
   resultado vazio com total zero — não um erro.
4. **Given** mais aplicações do que cabe em uma página, **When** listo, **Then** recebo a
   primeira página junto com o total existente e o suficiente para pedir a próxima.
5. **Given** aplicações criadas em instantes diferentes, **When** listo sem pedir ordenação,
   **Then** elas vêm da mais recente para a mais antiga.
6. **Given** aplicações ativas e inativas, **When** listo restringindo ao estado ativo,
   **Then** apenas as ativas aparecem, e o total reflete apenas as ativas.
7. **Given** um pedido de paginação fora dos limites aceitos, **When** listo, **Then** a
   operação é recusada por validação indicando o campo e o motivo, sem resultado parcial.

---

### User Story 2 - Consultar uma aplicação específica (Priority: P2)

Quem abre a tela de uma aplicação — ou quem confere uma configuração antes de publicar uma
pesquisa — precisa ver a aplicação inteira, não só o resumo que cabe numa linha de lista:
além de identificador, slug, nome, estado e instante de criação, também o intervalo de
descanso, o prazo de retenção, o prazo de retenção de texto livre e o instante da última
alteração. A consulta devolve isso para uma única aplicação.

**Why this priority**: é o que transforma uma linha da lista em uma tela. Mas quem tem a
listagem já sabe que a aplicação existe e como ela se chama — a consulta aprofunda, não
desbloqueia. Por isso vem depois.

**Independent Test**: criar uma aplicação com prazos configurados, consultá-la e conferir
que todos os campos batem com o que foi definido na criação; depois consultar um
identificador inexistente e conferir que é recusado como não encontrada.

**Acceptance Scenarios**:

1. **Given** uma aplicação cadastrada com prazos configurados, **When** a consulto, **Then**
   recebo identificador, slug, nome, estado, intervalo de descanso, prazo de retenção,
   prazo de retenção de texto livre, instante de criação e instante da última alteração.
2. **Given** uma aplicação sem nenhum prazo configurado, **When** a consulto, **Then** ela é
   encontrada e os prazos não configurados são apresentados como ausentes — não como zero.
3. **Given** uma aplicação inativa, **When** a consulto, **Then** ela é encontrada, com o
   estado de inativa.
4. **Given** um identificador de aplicação que não existe, **When** a consulto, **Then** a
   operação é recusada informando que a aplicação não foi encontrada.
5. **Given** um identificador em formato inválido, **When** a consulto, **Then** a operação
   é recusada com o mesmo código de aplicação não encontrada, sem distinguir malformado de
   inexistente.

---

### Edge Cases

- Nenhuma aplicação cadastrada: resultado vazio com total zero, nunca recusa.
- Página além do fim do conjunto: resultado vazio com o total correto, nunca recusa.
- Parâmetro de paginação fora do aceito (página negativa, tamanho zero, tamanho acima do
  máximo): recusado por validação, indicando o campo e o motivo.
- Filtro de estado com valor desconhecido: recusado por validação, indicando os valores
  aceitos — não silenciosamente ignorado.
- Identificador de aplicação em formato inválido: tratado como não encontrado, sem
  distinguir malformado de inexistente — o mesmo tratamento já dado na listagem de chaves.
- Aplicação criada entre a montagem de uma página e o pedido da seguinte: cada página
  reflete o estado no instante em que foi lida; a listagem não promete uma foto imutável.
- Duas aplicações criadas no mesmo instante: a ordenação permanece determinística, sem
  repetir nem omitir uma delas entre páginas.
- Volume grande de aplicações: a resposta continua paginada e o custo de ler uma página não
  cresce com o total cadastrado.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir listar as aplicações cadastradas.
- **FR-002**: Cada aplicação listada MUST expor identificador, slug, nome, estado (ativa ou
  inativa) e instante de criação.
- **FR-003**: A listagem sem filtro MUST incluir tanto as aplicações ativas quanto as
  inativas, distinguíveis pelo estado — desativar uma aplicação não a faz sumir do painel.
- **FR-004**: A listagem MUST oferecer um filtro por estado, permitindo restringir o
  resultado às ativas ou às inativas.
- **FR-005**: A listagem MUST ser paginada, aceitando página e tamanho de página, com
  valores padrão quando não informados.
- **FR-006**: A listagem MUST informar, junto com a página devolvida, o total de aplicações
  que atendem ao pedido e o suficiente para navegar até a próxima página.
- **FR-007**: A ordenação padrão da listagem MUST ser por instante de criação, da mais
  recente para a mais antiga, e MUST ser determinística mesmo entre aplicações criadas no
  mesmo instante.
- **FR-008**: O sistema MUST devolver resultado vazio, e não recusa, quando não houver
  aplicação que atenda ao pedido.
- **FR-009**: O sistema MUST recusar por validação um pedido de paginação fora dos limites
  aceitos, ou um filtro de estado com valor desconhecido, indicando o campo e o motivo, e
  MUST NOT devolver resultado parcial nesse caso.
- **FR-010**: O sistema MUST permitir consultar uma única aplicação pelo seu identificador.
- **FR-011**: A consulta individual MUST expor, além do que a listagem expõe, o intervalo de
  descanso, o prazo de retenção, o prazo de retenção de texto livre e o instante da última
  alteração.
- **FR-012**: Prazos não configurados MUST ser apresentados como ausentes, distinguíveis de
  um prazo configurado com valor — a ausência é uma decisão, não um zero.
- **FR-013**: O sistema MUST recusar a consulta de uma aplicação inexistente com o mesmo
  código de erro estável de aplicação não encontrada já usado nas operações sobre chaves.
- **FR-014**: A consulta MUST encontrar aplicações inativas, apresentando-as com o estado de
  inativa.
- **FR-015**: Nenhuma leitura MUST expor segredo de chave de API, nem em texto claro nem na
  representação irreversível dele — nem no corpo, nem em mensagem de erro, nem em log.
- **FR-016**: Nenhuma operação desta fatia MUST alterar o estado armazenado — listagem e
  consulta são estritamente de leitura.
- **FR-017**: Toda recusa MUST ser comunicada com um código de erro estável, distinto por
  motivo, que o cliente possa tratar sem depender do texto da mensagem.
- **FR-018**: O custo de ler uma página MUST NOT crescer com o total de aplicações
  cadastradas — o conjunto lido é limitado ao tamanho da página pedida.
- **FR-019**: Ambas as leituras MUST pertencer à superfície administrativa, recusando
  credencial de aplicação com o mesmo código estável já usado nas demais operações
  administrativas.

### Key Entities

- **Aplicação**: entidade já existente, criada na fatia de gestão de chaves. Esta fatia não
  muda sua forma: apenas dá caminhos de leitura sobre o que já está guardado —
  identificador, slug, nome, estado, intervalo de descanso, prazo de retenção, prazo de
  retenção de texto livre, instante de criação e instante da última alteração.
- **Página de aplicações**: recorte do conjunto de aplicações que atende a um pedido — as
  aplicações da página, o total que atende ao pedido e a posição dentro do conjunto.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Quem opera o painel descobre o identificador de qualquer aplicação em uma
  única operação, sem ter guardado nada da criação — o identificador deixa de ser
  informação perecível.
- **SC-002**: 100% das aplicações cadastradas são alcançáveis pela listagem, percorrendo as
  páginas, sem repetição e sem omissão.
- **SC-003**: A partir de uma aplicação recém-criada, é possível chegar a todas as suas
  configurações em no máximo duas operações de leitura — listar e consultar.
- **SC-004**: 100% dos caminhos de recusa previstos nesta especificação respondem com um
  código de erro estável e distinto por motivo.
- **SC-005**: Nenhuma leitura altera o estado armazenado — verificável relendo o conjunto de
  aplicações antes e depois de cada leitura, inclusive nas que falham.
- **SC-006**: Nenhum segredo de chave de API aparece em qualquer resposta desta fatia,
  verificável varrendo todos os caminhos de sucesso e de falha.
- **SC-007**: Um ambiente com muitas aplicações responde a listagem no mesmo patamar de
  tempo de um ambiente com poucas — o crescimento do conjunto não degrada a leitura de uma
  página.

## Assumptions

- A leitura de aplicações é global: não existe hoje o conceito de dono acima da aplicação,
  então toda aplicação cadastrada é visível a quem alcança a superfície administrativa.
  Escopar a visibilidade por dono é trabalho de uma fatia futura de contas e é pré-requisito
  para operar com mais de um cliente.
- A superfície administrativa segue com o mesmo patamar de proteção do que já existe para
  criar aplicação e emitir chave: credencial de aplicação é recusada, e o endurecimento
  restante pertence à fatia de autenticação do painel.
- Busca textual por nome ou slug, filtro por intervalo de datas e ordenação escolhida por
  quem chama estão **fora de escopo** nesta fatia. O filtro por estado entra porque separa o
  que está em operação do que foi desativado — a pergunta que motiva a leitura.
- Tamanho de página padrão de 20 e máximo de 100, alinhado ao que já vale para a listagem de
  chaves; nenhum caso conhecido exige mais.
- Contagens agregadas na linha da listagem — quantas chaves, quantas pesquisas, quantas
  respostas cada aplicação tem — estão **fora de escopo**: cada uma é uma pergunta com custo
  próprio, e nenhuma tela hoje depende delas. Entram quando houver a tela que as exija.
- A listagem devolve o resumo e a consulta devolve o detalhe. São formas diferentes de
  propósito: carregar prazos de retenção em cada linha de uma lista é peso sem leitor.
- Depende das entidades e do armazenamento criados na fatia `001-api-key-management` como
  fonte de verdade; esta fatia não introduz dado novo na aplicação e não cria migration de
  coluna — apenas o índice que a ordenação e o filtro exigirem.
- A consulta individual acontece pelo identificador, não pelo slug: é o identificador que
  volta no cabeçalho `Location` da criação e o que já endereça a aplicação nas operações
  sobre chaves. Um caminho por slug entra se o painel precisar montar URL legível.
- O código de erro de aplicação inexistente já existe e é reaproveitado — não se cria código
  novo para o mesmo motivo.
