# Feature Specification: Chaves de API — listagem e consulta

**Feature Branch**: `002-api-key-read-list`

**Created**: 2026-09-08

**Status**: Draft

**Input**: User description: "Vamos criar o read/ list de api keys."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver as chaves de uma aplicação (Priority: P1)

Quem administra uma aplicação precisa saber quais chaves existem hoje: quantas são, para
onde vai cada uma, quando nasceram e quais já foram revogadas. Hoje esse conhecimento só
existe na resposta da emissão — se a pessoa não anotou o identificador, ela perdeu a
capacidade de revogar a chave. A listagem devolve, para a aplicação informada, o conjunto
de chaves com identificador, rótulo, prefixo público, instante de criação, estado e — quando
houver — instante da revogação. O segredo em texto claro nunca aparece.

**Why this priority**: é a história que devolve o controle. Sem ela, uma chave cujo
identificador se perdeu é uma chave que não se pode revogar; com ela, um vazamento volta
a ter remédio. Entrega valor sozinha e não depende da consulta individual existir.

**Independent Test**: emitir duas chaves para uma aplicação, revogar uma, listar as chaves
da aplicação e conferir que as duas aparecem com o estado correto, que nenhum segredo em
texto claro está presente na resposta, e que chaves de outra aplicação não aparecem.

**Acceptance Scenarios**:

1. **Given** uma aplicação com três chaves emitidas, **When** listo as chaves dessa
   aplicação, **Then** recebo as três, cada uma com identificador, rótulo, prefixo público,
   instante de criação e estado.
2. **Given** uma aplicação com uma chave válida e uma revogada, **When** listo as chaves,
   **Then** as duas aparecem, distinguíveis pelo estado, e a revogada traz o instante da
   revogação.
3. **Given** duas aplicações, cada uma com suas chaves, **When** listo as chaves de uma
   delas, **Then** nenhuma chave da outra aplicação aparece no resultado.
4. **Given** uma aplicação sem nenhuma chave emitida, **When** listo as chaves, **Then**
   recebo um resultado vazio — não um erro.
5. **Given** uma aplicação com chaves, **When** listo as chaves, **Then** nenhum segredo em
   texto claro nem representação irreversível dele aparece na resposta.
6. **Given** uma aplicação com mais chaves do que cabe em uma página, **When** listo as
   chaves, **Then** recebo a primeira página junto com a informação de quantas chaves
   existem no total e de como pedir a próxima página.
7. **Given** uma aplicação com chaves emitidas em instantes diferentes, **When** listo as
   chaves sem pedir ordenação, **Then** elas vêm da mais recente para a mais antiga.
8. **Given** um identificador de aplicação que não existe, **When** listo as chaves,
   **Then** a operação é recusada informando que a aplicação não foi encontrada.
9. **Given** uma aplicação inativa com chaves emitidas, **When** listo as chaves, **Then**
   a listagem funciona normalmente — inatividade impede emitir, não impede enxergar.

---

### User Story 2 - Consultar uma chave específica (Priority: P2)

Quem investiga um acesso suspeito tem em mãos apenas o prefixo público que apareceu em um
log, ou o identificador que anotou na emissão, e quer confirmar de qual chave se trata:
qual rótulo ela carrega, quando foi criada e se ainda está válida. A consulta devolve os
mesmos dados de uma linha da listagem, para uma única chave, no contexto da aplicação que
a emitiu.

**Why this priority**: resolve a pergunta pontual sem obrigar a varrer a listagem inteira,
e é o par natural do identificador que já volta na emissão e na exclusão. Mas quem tem a
listagem já consegue responder a mesma pergunta — por isso vem depois.

**Independent Test**: emitir uma chave, consultá-la pelo identificador e conferir que os
dados batem com os da emissão, sem o segredo; depois consultá-la informando outra aplicação
e conferir que não é encontrada.

**Acceptance Scenarios**:

1. **Given** uma chave válida existente, **When** consulto essa chave pelo identificador na
   aplicação dona, **Then** recebo identificador, rótulo, prefixo público, instante de
   criação e o estado de válida, sem nenhum segredo.
2. **Given** uma chave revogada, **When** a consulto, **Then** ela é encontrada, com o
   estado de revogada e o instante da revogação — revogar não apaga o registro.
