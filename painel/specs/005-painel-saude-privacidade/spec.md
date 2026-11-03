# Feature Specification: Painel de saúde do SDK e privacidade — versões, erros, supressões, retenção e exclusão de respondente

**Feature Branch**: `005-painel-saude-privacidade`

**Created**: 2026-09-12

**Status**: Implemented

**Input**: User description: "falta enxergar o outro lado e cuidar do dado. quero ver quais versões do sdk estão falando com a aplicação, os erros que ele manda, quando uma pesquisa não aparece porque o sdk não sabe desenhar, e na privacidade a política de retenção com o que vai sumir e a exclusão de respondente com registro."

## Contexto

Até aqui o painel enxerga o que o respondente faz — exibições, respostas, resultados. Não
enxerga o que acontece no app: que versão do SDK está em uso, que erros ela reporta, e quando
uma pesquisa publicada simplesmente não aparece porque o SDK daquele app não sabe desenhar um
tipo de pergunta. Para quem pesquisa, esses três casos têm o mesmo sintoma — "ninguém respondeu"
— e pedem correções opostas.

E até aqui o painel não oferece nada para cumprir as obrigações de quem guarda resposta de
gente: saber o que a política de retenção vai apagar e quando, e atender a um pedido de exclusão
de um titular com um registro que prove que foi feito — sem guardar quem.

O backend passou a oferecer as duas coisas: versões do SDK por aplicação, erros reportados pelo
canal próprio do SDK, saúde de cada pesquisa (supressões e se o evento do disparo já chegou),
prévia da retenção, exclusão de respondente por referência ou dispositivo e o registro das
exclusões. Esta feature leva isso ao painel em duas telas da aplicação — **Saúde** e
**Privacidade** — e em avisos nos resultados.

## Clarifications

### Session 2026-09-12

- Q: Quando uma versão do SDK deixa de contar como "em uso"? → A: Quando não consulta a
  elegibilidade há mais de 14 dias — o backend a marca, e o painel diz "Sumiu do tráfego". A
  proporção mostrada é a das consultas recentes; o total conta desde a primeira aparição.
- Q: Supressão e "nenhuma exibição" podem aparecer juntos? → A: Não. Quando a pesquisa está
  sendo suprimida, o vazio "Nenhuma exibição ainda" não aparece: quem não viu não teve como
  responder, e o aviso de supressão já diz a causa.
- Q: A exclusão de respondente guarda quem foi excluído? → A: Nunca. Fica registrado quando
  aconteceu e quanto saiu (exibições e respostas). O pedido para quem não existe não cria registro.
- Q: Onde se edita a política de retenção? → A: Nos prazos da aplicação (retenção de respostas e
  de texto livre), pelo diálogo de edição da aplicação. A tela de Privacidade lê a política, avisa
  o que vai sumir e leva até a edição.
- Q: Os resultados continuam somando o que a retenção apagou? → A: Sim, pelo agregado congelado
  antes do descarte — mas só quando o recorte não exige data nem atributo, que o agregado não tem.
  A tela diz qual dos dois casos vale.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver as versões do SDK em uso (Priority: P1)

Na aba Saúde da aplicação, quem opera vê cada versão do SDK que já consultou a elegibilidade: a
fatia dela no tráfego recente, as consultas recentes e totais, e quando foi vista pela última vez.
Versões que não aparecem há mais de 14 dias são marcadas "Sumiu do tráfego".

**Why this priority**: é o que responde "posso usar o tipo de pergunta novo?" e "quantos usuários
ainda estão na versão com bug?". Sem isso, a decisão de publicar é cega quanto ao lado do app.

**Independent Test**: com três versões semeadas (uma recente, uma parcialmente recente e uma de
40 dias atrás), conferir a ordem, a fatia de 75% da mais nova e a marca na antiga.

**Acceptance Scenarios**:

1. **Given** versões do SDK registradas, **When** abro Saúde, **Then** vejo cada versão com fatia
   recente, consultas recentes, consultas totais e última vez vista, na ordem em que o backend as
   devolve (a mais nova primeiro).
