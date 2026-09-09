# Feature Specification: Coleta — leitura das exibições, respostas e respondentes

**Feature Branch**: `006-collection-read-list`

**Created**: 2026-09-09

**Status**: Draft

**Input**: User description: "Precisamos implementar os endpoints de leitura / listagem dos nossos dominios, o que faltam."

## Contexto

A leitura administrativa está completa em dois dos três domínios do Pitaco. Aplicação e chave
de acesso têm listagem e consulta individual. Pesquisa tem listagem, consulta com conteúdo,
listagem e consulta de versão, histórico de transições e comparabilidade.

O terceiro domínio — a **coleta** — é escrita pura. O SDK abre exibição, a pessoa responde ou
dispensa, o Pitaco guarda tudo, e **nada disso pode ser lido de volta**. Quem opera o painel
não tem como saber se uma pesquisa publicada foi exibida uma vez ou dez mil, quantas pessoas
responderam, quantas dispensaram, nem o que qualquer uma delas respondeu. A única evidência
de que a coleta funciona hoje é o teste automatizado.

Esta feature fecha essa lacuna, e só ela. Três fronteiras desenham o escopo:

- **Não é resultado agregado.** Somar, cruzar, calcular proporção de resposta, montar
  distribuição por opção e exportar continua sendo da feature de resultados. Aqui a leitura é
  crua: os registros como foram gravados, paginados, filtráveis, sem cálculo derivado além do
  total de itens que a paginação já exige.
- **Não é escrita.** Nenhuma operação desta fatia altera, corrige ou apaga exibição, resposta
  ou respondente. A resposta é imutável desde a coleta e continua imutável aqui.
- **Não é a superfície do SDK.** Estas leituras são do painel. O SDK não ganha leitura nova e
  não passa a poder consultar o que outros respondentes responderam.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver as exibições de uma pesquisa (Priority: P1)

Quem publicou uma pesquisa precisa saber o que aconteceu com ela no campo: quantas vezes foi
exibida, quando, em qual versão publicada, e como cada exibição terminou — respondida,
dispensada ou ainda aberta. A listagem devolve as exibições da pesquisa com identificador,
versão exibida, grupo de comparabilidade, desfecho, instante de abertura e instante de
fechamento quando houver, paginada e com total.

Sem essa listagem o painel não tem primeira tela de coleta: não existe caminho para chegar a
uma exibição, porque o identificador dela só existiu na resposta que o SDK recebeu.

**Why this priority**: é a porta de entrada de todo o resto da leitura de coleta e a primeira
prova, fora do teste automatizado, de que a coleta está funcionando em produção. Entrega valor
sozinha e não depende de nenhuma das outras histórias existir.

**Independent Test**: publicar uma pesquisa, abrir três exibições — uma respondida, uma
dispensada, uma ainda aberta —, listar as exibições da pesquisa e conferir que as três
aparecem com o desfecho correto, a versão exibida e os instantes, e que a paginação informa o
total.

**Acceptance Scenarios**:

1. **Given** uma pesquisa com três exibições em desfechos diferentes, **When** listo as
   exibições da pesquisa, **Then** recebo as três, cada uma com identificador, versão exibida,
   grupo de comparabilidade, desfecho e instante de abertura.
2. **Given** uma exibição já fechada, **When** a vejo na listagem, **Then** o instante de
   fechamento está presente; **Given** uma exibição ainda aberta, **Then** o instante de
   fechamento está ausente, distinguível de um fechamento no mesmo instante da abertura.
3. **Given** uma pesquisa sem nenhuma exibição, **When** listo, **Then** recebo resultado vazio
   com total zero — não um erro.
4. **Given** uma pesquisa com exibições em duas versões publicadas diferentes, **When** filtro
   por uma das versões, **Then** recebo apenas as exibições daquela versão.
5. **Given** exibições com desfechos diferentes, **When** filtro por desfecho, **Then** recebo
   apenas as que terminaram daquela forma.
6. **Given** exibições abertas em instantes diferentes, **When** filtro por um período,
   **Then** recebo apenas as abertas dentro dele, inclusive nos extremos.
7. **Given** mais exibições do que cabe em uma página, **When** listo, **Then** recebo a
   primeira página junto com o total existente e o suficiente para pedir a próxima.
8. **Given** exibições abertas em instantes diferentes, **When** listo sem pedir ordenação,
   **Then** recebo da mais recente para a mais antiga, com ordem estável entre exibições
   abertas no mesmo instante.
