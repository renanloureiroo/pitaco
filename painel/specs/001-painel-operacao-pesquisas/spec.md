# Feature Specification: Painel de operação — aplicações, chaves e pesquisas ponta a ponta

**Feature Branch**: `001-painel-operacao-pesquisas`

**Created**: 2026-09-08

**Status**: Draft

**Input**: User description: "Podemos continuar, agora tempos mais endpoints. Vamos implementar o painel com tudo que temos, só não teremos ainda o sdk e app teste."

## Contexto

O backend já sabe tudo que precisa saber para a autoria: cadastra aplicações, emite e revoga
chaves, cria pesquisas em rascunho, monta perguntas, define o disparo e suas regras de
segmentação, congela versões, publica e controla o que está no ar. Hoje esse trabalho só é
possível por chamadas diretas à API — quem pesquisa depende de quem programa.

Esta feature entrega a superfície onde esse trabalho acontece de fato: o painel. O escopo é
**tudo que a API administrativa já oferece**, sem antecipar nada que ela ainda não oferece.
Duas fronteiras ficam explicitamente de fora, por decisão do solicitante: a integração via SDK
e a aplicação de teste. E uma terceira fica de fora por ausência de superfície no backend: a
leitura de respostas e resultados — nenhuma pesquisa coletada é visível no painel nesta
entrega, porque não há de onde lê-la.

O valor entregue é verificável por um observador externo sem tocar em código: alguém que
pesquisa consegue, do primeiro clique à publicação, colocar uma pesquisa no ar e controlá-la.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver e criar aplicações (Priority: P1)

Quem opera o Pitaco abre o painel e vê a lista das aplicações cadastradas, com o nome, o
identificador legível e se está ativa ou inativa. Pode filtrar por situação, navegar por
páginas quando houver muitas, abrir uma aplicação para ver seus detalhes — inclusive os prazos
de descanso e de retenção, quando configurados — e cadastrar uma aplicação nova.

**Why this priority**: a aplicação é a raiz de tudo. Chave e pesquisa só existem dentro de uma.
Sem esta tela não há por onde entrar no produto.

**Independent Test**: cadastrar duas aplicações pelo painel, conferir que ambas aparecem na
lista, filtrar por ativas, abrir uma delas e conferir que os dados exibidos são os informados
no cadastro.

**Acceptance Scenarios**:

1. **Given** um painel aberto sem nenhuma aplicação cadastrada, **When** acesso a lista,
   **Then** vejo uma mensagem de lista vazia convidando ao cadastro — não um erro.
2. **Given** aplicações cadastradas, **When** acesso a lista, **Then** vejo cada uma com nome,
   identificador legível, situação e data de criação, da mais recente para a mais antiga.
3. **Given** mais aplicações do que cabe em uma página, **When** chego ao fim da página,
   **Then** consigo avançar para a próxima e volto para a anterior sem perder o filtro aplicado.
4. **Given** a lista aberta, **When** filtro por situação, **Then** só as aplicações naquela
   situação aparecem, e o total exibido reflete o conjunto filtrado inteiro.
5. **Given** o formulário de cadastro, **When** informo os dados e confirmo, **Then** a
   aplicação é criada e sou levado à sua página de detalhe.
6. **Given** o formulário de cadastro, **When** o backend recusa por dado inválido, **Then**
   vejo a recusa apontando o campo culpado e o que digitei permanece no formulário.
7. **Given** uma aplicação sem prazos configurados, **When** abro seu detalhe, **Then** os
   prazos aparecem como não configurados — nunca como zero.
8. **Given** um identificador de aplicação inexistente na URL, **When** acesso a página,
   **Then** vejo uma mensagem de aplicação não encontrada, com caminho de volta para a lista.

---

### User Story 2 - Emitir e revogar chaves de acesso (Priority: P2)

Dentro de uma aplicação, quem opera vê as chaves emitidas, com sua situação e datas, emite uma
chave nova e revoga uma existente. O segredo da chave só é exibido no instante da emissão — o
painel deixa isso explícito e oferece a cópia ali mesmo, porque não haverá segunda chance.