2. **Given** uma versão sem consulta há mais de 14 dias, **When** a vejo, **Then** ela tem a marca
   "Sumiu do tráfego"; as demais não.
3. **Given** nenhuma consulta recente de uma versão, **When** vejo a fatia, **Then** ela aparece
   como ausente ("—"), nunca 0%.
4. **Given** uma aplicação que nenhum SDK consultou, **When** abro Saúde, **Then** vejo um vazio
   explicando que as versões aparecem quando o app consultar a elegibilidade.

---

### User Story 2 - Investigar os erros do SDK (Priority: P1)

Na mesma aba, a lista de erros reportados pelo SDK: tipo (renderização, rede, resposta
malformada, armazenamento, desconhecido), versão, momento, mensagem e, recolhido, o contexto. A
lista é filtrável por tipo e por versão, com o filtro na URL, e paginada. Um texto lembra que o
relatório não carrega dado do usuário.

**Why this priority**: uma pesquisa que quebra no app é invisível para quem pesquisa até que
alguém reclame. Os erros mostram isso antes.

**Independent Test**: com dois erros semeados de tipos diferentes, filtrar por Renderização,
conferir `tipo=render_error` na URL, um item só, e o contexto ao abrir.

**Acceptance Scenarios**:

1. **Given** erros reportados, **When** abro Saúde, **Then** vejo cada erro com tipo, versão,
   momento e mensagem.
2. **Given** a lista, **When** filtro por tipo ou por versão, **Then** só os erros correspondentes
   aparecem, a URL guarda o filtro e a página volta à primeira.
3. **Given** um erro com contexto, **When** o abro, **Then** vejo cada chave e valor do contexto.
4. **Given** um erro sem versão ou sem mensagem, **When** o vejo, **Then** leio "versão não
   informada" ou "Sem mensagem.", nunca um campo vazio.
5. **Given** um tipo de erro novo vindo do backend, **When** a lista é lida, **Then** ele aparece
   como Desconhecido em vez de quebrar a tela.
6. **Given** nenhum erro, **When** abro a lista, **Then** ela diz "Nenhum erro reportado" — com
   filtro, "neste recorte".
7. **Given** filtros aplicados, **When** limpo, **Then** eles saem da URL.

---

### User Story 3 - Saber por que uma pesquisa não está sendo respondida (Priority: P2)

Nos resultados de uma pesquisa, dois avisos distinguem as causas de zero respostas que não são
desinteresse: a pesquisa está sendo **suprimida** (o SDK do app não conhece um tipo de pergunta
ou não suporta um recurso usado nela), com a proporção das vezes em que seria exibida, o motivo
dominante e a versão mínima que a desenharia; ou o **evento do disparo nunca chegou** da
aplicação.

**Why this priority**: sem esses avisos, as duas situações aparecem como "Nenhuma exibição ainda",
e quem pesquisa espera em vez de agir.

**Independent Test**: com supressões semeadas, abrir os resultados e conferir o aviso com motivo e
versão mínima e a ausência do vazio; numa pesquisa cujo evento nunca chegou, conferir o aviso e
que ele some quando o evento é registrado.

**Acceptance Scenarios**:

1. **Given** supressões acima do limiar do backend, **When** abro os resultados, **Then** vejo "A
   pesquisa está sendo suprimida" com a proporção, o motivo dominante e a versão mínima, e a frase
   de que não é falta de interesse.
2. **Given** supressões abaixo do limiar, **When** abro os resultados, **Then** nenhum aviso de
   supressão aparece.
3. **Given** a pesquisa suprimida sem exibição, **When** abro os resultados, **Then** o vazio
   "Nenhuma exibição ainda" não aparece.
4. **Given** o evento do disparo nunca recebido, **When** abro os resultados, **Then** vejo "O
   evento do disparo nunca chegou" nomeando o evento; **When** o evento chega, **Then** o aviso
   some.
5. **Given** a leitura de saúde indisponível, **When** abro os resultados, **Then** a tela de
   resultados continua de pé, sem os avisos.

---

### User Story 4 - Conhecer e ajustar a política de retenção (Priority: P1)