9. **Given** uma pesquisa que não existe na aplicação informada, **When** listo suas exibições,
   **Then** recebo recusa de pesquisa não encontrada, com código de erro estável.
10. **Given** uma pesquisa de outra aplicação, **When** tento listar suas exibições pelo
    caminho da minha aplicação, **Then** recebo a mesma recusa de pesquisa não encontrada —
    a existência de pesquisa alheia não é revelada.

---

### User Story 2 - Ver o que foi respondido em uma exibição (Priority: P1)

Uma linha da listagem diz que a exibição foi respondida; ela não diz o que a pessoa
respondeu. A consulta de uma exibição devolve, além do que a listagem expõe, o instantâneo de
atributos capturado na abertura, a versão do SDK quando informada, o respondente a que
pertence e as respostas coletadas: para cada pergunta da versão exibida, a chave estável, a
situação — respondida ou pulada — e o valor quando houver, na ordem em que as perguntas
aparecem na versão.

**Why this priority**: é o par inseparável da história 1. Uma listagem de exibições que não
permite abrir nenhuma delas informa que houve coleta e esconde o que foi coletado — que é a
única razão de existir da coleta.

**Independent Test**: abrir uma exibição, responder parte das perguntas e pular uma
obrigatória permitida, consultar a exibição e conferir que cada pergunta aparece com sua
situação, que a pulada não traz valor e que a ordem das respostas espelha a ordem das
perguntas na versão exibida.

**Acceptance Scenarios**:

1. **Given** uma exibição respondida, **When** a consulto, **Then** recebo suas respostas com
   chave da pergunta, situação e valor, na ordem das perguntas da versão exibida.
2. **Given** uma pergunta pulada, **When** consulto a exibição, **Then** ela aparece com
   situação de pulada e sem valor — distinguível de uma resposta em branco.
3. **Given** uma exibição ainda aberta, **When** a consulto, **Then** recebo a exibição com
   lista de respostas vazia e sem instante de fechamento — não um erro.
4. **Given** uma exibição dispensada, **When** a consulto, **Then** recebo o desfecho de
   dispensa e nenhuma resposta.
5. **Given** uma exibição aberta com atributos e versão do SDK, **When** a consulto, **Then**
   recebo o instantâneo de atributos como foi capturado e a versão do SDK.
6. **Given** uma exibição aberta sem versão do SDK informada, **When** a consulto, **Then** a
   versão do SDK aparece como ausente.
7. **Given** um identificador de exibição que não existe, **When** consulto, **Then** recebo
   recusa de exibição não encontrada com o mesmo código de erro estável já usado no envio de
   respostas.
8. **Given** uma exibição de outra aplicação, **When** a consulto pelo caminho da minha
   aplicação, **Then** recebo a mesma recusa de exibição não encontrada.

---

### User Story 3 - Ver os respondentes de uma aplicação (Priority: P2)

Quem opera a aplicação precisa saber quantas pessoas distintas o Pitaco já viu, como cada uma
está identificada — por referência do app ou por dispositivo — e quando foi vista pela
primeira e pela última vez. A listagem devolve os respondentes da aplicação com
identificador, tipo e valor da identificação, primeiro e último contato, paginada e com total.

**Why this priority**: transforma exibição em pessoa. É o que separa "houve mil exibições" de
"houve mil exibições para trezentas pessoas" — leitura que a feature de resultados vai
depender e que nenhum agregado substitui. Vem depois das duas primeiras porque o painel já
entrega valor sem ela.

**Independent Test**: gerar exibições para dois respondentes identificados de formas
diferentes, listar os respondentes da aplicação e conferir que os dois aparecem com o tipo de
identificação correto e com os instantes de primeiro e último contato.

**Acceptance Scenarios**:

1. **Given** dois respondentes, um identificado por referência do app e outro por dispositivo,
   **When** listo os respondentes da aplicação, **Then** os dois aparecem com o tipo de
   identificação distinguível.
2. **Given** um respondente visto em dois instantes diferentes, **When** o vejo na listagem,
   **Then** o primeiro contato é o mais antigo e o último é o mais recente.
3. **Given** respondentes de duas aplicações diferentes, **When** listo os de uma delas,
   **Then** recebo apenas os dela.
4. **Given** uma aplicação sem respondentes, **When** listo, **Then** recebo resultado vazio
   com total zero.
