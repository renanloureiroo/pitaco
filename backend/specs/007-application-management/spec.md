# Feature Specification: Aplicação — cadastro, edição, ativação e eventos observados

**Feature Branch**: `007-application-management`

**Created**: 2026-09-05

**Status**: Implemented

**Input**: User description: "Precisamos fechar o ciclo da aplicação: cadastrar com nome e slug, editar o nome e os prazos (descanso, retenção, texto livre), desligar e religar uma aplicação, e guardar os eventos que o app já mandou pra gente poder escolher o disparo sem digitar de memória."

## Contexto

A aplicação é a raiz de tudo no Pitaco: chaves, pesquisas, respondentes e políticas de
privacidade penduram nela. As fatias `001` e `002` trataram das chaves; a `005` trouxe a leitura
das aplicações. Faltava o ciclo de vida da própria aplicação — nascer, mudar de nome e de
política, sair do ar sem perder histórico e voltar.

Esta fatia entrega quatro coisas, e só elas:

- **Cadastro**: nome e identificador público legível (slug), único entre todas as aplicações e
  derivado do nome quando quem cadastra não o informa.
- **Edição parcial**: nome e os três prazos de política — intervalo de descanso, retenção das
  respostas e retenção do texto livre — mudam campo a campo, e um prazo pode ser removido.
- **Ativar e desativar**: desligar uma aplicação silencia a superfície pública sem apagar nada;
  religá-la devolve tudo como estava.
- **Catálogo de eventos da aplicação**: cada nome de evento que o app hospedeiro já enviou na
  consulta de elegibilidade fica registrado, uma linha por nome, para que a autoria escolha o
  evento de disparo entre os já vistos.

Três fronteiras desenham o escopo:

- **Não é leitura.** Listar e consultar aplicações é da `005-application-read-list`. A leitura
  do catálogo de eventos observados é da `008-exposure-control`, junto do catálogo de atributos.
- **Não é aplicação da política.** Os prazos são definidos aqui; quem os aplica é a elegibilidade
  (descanso, `008-exposure-control`), a leitura de coleta (texto livre, `006`) e o descarte
  (`011-privacy-retention`).
- **Não é exclusão.** Uma aplicação não é apagada por esta fatia. Desativar é o caminho para
  tirá-la do ar.

## Clarifications

### Session 2026-09-05

- Q: O slug pode mudar depois do cadastro? → A: Não. Ele aparece em URL e em chave; é imutável e
  fica fora da edição.
- Q: Como a edição distingue "não mexer" de "remover o prazo"? → A: Campo ausente não mexe; campo
  enviado como `null` remove o prazo; valor define. O nome não é removível: `null` nele é corpo
  malformado.
- Q: Corpo vazio na edição é erro? → A: Não. É aceito, devolve a aplicação como está e não altera
  nem o instante da última alteração.
- Q: Desativar uma aplicação já inativa é erro? → A: Não. As duas ações são idempotentes:
  respondem o estado atual sem gravar nada.
- Q: Aplicação inativa recusa o SDK com erro? → A: A consulta de elegibilidade responde silêncio
  (sem pesquisa); abrir e enviar exibição são recusados com `application.inactive`; os canais que
  só recebem (supressões, erros do SDK, eventos de interação) descartam com a mesma resposta de
  quando gravam. O painel continua lendo tudo.
- Q: O catálogo de eventos é um log de ocorrências? → A: Não. Uma linha por nome de evento por
  aplicação, com primeira e última ocorrência; a última avança no máximo a cada poucos minutos.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cadastrar uma aplicação (Priority: P1)

Quem vai integrar um app ao Pitaco cadastra a aplicação informando um nome. O identificador
público legível sai do nome — "Pitaço Café" vira `pitaco-cafe` — ou é informado à parte. Os
prazos de política podem ser definidos já no cadastro ou deixados em branco.

**Why this priority**: sem aplicação não há chave, pesquisa nem coleta. É o primeiro passo de
qualquer integração e entrega valor sozinho.

**Independent Test**: cadastrar só com o nome e conferir que a resposta traz o identificador e o
slug derivado, que o cabeçalho `Location` aponta para a aplicação e que ela nasce ativa e sem
política.

**Acceptance Scenarios**:

1. **Given** nenhum cadastro com o mesmo slug, **When** cadastro "Acme App" sem slug, **Then** a
   aplicação nasce ativa com slug `acme-app`, sem prazos, e a resposta é 201 com `Location`.
2. **Given** um slug informado e válido, **When** cadastro, **Then** o slug informado é usado
   como está.
3. **Given** uma aplicação com slug `acme-app`, **When** cadastro outra que chegaria ao mesmo
   slug, **Then** a operação é recusada como conflito e nenhuma segunda aplicação é criada.