Na aba Privacidade da aplicação, a política de retenção em linguagem de gente — em quantos dias as
respostas são apagadas, em quantos o texto livre some, e que o agregado continua nos resultados.
Quando algo vai ser descartado, um aviso diz quanto e quando (na próxima execução, ou até uma
semana depois dela), com o caminho para exportar antes. Também diz se já houve descarte. Os
prazos são editados no diálogo de edição da aplicação, junto do nome e do intervalo de descanso:
campo em branco remove o prazo.

**Why this priority**: retenção apaga dado sem volta. Quem opera precisa ver o que vai sumir antes
de sumir, com tempo de exportar.

**Independent Test**: com retenção de 30 dias e uma resposta de 45 dias atrás, abrir Privacidade
e conferir "30 dias", "1 resposta e 1 texto livre serão descartados" e "Nenhum descarte".

**Acceptance Scenarios**:

1. **Given** uma aplicação sem prazo, **When** abro Privacidade, **Then** leio que nada é
   descartado automaticamente, sem aviso nem convite a exportar.
2. **Given** prazos configurados, **When** abro Privacidade, **Then** leio os dois prazos (o de
   texto livre só quando difere do geral) e que o agregado continua nos resultados.
3. **Given** respostas que vencem na próxima execução, **When** abro Privacidade, **Then** o aviso
   diz a data e quantas respostas e textos livres serão descartados, convidando a exportar.
4. **Given** nada na próxima execução mas algo na semana seguinte, **When** abro Privacidade,
   **Then** o aviso diz até quando e quanto terá sido descartado.
5. **Given** o descarte automático desligado na instância, **When** abro Privacidade, **Then** leio
   isso em vez de uma data prevista.
6. **Given** a política, **When** sigo "Editar prazos", **Then** chego à aplicação, onde o diálogo
   de edição muda nome, descanso, retenção e retenção de texto livre; prazo em branco é removido.
7. **Given** retenção de texto livre maior que a geral, **When** salvo, **Then** a recusa do
   backend aparece sem fechar o diálogo, preservando o que foi digitado.

---

### User Story 5 - Atender a um pedido de exclusão (Priority: P1)

Na aba Privacidade, quem opera informa como o respondente é identificado (referência do app ou
dispositivo) e a identificação. A exclusão pede confirmação explícita, dizendo que é irreversível
e que fica registrado só que houve exclusão, quando e quanto saiu. Confirmada, a tela diz quanto
saiu — ou que não havia ninguém com aquela identificação. A lista de exclusões feitas mostra
quando e quanto, nunca quem.

**Why this priority**: é obrigação legal de quem guarda resposta de gente. Sem a tela, o pedido
depende de alguém com acesso ao banco.

**Independent Test**: com um respondente semeado, pedir a exclusão e cancelar (ele continua),
pedir e confirmar (some da lista de respondentes, aparece um registro sem a identificação);
pedir a exclusão de alguém inexistente e conferir "Nada foi apagado" sem registro.

**Acceptance Scenarios**:

1. **Given** o formulário vazio, **When** o vejo, **Then** a ação de excluir está desabilitada.
2. **Given** uma identificação, **When** peço a exclusão, **Then** um diálogo diz que é
   irreversível e o que fica registrado.
3. **Given** o diálogo, **When** cancelo, **Then** nada é apagado e nenhum resultado aparece.
4. **Given** o diálogo, **When** confirmo, **Then** a tela diz "Excluído: N exibições e M
   respostas apagadas", o registro aparece na lista e o respondente some da listagem.
5. **Given** uma identificação que não existe, **When** confirmo, **Then** a tela diz que nada foi
   apagado e nenhum registro é criado.
6. **Given** a lista de exclusões, **When** a leio, **Then** vejo quando e quantas exibições e
   respostas saíram, e em lugar nenhum a identificação.
7. **Given** nenhuma exclusão, **When** abro a lista, **Then** ela diz que nenhuma foi feita.

---

### User Story 6 - Ler resultados sabendo o que a retenção apagou (Priority: P2)