5. **Given** respondentes vistos pela última vez em instantes diferentes, **When** listo sem
   pedir ordenação, **Then** recebo do último contato mais recente para o mais antigo, com
   ordem estável entre empates.
6. **Given** uma aplicação que não existe, **When** listo seus respondentes, **Then** recebo
   recusa de aplicação não encontrada com o código de erro estável já usado nas operações
   sobre chaves.

---

### User Story 4 - Ver o histórico de exibições de um respondente (Priority: P3)

Diante de um respondente, quem investiga precisa saber tudo o que já foi exibido para ele:
quais pesquisas, em que versões, quando e com que desfecho. É a mesma listagem de exibições
da história 1 vista pelo outro eixo — por pessoa em vez de por pesquisa.

**Why this priority**: é investigação, não operação diária. Responde "por que esta pessoa
recebeu esta pesquisa duas vezes" e "o histórico de elegibilidade está sendo respeitado" —
perguntas raras e importantes, que não travam o painel enquanto não existirem.

**Independent Test**: gerar exibições de duas pesquisas diferentes para o mesmo respondente,
listar as exibições daquele respondente e conferir que as duas aparecem, cada uma apontando a
pesquisa e a versão a que pertence.

**Acceptance Scenarios**:

1. **Given** um respondente com exibições de duas pesquisas, **When** listo suas exibições,
   **Then** recebo as duas, cada uma identificando a pesquisa e a versão exibida.
2. **Given** um respondente sem exibição alguma, **When** listo, **Then** recebo resultado
   vazio com total zero.
3. **Given** exibições em desfechos diferentes, **When** filtro por desfecho, **Then** recebo
   apenas as que terminaram daquela forma.
4. **Given** um respondente que não existe na aplicação informada, **When** listo suas
   exibições, **Then** recebo recusa de respondente não encontrado com código de erro estável.

---

### Edge Cases

- **Versão publicada descartada ou pesquisa encerrada**: exibições continuam legíveis e
  continuam apontando a versão que foi realmente exibida, mesmo que a pesquisa já esteja
  encerrada. Leitura de coleta é registro histórico e não acompanha o estado atual da autoria.
- **Resposta a pergunta que não está mais na versão em rascunho**: a resposta pertence à
  versão exibida, não ao rascunho atual. A consulta apresenta a chave da pergunta como foi
  gravada, ainda que essa chave já não exista no rascunho.
- **Exibição sem resposta nenhuma e com desfecho de respondida**: estado inconsistente que a
  coleta não produz; a leitura apresenta o que está gravado sem tentar corrigir nem esconder.
- **Página pedida além do fim**: resultado vazio com o total correto, não recusa.
- **Tamanho de página zero, negativo ou acima do máximo**: recusa por validação, apontando o
  campo, sem resultado parcial.
- **Período com início posterior ao fim**: recusa por validação apontando o campo, sem
  resultado parcial.
- **Desfecho ou versão inexistente no filtro**: valor desconhecido de desfecho é recusa por
  validação; versão que não existe na pesquisa é resultado vazio, porque o filtro é legítimo e
  simplesmente não casa com nada.
- **Respostas de texto livre já expiradas pelo prazo de retenção da aplicação**: apresentadas
  como ausentes, distinguíveis de uma pergunta pulada — expiração não é escolha do
  respondente.
- **Volume alto de exibições em uma pesquisa**: o custo de ler uma página não cresce com o
  total de exibições da pesquisa.

## Requirements *(mandatory)*

### Functional Requirements

**Exibições — listagem**

- **FR-001**: O sistema MUST permitir listar as exibições de uma pesquisa.
- **FR-002**: Cada exibição listada MUST expor identificador, versão exibida, grupo de
  comparabilidade, desfecho, instante de abertura e instante de fechamento quando houver.
- **FR-003**: A ausência de fechamento MUST ser apresentada como ausente, distinguível de um
  fechamento no mesmo instante da abertura.
- **FR-004**: A listagem MUST oferecer filtro por versão exibida, por desfecho e por período
  de abertura, combináveis entre si.
- **FR-005**: O filtro por período MUST incluir os instantes extremos informados.
- **FR-006**: A listagem MUST ser paginada, aceitando página e tamanho de página, com valores
  padrão quando não informados, e MUST informar o total de exibições que atendem ao pedido.
- **FR-007**: A ordenação padrão MUST ser por instante de abertura, da mais recente para a
  mais antiga, e MUST ser determinística entre exibições abertas no mesmo instante.