**Why this priority**: é a chave que liga o mundo externo ao Pitaco. Vem depois da aplicação
porque depende dela, e antes da pesquisa porque é o que torna a coleta possível — mesmo que o
consumo por SDK esteja fora deste escopo.

**Independent Test**: emitir uma chave em uma aplicação, conferir que o segredo aparece uma
única vez, recarregar a página e conferir que ele não é mais exibido, revogar a chave e
conferir que sua situação muda na lista.

**Acceptance Scenarios**:

1. **Given** uma aplicação sem chaves, **When** abro suas chaves, **Then** vejo lista vazia com
   a ação de emitir em destaque.
2. **Given** a ação de emitir, **When** confirmo a emissão, **Then** o segredo é exibido uma
   única vez, com aviso claro de que não poderá ser recuperado e com ação de copiar.
3. **Given** um segredo recém-exibido, **When** fecho o aviso ou recarrego a página, **Then** o
   segredo não é exibido novamente em lugar nenhum.
4. **Given** chaves emitidas, **When** vejo a lista, **Then** cada chave mostra sua situação e
   suas datas, sem nunca mostrar o segredo.
5. **Given** uma chave ativa, **When** peço a revogação, **Then** sou obrigado a confirmar antes,
   e ao confirmar a chave passa a constar como revogada.
6. **Given** uma chave já revogada, **When** vejo a lista, **Then** a ação de revogar não está
   disponível para ela.

---

### User Story 3 - Criar e montar o rascunho de uma pesquisa (Priority: P1)

Quem pesquisa cria uma pesquisa dentro de uma aplicação, dá um nome a ela, escreve as
perguntas, edita e remove perguntas e reordena a sequência em que serão apresentadas. Tudo
isso com o rascunho incompleto: dá para sair e voltar depois sem perder trabalho. O rascunho
pode ser renomeado, consultado, listado ao lado das outras pesquisas da aplicação e descartado
enquanto nunca tiver sido publicado.

**Why this priority**: é a razão de existir do produto e o maior volume de trabalho manual —
justamente o que a ausência de painel torna insuportável hoje. Entrega valor sozinha: já
permite escrever e organizar pesquisas, mesmo antes de configurar disparo ou publicar.

**Independent Test**: criar uma pesquisa, adicionar três perguntas de tipos diferentes, editar
uma, remover outra, reordenar as restantes, sair da página e voltar conferindo que a montagem
foi preservada exatamente como ficou.

**Acceptance Scenarios**:

1. **Given** uma aplicação, **When** crio uma pesquisa informando o nome, **Then** ela nasce em
   rascunho, sem perguntas e sem disparo, e sou levado à sua página de montagem.
2. **Given** pesquisas de duas aplicações diferentes, **When** listo as de uma, **Then** nenhuma
   pesquisa da outra aparece.
3. **Given** uma pesquisa em rascunho, **When** adiciono uma pergunta escolhendo seu tipo,
   **Then** ela passa a constar ao fim da sequência.
4. **Given** um tipo de pergunta que exige opções, **When** monto a pergunta, **Then** o painel
   me obriga a informar as opções antes de aceitar o envio.
5. **Given** perguntas montadas, **When** altero o texto de uma, **Then** a alteração é
   persistida e refletida imediatamente na sequência.
6. **Given** perguntas montadas, **When** removo uma, **Then** sou obrigado a confirmar, e a
   sequência das demais permanece coerente após a remoção.
7. **Given** três ou mais perguntas, **When** reordeno a sequência, **Then** a nova ordem é
   persistida e sobrevive ao recarregamento da página.
8. **Given** uma pesquisa nunca publicada, **When** peço para descartá-la e confirmo, **Then**
   ela some da listagem da aplicação.
9. **Given** uma pesquisa que já foi publicada alguma vez, **When** vejo suas ações, **Then** a
   ação de descartar não está disponível.
10. **Given** uma edição recusada pelo backend, **When** recebo a recusa, **Then** vejo o motivo
    e a montagem exibida continua correspondendo ao que está de fato salvo.