Quando a retenção já descartou respostas de uma pesquisa, os resultados dizem se os números
incluem o agregado guardado antes do descarte — ou, quando o recorte exige data ou atributo que o
agregado não tem, que as respostas anteriores ficaram de fora.

**Why this priority**: sem a nota, a queda nos números depois de um descarte parece queda de
resposta.

**Independent Test**: com agregado congelado semeado, abrir os resultados sem recorte e conferir
"Inclui agregados"; com `?periodo=7`, conferir "não entram neste recorte".

**Acceptance Scenarios**:

1. **Given** agregado congelado aplicado, **When** abro os resultados, **Then** a nota diz que os
   números incluem agregados de respostas descartadas antes de uma data, e que elas não aparecem
   nas respostas abertas nem no export.
2. **Given** um recorte por período ou atributo, **When** abro os resultados, **Then** a nota diz
   que as respostas descartadas antes da data não entram no recorte.
3. **Given** uma pesquisa sem descarte, **When** abro os resultados, **Then** não há nota.

---

### Edge Cases

- **Contexto de erro malformado**: vira contexto vazio; o erro continua listado.
- **Filtro de versão longo**: cortado em 40 caracteres.
- **Tipo de erro inválido na URL**: ignorado.
- **Proporção recente ausente**: "—".
- **Saúde de pesquisa nunca publicada**: sem versão mínima nem evento, sem aviso.
- **Exclusão com identificação longa**: até 200 caracteres, aparada.
- **Duplo clique em confirmar exclusão**: a ação fica desabilitada enquanto está em curso.
- **Aplicação inexistente**: Saúde e Privacidade levam à tela de não encontrado.
- **Falha de leitura**: Saúde e Privacidade caem no estado de falha da aplicação, com nova
  tentativa.

## Requirements *(mandatory)*

### Functional Requirements

**Saúde do SDK**

- **FR-001**: A aplicação MUST ter uma tela Saúde com as versões do SDK em uso e os erros
  reportados.
- **FR-002**: Cada versão MUST mostrar fatia do tráfego recente, consultas recentes e totais e
  última vez vista; versões marcadas pelo backend como fora do tráfego MUST ter a marca "Sumiu do
  tráfego", com a explicação do critério de 14 dias.
- **FR-003**: Fatia ausente MUST aparecer como ausente, nunca como 0%.
- **FR-004**: A lista de erros MUST mostrar tipo em português, versão, momento, mensagem e contexto
  recolhido, com texto próprio para versão e mensagem ausentes.
- **FR-005**: A lista de erros MUST ser filtrável por tipo e por versão, com os filtros na URL
  (`tipo`, `versao`), volta à primeira página ao filtrar, e ação de limpar.
- **FR-006**: Tipo de erro desconhecido MUST aparecer como Desconhecido; valor inválido na URL MUST
  ser ignorado.
- **FR-007**: A tela MUST lembrar que os relatórios de erro não carregam dado do usuário.

**Saúde da pesquisa**

- **FR-008**: Os resultados MUST exibir aviso de supressão quando o backend a considerar relevante,
  com proporção, motivo dominante em linguagem de gente e versão mínima.
- **FR-009**: Os resultados MUST exibir aviso de evento nunca recebido quando a pesquisa tem
  evento de disparo e ele nunca chegou, nomeando o evento.
- **FR-010**: Com aviso de supressão, o vazio de "nenhuma exibição" MUST NOT aparecer.
- **FR-011**: Falha na leitura de saúde MUST NOT derrubar a tela de resultados.

**Retenção**

- **FR-012**: A aplicação MUST ter uma tela Privacidade que descreva a política de retenção em
  linguagem de gente, incluindo que o agregado continua nos resultados.
- **FR-013**: A tela MUST avisar o que será descartado na próxima execução, ou até uma semana
  depois dela, com data e quantidade de respostas e textos livres, e o caminho para exportar.
- **FR-014**: A tela MUST dizer quando o descarte automático está desligado e se já houve descarte.
- **FR-015**: A tela MUST levar à edição dos prazos, e o diálogo de edição da aplicação MUST
  permitir mudar nome, descanso, retenção e retenção de texto livre, com campo em branco removendo
  o prazo.