- **FR-008**: O sistema MUST devolver resultado vazio, e não recusa, quando nenhuma exibição
  atender ao pedido — inclusive quando a página pedida está além do fim.
- **FR-009**: O sistema MUST recusar por validação paginação fora dos limites aceitos, desfecho
  desconhecido e período com início posterior ao fim, indicando o campo e o motivo, e MUST NOT
  devolver resultado parcial nesses casos.
- **FR-010**: O sistema MUST recusar a listagem quando a pesquisa não existe na aplicação
  informada, com o mesmo código de erro estável de pesquisa não encontrada já usado nas
  operações de autoria.

**Exibições — consulta individual**

- **FR-011**: O sistema MUST permitir consultar uma única exibição pelo seu identificador.
- **FR-012**: A consulta individual MUST expor, além do que a listagem expõe, o respondente a
  que a exibição pertence, o instantâneo de atributos capturado na abertura e a versão do SDK
  quando informada.
- **FR-013**: A versão do SDK não informada MUST ser apresentada como ausente.
- **FR-014**: A consulta individual MUST expor as respostas coletadas na exibição, cada uma
  com a chave estável da pergunta, a situação — respondida ou pulada — e o valor quando
  houver.
- **FR-015**: A ordem das respostas MUST espelhar a ordem das perguntas na versão exibida.
- **FR-016**: Uma pergunta pulada MUST ser apresentada sem valor, distinguível de uma resposta
  cujo valor é vazio.
- **FR-017**: Uma exibição ainda aberta MUST ser consultável, devolvendo lista de respostas
  vazia e sem instante de fechamento.
- **FR-018**: O sistema MUST recusar a consulta de exibição inexistente, ou pertencente a
  outra aplicação, com o mesmo código de erro estável de exibição não encontrada já usado no
  envio de respostas.

**Respondentes**

- **FR-019**: O sistema MUST permitir listar os respondentes de uma aplicação.
- **FR-020**: Cada respondente listado MUST expor identificador, tipo e valor da
  identificação, instante do primeiro contato e instante do último contato.
- **FR-021**: A listagem de respondentes MUST ser paginada com total, com ordenação padrão por
  último contato do mais recente para o mais antigo, determinística entre empates.
- **FR-022**: A listagem de respondentes MUST conter apenas respondentes da aplicação
  informada.
- **FR-023**: O sistema MUST recusar a listagem quando a aplicação não existe, com o mesmo
  código de erro estável de aplicação não encontrada já usado nas operações sobre chaves.
- **FR-024**: O sistema MUST permitir listar as exibições de um respondente, cada uma
  identificando a pesquisa e a versão exibida além do que a listagem por pesquisa expõe.
- **FR-025**: A listagem de exibições por respondente MUST aceitar filtro por desfecho e por
  período, ser paginada com total e usar a mesma ordenação padrão da listagem por pesquisa.
- **FR-026**: O sistema MUST recusar a listagem quando o respondente não existe na aplicação
  informada, com código de erro estável distinto do de aplicação não encontrada.

**Transversais**

- **FR-027**: Nenhuma operação desta fatia MUST alterar o estado armazenado — todas as
  leituras são estritamente de leitura.
- **FR-028**: Respostas de texto livre cujo prazo de retenção da aplicação já expirou MUST ser
  apresentadas como ausentes, distinguíveis de pergunta pulada.
- **FR-029**: Nenhuma leitura MUST expor segredo de chave de acesso, em texto claro ou na
  representação irreversível dele, nem no corpo, nem em mensagem de erro, nem em log.
- **FR-030**: O valor da identificação do respondente MUST NOT ir para log em nenhuma destas
  leituras, mantendo a regra já estabelecida na coleta.
- **FR-031**: Toda recusa MUST ser comunicada com código de erro estável, distinto por motivo,
  tratável pelo cliente sem depender do texto da mensagem.
- **FR-032**: Todas as leituras desta fatia MUST pertencer à superfície administrativa,
  recusando credencial de aplicação com o mesmo código estável já usado nas demais operações
  administrativas.
- **FR-033**: O custo de ler uma página MUST NOT crescer com o total de exibições, respostas
  ou respondentes armazenados — o conjunto lido é limitado ao tamanho da página pedida, e a
  leitura de uma página não faz uma consulta por item devolvido.
- **FR-034**: A existência de recurso de outra aplicação MUST NOT ser revelada: pesquisa,
  exibição e respondente alheios recusam como inexistentes, não como proibidos.

