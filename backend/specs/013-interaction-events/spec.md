# Feature Specification: Eventos de interação — catálogo, ingestão e leitura de comportamento

**Feature Branch**: `013-interaction-events`

**Created**: 2026-09-12

**Status**: Implemented

**Input**: User description: "Quero saber como as pessoas respondem, não só o que respondem: em que pergunta param, quanto tempo levam, se voltam, se trocam de resposta, por onde dispensam. O SDK vai mandar eventos de interação num catálogo fechado nosso, e o painel precisa de uma leitura de comportamento com as definições de cada número."

## Contexto

Os resultados (`012`) dizem o que foi respondido. Eles não dizem **como**: em qual pergunta as
pessoas desistem, quanto tempo de atenção cada pergunta custa, onde a validação trava, por qual
gesto a pesquisa é dispensada. Essas respostas só existem do lado do SDK, durante a exibição.

Esta fatia cria o caminho completo desses sinais:

- **Catálogo fechado e versionado** de 18 eventos de interação, igual para toda aplicação. O app
  hospedeiro pode ouvir os eventos, nunca criar tipo nem alterar payload. Nenhum evento carrega
  conteúdo de texto livre.
- **Ingestão** em lotes, por exibição, com descarte evento a evento — nunca um lote recusado por um
  evento ruim —, idempotência pelo par (exibição, sequência), teto por exibição e janela de
  aceitação contada no relógio do servidor.
- **Esquema publicado no OpenAPI**, gerado do catálogo do servidor, como fonte única para o teste de
  contrato do SDK.
- **Leitura de comportamento** por pesquisa: funil por pergunta, tempo ativo (mediana e p90), volta,
  troca de resposta, bloqueio de validação e vias de dispensa, com a definição de cada número.

Fronteiras:

- **O SDK que produz os eventos** é documentado na spec de eventos do SDK. Aqui está o contrato do
  lado do servidor.
- **Eventos anteriores à exibição** (bloqueio, adiamento) não fazem parte do catálogo enviado: vão
  só para o app hospedeiro.
- **Não há agregado congelado de comportamento**: depois do descarte por retenção, a leitura conta
  só o que ficou.

## Clarifications

### Session 2026-09-12

- Q: Um evento inválido recusa o lote? → A: Não. Cada evento é lido sozinho; o inválido é descartado
  e contado, os outros seguem. `400` só para corpo ilegível, lote vazio, ausente ou acima de 100, ou
  campo do envelope com tipo JSON errado.
- Q: Exibição inexistente ou de outra aplicação é `404`? → A: Não. Exibição inexistente, de outra
  aplicação ou de aplicação inativa descartam o lote inteiro com o mesmo `202`, sem revelar qual é o
  caso.
- Q: O que o `202` devolve? → A: A contagem: aceitos, repetidos e descartados por motivo. Serve à
  depuração do SDK; a fila local remove o lote em qualquer `202`.
- Q: A janela de aceitação usa o relógio do dispositivo? → A: Não. Conta do instante de abertura da
  exibição no servidor (padrão 7 dias). O instante do dispositivo é só informativo.
- Q: E se o SDK for mais novo que o servidor? → A: Tipo conhecido é aceito com os campos que o
  servidor conhece; tipo desconhecido é descartado e contado.
- Q: Escolha em pergunta de texto livre? → A: É descartada: o valor seria o texto digitado.
- Q: Qual a base das taxas de comportamento? → A: A exibição instrumentada — com ao menos um evento
  aceito —, para que SDKs sem eventos não diluam as taxas. O total de exibidas do recorte vem à parte.
- Q: Eventos vencem com a retenção? → A: Sim. A retenção de respostas da aplicação também apaga os
  eventos recebidos antes do prazo, em lotes; o registro de execução continua contando só respostas
  e textos.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Receber os eventos de uma exibição (Priority: P1)

O SDK envia, depois de abrir a exibição, lotes de até 100 eventos do catálogo. O servidor grava o que
é legível, descarta e conta o resto, e responde `202` com a contagem.

**Why this priority**: sem ingestão não há comportamento.

**Independent Test**: enviar um lote com eventos válidos, um de tipo desconhecido e um repetido, e
conferir as linhas no banco e a contagem do recibo.