- **FR-016**: Recusa da edição MUST aparecer no diálogo, sem fechá-lo e sem perder o digitado.

**Exclusão de respondente**

- **FR-017**: A tela Privacidade MUST permitir excluir um respondente pela referência do app ou
  pelo dispositivo, com a identificação obrigatória.
- **FR-018**: A exclusão MUST exigir confirmação explícita que diga que é irreversível e o que fica
  registrado.
- **FR-019**: A tela MUST dizer quanto saiu, ou que nada foi apagado quando não havia respondente.
- **FR-020**: A tela MUST listar as exclusões feitas com quando e quanto, paginadas, e MUST NOT
  exibir a identificação de quem foi excluído em lugar nenhum.

**Nota de retenção nos resultados**

- **FR-021**: Os resultados MUST exibir a nota de retenção quando o backend a enviar, distinguindo
  "inclui o agregado" de "o recorte deixou o agregado de fora", com a data no fuso de referência.

### Key Entities

- **Uso de versão do SDK**: versão, consultas totais e recentes, fatia recente, primeira e última
  vez vista, se sumiu do tráfego.
- **Erro do SDK**: identificador, versão, tipo, mensagem, contexto, momento da ocorrência e do
  recebimento.
- **Saúde da pesquisa**: janela, exibições, supressões por motivo e por versão, proporção de
  supressão, relevância, versão mínima, evento do disparo e última vez visto.
- **Prévia de retenção**: se há política, prazos de resposta e de texto livre, próxima execução, o
  que sai nela e na semana seguinte, último descarte, primeiro descarte pendente.
- **Exclusão de respondente**: se apagou, quantas exibições e respostas.
- **Registro de exclusão**: quando, quantas exibições e respostas — nunca quem.
- **Nota de retenção**: se o agregado entrou, data do descarte, texto.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Quem opera identifica a versão do SDK que mais consulta a aplicação e as que saíram
  do tráfego em uma única tela, sem consultar o banco.
- **SC-002**: As duas causas de "zero respostas" que não são desinteresse (supressão, evento que
  nunca chegou) são distinguíveis uma da outra e do vazio comum, verificado ponta a ponta.
- **SC-003**: 100% das exclusões exigem confirmação, e nenhuma tela exibe a identificação de um
  respondente excluído — verificado ponta a ponta.
- **SC-004**: Antes de um descarte por retenção, a tela de Privacidade diz quanto e quando, com ao
  menos uma execução de antecedência.
- **SC-005**: Os fluxos de versões, erros filtrados, supressão, evento nunca visto, exclusão
  (cancelar e confirmar), exclusão sem alvo, prévia de retenção e nota de retenção estão cobertos
  por teste ponta a ponta.

## Assumptions

- Os limiares (14 dias para "sumiu do tráfego", relevância da supressão) e as previsões de
  retenção são do backend; o painel só exibe.
- O mascaramento de dado pessoal nos erros do SDK é feito no servidor do backend.
- A identificação usada na exclusão é a mesma que o app enviou ao SDK; o painel não a valida além
  da forma.
- O registro de exclusão pode carregar quem executou (`performedBy`) quando houver autenticação de
  operador; nesta entrega ele não é exibido.
- As convenções de 001 e 002 valem: confirmação em ação irreversível, fuso de referência rotulado,
  paginação compartilhada.

## Dependencies

- `001-painel-operacao-pesquisas`: aplicação, confirmação destrutiva, formulários.
- `002-painel-leitura-coleta`: respondentes (a exclusão some da listagem), semeadura de coleta.
- `003-painel-autoria-avancada`: o aviso de compatibilidade do SDK na publicação usa as mesmas
  versões em uso.
- `004-painel-resultados-comportamento`: tela de resultados, onde os avisos de saúde e a nota de
  retenção aparecem.
- API administrativa do backend: versões e erros do SDK, saúde da pesquisa (módulo `collect`),
  retenção, exclusão e registro (módulo de privacidade), e `backend 005-application-read-list`
  para os prazos da aplicação.