4. **Given** prazos informados no cadastro, **When** cadastro, **Then** eles são gravados como
   foram enviados.
5. **Given** um prazo de texto livre maior que o prazo geral, **When** cadastro, **Then** a
   operação é recusada por regra de negócio e nada é criado.
6. **Given** nome em branco, nome acima de 120 caracteres, slug fora do formato ou prazo abaixo
   de um dia, **When** cadastro, **Then** a operação é recusada por validação apontando cada
   campo.

---

### User Story 2 - Editar nome e prazos (Priority: P1)

A política muda com o tempo: o jurídico pede retenção menor, o produto quer mais descanso entre
pesquisas. A edição parcial muda só o que foi enviado e permite remover um prazo.

**Why this priority**: os prazos governam o que o respondente vê e o que o Pitaco guarda. Sem
edição, corrigir um prazo exigiria recriar a aplicação e perder o histórico.

**Independent Test**: definir os três prazos, remover um deles enviando `null`, omitir os outros
e conferir que só o removido sumiu da resposta e do banco.

**Acceptance Scenarios**:

1. **Given** uma aplicação, **When** envio nome e prazos, **Then** recebo a aplicação inteira
   atualizada e o estado gravado confere.
2. **Given** um prazo definido, **When** envio esse campo como `null`, **Then** o prazo é
   removido e deixa de aparecer na resposta; os campos omitidos ficam como estavam.
3. **Given** uma aplicação, **When** envio corpo vazio, **Then** recebo 200 com a aplicação como
   está, sem mudança de nenhum campo nem do instante da última alteração.
4. **Given** um prazo geral de 30 dias, **When** defino texto livre em 60, **Then** a operação é
   recusada por regra de negócio e nada do pedido é gravado.
5. **Given** prazos de 30 (geral) e 7 (texto livre), **When** envio geral 90 e texto livre 60 no
   mesmo pedido, **Then** os dois sobem juntos: a retenção geral é aplicada antes da de texto
   livre.
6. **Given** nome enviado como `null` ou prazo com tipo errado, **When** edito, **Then** recebo
   recusa de corpo malformado e nada muda.
7. **Given** uma aplicação inexistente ou identificador malformado, **When** edito, **Then**
   recebo a mesma recusa de aplicação não encontrada.

---

### User Story 3 - Desligar e religar uma aplicação (Priority: P2)

Uma aplicação pode precisar sair do ar — app descontinuado, contrato encerrado, incidente. Desligar
faz a superfície pública parar de entregar pesquisa e de aceitar coleta, sem apagar nada. Religar
devolve tudo como estava.

**Why this priority**: é a válvula de segurança da operação. Não trava o cadastro nem a edição,
mas sem ela a única forma de silenciar um app seria revogar todas as chaves.

**Independent Test**: com uma pesquisa publicada, consultar a elegibilidade e receber a pesquisa;
desativar e receber silêncio; reativar e voltar a receber.

**Acceptance Scenarios**:

1. **Given** uma aplicação ativa, **When** a desativo, **Then** ela fica inativa, o estado é
   gravado e as políticas seguem intactas.
2. **Given** uma aplicação já inativa, **When** a desativo de novo, **Then** recebo 200 com o
   estado e o instante da última alteração não anda.
3. **Given** uma aplicação inativa, **When** a reativo, **Then** ela volta a ficar ativa;
   reativar a já ativa também é idempotente.
4. **Given** uma aplicação inativa com pesquisa publicada, **When** o SDK consulta a
   elegibilidade, **Then** recebe silêncio; depois de reativada, volta a receber a pesquisa.
5. **Given** uma aplicação inativa, **When** o painel lê suas pesquisas, exibições e respostas,
   **Then** tudo continua legível.
6. **Given** uma aplicação inexistente, **When** desativo ou reativo, **Then** recebo aplicação
   não encontrada.

---

### User Story 4 - Lembrar os eventos que o app já enviou (Priority: P3)

Quem configura o disparo de uma pesquisa precisa escrever o nome exato do evento do app. Digitar
de memória é a principal fonte de pesquisa que nunca dispara. Toda consulta de elegibilidade
registra o nome do evento no catálogo da aplicação — inclusive as que não encontram pesquisa —,
para que a autoria escolha entre os já vistos.

**Why this priority**: melhora a autoria e alimenta o diagnóstico de "evento nunca visto", mas o
Pitaco funciona sem ele.

**Independent Test**: consultar a elegibilidade com um evento sem pesquisa, repetir a consulta e
conferir que o catálogo tem exatamente uma linha para aquele nome.

