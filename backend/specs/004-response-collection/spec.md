# Feature Specification: Respondente, coleta e respostas — a superfície pública do SDK

**Feature Branch**: `004-response-collection`

**Created**: 2026-09-08

**Status**: Draft

**Input**: User description: "Vamos lidar com a parte do respondente, coleta e respostas. Obsidian tem documentação mas a documentação principal é esse projeto. Obsidian: Pitaco"

## Contexto

A autoria já produziu o artefato: uma versão publicada e congelada, com perguntas em ordem,
disparo e regras de segmentação. O que o Pitaco ainda não sabe fazer é a outra metade do
produto — **entregar essa pesquisa a alguém e guardar o que essa pessoa respondeu**.

Esta feature abre a primeira superfície consumida pelo **SDK** e não pelo painel. O app
hospedeiro avisa que um evento aconteceu; o Pitaco decide se há pesquisa para aquele
respondente naquele instante e devolve **a pesquisa inteira em uma resposta só**; o SDK
abre uma sessão, exibe, a pessoa responde ou dispensa, e o SDK devolve o que foi coletado
amarrado àquela sessão.

Duas propriedades governam o desenho, e valem mais do que qualquer requisito individual:

- **O caso mais comum é o nada.** A grande maioria dos eventos não resulta em pesquisa, e
  isso não é erro — é o funcionamento normal. Essa consulta precisa ser barata e incapaz de
  segurar a interface do app hospedeiro.
- **A ordem das camadas de elegibilidade muda o resultado.** Sortear antes ou depois de
  consultar o histórico do respondente altera a proporção efetiva de exibição. A ordem é
  requisito, não detalhe de implementação.

Quatro fronteiras desenham o escopo:

- **Não é autoria.** Nada aqui cria, altera ou publica pesquisa. A coleta lê a versão
  publicada e nunca a modifica.
- **Não é resultado.** Esta feature guarda a resposta crua. Somar, cruzar, apresentar
  agregado e exportar é da feature de resultados.
- **Não é controle de exposição.** Intervalo de descanso entre pesquisas, cota que encerra a
  pesquisa sozinha e prioridade declarada de desempate são da feature de controle de exposição.
  A **segmentação por atributo** é a exceção: as regras já nascem congeladas na versão
  publicada desde a autoria, e deixá-las escritas sem serem avaliadas entregaria a pesquisa
  exatamente a quem ela queria evitar.
- **Não é o SDK.** O renderizador, a fila local e a degradação silenciosa moram no
  repositório do SDK. Aqui está o que a API precisa oferecer para que tudo isso seja possível
  — e o que ela precisa recusar para que o SDK nunca decida sozinho.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Descobrir se há pesquisa para este respondente agora (Priority: P1)

O app hospedeiro informa que um evento nomeado aconteceu para um respondente. O Pitaco
resolve a aplicação **pela chave de acesso**, nunca por um identificador informado no
caminho, e aplica as camadas de elegibilidade **nesta ordem**: aplicação ativa, pesquisa no
ar, janela de datas, evento que casa, histórico do respondente, regras de segmentação,
sorteio de amostragem.

Quando sobra pesquisa, a resposta traz o conteúdo integral da versão publicada — perguntas na
ordem definida, com chave estável, enunciado, tipo, obrigatoriedade, opções e faixa numérica.
Uma resposta só: o SDK não faz segunda chamada para renderizar e consegue exibir a pesquisa
inteira ainda que a rede caia logo depois. Quando não sobra, a resposta diz "não há nada" de
forma barata e explícita — **e isso não é erro**.

**Why this priority**: é o coração da regra de negócio e a porta de entrada. Sem ela o SDK não
tem o que mostrar e nenhuma resposta pode existir. Entrega valor sozinha porque já permite
verificar, ponta a ponta, se uma pesquisa publicada chega a quem deveria — e não chega a quem
não deveria.

**Independent Test**: publicar uma pesquisa pela superfície de autoria, consultar com a chave
da aplicação, o evento correspondente e os atributos que satisfazem as regras, e receber as
perguntas na ordem montada; repetir com evento diferente, fora da janela, com a pesquisa
pausada e com atributo que viola a regra, e receber "nada a exibir" nos quatro; repetir com
chave revogada e receber recusa.

**Acceptance Scenarios**:

1. **Given** uma pesquisa publicada e ativa, com disparo no evento `checkout_concluido` e
   proporção 1, **When** o SDK consulta com a chave daquela aplicação e esse evento, **Then**
   recebe a versão publicada com todas as suas perguntas na ordem definida, em uma única
   resposta.
