# Feature Specification: Chaves de API — criação e exclusão

**Feature Branch**: `001-api-key-management`

**Created**: 2026-09-08

**Status**: Draft

**Input**: User description: "Vamos criar o inicio do dominio de api_keys, por enquanto vamos criar e excluir apenas. Nossa referência de documentaçõa está no obsidian, mas a fonte de verdade é no projeto. Pitaco"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Emitir uma chave para a aplicação (Priority: P1)

Quem administra uma aplicação já cadastrada no Pitaco precisa de uma credencial para
que o produto dessa pessoa consiga se identificar perante o Pitaco. Ela pede a emissão
de uma chave informando a aplicação e um rótulo que descreva onde a chave será usada
("app iOS", "site de marketing", "ambiente de homologação"). O sistema devolve, uma
única vez, o segredo em texto claro, junto com o identificador e o prefixo público da
chave. A partir daí o segredo é problema de quem o guardou: o Pitaco não consegue
mostrá-lo de novo.

**Why this priority**: sem emissão não existe nada para excluir, e é a emissão que
entrega valor sozinha — uma aplicação sem chave nenhuma não tem como ser integrada.

**Independent Test**: emitir uma chave para uma aplicação existente e conferir que o
segredo veio na resposta, que a chave passou a existir vinculada àquela aplicação e que
o segredo em claro não é recuperável por nenhum outro caminho.

**Acceptance Scenarios**:

1. **Given** uma aplicação ativa cadastrada, **When** solicito a emissão de uma chave com
   um rótulo válido, **Then** recebo confirmação da criação com o identificador da chave,
   o prefixo público e o segredo em texto claro exibido uma única vez.
2. **Given** uma aplicação ativa cadastrada, **When** emito duas chaves em sequência,
   **Then** as duas coexistem, com identificadores e segredos distintos.
3. **Given** um identificador de aplicação que não existe, **When** solicito a emissão,
   **Then** a operação é recusada informando que a aplicação não foi encontrada e nenhuma
   chave é criada.
4. **Given** uma aplicação inativa, **When** solicito a emissão, **Then** a operação é
   recusada por regra de negócio e nenhuma chave é criada.
5. **Given** um rótulo vazio ou maior que o limite aceito, **When** solicito a emissão,
   **Then** a operação é recusada por validação, indicando o campo e o motivo, e nenhuma
   chave é criada.

---

### User Story 2 - Excluir uma chave comprometida ou obsoleta (Priority: P2)

A pessoa que administra a aplicação descobre que uma chave vazou, ou que o ambiente onde
ela era usada foi desligado. Ela pede a exclusão da chave pelo identificador recebido na
emissão. A chave deixa de ser válida imediatamente, sem afetar as demais chaves da mesma
aplicação.

**Why this priority**: é a contrapartida indispensável da emissão — sem ela, um vazamento
não tem remédio —, mas depende da história 1 existir para ter o que excluir.

**Independent Test**: emitir uma chave, excluí-la e conferir que ela não consta mais como
chave válida da aplicação, enquanto outra chave da mesma aplicação segue intacta.

**Acceptance Scenarios**:

1. **Given** uma chave existente e válida, **When** solicito sua exclusão, **Then** a
   operação é confirmada e a chave deixa de ser válida.
2. **Given** duas chaves na mesma aplicação, **When** excluo uma delas, **Then** a outra
   permanece válida e inalterada.
3. **Given** um identificador de chave que não existe, **When** solicito a exclusão,
   **Then** a operação é recusada informando que a chave não foi encontrada.
4. **Given** uma chave já excluída, **When** solicito a exclusão de novo, **Then** a
   operação é recusada por conflito — a chave existe, mas já não é válida —, o instante
   da primeira revogação é preservado e o estado das demais chaves não muda.
5. **Given** uma chave excluída, **When** se examina o que ficou registrado, **Then** o
   registro da chave permanece — rótulo, prefixo público, instante de criação e instante
   da revogação —, sem nenhum vestígio do segredo.

---

### Edge Cases

- Duas emissões simultâneas para a mesma aplicação: as duas chaves nascem, com segredos
  distintos, sem uma sobrescrever a outra.
- Duas exclusões simultâneas da mesma chave: só uma tem efeito; a outra recebe recusa por
  conflito, o instante de revogação gravado é o da primeira, e o estado final é o mesmo de
  uma única exclusão.
- Rótulo repetido dentro da mesma aplicação: é aceito — o rótulo é descrição humana, não
  identidade.
- Rótulo com espaços nas pontas: normalizado antes da validação de tamanho.
- Requisição com corpo malformado ou campo obrigatório ausente: recusada por validação,
  sem efeito colateral.
- Identificador de chave em formato inválido: tratado como chave não encontrada, sem
  distinguir "malformado" de "inexistente" para quem chama.
- Exclusão de chave pertencente a outra aplicação: recusada; uma chave só é gerenciável
  no contexto da aplicação que a emitiu.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema MUST permitir emitir uma chave de API vinculada a uma aplicação já
  cadastrada.
- **FR-002**: O sistema MUST recusar a emissão quando a aplicação informada não existir,
  identificando a causa por um código de erro estável.
- **FR-003**: O sistema MUST recusar a emissão quando a aplicação estiver inativa.
- **FR-004**: Toda chave MUST ter um rótulo textual obrigatório, informado por quem a emite,
  com tamanho entre 1 e 80 caracteres após remoção de espaços nas pontas.
- **FR-005**: O segredo da chave MUST ser gerado pelo próprio sistema, de forma aleatória e
  imprevisível; quem emite não escolhe nem influencia o valor.