---

### User Story 4 - Configurar o disparo e as regras de segmentação (Priority: P2)

Quem pesquisa define em que momento a pesquisa dispara e para quem: configura o disparo e
adiciona regras de segmentação, podendo remover uma regra existente. As regras montadas ficam
visíveis em conjunto, para que dê para ler de relance a quem aquela pesquisa vai aparecer.

**Why this priority**: sem disparo a pesquisa não pode ser publicada, mas ela pode ser inteira
escrita antes disso. Vem depois da montagem por dependência de ordem prática, não técnica.

**Independent Test**: definir o disparo de uma pesquisa, adicionar duas regras de segmentação,
remover uma, recarregar a página e conferir que a configuração exibida é a que ficou salva.

**Acceptance Scenarios**:

1. **Given** uma pesquisa sem disparo, **When** abro sua configuração, **Then** vejo o disparo
   como não configurado e a ação de defini-lo.
2. **Given** a configuração de disparo, **When** informo os dados e salvo, **Then** o disparo
   passa a constar configurado na página da pesquisa.
3. **Given** um disparo já definido, **When** o redefino com outros dados, **Then** o valor
   anterior é substituído, sem duplicar configuração.
4. **Given** um disparo configurado, **When** adiciono uma regra de segmentação escolhendo
   atributo, operação e valor, **Then** ela passa a constar entre as regras da pesquisa.
5. **Given** regras adicionadas, **When** removo uma e confirmo, **Then** ela some e as demais
   permanecem intactas.
6. **Given** uma combinação de atributo, operação e valor recusada pelo backend, **When** tento
   salvar, **Then** vejo o motivo da recusa sem que nenhuma regra seja criada.

---

### User Story 5 - Publicar e acompanhar versões (Priority: P2)

Antes de publicar, quem pesquisa consulta o que ainda impede a publicação e resolve cada
impedimento. Ao publicar, o conteúdo é congelado em uma versão imutável. O painel lista as
versões da pesquisa, permite consultar o conteúdo exato de uma versão publicada, abrir uma nova
versão de rascunho a partir da publicada, descartar esse rascunho de versão e consultar a
comparabilidade entre versões.

**Why this priority**: é o desfecho do trabalho de autoria e a fronteira entre editar e estar no
ar. Depende da montagem e do disparo estarem prontos.

**Independent Test**: com uma pesquisa incompleta, consultar os impedimentos e conferir que a
publicação está bloqueada; completar o que falta, publicar, e conferir que a versão aparece na
listagem com o conteúdo congelado.

**Acceptance Scenarios**:

1. **Given** uma pesquisa incompleta, **When** consulto os impedimentos, **Then** vejo a lista do
   que falta e a ação de publicar permanece indisponível.
2. **Given** uma pesquisa sem impedimentos, **When** publico, **Then** uma versão é criada e a
   pesquisa passa a constar publicada.
3. **Given** uma pesquisa publicada, **When** listo suas versões, **Then** vejo cada versão com
   seu número e o instante da publicação, da mais recente para a mais antiga.
4. **Given** uma versão publicada, **When** a consulto, **Then** vejo o conteúdo congelado dela —
   perguntas e configuração como estavam no momento da publicação.
5. **Given** uma pesquisa publicada, **When** abro uma nova versão, **Then** volto a poder editar
   o conteúdo sem alterar a versão que está no ar.
6. **Given** um rascunho de versão aberto, **When** o descarto e confirmo, **Then** a pesquisa
   volta a exibir apenas a versão publicada.
7. **Given** duas versões de uma mesma pesquisa, **When** consulto a comparabilidade, **Then**
   vejo se os resultados delas podem ou não ser comparados, e por quê.

---

### User Story 6 - Controlar o que está no ar (Priority: P3)

Quem opera pausa uma pesquisa publicada, retoma uma pausada e encerra definitivamente uma
pesquisa. O painel só oferece as transições que de fato são permitidas naquele estado, e
encerrar exige confirmação explícita por ser irreversível.