2. **Given** a mesma pesquisa, **When** a consulta informa outro evento, **Then** a resposta
   indica ausência de pesquisa e **nenhum registro é criado**.
3. **Given** uma pesquisa cuja janela ainda não abriu, e outra cuja janela fechou há um
   minuto, **When** o SDK consulta pelo evento de cada uma, **Then** nenhuma é entregue.
4. **Given** uma pesquisa pausada e outra ativa escutando o mesmo evento, **When** o SDK
   consulta, **Then** só a ativa é entregue.
5. **Given** uma pesquisa em rascunho com uma segunda versão em rascunho sobre uma publicada,
   **When** o SDK consulta, **Then** recebe o conteúdo da versão **publicada**, nunca o do
   rascunho.
6. **Given** uma chave revogada, desconhecida ou ausente, **When** o SDK consulta, **Then** a
   consulta é recusada sem revelar nada sobre pesquisas.
7. **Given** uma aplicação inativa, **When** o SDK consulta com uma chave válida dela,
   **Then** nada é entregue.
8. **Given** um respondente não sorteado pela proporção de amostragem, **When** ele dispara o
   mesmo evento cinco vezes, **Then** continua não recebendo, nas cinco.
9. **Given** uma pesquisa com a regra "plano é igual a premium", **When** a consulta informa
   um respondente com plano `free`, **Then** nada é entregue.
10. **Given** a mesma pesquisa, **When** a consulta **não informa** o atributo `plano`,
    **Then** nada é entregue — atributo ausente não casa com regra que o exige.
11. **Given** uma pesquisa com a regra "cupom está ausente", **When** a consulta informa um
    respondente sem o atributo `cupom`, **Then** a pesquisa é entregue.
12. **Given** uma pesquisa com duas regras, **When** o respondente satisfaz uma e viola a
    outra, **Then** nada é entregue — as regras valem em conjunção.
13. **Given** duas pesquisas ativas escutando o mesmo evento e ambas elegíveis, **When** o SDK
    consulta, **Then** recebe exatamente **uma**, e a mesma consulta repetida devolve a mesma.
14. **Given** uma chave pública, **When** ela é usada em uma rota administrativa, **Then** é
    recusada.

---

### User Story 2 - Abrir a sessão e registrar as respostas (Priority: P1)

Com a pesquisa em mãos, o SDK abre uma **sessão** — o registro de que aquela pesquisa foi
exibida àquele respondente sob aquela versão. A sessão é aberta pelo SDK, e não pela consulta
de elegibilidade, porque o SDK pode descartar em silêncio o tipo de pergunta que não sabe
renderizar: se não sobrar nenhuma pergunta, nada é exibido e **nenhuma sessão é aberta** —
pesquisa vazia não é impressão, e contá-la estragaria a taxa de resposta.

A sessão carrega um identificador gerado no próprio dispositivo. É ele que faz o reenvio da
fila local do SDK continuar sendo **uma resposta só**: o mesmo envio que chega duas vezes não
cria uma segunda sessão nem uma segunda resposta.

As respostas apontam a pergunta pela **chave estável** dela, nunca pela posição, e trazem o
valor no formato que o tipo exige. Cada resposta é registrada como *respondida* ou *pulada* —
a pergunta opcional deixada em branco é dado, não ausência de dado. O envio é validado
inteiro antes de qualquer gravação, e a recusa aponta a pergunta e o motivo de **cada**
problema, não apenas o primeiro.

**Why this priority**: é a razão de a pesquisa existir. Junto com a US1 forma o ciclo
verificável: pesquisa entra pela autoria, sai pela entrega, volta como resposta.

**Independent Test**: consultar elegibilidade, abrir sessão, enviar um conjunto válido de
respostas e ler de volta do banco exatamente o que foi enviado, amarrado à versão publicada;
reenviar o mesmo pacote e confirmar que continua havendo uma resposta só; enviar um conjunto
inválido e confirmar que nada foi gravado.

**Acceptance Scenarios**:

1. **Given** uma pesquisa entregue com cinco perguntas, **When** o SDK abre a sessão e envia
   resposta válida para todas, **Then** a sessão fica concluída e as cinco respostas ficam
   guardadas amarradas à versão publicada.
2. **Given** uma sessão aberta, **When** o mesmo envio chega duas vezes com o mesmo
   identificador de dispositivo, **Then** continua existindo exatamente uma sessão e uma
   resposta por pergunta.
3. **Given** uma versão com uma pergunta obrigatória e uma opcional, **When** o envio traz só
   a opcional, **Then** é recusado apontando a pergunta obrigatória e nada é gravado.