3. **Given** um identificador de chave que não existe, **When** a consulto, **Then** a
   operação é recusada informando que a chave não foi encontrada.
4. **Given** uma chave que pertence a outra aplicação, **When** a consulto informando uma
   aplicação que não é a dona, **Then** a operação é recusada como chave não encontrada,
   sem revelar que a chave existe em outro lugar.
5. **Given** um identificador de aplicação que não existe, **When** consulto qualquer
   chave sob ele, **Then** a operação é recusada informando que a aplicação não foi
   encontrada.

---

### Edge Cases

- Aplicação sem chave nenhuma: resultado vazio com total zero, nunca recusa.
- Página além do fim do conjunto: resultado vazio com o total correto, nunca recusa.
- Parâmetro de paginação fora do aceito (página negativa, tamanho zero, tamanho acima do
  máximo): recusado por validação, indicando o campo e o motivo.
- Identificador de chave ou de aplicação em formato inválido: tratado como não encontrado,
  sem distinguir "malformado" de "inexistente" para quem chama — o mesmo tratamento já dado
  na exclusão.
- Chave revogada entre a montagem de uma página e o pedido da seguinte: cada página reflete
  o estado no instante em que foi lida; a listagem não promete uma foto imutável do conjunto.
- Duas chaves criadas no mesmo instante: a ordenação permanece determinística, sem repetir
  nem omitir uma delas entre páginas.
- Aplicação com um volume grande de chaves: a resposta continua paginada e o custo de ler
  uma página não cresce com o total de chaves da aplicação.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir listar as chaves de API de uma aplicação já cadastrada.
- **FR-002**: A listagem MUST conter exclusivamente chaves da aplicação informada.
- **FR-003**: Cada chave listada MUST expor identificador, aplicação dona, rótulo, prefixo
  público, instante de criação, estado (válida ou revogada) e, quando revogada, o instante
  da revogação.
- **FR-004**: Nenhuma operação de leitura MUST expor o segredo em texto claro nem a
  representação irreversível dele — nem na listagem, nem na consulta individual, nem em
  mensagem de erro ou log.
- **FR-005**: A listagem sem filtro MUST incluir tanto as chaves válidas quanto as revogadas,
  distinguíveis pelo estado de cada uma — esconder as revogadas por padrão faria uma chave
  desaparecer no exato momento em que alguém precisa dela.
- **FR-006**: A listagem MUST oferecer um filtro por estado, permitindo restringir o
  resultado às válidas ou às revogadas.
- **FR-007**: A listagem MUST ser paginada, aceitando página e tamanho de página, com
  valores padrão quando não informados.
- **FR-008**: A listagem MUST informar, junto com a página devolvida, o total de chaves que
  atendem ao pedido e o suficiente para navegar até a próxima página.
- **FR-009**: A ordenação padrão da listagem MUST ser por instante de criação, da mais
  recente para a mais antiga, e MUST ser determinística mesmo entre chaves criadas no
  mesmo instante.
- **FR-010**: O sistema MUST recusar a listagem quando a aplicação informada não existir,
  identificando a causa pelo mesmo código de erro estável já usado nas demais operações
  sobre chaves.
- **FR-011**: O sistema MUST permitir a listagem de chaves de uma aplicação inativa —
  inatividade impede emitir, não impede ler.
- **FR-012**: O sistema MUST devolver resultado vazio, e não recusa, quando a aplicação
  existe mas não tem chave que atenda ao pedido.
- **FR-013**: O sistema MUST recusar por validação um pedido de paginação fora dos limites
  aceitos, indicando o campo e o motivo, e MUST NOT devolver resultado parcial nesse caso.
- **FR-014**: O sistema MUST permitir consultar uma única chave pelo seu identificador, no
  contexto da aplicação que a emitiu.
- **FR-015**: A consulta individual MUST expor exatamente os mesmos campos de uma chave
  listada, sem nenhum campo adicional sensível.
- **FR-016**: O sistema MUST recusar a consulta de uma chave inexistente, ou de uma chave
  que pertence a outra aplicação, com o mesmo código de erro estável de chave não
  encontrada, sem revelar a existência da chave em outra aplicação.
- **FR-017**: A consulta MUST encontrar chaves revogadas, apresentando-as com o estado de
  revogada e o instante da revogação.
- **FR-018**: Nenhuma operação desta fatia MUST alterar o estado armazenado — listagem e
  consulta são estritamente de leitura.