**Why this priority**: é controle operacional sobre algo que já está no ar. Sem as histórias
anteriores nada existe para controlar.

**Independent Test**: publicar uma pesquisa, pausá-la e conferir a mudança de estado, retomá-la,
encerrá-la, e conferir que depois do encerramento nenhuma transição é oferecida.

**Acceptance Scenarios**:

1. **Given** uma pesquisa em determinado estado, **When** abro sua página, **Then** só as
   transições permitidas para aquele estado são oferecidas.
2. **Given** uma pesquisa no ar, **When** a pauso, **Then** seu estado passa a pausada e a ação
   oferecida passa a ser retomar.
3. **Given** uma pesquisa pausada, **When** a retomo, **Then** ela volta ao ar.
4. **Given** qualquer pesquisa passível de encerramento, **When** peço o encerramento, **Then**
   sou avisado de que a ação é irreversível e preciso confirmar antes de ela ocorrer.
5. **Given** uma pesquisa encerrada, **When** abro sua página, **Then** nenhuma transição é
   oferecida e o conteúdo aparece somente para leitura.

---

### Edge Cases

- Backend indisponível ou lento: cada tela precisa distinguir "carregando", "vazio" e "falhou",
  e a falha precisa oferecer nova tentativa sem perder o que o usuário digitou.
- Estado alterado por outra pessoa entre a leitura e a ação (a pesquisa foi publicada, a chave
  foi revogada): a recusa do backend precisa ser exibida e o painel precisa se realinhar com o
  estado real, em vez de insistir na visão desatualizada.
- Identificador inválido ou inexistente na URL: aplicação, pesquisa, versão e chave inexistentes
  recebem o mesmo tratamento de "não encontrado", sem revelar se o formato estava certo.
- Apresentar a chave de aplicação junto de uma requisição do painel é recusado pelo backend por
  princípio; o painel nunca a envia, e se essa recusa ocorrer ela é tratada como erro de sistema,
  não como algo a pedir ao usuário.
- Sequência de perguntas com uma única pergunta: reordenar não faz sentido e a ação não é
  oferecida.
- Publicação disparada duas vezes por duplo clique: apenas uma versão pode ser criada.
- Conjunto de resultados grande: listagens paginadas precisam preservar página e filtro ao voltar
  de uma página de detalhe.

## Requirements *(mandatory)*

### Functional Requirements

**Navegação e fundamentos**

- **FR-001**: O painel MUST oferecer navegação a partir da lista de aplicações até qualquer
  pesquisa, chave ou versão em no máximo quatro passos, exibindo em cada tela a que aplicação e
  pesquisa o conteúdo pertence.
- **FR-002**: O painel MUST distinguir visualmente, em toda tela que lê dados, os estados de
  carregando, conteúdo, conteúdo vazio e falha de leitura.
- **FR-003**: Toda falha de leitura MUST oferecer nova tentativa sem exigir recarregar a página.
- **FR-004**: Toda recusa vinda do backend MUST ser exibida ao usuário com o motivo informado
  pelo backend, associada ao campo culpado quando o backend apontar um, e MUST NOT descartar o
  que o usuário digitou.
- **FR-005**: Toda ação destrutiva ou irreversível — revogar chave, remover pergunta, remover
  regra, descartar pesquisa, descartar rascunho de versão, encerrar pesquisa — MUST exigir
  confirmação explícita antes de ser executada.
- **FR-006**: Toda ação que altera dados MUST impedir envio duplicado enquanto estiver em curso.
- **FR-007**: O painel MUST NOT enviar a chave de acesso de aplicação em nenhuma requisição
  administrativa.

**Aplicações**

- **FR-008**: Usuários MUST poder listar as aplicações com nome, identificador legível, situação e
  data de criação, ordenadas da mais recente para a mais antiga.
- **FR-009**: Usuários MUST poder filtrar a listagem de aplicações por situação e navegar entre
  páginas, com o total refletindo o conjunto filtrado inteiro.
- **FR-010**: Usuários MUST poder cadastrar uma aplicação nova e, ao sucesso, ser levados à sua
  página de detalhe.