4. **Given** a mesma versão, **When** o envio traz a obrigatória respondida e a opcional
   marcada como pulada, **Then** é aceito e a pulada fica guardada como pulada.
5. **Given** uma pergunta de escolha única com as opções `a` e `b`, **When** a resposta traz
   `c`, **Then** o envio é recusado apontando aquela pergunta, e nada é gravado.
6. **Given** uma pergunta NPS, **When** a resposta traz `11`, **Then** o envio é recusado por
   valor fora da faixa.
7. **Given** um envio com três problemas diferentes, **When** ele é recusado, **Then** a
   resposta descreve os três, cada um com a pergunta que o causou.
8. **Given** uma sessão de outra aplicação ou inexistente, **When** o SDK envia respostas,
   **Then** o envio é recusado como inexistente, sem revelar nada.
9. **Given** uma sessão aberta enquanto a pesquisa estava ativa, **When** a pesquisa é pausada
   e só então o envio chega, **Then** o envio é aceito e continua apontando a versão exibida.
10. **Given** um envio com a mesma chave de pergunta repetida, **When** ele chega, **Then** é
    recusado.

---

### User Story 3 - Registrar a dispensa com o mesmo peso de uma resposta (Priority: P1)

Quem fecha a pesquisa no meio é dado tão relevante quanto quem responde — é o que permite
responder à pergunta mais útil de qualquer sistema de pesquisa: **quem viu e não respondeu**.
Sem isso todo resultado fica enviesado para quem se dispôs a responder, e não há como separar
pesquisa ruim de momento ruim.

O SDK informa que a sessão foi dispensada. As respostas já dadas antes da dispensa são
**preservadas**, e o que as marca como parciais é o desfecho da sessão, não um estado próprio
delas. O desfecho avança em uma direção só: sessão concluída não vira dispensada, e dispensada
não vira concluída.

**Why this priority**: é P1, e não P2, porque a taxa de resposta é o número que decide o
projeto. Uma coleta que só registra sucesso produz um resultado que ninguém consegue
interpretar.

**Independent Test**: abrir sessão, enviar resposta para a primeira pergunta, dispensar, e ler
de volta a sessão como dispensada com a primeira resposta preservada; tentar concluí-la depois
e confirmar a recusa.

**Acceptance Scenarios**:

1. **Given** uma sessão aberta com a primeira pergunta respondida, **When** o respondente
   fecha e o SDK informa a dispensa, **Then** a sessão fica dispensada, com o instante, e a
   primeira resposta permanece guardada.
2. **Given** uma sessão dispensada, **When** o SDK tenta enviar mais respostas para ela,
   **Then** o envio é recusado.
3. **Given** uma sessão concluída, **When** o SDK tenta marcá-la como dispensada, **Then** a
   mudança é recusada e o desfecho permanece concluída.
4. **Given** uma sessão dispensada sem nenhuma resposta, **When** ela é lida de volta,
   **Then** ainda se sabe sob qual versão a pesquisa foi exibida.

---

### User Story 4 - Não incomodar de novo quem já resolveu (Priority: P2)

Quem já respondeu ou já **dispensou** uma pesquisa não a recebe de novo. Dispensar conta como
resolver: insistir com quem já disse não é a forma mais rápida de o usuário desinstalar o app.

Quem abriu e **abandonou** sem concluir nem dispensar é caso diferente — pode ter perdido a
rede, trocado de tela, sido interrompido. Esse pode receber de novo, até um limite de
tentativas, e a partir dele para de receber.

"Resolvido" vale para o **grupo de comparabilidade** da versão, não para a pesquisa inteira.
Quando a pesquisa publica uma versão que a autoria classificou como **semântica**, ela abre um
grupo novo — passou a ser outra pergunta — e volta a ser entregue a quem já havia respondido a
anterior. Uma versão apenas **cosmética** permanece no mesmo grupo e não reabre nada. É o que
permite repesquisar sem precisar criar uma pesquisa nova, e o que dá uso, na entrega, à
classificação que a autoria já calcula.

**Why this priority**: é a camada 5 da elegibilidade e muda o resultado da US1, mas a US1
funciona sem ela — só incomoda demais. Depende da US2 e da US3 para saber o que foi resolvido.

**Independent Test**: responder uma pesquisa e consultar de novo com o mesmo respondente sem
receber nada; repetir com uma sessão dispensada; abrir uma sessão e abandoná-la, e voltar a
receber até o limite de tentativas; publicar uma versão semântica nova e voltar a receber,
depois uma cosmética e não receber.

**Acceptance Scenarios**:

1. **Given** um respondente que concluiu a pesquisa, **When** o evento acontece de novo,
   **Then** a pesquisa não é entregue a ele.
2. **Given** um respondente que dispensou a pesquisa, **When** o evento acontece de novo,
   **Then** a pesquisa não é entregue a ele.
3. **Given** um respondente cuja sessão foi abandonada, **When** o evento acontece de novo,
   **Then** a pesquisa **é** entregue de novo.
4. **Given** um respondente que abandonou a pesquisa tantas vezes quanto o limite de
   tentativas, **When** o evento acontece de novo, **Then** a pesquisa não é mais entregue.
5. **Given** um respondente que concluiu a versão 1, **When** a versão 2 é publicada como
   mudança **semântica** e o evento acontece, **Then** a pesquisa é entregue de novo.
6. **Given** o mesmo respondente, **When** a versão 2 é publicada como mudança **cosmética**,
   **Then** a pesquisa não é entregue de novo.
7. **Given** um respondente que **dispensou** a versão 1, **When** a versão 2 semântica é
   publicada, **Then** a pesquisa é entregue de novo — dispensar resolve o grupo, não a
   pesquisa.
8. **Given** dois respondentes distintos na mesma aplicação, **When** um responde, **Then** a
   pesquisa continua sendo entregue ao outro.
9. **Given** o mesmo identificador de respondente em duas aplicações diferentes, **When** ele
   responde em uma, **Then** continua elegível na outra.

---

### User Story 5 - Reconhecer o respondente sem saber quem ele é (Priority: P2)

O respondente é identificado por uma **referência opaca vinda do app hospedeiro** e por um
**identificador de dispositivo gerado pelo SDK**. Quando existe a referência do app, ela manda;
quando não existe, vale o dispositivo. O Pitaco nunca resolve essa referência para uma pessoa:
não guarda nome, e-mail nem nada que identifique.

O mesmo humano em duas aplicações é **dois respondentes**, por desenho — é a consequência
direta da segmentação por aplicação.

**Why this priority**: sustenta o histórico da US4 e o desenho de privacidade inteiro. Não é
P1 porque a US1 e a US2 já funcionam com o dispositivo sozinho.

**Independent Test**: consultar com referência do app e depois com o mesmo dispositivo e outra
referência, verificando que são respondentes distintos; consultar duas vezes só com
dispositivo e verificar que é o mesmo respondente.

**Acceptance Scenarios**:

1. **Given** um respondente novo, **When** a primeira consulta chega, **Then** ele é
   registrado e reconhecido nas consultas seguintes pela mesma identificação.
2. **Given** consultas sem referência do app, **When** elas chegam do mesmo dispositivo,
   **Then** todas pertencem ao mesmo respondente.
3. **Given** um respondente identificado por dispositivo que passa a informar a referência do
   app, **When** a consulta chega, **Then** a referência do app prevalece a partir dali.
4. **Given** a mesma referência de app em duas aplicações, **When** ambas consultam, **Then**
   são dois respondentes distintos, com históricos independentes.

---

### Edge Cases

- **Evento que nenhuma pesquisa escuta.** Não há pesquisa e **nenhum registro é criado** — nem
  sessão, nem tentativa. É o caminho mais frequente do sistema e precisa ser o mais barato.
- **Duas pesquisas publicadas disputam o mesmo evento e o mesmo respondente.** Exatamente uma
  é entregue. O critério de desempate por prioridade declarada é da feature de controle de
  exposição; aqui o desempate é determinístico e documentado, e a consulta jamais devolve duas.
- **A versão publicada muda entre a abertura da sessão e o envio.** As respostas continuam
  amarradas à versão sob a qual a pesquisa foi exibida.
- **A pesquisa é pausada, encerrada ou tem a janela fechada depois de a sessão abrir.** Quem
  já recebeu termina de responder; a consulta de elegibilidade para de entregá-la
  imediatamente.
- **A sessão fica aberta indefinidamente.** Sem desfecho informado, é considerada abandonada
  depois de um prazo, e o respondente volta a ser elegível dentro do limite de tentativas.
- **O mesmo envio chega duas vezes** (rede instável, retentativa da fila local do SDK). O
  segundo não cria resposta nova nem falha de forma barulhenta.
- **Envio com chave de pergunta que não pertence à versão exibida.** Recusado.
- **Envio com a mesma chave de pergunta repetida.** Recusado.
- **Proporção de amostragem 0.** Nenhum respondente é elegível; a pesquisa publicada
  simplesmente nunca aparece.
- **Texto livre grande demais.** Recusado por limite explícito, não truncado em silêncio.
- **Atributo informado com nome que nenhuma regra usa.** Aceito e guardado no instantâneo da
  sessão, sem erro.