**Acceptance Scenarios**:

1. **Given** uma exibição aberta, **When** o SDK envia um lote legível, **Then** recebe `202` e as
   linhas gravadas trazem envelope e payload fechado pelo catálogo.
2. **Given** o mesmo lote reenviado, **Then** nada é gravado de novo e os eventos voltam como
   repetidos; o mesmo `seq` duas vezes no próprio lote grava uma vez.
3. **Given** exibição de outra aplicação, inexistente, identificador malformado ou aplicação inativa,
   **Then** o lote inteiro é descartado como exibição indisponível, com a mesma resposta.
4. **Given** um tipo fora do catálogo, **Then** é descartado e contado; `catalogVersion` mais nova com
   tipo conhecido grava os campos conhecidos.
5. **Given** um evento de texto com conteúdo, **Then** só o tamanho sobrevive; escolha registrada em
   pergunta de texto livre é descartada.
6. **Given** pergunta fora da versão exibida ou envelope incompleto, **Then** o evento é descartado e
   o resto grava.
7. **Given** o teto de 500 eventos por exibição, **Then** o excesso é cortado pela ordem de `seq` e o
   que passou dele não entra depois.
8. **Given** a janela de 7 dias da abertura vencida, **Then** o lote é descartado; dentro dela,
   gravado.
9. **Given** lote vazio, ausente, acima de 100, JSON malformado ou `seq` com tipo errado, **Then**
   `400` com o campo, e nada é gravado.
10. **Given** sem chave ou com chave desconhecida, **Then** `401`, e nada é gravado.
11. **Given** a exibição apagada, **Then** os eventos dela vão junto.

---

### User Story 2 - Publicar o catálogo como contrato (Priority: P1)

O OpenAPI publica o catálogo — a lista de tipos com a versão, a união discriminada por tipo e o
payload fechado de cada um — gerado da mesma fonte que o servidor usa para validar.

**Why this priority**: é o que impede o SDK e o servidor de divergirem em silêncio.

**Independent Test**: ler `/api/v3/api-docs` e comparar a lista de tipos com o catálogo do servidor.

**Acceptance Scenarios**:

1. **Given** o OpenAPI, **Then** `InteractionEventType` lista os 18 nomes, com a versão do catálogo
   como extensão.
2. **Given** o OpenAPI, **Then** `InteractionEvent` é união discriminada por `type`, uma variante por
   tipo, e o lote aponta para ela.
3. **Given** cada `<Tipo>Data`, **Then** traz só os campos do catálogo e nenhum outro.
4. **Given** evento de pergunta, **Then** exige `questionKey`; evento de pesquisa nem o declara.

---

### User Story 3 - Ler o comportamento de uma pesquisa (Priority: P1)

O operador abre o comportamento e vê, por pergunta, quantas exibições a viram, responderam, pularam
e pararam nela; o tempo ativo mediano e o p90; quantas voltaram, trocaram de resposta e travaram na
validação; e, para a pesquisa, por qual via ela foi dispensada — tudo com a definição ao lado.

**Why this priority**: é a razão de coletar os eventos.

**Independent Test**: enviar eventos conhecidos para três exibições e conferir cada número contra a
definição.

**Acceptance Scenarios**:

1. **Given** eventos enviados, **Then** funil, tempo ativo, volta, troca, bloqueio e dispensa conferem
   com o que foi enviado.
2. **Given** uma pergunta sem evento, **Then** ela aparece com zeros, na ordem da pesquisa.
3. **Given** dispensas, **Then** as seis vias aparecem sempre, com fração sobre o total; via ausente ou
   fora do catálogo conta como não informada.
4. **Given** qualquer leitura, **Then** cada métrica vem com a definição escrita.
5. **Given** os recortes de atributo, período e versão, **Then** são os mesmos dos resultados.
6. **Given** pesquisa nunca publicada, **Then** vazio sem consultar os eventos; recorte inválido,
   `400`; chave do SDK, `403`; fora do escopo, `404`.
7. **Given** qualquer leitura, **Then** nada gravado é alterado.

---

### User Story 4 - Eventos vencem com as respostas (Priority: P2)