- **FR-011**: Usuários MUST poder consultar o detalhe de uma aplicação, incluindo os prazos de
  descanso e de retenção; prazos não configurados MUST ser exibidos como não configurados e
  MUST NOT ser exibidos como zero.

**Chaves de acesso**

- **FR-012**: Usuários MUST poder listar as chaves de uma aplicação com sua situação e datas, e
  consultar o detalhe de uma chave.
- **FR-013**: Usuários MUST poder emitir uma chave; o segredo MUST ser exibido uma única vez, no
  instante da emissão, com aviso de que não poderá ser recuperado e com ação de cópia.
- **FR-014**: O painel MUST NOT exibir, armazenar ou reexibir o segredo de uma chave após o
  usuário sair da tela de emissão.
- **FR-015**: Usuários MUST poder revogar uma chave ativa, e a ação MUST NOT ser oferecida para
  chaves já revogadas.

**Pesquisas e perguntas**

- **FR-016**: Usuários MUST poder criar uma pesquisa dentro de uma aplicação e listar as pesquisas
  daquela aplicação, com estado, paginação e ordenação da mais recente para a mais antiga.
- **FR-017**: A listagem de pesquisas de uma aplicação MUST NOT exibir pesquisas de outra
  aplicação.
- **FR-018**: Usuários MUST poder consultar o detalhe de uma pesquisa, vendo nome, estado,
  perguntas, disparo e situação de publicação.
- **FR-019**: Usuários MUST poder renomear uma pesquisa.
- **FR-020**: Usuários MUST poder descartar uma pesquisa nunca publicada; a ação MUST NOT ser
  oferecida para pesquisas já publicadas.
- **FR-021**: Usuários MUST poder adicionar uma pergunta escolhendo seu tipo entre os tipos que o
  backend suporta, informando os dados que aquele tipo exige.
- **FR-022**: O painel MUST validar, antes do envio, as exigências de forma da pergunta que sejam
  conhecidas do contrato — como a presença de opções em tipos que as exigem.
- **FR-023**: Usuários MUST poder editar e remover perguntas de uma pesquisa.
- **FR-024**: Usuários MUST poder reordenar a sequência de perguntas, e a nova ordem MUST
  sobreviver ao recarregamento da página.

**Disparo e regras**

- **FR-025**: Usuários MUST poder definir e redefinir o disparo de uma pesquisa, sem que a
  redefinição duplique configuração.
- **FR-026**: Usuários MUST poder adicionar uma regra de segmentação informando atributo, operação
  e valor, restritos às operações que o backend suporta.
- **FR-027**: Usuários MUST poder remover uma regra de segmentação sem afetar as demais.
- **FR-028**: O painel MUST exibir em conjunto o disparo e todas as regras vigentes da pesquisa.

**Publicação e versões**

- **FR-029**: Usuários MUST poder consultar os impedimentos de publicação de uma pesquisa antes de
  publicar.
- **FR-030**: O painel MUST manter a ação de publicar indisponível enquanto houver impedimento
  conhecido.
- **FR-031**: Usuários MUST poder publicar uma pesquisa sem impedimentos, e a versão criada MUST
  passar a constar na listagem de versões.
- **FR-032**: Usuários MUST poder listar as versões de uma pesquisa e consultar o conteúdo
  congelado de uma versão específica.
- **FR-033**: Usuários MUST poder abrir uma nova versão de rascunho a partir da pesquisa publicada
  e descartar esse rascunho.
- **FR-034**: Usuários MUST poder consultar a comparabilidade entre versões de uma pesquisa.

**Ciclo de vida**

- **FR-035**: O painel MUST oferecer apenas as transições de estado que o backend informa serem
  permitidas para a pesquisa naquele momento.
- **FR-036**: Usuários MUST poder pausar, retomar e encerrar uma pesquisa conforme as transições
  permitidas.
- **FR-037**: Uma pesquisa encerrada MUST ser exibida somente para leitura, sem transições
  oferecidas.

### Key Entities