- **Versão publicada sem nenhuma pergunta.** Impossível pela autoria; a coleta não precisa
  tratar.

## Requirements *(mandatory)*

### Functional Requirements

**Porta pública**

- **FR-001**: O sistema DEVE autenticar toda operação da superfície pública pela chave de
  acesso da aplicação, recusando chave ausente, desconhecida ou revogada.
- **FR-002**: O sistema DEVE derivar a aplicação **da própria chave**, e NÃO DEVE aceitar a
  aplicação como parâmetro informado pelo chamador em nenhuma operação pública.
- **FR-003**: O sistema DEVE manter separadas a superfície pública, consumida pelo SDK, e a
  administrativa, consumida pelo painel, recusando o uso de chave pública em rota
  administrativa.
- **FR-004**: O sistema DEVE registrar o instante do último uso de cada chave de acesso, com
  precisão de minuto — o requisito é saber se a chave está viva, não contar requisições.

**Identidade do respondente**

- **FR-005**: O sistema DEVE identificar o respondente por uma referência opaca fornecida pelo
  app hospedeiro e por um identificador de dispositivo gerado pelo SDK, prevalecendo a
  referência do app quando ela existir.
- **FR-006**: O sistema DEVE tratar a mesma referência em aplicações diferentes como
  respondentes diferentes, com históricos independentes.
- **FR-007**: O sistema DEVE registrar o respondente na primeira vez que ele aparece, guardando
  o instante da primeira e da última vez em que foi visto.
- **FR-008**: O sistema NÃO DEVE exigir, resolver nem armazenar dado que identifique
  pessoalmente o respondente, e NÃO DEVE registrar em log a referência do app, o identificador
  de dispositivo nem os atributos informados.
- **FR-009**: O sistema DEVE aceitar, junto da consulta, um conjunto de atributos nome→valor do
  respondente e guardá-los como instantâneo da sessão quando ela for aberta, sem acumular
  perfil do respondente ao longo do tempo.

**Elegibilidade e entrega**

- **FR-010**: O sistema DEVE oferecer uma consulta que, dados a chave de acesso, a
  identificação do respondente, um nome de evento e os atributos conhecidos, devolva **no
  máximo uma** pesquisa a exibir.
- **FR-011**: O sistema DEVE aplicar as camadas de elegibilidade nesta ordem: aplicação ativa,
  pesquisa no ar, janela de datas, evento que casa, histórico do respondente, regras de
  segmentação, sorteio de amostragem — sortear antes de descartar quem não era elegível
  distorceria a proporção efetiva de exibição.
- **FR-012**: O sistema DEVE considerar apenas pesquisas cujo estado no instante da consulta
  seja **ativa** — rascunho, agendada, pausada e encerrada nunca são entregues, inclusive nos
  limites exatos da janela.
- **FR-013**: O sistema DEVE exigir correspondência exata entre o nome do evento informado e o
  nome do evento do disparo da versão publicada.
- **FR-014**: O sistema DEVE excluir da disputa as pesquisas que o respondente já resolveu,
  conforme a regra de pesquisa resolvida (FR-027).
- **FR-015**: O sistema DEVE avaliar **todas** as regras de segmentação da versão publicada
  em conjunção — o respondente precisa satisfazer todas — sobre os atributos informados na
  consulta, considerando as operações igual, diferente, presente e ausente.
- **FR-016**: O sistema DEVE tratar atributo ausente ou vazio como **não casando** com regra
  que o exige: a avaliação falha fechado, porque tratar ausência como "pode ser" entrega a
  pesquisa exatamente a quem ela queria evitar.
- **FR-017**: O sistema DEVE aplicar a proporção de amostragem da versão publicada **no
  servidor**, de modo que a decisão para um mesmo par respondente–pesquisa seja **estável**:
  consultas repetidas não alternam entre elegível e não elegível.
- **FR-018**: Quando mais de uma pesquisa sobreviver às camadas, o sistema DEVE escolher uma
  por critério determinístico e documentado, e devolver apenas ela.
- **FR-019**: Quando houver pesquisa, o sistema DEVE devolver, **em uma única resposta**, o
  conteúdo integral da versão publicada — identificador e número da versão, e as perguntas em
  ordem com chave estável, enunciado, tipo, obrigatoriedade, opções e faixa numérica quando
  houver.
- **FR-020**: Quando não houver pesquisa, o sistema DEVE responder com sucesso indicando
  ausência, NÃO DEVE tratar isso como erro e NÃO DEVE criar nenhum registro.
