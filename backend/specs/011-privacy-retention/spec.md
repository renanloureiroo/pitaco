# Feature Specification: Privacidade e retenção — descarte, exclusão do titular e auditoria

**Feature Branch**: `011-privacy-retention`

**Created**: 2026-09-12

**Status**: Implemented

**Input**: User description: "A gente guarda resposta de gente de verdade. Preciso que a política de retenção da aplicação seja cumprida de fato (respostas e texto livre), com uma prévia do que vai sair, sem os resultados perderem o histórico, e que dê pra apagar um respondente quando ele pedir, com registro de que a exclusão aconteceu."

## Contexto

A aplicação já guarda dois prazos (`007`): retenção das respostas e retenção do texto livre, mais
curta. A leitura de coleta (`006`) já esconde o texto livre vencido. Mas nada era apagado: o prazo
era promessa, não prática. E não havia como atender o pedido de um titular que quer ser esquecido.

Esta fatia transforma a política em descarte real e dá ao operador as ferramentas de privacidade:

- **Descarte diário**: respostas vencidas pelo prazo geral saem; texto livre vencido pelo prazo
  próprio é apagado e a resposta continua contando como dada.
- **Agregado congelado**: antes de apagar, o agregado das respostas que vão sair é congelado por
  versão, para os resultados continuarem somando o histórico.
- **Prévia**: quanto sai na próxima execução e na semana seguinte, para dar tempo de exportar.
- **Exclusão do titular**: apagar tudo de um respondente pela mesma identidade opaca com que o SDK
  o apresentou — referência do app ou dispositivo.
- **Auditoria**: registro de cada exclusão — quando e quanto saiu, nunca quem.

## Clarifications

### Session 2026-09-12

- Q: Sem prazo configurado, o que é descartado? → A: Nada. O padrão é não apagar.
- Q: O prazo de texto livre apaga a resposta? → A: Não. Apaga só o texto; a resposta continua
  existindo e contando como dada.
- Q: Os resultados perdem o histórico depois do descarte? → A: Não. O agregado das respostas que
  saem é congelado por versão antes de apagar; os resultados somam o congelado ao que ficou quando
  o recorte é só de versão.
- Q: A exclusão do titular é por identificador interno? → A: Não. É pela identidade opaca que chega
  do app hospedeiro: referência do app ou, para quem nunca teve uma, o dispositivo. Exatamente uma
  das duas.
- Q: Pedido repetido de exclusão é erro? → A: Não. Responde que nada foi apagado e não cria registro.
- Q: O registro guarda quem foi excluído? → A: Não. Guarda quando e quantas exibições e respostas
  saíram. Guardar a referência seria o mesmo dado num lugar diferente. O campo de quem executou
  existe e fica vazio até haver login.
- Q: O agregado congelado é afetado pela exclusão do titular? → A: Não. Ele é anônimo e fica.
- Q: E se o descarte cair no meio? → A: Cada lote é atômico: o agregado do lote é congelado e as
  respostas dele saem juntas. Rodar de novo é seguro.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Cumprir a política de retenção (Priority: P1)

Todo dia, fora de pico, o Pitaco apaga o que venceu pela política de cada aplicação: as respostas
pelo prazo geral, o texto livre pelo prazo próprio. Antes de apagar, congela o agregado.

**Why this priority**: retenção prometida e não cumprida é risco legal; é o motivo de existir da
política.

**Independent Test**: com prazo de 30 dias, semear respostas de 40 e de 10 dias, rodar o descarte e
conferir que só as antigas saíram, que o agregado foi congelado e que a execução foi registrada.

**Acceptance Scenarios**:

1. **Given** uma aplicação sem prazo, **When** o descarte roda, **Then** nada é apagado nem
   registrado.
2. **Given** respostas além do prazo geral, **When** o descarte roda, **Then** elas saem em lotes, o
   agregado de cada lote é congelado por versão e as recentes ficam.
3. **Given** texto livre além do prazo de texto e dentro do prazo geral, **When** o descarte roda,
   **Then** o texto é apagado e a resposta continua contando como dada.
4. **Given** o descarte já executado, **When** roda de novo, **Then** nada é apagado nem congelado de
   novo e nenhuma execução vazia é registrada.
5. **Given** duas aplicações, **When** o descarte roda, **Then** uma não descarta o que é da outra.
6. **Given** uma execução que apagou algo, **Then** ela é registrada com a quantidade de respostas
   apagadas e de textos limpos.