- **Aplicação**: o produto cujos usuários serão pesquisados. Tem nome, identificador legível,
  situação (ativa ou inativa), prazos opcionais de descanso e retenção, e é a dona de chaves e
  pesquisas.
- **Chave de acesso**: credencial emitida para uma aplicação, com situação e datas. Seu segredo
  existe para o portador apenas no instante da emissão.
- **Pesquisa**: o artefato de autoria dentro de uma aplicação. Tem nome, estado (rascunho,
  publicada, pausada, encerrada), uma sequência de perguntas, um disparo e um histórico de versões.
- **Pergunta**: item da pesquisa, com tipo, enunciado, posição na sequência e, quando o tipo
  exigir, um conjunto de opções.
- **Disparo**: a condição de momento que faz a pesquisa aparecer, com um conjunto de regras de
  segmentação que restringem para quem ela aparece.
- **Regra de segmentação**: atributo, operação e valor que juntos delimitam o público da pesquisa.
- **Versão**: recorte imutável do conteúdo da pesquisa no instante da publicação, identificado por
  número e comparável (ou não) com outras versões.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma pessoa que pesquisa consegue, sem ajuda de quem programa e sem tocar em uma
  chamada de API, levar uma pesquisa nova do cadastro à publicação em menos de 10 minutos.
- **SC-002**: 100% das capacidades já expostas pela API administrativa têm caminho correspondente
  no painel — nenhuma operação continua exigindo chamada direta à API.
- **SC-003**: 100% das ações irreversíveis exigem confirmação explícita, verificado por teste
  automatizado.
- **SC-004**: O segredo de uma chave nunca é observável fora do instante da emissão, verificado
  por teste automatizado que recarrega a tela após emitir.
- **SC-005**: Toda recusa do backend resulta em mensagem compreensível na tela, sem tela em branco
  e sem perda do que o usuário havia digitado, em 100% dos fluxos de formulário.
- **SC-006**: Os fluxos críticos — cadastrar aplicação, emitir chave, montar pesquisa, publicar e
  pausar — são cobertos por teste ponta a ponta que passa na suíte do projeto.
- **SC-007**: Nove em cada dez tentativas de montar uma pesquisa completa terminam sem que o
  usuário precise consultar documentação externa ao painel.

## Assumptions

- **Autenticação de usuário está fora do escopo**: a superfície administrativa do backend hoje não
  autentica pessoas — ela apenas recusa requisições que apresentem a chave de aplicação. O painel
  assume acesso confiável (uso interno ou ambiente restrito) e não implementa login. Quando o
  backend passar a autenticar, isso será uma feature própria.
- **Leitura de respostas e resultados está fora do escopo**: o backend expõe coleta apenas na
  superfície pública, e não oferece nenhuma leitura agregada de respostas. O painel, portanto, não
  exibe resultado de pesquisa nesta entrega.
- **SDK e aplicação de teste estão fora do escopo**, por decisão explícita do solicitante. O painel
  não oferece instruções de integração nem ambiente de simulação.
- O painel é a única superfície de escrita administrativa: ele não replica regra de negócio do
  backend, e toda invariante de domínio é decidida lá. A validação feita no painel é de forma, para
  encurtar o laço de feedback, nunca substituta da recusa do backend.
- Os tipos de pergunta, as operações de regra e os estados de pesquisa são os que o backend já
  suporta; o painel não introduz nem restringe o conjunto por conta própria.
- O painel é usado em navegador de desktop; responsividade para telas pequenas é desejável mas não
  é critério de aceite desta entrega.
- O endereço do backend é configurável por ambiente, e o painel e o backend são operados juntos.
- Idioma da interface é português do Brasil, alinhado à documentação existente do projeto.

## Dependencies

- API administrativa do backend Pitaco, nas capacidades já entregues pelas features
  `001-api-key-management`, `002-api-key-read-list`, `003-survey-authoring`, `004-response-collection`
  (apenas no que toca à autoria) e `005-application-read-list`.
- Constituição do painel (`.specify/memory/constitution.md`), cujos princípios e portões de
  qualidade regem esta entrega.