**Acceptance Scenarios**:

1. **Given** uma aplicação ativa, **When** o SDK consulta a elegibilidade com um evento sem
   pesquisa, **Then** o evento passa a constar do catálogo, com primeira e última ocorrência.
2. **Given** um evento já no catálogo, **When** ele chega de novo, **Then** continua sendo uma
   linha só; a última ocorrência avança.
3. **Given** uma aplicação inativa ou desconhecida, **When** chega uma consulta, **Then** nada é
   registrado no catálogo.
4. **Given** uma falha ao gravar o catálogo, **When** a consulta de elegibilidade acontece,
   **Then** a decisão de entrega não é afetada: a falha vira aviso no log, nunca erro para o
   SDK.

---

### Edge Cases

- **Nome sem caractere aproveitável para o slug** (só ideogramas ou só pontuação): o slug não é
  derivável e o cadastro é recusado pedindo o slug à parte.
- **Nome longo**: o slug derivado é cortado no limite de 50 caracteres, sem hífen nas pontas.
- **Dois nomes diferentes chegando ao mesmo slug**: a unicidade é garantida pelo banco, além da
  checagem do caso de uso; o segundo cadastro é recusado como conflito.
- **Remover a retenção geral com texto livre definido**: o prazo de texto livre passa a valer
  sozinho.
- **Remover o prazo de texto livre**: o texto livre volta a seguir o prazo geral (prazo efetivo).
- **Retenção geral menor que o texto livre já definido**: recusada por regra de negócio.
- **Edição recusada pelo domínio**: nenhum campo do pedido é gravado, nem o instante da última
  alteração.
- **Evento repetido em rajada**: a última ocorrência é amortizada; a linha só é reescrita quando o
  registrado é mais antigo que o limiar de alguns minutos.
- **Duas primeiras ocorrências simultâneas do mesmo evento**: a chave única por aplicação e nome
  resolve a corrida; continua uma linha só.

## Requirements *(mandatory)*

### Functional Requirements

**Cadastro**

- **FR-001**: O sistema MUST permitir cadastrar uma aplicação com nome obrigatório de até 120
  caracteres, sem espaço nas pontas.
- **FR-002**: O sistema MUST aceitar um slug informado, de até 50 caracteres, com apenas
  minúsculas, dígitos e hífen entre termos.
- **FR-003**: Quando o slug é omitido, o sistema MUST derivá-lo do nome: acento vira a letra sem
  acento, pontuação vira separador, o excesso é cortado no limite.
- **FR-004**: O sistema MUST recusar o cadastro cujo slug já exista, com código de conflito
  estável, e garantir a unicidade também por restrição no armazenamento.
- **FR-005**: A aplicação MUST nascer ativa, com o instante de criação igual ao da última
  alteração.
- **FR-006**: O cadastro MUST aceitar, opcionalmente, intervalo de descanso, prazo de retenção e
  prazo de retenção de texto livre, cada um de ao menos um dia.
- **FR-007**: A criação bem-sucedida MUST responder com o identificador e o slug, e indicar onde
  a aplicação pode ser consultada.

**Edição**

- **FR-008**: O sistema MUST permitir editar nome, intervalo de descanso, prazo de retenção e
  prazo de retenção de texto livre de forma parcial.
- **FR-009**: Campo ausente MUST NOT ser alterado; prazo enviado como nulo MUST ser removido;
  valor enviado MUST ser definido.
- **FR-010**: O nome MUST NOT ser removível: nulo nele é corpo malformado.
- **FR-011**: Corpo vazio MUST ser aceito sem alterar nenhum campo nem o instante da última
  alteração.
- **FR-012**: O slug MUST ser imutável e ficar fora da edição.
- **FR-013**: O prazo de texto livre MUST NOT ser maior que o prazo de retenção geral, e o prazo
  geral MUST NOT ficar menor que o de texto livre já definido; a violação é recusa por regra de
  negócio.
- **FR-014**: Num mesmo pedido, a retenção geral MUST ser aplicada antes da de texto livre, de
  modo que as duas possam subir juntas.
- **FR-015**: Edição recusada MUST NOT gravar nenhum campo do pedido.
- **FR-016**: A resposta da edição MUST trazer a aplicação inteira; prazo não configurado MUST
  ficar ausente da resposta, e não nulo nem zero.
- **FR-017**: Sem prazo de texto livre próprio, o prazo efetivo de texto livre MUST ser o prazo
  geral; sem nenhum dos dois, não há expiração.

**Ativação**

- **FR-018**: O sistema MUST permitir desativar e reativar uma aplicação, respondendo o estado
  resultante.