- **FR-006**: O segredo em texto claro MUST ser devolvido uma única vez, na resposta da
  emissão, e MUST NOT ser recuperável por qualquer operação posterior.
- **FR-007**: O sistema MUST NOT armazenar o segredo de forma que permita reconstruí-lo, e
  MUST NOT registrá-lo em log, mensagem de erro ou trilha de diagnóstico.
- **FR-008**: Toda chave MUST ter um identificador próprio e um prefixo público não sensível,
  ambos livres para aparecer em respostas e logs, permitindo reconhecer de qual chave se
  fala sem expor o segredo.
- **FR-009**: O sistema MUST registrar o instante de criação de cada chave.
- **FR-010**: O sistema MUST permitir excluir uma chave pelo seu identificador, no contexto
  da aplicação que a emitiu.
- **FR-011**: A exclusão MUST ser uma revogação: a chave deixa de ser válida de forma
  imediata e irreversível, e o registro permanece armazenado.
- **FR-012**: O sistema MUST registrar o instante da revogação de cada chave revogada, e
  esse instante MUST NOT mudar depois de gravado.
- **FR-013**: O sistema MUST recusar a revogação de uma chave já revogada, identificando a
  causa como conflito por um código de erro estável, distinto do código de chave
  inexistente.
- **FR-014**: Uma chave revogada MUST NOT poder voltar a ser válida — não existe operação
  de reativação.
- **FR-015**: O sistema MUST recusar a exclusão de uma chave inexistente, identificando a
  causa por um código de erro estável.
- **FR-016**: A exclusão de uma chave MUST NOT afetar nenhuma outra chave, nem a aplicação
  à qual ela pertence.
- **FR-017**: Em qualquer caminho de falha (validação, aplicação inexistente, aplicação
  inativa, chave inexistente, chave já revogada), o sistema MUST deixar o estado
  armazenado exatamente como estava antes da tentativa.
- **FR-018**: Toda recusa MUST ser comunicada com um código de erro estável, distinto por
  motivo, que o cliente possa tratar sem depender do texto da mensagem.
- **FR-019**: Uma aplicação MUST poder ter várias chaves válidas ao mesmo tempo, sem limite
  fixado nesta fatia.
- **FR-020**: A revogação MUST NOT apagar o rótulo, o prefixo público nem o instante de
  criação da chave — é essa trilha que permite investigar um vazamento depois do fato.

### Key Entities

- **Chave de API**: credencial emitida para uma aplicação. Guarda o identificador próprio,
  a aplicação dona, o rótulo humano, o prefixo público, a representação irreversível do
  segredo, o instante de criação e — quando revogada — o instante da revogação. O estado
  é derivado dessa marca: sem instante de revogação, a chave é válida; com ele, revogada.
  A transição acontece uma única vez e não tem volta.
- **Aplicação**: entidade já existente no produto, dona das chaves. Uma aplicação tem zero
  ou muitas chaves; uma chave pertence a exatamente uma aplicação, e essa ligação não muda
  ao longo da vida da chave.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Quem administra uma aplicação obtém uma chave utilizável em uma única
  operação, sem passo intermediário e sem consultar outra tela ou canal.
- **SC-002**: 100% das chaves excluídas deixam de ser válidas a partir da confirmação da
  exclusão — nenhuma janela de tolerância, nenhum caminho de volta.
- **SC-003**: O segredo em texto claro é observável em exatamente um lugar — a resposta da
  emissão. Uma varredura de registros armazenados, logs e demais respostas não encontra
  nenhuma ocorrência.
- **SC-004**: 100% dos caminhos de recusa previstos nesta especificação respondem com um
  código de erro estável e distinto por motivo.
- **SC-005**: Nenhuma tentativa recusada altera o estado armazenado — verificável relendo o
  conjunto de chaves da aplicação antes e depois de cada falha.
- **SC-006**: A perda de uma chave não obriga a recriar a aplicação nem invalida as demais
  chaves: o tempo de recuperação de um vazamento é o de emitir uma nova chave e excluir a
  antiga.
- **SC-007**: Depois de revogada, uma chave continua identificável pela trilha — rótulo,
  prefixo público, instante de criação e instante da revogação — por tempo indeterminado,
  o que permite responder "de onde veio este acesso?" sobre uma chave que já não está em
  uso.

## Assumptions

- Autenticação e autorização ainda não existem no produto (nenhum endpoint atual é
  protegido). Esta fatia segue o mesmo patamar do que já existe; proteger a gestão de
  chaves é trabalho de uma feature própria, e é pré-requisito para ir a produção.
- Verificar uma chave para autenticar requisições de entrada está **fora de escopo**: aqui
  só se emite e se exclui. A verificação entra quando existir o endpoint que a consome.
- Listar, renomear, rotacionar e consultar uma chave estão **fora de escopo** nesta fatia —
  o usuário pediu criar e excluir apenas. O identificador necessário para excluir é o que
  volta na emissão.
- Chaves não têm prazo de validade, escopos nem permissões nesta fatia. Todas valem para
  a aplicação inteira, até serem excluídas.
- Não há registro de último uso nem trilha de auditoria de acesso — não há uso a registrar
  enquanto a verificação não existir. A trilha desta fatia é só o ciclo de vida da chave:
  quando nasceu e quando foi revogada.
- Chaves revogadas ficam armazenadas por tempo indeterminado. Expurgo automático depois de
  um prazo está **fora de escopo**: entra quando existir rotina agendada no projeto e o
  volume justificar.
- Rótulo não é único dentro da aplicação: serve para leitura humana, não para identificar.
- Depende do módulo de aplicações já existente (identificador e estado ativo/inativo da
  aplicação) como fonte de verdade sobre a aplicação dona da chave.