- **FR-021**: O sistema NÃO DEVE expor conteúdo de versão em rascunho em nenhum ponto da
  superfície pública.

**Sessão**

- **FR-022**: O sistema DEVE permitir que o SDK abra uma sessão para uma pesquisa entregue,
  amarrada ao respondente, à pesquisa e à versão publicada naquele instante, guardando o
  instante de abertura, o instantâneo dos atributos e a versão do SDK.
- **FR-023**: O sistema DEVE aceitar um identificador de sessão gerado no dispositivo e usá-lo
  como **chave de idempotência**: a mesma abertura repetida NÃO DEVE criar uma segunda sessão.
- **FR-024**: O sistema NÃO DEVE abrir sessão como efeito da consulta de elegibilidade — a
  abertura é um ato explícito do SDK, para que pesquisa descartada por incompatibilidade não
  conte como exibição.
- **FR-025**: O sistema DEVE permitir registrar o desfecho da sessão como **concluída** ou
  **dispensada**, guardando o instante, e DEVE recusar qualquer mudança de desfecho de uma
  sessão já fechada.
- **FR-026**: O sistema DEVE tratar como **abandonada** a sessão que permanecer sem desfecho
  além de um prazo, contado a partir da sua abertura, derivando esse estado na leitura, sem
  depender de rotina agendada.
- **FR-027**: O sistema DEVE considerar uma pesquisa **resolvida** para um respondente quando
  existir sessão dela concluída ou dispensada sob uma versão do **mesmo grupo de
  comparabilidade** da versão publicada corrente; sessão abandonada NÃO resolve, mas conta para
  um limite de tentativas, atingido o qual a pesquisa deixa de ser entregue àquele respondente.
- **FR-028**: Quando a pesquisa publicar uma versão que abre um grupo de comparabilidade novo
  — a mudança classificada como semântica pela autoria —, o sistema DEVE voltar a entregá-la a
  quem havia resolvido uma versão de grupo anterior; uma versão cosmética NÃO DEVE reabrir
  nada.

**Coleta das respostas**

- **FR-029**: O sistema DEVE aceitar o envio das respostas de uma sessão, com cada resposta
  referenciando a pergunta pela **chave estável** dela.
- **FR-030**: O sistema DEVE recusar o envio quando a sessão não existir, pertencer a outra
  aplicação ou já estiver fechada, tratando os dois primeiros casos como inexistência.
- **FR-031**: O sistema DEVE validar cada resposta contra o tipo da pergunta na versão da
  sessão: uma opção declarada para escolha única, um conjunto não vazio de opções declaradas e
  sem repetição para escolha múltipla, um inteiro dentro da faixa para avaliação, escala e NPS,
  e texto dentro do limite para texto livre.
- **FR-032**: O sistema DEVE registrar cada resposta como **respondida** ou **pulada**,
  exigindo resposta para toda pergunta obrigatória de uma sessão que se conclui e aceitando
  pulada nas opcionais.
- **FR-033**: O sistema DEVE recusar chave de pergunta que não pertença à versão da sessão e
  chave repetida no mesmo envio.
- **FR-034**: O sistema DEVE validar o envio **inteiro** antes de gravar e, ao recusar, NÃO
  DEVE gravar nenhuma resposta.
- **FR-035**: Toda recusa de envio DEVE identificar cada problema encontrado com a pergunta e
  o motivo, listando todos de uma vez.
- **FR-036**: O sistema DEVE aceitar o mesmo envio repetido para a mesma sessão sem criar uma
  segunda resposta para a mesma pergunta.
- **FR-037**: O sistema DEVE preservar as respostas já enviadas quando a sessão termina
  dispensada, sem estado próprio de "parcial" na resposta — o desfecho da sessão é o que dá
  esse contexto.
- **FR-038**: O sistema DEVE aceitar o envio de uma sessão aberta enquanto a pesquisa estava
  ativa, ainda que ela tenha sido pausada, encerrada, republicada ou tenha fechado a janela
  entre a abertura e o envio.
- **FR-039**: O sistema NÃO DEVE registrar em log o conteúdo das respostas.

**Fronteiras**

- **FR-040**: O sistema NÃO DEVE, nesta feature, aplicar intervalo de descanso entre pesquisas,
  aplicar cota de respostas que encerre a pesquisa sozinha nem usar prioridade declarada de
  desempate.
- **FR-041**: O sistema NÃO DEVE, nesta feature, limitar a taxa de requisições da superfície
  pública, nem acumular o catálogo de atributos e valores já observados para montar regra no
  painel.
- **FR-042**: O sistema NÃO DEVE, nesta feature, agregar, somar, exportar nem calcular
  indicador sobre as respostas coletadas, nem oferecer leitura administrativa delas.