---

### User Story 2 - Ver o que vai sair antes que saia (Priority: P2)

Na tela de privacidade, o operador vê os prazos efetivos, quando roda o próximo descarte, quanto sai
nele e quanto terá saído uma semana depois, e se já houve algum descarte.

**Why this priority**: dá tempo de exportar antes do descarte.

**Independent Test**: com prazo configurado e respostas antigas, consultar a prévia antes do
primeiro descarte.

**Acceptance Scenarios**:

1. **Given** uma aplicação sem prazo, **When** consulto a prévia, **Then** ela diz que nada é
   descartado e não há próxima execução.
2. **Given** um prazo e respostas vencendo, **When** consulto, **Then** recebo a próxima execução e
   quantas respostas e textos saem nela e até uma semana depois.
3. **Given** o descarte desligado, **When** consulto, **Then** não há próxima execução e a prévia é
   calculada sobre o agora.
4. **Given** um descarte já feito, **When** consulto, **Then** a prévia informa quando foi.
5. **Given** uma aplicação inexistente, **Then** recebo aplicação não encontrada.

---

### User Story 3 - Apagar um respondente a pedido (Priority: P1)

O titular pede a exclusão ao app hospedeiro, que repassa a referência dele. O operador apaga tudo do
respondente — exibições, respostas, opções, atributos, supressões — e os resultados e o export param
de contar o que era dele.

**Why this priority**: obrigação legal; sem ela, o Pitaco não pode guardar dado pessoal de ninguém.

**Independent Test**: um respondente com respostas em duas pesquisas é apagado pela referência; os
resultados das duas deixam de contá-lo e o registro diz quanto saiu.

**Acceptance Scenarios**:

1. **Given** um respondente com exibições em duas pesquisas, **When** o apago pela referência,
   **Then** tudo dele sai, e resultados e export param de contá-lo.
2. **Given** um respondente sem referência, **When** o apago pelo dispositivo, **Then** ele é apagado.
3. **Given** um pedido repetido, **When** executo, **Then** recebo que nada foi apagado e nenhum
   registro é criado.
4. **Given** um respondente de outra aplicação, **When** peço pela minha, **Then** ele não é alcançado.
5. **Given** nenhuma identidade, ou as duas, **When** peço, **Then** recebo recusa por validação e
   nada é apagado nem registrado.
6. **Given** uma aplicação inexistente ou identificador malformado, **Then** recebo aplicação não
   encontrada.

---

### User Story 4 - Provar que a exclusão aconteceu (Priority: P2)

O operador lista os registros de exclusão da aplicação: quando, quantas exibições e respostas.

**Why this priority**: a exclusão precisa ser demonstrável sem guardar o que foi excluído.

**Independent Test**: apagar dois respondentes e listar os registros.

**Acceptance Scenarios**:

1. **Given** exclusões feitas, **When** listo, **Then** recebo os registros da aplicação, do mais
   recente para o mais antigo, paginados.
2. **Given** um registro, **Then** ele traz quando e quanto saiu, e nunca a referência excluída.

---

### Edge Cases

- **Só prazo de texto, sem prazo geral**: o texto vence; a resposta fica.
- **Sem prazo de texto próprio**: o texto segue o prazo geral (prazo efetivo).
- **Exibição só com respostas puladas** entre as descartadas: não conta como exibição com resposta
  no agregado congelado.
- **Apagar a aplicação**: nenhuma linha dela fica em tabela nenhuma (cascata).
- **Apagar a pesquisa**: leva versões, perguntas, exibições, respostas e o congelado.
- **Descarte desligado por configuração**: o job não existe; a política da aplicação continua
  configurada e a prévia diz que não há próxima execução.
- **Falha no descarte**: logada uma vez pelo job, com a natureza da falha; o que já foi feito por lote
  continua consistente.

## Requirements *(mandatory)*

### Functional Requirements

**Descarte**

- **FR-001**: O sistema MUST executar o descarte uma vez por dia, em horário configurável (padrão
  03:47 UTC), podendo ser desligado por configuração.
- **FR-002**: Só aplicações com algum prazo configurado MUST ser processadas; sem prazo, nada é
  apagado.
- **FR-003**: Respostas respondidas antes de "agora menos o prazo geral" MUST ser apagadas, em lotes
  (padrão 1.000).