- **FR-019**: Desativar já inativa e reativar já ativa MUST ser idempotente: responde o estado sem
  gravar nada.
- **FR-020**: Desativar MUST NOT apagar nem alterar política, chave, pesquisa, exibição ou
  resposta.
- **FR-021**: Com a aplicação inativa, a consulta de elegibilidade MUST responder sem pesquisa;
  abrir e enviar exibição MUST ser recusado com o código `application.inactive`; supressões,
  registros de erro do SDK e eventos de interação MUST ser descartados com a mesma resposta de
  quando são gravados.
- **FR-022**: Com a aplicação inativa, emitir chave e criar pesquisa MUST ser recusado com
  `application.inactive`, e a leitura administrativa MUST continuar disponível.

**Catálogo de eventos**

- **FR-023**: Toda consulta de elegibilidade de aplicação ativa MUST registrar o nome do evento no
  catálogo da aplicação, com ou sem pesquisa para ele.
- **FR-024**: O catálogo MUST ter uma linha por nome de evento por aplicação, com primeira e
  última ocorrência; a última ocorrência MUST NOT recuar.
- **FR-025**: A gravação do catálogo MUST ser amortizada: a linha só é reescrita quando a última
  ocorrência registrada é mais antiga que um limiar de minutos.
- **FR-026**: Falha ao gravar o catálogo MUST NOT alterar a resposta da elegibilidade.
- **FR-027**: Aplicação inativa ou desconhecida MUST NOT alimentar o catálogo.

**Transversais**

- **FR-028**: Toda recusa MUST trazer código estável no formato `<contexto>.<motivo>`.
- **FR-029**: Aplicação inexistente e identificador malformado MUST receber a mesma recusa de
  aplicação não encontrada.
- **FR-030**: Cadastro, edição e ativação MUST pertencer à superfície administrativa, recusando
  credencial de aplicação com `api_key.forbidden_surface`.
- **FR-031**: Recusas MUST registrar no log o campo e o motivo, nunca o valor rejeitado; o
  sucesso registra identificadores, e a falha do catálogo registra a aplicação e a natureza da
  falha, nunca o nome do evento.

### Key Entities

- **Aplicação**: identificador opaco, slug imutável, nome, estado (ativa/inativa), intervalo de
  descanso, prazo de retenção, prazo de retenção de texto livre, instantes de criação e de última
  alteração.
- **Evento observado**: um nome de evento que o app já enviou, por aplicação, com primeira e
  última ocorrência. Catálogo, não log.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma aplicação nova pode ser cadastrada informando apenas o nome, em uma chamada.
- **SC-002**: 100% das edições recusadas deixam a aplicação idêntica ao que era antes do pedido,
  inclusive o instante da última alteração.
- **SC-003**: Depois de desativar, nenhuma pesquisa é entregue ao SDK daquela aplicação; depois de
  reativar, a mesma pesquisa volta a ser entregue sem nenhum outro passo.
- **SC-004**: Nenhum dado de chave, pesquisa, exibição ou resposta é perdido por desativar e
  reativar uma aplicação.
- **SC-005**: Mil consultas de elegibilidade com o mesmo evento produzem exatamente uma linha no
  catálogo daquele nome.
- **SC-006**: A indisponibilidade do catálogo de eventos não muda nenhuma resposta da consulta de
  elegibilidade.

## Assumptions

- A aplicação não tem dono acima dela: a superfície administrativa não autentica usuário, e o
  patamar de proteção é o mesmo das demais operações administrativas.
- O slug derivado é uma aproximação legível, não uma tradução fiel; dois nomes diferentes podem
  chegar ao mesmo slug, e a unicidade é responsabilidade de quem persiste.
- Os prazos são dias inteiros. Prazo ausente significa "sem prazo", nunca "zero dias".
- Exclusão de aplicação não faz parte desta fatia; a cascata que a tornaria possível nasce na
  `011-privacy-retention`.
- O catálogo de eventos não guarda quem enviou o evento nem quantas vezes: é o nome e quando foi
  visto. Contagem de ocorrências não tem leitor hoje.

## Dependencies

- `backend 001-api-key-management` e `backend 002-api-key-read-list`: a chave autentica a
  superfície pública e carrega o estado da aplicação; emitir chave respeita o estado definido aqui.
- `backend 004-response-collection`: a consulta de elegibilidade é onde o catálogo de eventos é
  alimentado e onde o silêncio da aplicação inativa se manifesta.
- `backend 005-application-read-list`: lê o que esta fatia grava.
- `backend 008-exposure-control`: aplica o intervalo de descanso e expõe a leitura do catálogo de
  eventos.
- `backend 011-privacy-retention`: aplica os prazos de retenção definidos aqui.