### Key Entities

- **Exibição**: uma sessão em que uma versão publicada foi entregue a um respondente. Aponta
  aplicação, respondente, pesquisa e versão exibida; carrega grupo de comparabilidade,
  desfecho, versão do SDK, instantâneo de atributos, instante de abertura e, quando fechada,
  instante de fechamento.
- **Resposta**: o que foi coletado para uma pergunta em uma exibição. Aponta a exibição e a
  chave estável da pergunta; carrega situação, valor quando respondida e instante em que foi
  dada. Imutável.
- **Respondente**: a pessoa ou dispositivo que o Pitaco já viu em uma aplicação. Carrega
  identificação — tipo e valor opacos — e os instantes de primeiro e último contato.
- **Versão publicada**: o conteúdo congelado que a exibição entregou. Já existe; aqui é
  referenciada como registro histórico, e determina a ordem em que as respostas são
  apresentadas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Partindo apenas do identificador de uma pesquisa, quem opera o painel chega ao
  conteúdo de uma resposta específica em no máximo duas consultas.
- **SC-002**: 100% das exibições gravadas pela coleta são legíveis, em qualquer desfecho,
  incluindo as ainda abertas.
- **SC-003**: Ler uma página de exibições de uma pesquisa com cem mil exibições gravadas leva
  o mesmo tempo, dentro da variação normal de medição, que ler uma página de uma pesquisa com
  cem — e não faz nenhuma consulta adicional por item devolvido.
- **SC-004**: Toda recusa das quatro leituras é distinguível por um código estável, sem que o
  cliente precise interpretar o texto da mensagem; 100% dos códigos possíveis estão declarados
  no contrato público de cada leitura.
- **SC-005**: Nenhuma sequência de pedidos válidos ou inválidos a estas leituras altera
  qualquer registro de coleta — verificável relendo o estado antes e depois.
- **SC-006**: Nenhum corpo de resposta, mensagem de erro ou registro de log produzido por esta
  fatia contém segredo de chave de acesso ou o valor da identificação de respondente.
- **SC-007**: Uma pesquisa recém-publicada e nunca exibida devolve resultado vazio com total
  zero nas quatro leituras aplicáveis, sem recusa.

## Assumptions

- **A superfície é administrativa, do painel.** As quatro leituras seguem o mesmo regime de
  credencial das demais leituras administrativas já existentes (aplicações, chaves,
  pesquisas), recusando credencial de aplicação usada pelo SDK. Nenhuma leitura nova é
  exposta ao SDK.
- **O eixo de acesso é a aplicação.** Exibições são alcançadas pelo caminho da pesquisa dentro
  da aplicação, e respondentes pelo caminho da aplicação, coerentemente com o que já existe
  para chaves e pesquisas. Não há listagem global que atravesse aplicações.
- **Paginação e envelope seguem o que já existe.** Página, tamanho de página, limites, valores
  padrão e formato do envelope de listagem são os mesmos já adotados nas listagens de
  aplicações, chaves e pesquisas — nada novo é inventado nesta fatia.
- **A leitura é crua e sem agregado.** Nenhuma contagem por desfecho, proporção de resposta,
  distribuição por opção ou exportação entra aqui; o único número derivado é o total que a
  paginação exige. Agregação é da feature de resultados.
- **Respostas de texto livre são visíveis para quem opera a aplicação enquanto não expiram.**
  O conteúdo foi coletado pela própria aplicação e o prazo de retenção de texto livre já é uma
  decisão dela; a leitura respeita esse prazo e não impõe outro filtro.
- **A identificação do respondente é apresentada como foi recebida.** O par tipo/valor é
  opaco para o Pitaco e foi fornecido pela aplicação hospedeira; devolvê-lo a quem opera essa
  mesma aplicação não introduz exposição nova. A regra de nunca registrá-lo em log continua
  valendo.
- **Ordenação é fixa por padrão e não configurável nesta fatia.** Ordenação escolhida pelo
  cliente entra quando houver um caso concreto que a exija, seguindo a regra de só abstrair no
  segundo caso.
- **Exibições e respostas continuam imutáveis.** Não há correção, anulação nem remoção de
  resposta nesta fatia; se isso vier a ser necessário, é feature própria, com trilha de
  auditoria.
- **A leitura não depende do estado atual da autoria.** Exibições de pesquisa encerrada ou de
  versão descartada permanecem legíveis, porque são registro histórico.