- **FR-019**: Toda recusa MUST ser comunicada com um código de erro estável, distinto por
  motivo, que o cliente possa tratar sem depender do texto da mensagem.
- **FR-020**: O custo de ler uma página MUST NOT crescer com o total de chaves da aplicação
  — o conjunto lido é limitado ao tamanho da página pedida.

### Key Entities

- **Chave de API**: entidade já existente, criada na fatia de emissão. Esta fatia não muda
  sua forma: apenas dá caminhos de leitura sobre o que já está guardado — identificador,
  aplicação dona, rótulo, prefixo público, instante de criação e instante da revogação. O
  estado (válida ou revogada) continua derivado da presença do instante de revogação.
- **Aplicação**: entidade já existente, dona das chaves e contexto obrigatório de toda
  leitura. Uma leitura sempre acontece dentro de uma aplicação; não existe caminho que
  liste chaves de várias aplicações de uma vez nesta fatia.
- **Página de chaves**: recorte do conjunto de chaves de uma aplicação que atende a um
  pedido — as chaves da página, o total que atende ao pedido e a posição dentro do conjunto.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Quem administra uma aplicação descobre o identificador de qualquer chave dela
  em uma única operação, sem ter guardado nada da emissão — o identificador deixa de ser
  informação perecível.
- **SC-002**: O segredo em texto claro continua observável em exatamente um lugar — a
  resposta da emissão. Uma varredura das respostas de listagem e consulta, em todos os
  caminhos de sucesso e de falha, não encontra nenhuma ocorrência do segredo nem da sua
  representação irreversível.
- **SC-003**: 100% das chaves de uma aplicação são alcançáveis pela listagem, percorrendo
  as páginas, sem repetição e sem omissão.
- **SC-004**: Nenhuma chave de outra aplicação aparece em nenhuma leitura — verificável com
  duas aplicações povoadas em paralelo.
- **SC-005**: 100% dos caminhos de recusa previstos nesta especificação respondem com um
  código de erro estável e distinto por motivo.
- **SC-006**: Nenhuma leitura altera o estado armazenado — verificável relendo o conjunto de
  chaves da aplicação antes e depois de cada leitura, inclusive nas que falham.
- **SC-007**: O tempo para responder "esta chave que apareceu no log ainda está válida, e de
  onde ela é?" é o de uma única consulta, partindo do identificador ou de uma listagem
  filtrada.
- **SC-008**: Uma aplicação com muitas chaves responde a listagem no mesmo patamar de tempo
  de uma aplicação com poucas — o crescimento do conjunto não degrada a leitura de uma
  página.

## Assumptions

- Autenticação e autorização seguem inexistentes no produto. Esta fatia mantém o mesmo
  patamar do que já existe; proteger a leitura de chaves — inclusive impedir que alguém
  enumere as chaves de uma aplicação alheia — é trabalho da feature de autenticação, e é
  pré-requisito para ir a produção.
- Leitura acontece sempre dentro de uma aplicação. Uma listagem global de chaves, de todas
  as aplicações, está **fora de escopo**: não há hoje o conceito de dono acima da aplicação
  que a justificaria.
- Busca textual por rótulo, filtro por intervalo de datas e ordenação escolhida por quem
  chama estão **fora de escopo** nesta fatia. O filtro por estado entra porque distingue o
  que está em uso do que já foi revogado — a pergunta que motiva a leitura.
- Tamanho de página padrão de 20 e máximo de 100, alinhado ao que se espera de uma listagem
  administrativa; nenhum caso conhecido exige mais.
- Consulta por prefixo público está **fora de escopo**: o prefixo serve para reconhecer a
  chave dentro de um resultado, e a listagem filtrada já responde a partir dele. Um caminho
  direto entra se o volume tornar isso incômodo.
- Registro de último uso não existe e não entra aqui — não há uso a registrar enquanto a
  verificação de chave não existir. A listagem mostra o ciclo de vida, não a atividade.
- Depende das entidades e do armazenamento criados na fatia de emissão e exclusão
  (`001-api-key-management`) como fonte de verdade; esta fatia não introduz dado novo na
  chave.
- Os códigos de erro de aplicação inexistente e de chave inexistente já existem e são
  reaproveitados — não se cria código novo para o mesmo motivo.