- **FR-043**: O sistema NÃO DEVE, nesta feature, registrar pesquisa suprimida por
  incompatibilidade do SDK, acumular catálogo de eventos e atributos observados nem receber
  relato de erro do SDK.
- **FR-044**: O sistema NÃO DEVE, nesta feature, tratar lógica condicional entre perguntas —
  nenhuma resposta é registrada como não aplicável.

### Key Entities

- **Respondente**: quem responde. Pertence a uma aplicação e é identificado pela referência
  opaca do app hospedeiro ou, na falta dela, pelo identificador de dispositivo do SDK. Guarda
  quando foi visto pela primeira e pela última vez. **Não guarda atributos**: eles chegam a
  cada consulta e vivem no instantâneo da sessão.
- **Sessão de pesquisa**: o registro de que uma pesquisa foi exibida a um respondente sob uma
  versão. Carrega o identificador gerado no dispositivo — a chave de idempotência do reenvio —,
  o desfecho (iniciada, concluída, dispensada, abandonada), a versão do SDK, o instantâneo dos
  atributos informados e os instantes de abertura e fechamento. É o que amarra a resposta à
  exibição e o que torna a taxa de resposta possível.
- **Resposta**: o valor respondido para uma pergunta dentro de uma sessão. Uma por pergunta por
  sessão. Referencia a pergunta pela chave estável, registra se foi respondida ou pulada,
  guarda o valor no formato do tipo e o instante.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma pesquisa publicada pela superfície de autoria chega ao respondente certo e
  volta como resposta guardada, sem nenhum passo manual e sem tocar no banco.
- **SC-002**: A consulta de elegibilidade nunca devolve mais de uma pesquisa, em 100% das
  execuções, inclusive quando duas pesquisas ativas disputam o mesmo evento.
- **SC-003**: Um evento que nenhuma pesquisa escuta não cria nenhum registro, verificado por
  contagem de linhas antes e depois em 100% das execuções.
- **SC-004**: Conteúdo de versão em rascunho não aparece em nenhuma resposta da superfície
  pública, verificado por tentativa explícita em cada operação pública.
- **SC-005**: Consultas repetidas para o mesmo respondente e a mesma pesquisa produzem sempre a
  mesma decisão de amostragem, em 100% das repetições.
- **SC-006**: O mesmo pacote de abertura e envio repetido produz exatamente uma sessão e uma
  resposta por pergunta, em 100% das repetições.
- **SC-007**: 100% das recusas de envio identificam a pergunta e o motivo de cada problema —
  nenhuma recusa genérica do tipo "respostas inválidas".
- **SC-008**: Um envio recusado deixa zero respostas gravadas, verificado por leitura do banco
  em cada categoria de recusa.
- **SC-009**: Um respondente que concluiu ou dispensou uma pesquisa não a recebe de novo
  enquanto a versão publicada permanecer no mesmo grupo de comparabilidade, em 100% das
  consultas seguintes; um que abandonou volta a recebê-la até o limite de tentativas; e a
  publicação de uma versão semântica volta a torná-la elegível, ao contrário de uma cosmética.
- **SC-009b**: Uma pesquisa cujas regras de segmentação o respondente não satisfaz nunca é
  entregue a ele, verificado nas quatro operações — igual, diferente, presente e ausente — e
  com o atributo ausente da consulta.
- **SC-010**: Quem pesquisa consegue, a partir do que esta feature guarda, distinguir "ninguém
  viu" de "viram e não responderam" — as duas situações produzem registros diferentes e
  distinguíveis, sem inferência.
- **SC-011**: Uma sessão dispensada na segunda pergunta preserva a resposta da primeira, em
  100% das execuções.
- **SC-012**: Uma pesquisa de outra aplicação nunca é alcançada, nem tem sua existência
  revelada, em 100% das operações públicas — verificado com chave de aplicação diferente em
  cada operação.
- **SC-013**: Nenhuma referência de respondente, identificador de dispositivo, atributo ou
  conteúdo de resposta aparece em log, verificado por inspeção da saída durante a suíte de
  testes.
- **SC-014**: Uma resposta enviada depois de a pesquisa ser pausada, encerrada ou republicada
  continua sendo aceita e continua apontando a versão sob a qual a pesquisa foi exibida.

## Assumptions

- **A identidade do respondente é forjável, e isso é risco aceito.** Quem extrair a chave do
  bundle do app consegue se passar por outro respondente. A defesa prevista — o limite de
  requisições por chave e por origem — fica para a feature de operação da borda, então esta
  entrega roda sem ela. É tolerável enquanto o Pitaco não estiver publicado, e é a primeira
  coisa a existir antes da primeira pesquisa real.