A política de retenção de respostas da aplicação também apaga os eventos de interação recebidos antes
do prazo.

**Why this priority**: eventos de escolha carregam o valor da resposta; mantê-los depois da resposta
seria reter o mesmo dado por outro caminho.

**Independent Test**: com prazo de 30 dias, eventos de 40 e de 10 dias; rodar o descarte e conferir.

**Acceptance Scenarios**:

1. **Given** eventos recebidos antes do prazo de respostas, **When** o descarte roda, **Then** saem em
   lotes, e os recentes ficam.
2. **Given** só prazo de texto, ou nenhum prazo, **Then** nenhum evento sai.

---

### Edge Cases

- **Dois lotes simultâneos da mesma exibição**: passam um de cada vez pela contagem do teto.
- **`seq` fora da faixa de inteiro ou zero**: envelope inválido.
- **`displayId` no evento diferente do caminho**: envelope inválido; ausente vale o do caminho.
- **Inteiro como `12.0`** (JavaScript): aceito; fração, não.
- **Campo do `data` fora da forma**: some, e o evento fica sem ele.
- **Nenhum evento legível no lote**: nada é travado nem gravado.
- **Exibição de SDK sem eventos**: entra em `displayed`, não em `instrumented`.

## Requirements *(mandatory)*

### Functional Requirements

**Catálogo**

- **FR-001**: O sistema MUST manter um catálogo fechado, versão 1, com exatamente estes 18 tipos:
  `survey_presented`, `question_viewed`, `answer_selected`, `answer_changed`, `answer_deselected`,
  `text_focused`, `text_edited`, `text_blurred`, `validation_blocked`, `question_skipped`,
  `question_not_applicable`, `navigated_next`, `navigated_back`, `question_left`,
  `survey_backgrounded`, `survey_foregrounded`, `survey_dismissed`, `survey_completed`.
- **FR-002**: Cada tipo MUST ter um payload fechado; campo extra ou fora da forma MUST ser removido
  antes de gravar.
- **FR-003**: Eventos de pergunta MUST exigir a chave estável da pergunta; eventos de pesquisa MUST
  descartá-la.
- **FR-004**: Nenhum evento MUST carregar conteúdo de texto livre: eventos de texto guardam só o
  tamanho; valor de resposta é o valor da opção (1..120) ou um número.
- **FR-005**: O catálogo MUST ser publicado no OpenAPI, gerado da mesma fonte usada na validação, com
  a versão como extensão, a união discriminada por tipo e o payload fechado de cada tipo.
- **FR-006**: Tipo novo, campo novo ou valor novo em lista fechada MUST exigir versão nova do
  catálogo; o servidor MUST aceitar versões anteriores.

**Ingestão**

- **FR-007**: O sistema MUST receber lotes de 1 a 100 eventos por exibição, respondendo `202` com
  aceitos, repetidos e descartados por motivo: exibição indisponível, fora da janela, tipo
  desconhecido, envelope inválido, pergunta desconhecida, acima do teto.
- **FR-008**: Cada evento MUST ser avaliado sozinho; um evento inválido MUST NOT recusar o lote.
- **FR-009**: `400` MUST ser reservado a corpo ilegível, lote vazio, ausente ou acima de 100, e campo
  do envelope com tipo JSON errado.
- **FR-010**: Exibição inexistente, de outra aplicação ou de aplicação inativa MUST descartar o lote
  inteiro com a mesma resposta.
- **FR-011**: O par (exibição, sequência) MUST ser único; reenvio MUST NOT gravar de novo; no mesmo
  lote, o primeiro de cada sequência vence.
- **FR-012**: O número de eventos por exibição MUST ser limitado (padrão 500), cortando pela ordem
  de sequência e contando o que já está gravado, exato sob concorrência.
- **FR-013**: Lotes recebidos depois da janela de aceitação (padrão 7 dias da abertura, no relógio do
  servidor) MUST ser descartados.
- **FR-014**: Envelope sem versão do catálogo, sequência, instante ou tempo desde a apresentação,
  sequência menor que 1, tempo negativo ou exibição divergente do caminho MUST ser descartado como
  envelope inválido.