- **FR-004**: Antes de apagar um lote, o sistema MUST congelar, na mesma transação, o agregado das
  respostas dele por versão: contagem por situação (respondida, pulada, não aplicável), por opção
  marcada e por valor numérico, mais o número de exibições com ao menos uma resposta dada e o
  instante antes do qual tudo foi respondido.
- **FR-005**: Texto livre respondido antes de "agora menos o prazo efetivo de texto" MUST ser
  apagado, mantendo a resposta.
- **FR-006**: Cada execução que apagou ou limpou algo MUST ser registrada com as quantidades; execução
  sem efeito MUST NOT ser registrada.
- **FR-007**: Rodar o descarte de novo MUST ser seguro: o que já saiu não é encontrado de novo e nada
  é congelado duas vezes.

**Prévia**

- **FR-008**: O sistema MUST permitir consultar a prévia: se há política, os prazos efetivos, a
  próxima execução (ausente com descarte desligado ou sem política), quanto sai na próxima execução
  e quanto terá saído uma semana depois, e quando foi o último descarte.

**Exclusão do titular**

- **FR-009**: O sistema MUST permitir excluir um respondente da aplicação por referência do app ou
  por dispositivo, exigindo exatamente uma das duas.
- **FR-010**: A exclusão MUST apagar o respondente e, em cascata, as exibições, respostas, opções,
  atributos e supressões dele, de forma irreversível e atômica com o registro.
- **FR-011**: A resposta MUST informar se algo foi apagado e quantas exibições e respostas saíram.
- **FR-012**: Pedido sem alvo MUST responder que nada foi apagado, sem erro e sem registro.
- **FR-013**: O agregado congelado pela retenção MUST NOT ser afetado pela exclusão.
- **FR-014**: A identidade usada na exclusão MUST NOT ir para o log nem para o registro.

**Auditoria**

- **FR-015**: Cada exclusão efetiva MUST gerar um registro com instante e quantidades, e campo de
  autor reservado e vazio.
- **FR-016**: O sistema MUST permitir listar os registros da aplicação, paginados, do mais recente
  para o mais antigo.

**Armazenamento**

- **FR-017**: Apagar aplicação, pesquisa ou respondente MUST levar em cascata tudo que pende deles.

**Transversais**

- **FR-018**: As rotas desta fatia MUST pertencer à superfície administrativa.
- **FR-019**: Aplicação inexistente ou malformada MUST recusar com `application.not_found`.

### Key Entities

- **Política de retenção**: aplicação, prazo de respostas e prazo efetivo de texto livre.
- **Agregado congelado**: pesquisa, versão, motivo (retenção), "descartado antes de", exibições com
  resposta, e contagens por pergunta, dimensão (situação, opção, número) e valor.
- **Execução de retenção**: aplicação, respostas apagadas, textos limpos, instante.
- **Registro de exclusão**: aplicação, exibições e respostas apagadas, autor reservado, instante.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um dia depois de uma resposta vencer pelo prazo da aplicação, ela não existe mais.
- **SC-002**: Os números de um resultado consolidado por versão são os mesmos antes e depois do
  descarte.
- **SC-003**: Depois da exclusão de um titular, nenhuma tabela contém linha ligada a ele, e nenhum
  registro ou log contém a identidade dele.
- **SC-004**: A prévia antecipa com uma semana de folga o volume que vai sair.
- **SC-005**: Repetir o descarte ou o pedido de exclusão nunca apaga nem registra nada a mais.

## Assumptions

- O agregado congelado não guarda data nem atributo; por isso não entra em recortes de período ou
  de atributo dos resultados.
- O autor da exclusão fica vazio até a autenticação do painel.
- O horário do descarte é posterior ao expurgo de saúde do SDK e fora de qualquer pico.
- Os eventos de interação que vencem junto com as respostas são tratados na `013-interaction-events`.

## Dependencies

- `backend 007-application-management`: prazos de retenção da aplicação.
- `backend 004-response-collection` e `backend 006-collection-read-list`: respostas, exibições,
  respondentes e a supressão de texto vencido na leitura.
- `backend 010-sdk-health`: supressões, que entram na cascata do respondente.
- `backend 012-survey-results`: soma o agregado congelado e avisa quando ele foi aplicado.
- `backend 009-advanced-authoring`: as colunas do aviso de texto livre chegaram na mesma migration
  desta fatia.