- **A segmentação por atributo foi antecipada do controle de exposição.** As regras já são
  parte congelada da versão publicada desde a autoria; escrevê-las e não avaliá-las seria pior
  do que não tê-las. O que fica para a feature 06 é o resto do bloco: descanso, cota,
  prioridade e o catálogo de atributos observados.
- **Os atributos do respondente não viram perfil.** Chegam a cada consulta e são congelados no
  instantâneo da sessão. Nenhuma tabela acumula atributo por respondente ao longo do tempo —
  persistir perfil traria retenção de dado pessoal sem nenhuma pergunta desta feature precisar
  dele.
- **A pesquisa vai inteira em uma resposta só.** É o que permite ao SDK renderizar sem segunda
  chamada e sobreviver à queda de rede logo depois da consulta.
- **O SDK não decide elegibilidade.** Nem sorteio, nem histórico, nem escolha entre pesquisas.
  Cliente decidindo sorteio é cliente que eventualmente mente. Esta feature é a metade servidora
  desse contrato.
- **O estado abandonado é observado na leitura**, derivado do instante de abertura e do instante
  da consulta, sem rotina agendada — o mesmo tratamento que a autoria deu à transição de
  agendada para ativa.
- **A validação da resposta usa a versão da sessão, não a publicada corrente.** É o que torna a
  resposta interpretável anos depois: a pergunta que foi feita está congelada junto.
- **A resposta é guardada crua e imutável.** Não há edição nem exclusão nesta feature; retenção
  e expurgo são assunto próprio.
- **Não há autenticação de usuário na superfície administrativa**, mantendo o que a autoria já
  assumiu. Esta feature autentica apenas a superfície pública, e por chave de aplicação, não por
  usuário.
- **A documentação do Obsidian está atrás do repositório em três pontos**, e onde diverge, o
  repositório vence: o modelo de dados descreve Postgres com Drizzle e schema em TypeScript,
  superado pelo ADR-0001 deste repositório; o disparo e as regras de segmentação penduram na
  pesquisa naquele desenho e na **versão** neste, o que os congela junto com as perguntas; e a
  classificação de mudança, marcada lá como "nulo na v1", já existe aqui com grupo de
  comparabilidade.
- **O SDK, seu renderizador e sua fila local ficam fora deste repositório.** O que esta feature
  garante é o lado servidor do contrato: idempotência por identificador de dispositivo, sessão
  aberta em ato explícito, e desfecho registrado com o mesmo peso da resposta.
- **O limite de tentativas depois do abandono é um número de produto ainda não fixado.** O
  mecanismo entra aqui; o valor é configurável e tem um padrão conservador.
- **A reabertura por mudança semântica é a leitura do grupo de comparabilidade que a autoria
  já calcula.** Ela diverge da nota da vault, que diz apenas "quem respondeu não recebe de
  novo": aquela nota foi escrita antes de a classificação de mudança existir. Repesquisar sem
  precisar criar uma pesquisa nova é o ganho, e a autoria já recusa a cosmética que altera a
  estrutura das perguntas, então o gatilho não é autodeclaração desamparada.

## Dependencies

- **Autoria de pesquisa (`003-survey-authoring`), já entregue**: esta feature lê a versão
  publicada, o disparo, a proporção de amostragem, a chave estável da pergunta, o catálogo de
  tipos e o estado derivado da pesquisa. Nenhum deles é criado aqui.
- **Aplicações e chaves de acesso (`001`, `002`), já entregues**: a chave resolve a aplicação e
  autentica a porta pública. Esta feature adiciona a verificação da chave, que ainda não existe
  no repositório, e o registro do último uso.
- **Controle de exposição, ainda inexistente**: consumirá a sessão e a resposta produzidas aqui
  para decidir descanso, cota e prioridade, e acrescentará o catálogo de atributos observados
  sobre a segmentação que já é avaliada aqui. A dependência aponta dela para cá.
- **Operação da borda, ainda inexistente**: trará o limite de requisições por chave e por
  origem, que esta feature deliberadamente não implementa.
- **Resultados e exportação, ainda inexistente**: consumirá as sessões e respostas coletadas
  aqui. A taxa de resposta sai de graça da separação entre sessão e resposta.
- **Saúde e compatibilidade, ainda inexistente**: consumirá a versão do SDK registrada na
  sessão, e acrescentará o registro de supressão por incompatibilidade — que **não** é sessão,
  justamente para não estragar a taxa de resposta.