- **FR-015**: Pergunta fora da versão exibida MUST ser descartada; escolha em pergunta de texto
  livre MUST ser descartada como envelope inválido.
- **FR-016**: Versão do catálogo mais nova que a do servidor MUST ser aceita para tipos conhecidos,
  com os campos conhecidos.
- **FR-017**: O log MUST registrar contagens e motivos, nunca o payload.
- **FR-018**: Os eventos MUST ser apagados junto da exibição (e, por ela, do respondente e da
  pesquisa).
- **FR-019**: A capacidade de eventos de interação MUST ser declarada no catálogo de capacidades do
  SDK e MUST NOT entrar na versão mínima de uma pesquisa.

**Retenção**

- **FR-020**: O descarte por retenção de respostas MUST apagar, em lotes, os eventos recebidos antes
  do prazo de respostas da aplicação; prazo só de texto MUST NOT apagar eventos.

**Leitura de comportamento**

- **FR-021**: O sistema MUST devolver, por pesquisa e recorte (os mesmos dos resultados), o total de
  exibidas e o de instrumentadas.
- **FR-022**: Para cada pergunta, na ordem da pesquisa, MUST devolver vistas, respondidas, puladas,
  abandonadas nela, tempo ativo (amostras, mediana, p90), voltas e taxa de volta, escolhas, trocas e
  taxa de troca, exibições com bloqueio de validação e total de bloqueios — com zeros quando não há
  evento.
- **FR-023**: Taxas sem denominador MUST ser ausentes, não zero.
- **FR-024**: As dispensas MUST vir pelas seis vias do catálogo, sempre presentes, com fração sobre o
  total e a contagem de não informadas.
- **FR-025**: Cada métrica MUST vir com a definição textual, a mesma do contrato.
- **FR-026**: Abandono na pergunta MUST usar o mesmo prazo de abandono dos resultados.
- **FR-027**: A leitura MUST pertencer à superfície administrativa e MUST NOT alterar estado.

### Key Entities

- **Tipo de evento de interação**: nome no fio, se é de pergunta, e os campos do payload com forma.
- **Evento de interação**: exibição, sequência, versão do catálogo, tipo, chave da pergunta,
  instante no dispositivo, tempo desde a apresentação, payload fechado, instante de recebimento.
- **Recibo de ingestão**: aceitos, repetidos e descartados por motivo.
- **Comportamento da pesquisa**: exibidas, instrumentadas, métricas por pergunta, dispensas por via e
  definições.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Nenhum lote é perdido inteiro por causa de um evento inválido.
- **SC-002**: Reenviar o mesmo lote qualquer número de vezes resulta no mesmo conjunto de eventos
  gravados.
- **SC-003**: Nenhuma exibição passa de 500 eventos gravados, mesmo com lotes simultâneos.
- **SC-004**: Nenhum evento gravado contém texto digitado pelo respondente.
- **SC-005**: Um teste de contrato consegue comparar a lista de tipos do SDK com a do servidor lendo
  só o OpenAPI.
- **SC-006**: Toda métrica de comportamento exibida tem a definição escrita ao lado, idêntica à do
  contrato.

## Assumptions

- O SDK emite os eventos a partir das transições da pesquisa, nunca da UI, e numera `seq`
  monotonicamente por exibição a partir de 1.
- A fila local do SDK tem idade máxima menor que a janela de aceitação.
- Só existe a versão 1 do catálogo; "versão em que o tipo nasceu" não é modelada até haver a segunda.
- Os descartes são visíveis no recibo e no log; não há contador persistido.
- O valor de resposta nos eventos é validado só na forma (valor de opção ou número), não contra as
  opções da pergunta; `toKey` e `sourceKey`, só no formato de chave.

## Dependencies

- `backend 004-response-collection`: exibição aberta, versão exibida e chave estável da pergunta.
- `backend 010-sdk-health`: catálogo de capacidades e proteção da superfície (os eventos entram no
  balde por chave).
- `backend 011-privacy-retention`: o job de descarte, estendido aqui para os eventos.
- `backend 012-survey-results`: recorte, prazo de abandono e definição das perguntas reaproveitados
  na leitura de comportamento.
- Spec de eventos do SDK: produz os eventos deste catálogo.
