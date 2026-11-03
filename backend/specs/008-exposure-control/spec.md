# Feature Specification: Controle de exposição — prioridade, cota, descanso e catálogo observado

**Feature Branch**: `008-exposure-control`

**Created**: 2026-09-12

**Status**: Implemented

**Input**: User description: "Agora a gente precisa controlar quanto cada pesquisa aparece: prioridade quando duas disputam o mesmo evento, cota de respostas que encerra sozinha, respeitar o descanso da aplicação (com opção de furar), registrar quem encerrou e por quê, e mostrar pra autoria os eventos e atributos que o app já mandou."

## Contexto

A coleta (`004`) já decide se há pesquisa para um respondente: janela, evento, segmentação,
amostragem, histórico. Faltava decidir **quanto** cada pesquisa pode disputar da atenção dele.
Com mais de uma pesquisa no ar, três perguntas ficavam sem resposta:

- Duas pesquisas escutam o mesmo evento. Qual vence? Hoje vence a mais antiga, e o autor não tem
  como dizer "esta é mais importante".
- O estudo precisa de cem respostas, não de dez mil. Quem encerra a pesquisa quando chega lá?
- Quem acabou de responder uma pesquisa recebe outra no minuto seguinte. A aplicação já guarda um
  intervalo de descanso (`007`), mas ninguém o aplica.

Além disso, a autoria monta disparo e segmentação digitando nomes de memória. O Pitaco já vê os
eventos (`007`) e os atributos que o app envia; faltava devolvê-los à autoria.

Esta fatia entrega:

- **Exposição da pesquisa**: prioridade de −100 a 100, cota de respostas concluídas e isenção do
  descanso — atributos da pesquisa, não da versão, editáveis com a pesquisa no ar.
- **Elegibilidade com exposição**: descanso aplicado entre pesquisas da mesma aplicação, e
  desempate por prioridade, depois pela publicação mais antiga, depois pelo identificador.
- **Encerramento por cota**: a conclusão que atinge a cota encerra a pesquisa na mesma transação,
  uma vez só, com motivo próprio no histórico; a cota reduzida até o já coletado encerra na hora.
- **Progresso da cota** e **histórico de transições** com o motivo da cota.
- **Catálogo observado**: leitura dos eventos e dos atributos (com valores) que o app já enviou.

## Clarifications

### Session 2026-09-12

- Q: Exposição muda o instrumento de medida? → A: Não. Prioridade, cota e isenção são da pesquisa,
  não da versão: mudam sem abrir rascunho e sem afetar a comparabilidade.
- Q: O descanso conta a partir de quê? → A: Da última exibição de qualquer pesquisa da aplicação
  para aquele respondente — respondida, dispensada ou abandonada, porque em todas ela apareceu. O
  instante exato em que o intervalo se completa já libera.
- Q: A cota conta respostas de qual versão? → A: Concluídas de todas as versões da pesquisa.
- Q: Quem já estava com a pesquisa aberta quando a cota foi atingida pode concluir? → A: Sim.
  A conclusão é aceita normalmente; o total pode passar um pouco da cota.
- Q: Reduzir a cota abaixo do já coletado encerra? → A: Sim, na hora, se a pesquisa já foi
  publicada e não está encerrada. Rascunho com cota alcançável não encerra.
- Q: Pesquisa pausada atinge a cota? → A: Sim. Uma sessão aberta antes da pausa pode concluir e
  atingir a cota; a pesquisa vai para encerrada.
- Q: O histórico registra quem fez a transição? → A: O campo de autor existe e fica vazio até haver
  autenticação do painel; o motivo distingue manual de automático.
- Q: O catálogo de atributos guarda valores ilimitados? → A: Não. No máximo 200 valores distintos
  por atributo; o valor já conhecido continua avançando, o novo deixa de entrar.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Desempatar pesquisas pelo que importa (Priority: P1)

Duas pesquisas publicadas escutam `checkout.completed`. O autor quer que a de NPS pós-compra vença
a de pesquisa de catálogo, mesmo publicada depois. Ele define prioridades; o respondente recebe a
de maior prioridade, e a mesma consulta repetida devolve sempre a mesma.

**Why this priority**: sem prioridade, a ordem de publicação decide sozinha o que o usuário vê —
uma regra que o autor não controla e que muda quando alguém republica.

**Independent Test**: publicar duas pesquisas no mesmo evento, dar prioridade maior à mais nova,
consultar a elegibilidade várias vezes e conferir que vem sempre a mais nova.

**Acceptance Scenarios**:

1. **Given** duas pesquisas no mesmo evento com prioridades 0 e 10, **When** o SDK consulta,
   **Then** recebe a de prioridade 10, mesmo publicada depois.
2. **Given** duas pesquisas com a mesma prioridade, **When** o SDK consulta, **Then** recebe a que
   foi publicada há mais tempo; com o mesmo instante, decide o identificador.
3. **Given** a mesma consulta repetida, **When** nada mudou, **Then** a pesquisa escolhida é sempre
   a mesma.
4. **Given** uma pesquisa nova, **When** a consulto, **Then** ela tem prioridade 0, sem cota e
   respeitando o descanso.
5. **Given** prioridade fora de −100..100, **When** edito, **Then** recebo recusa por validação e
   nada muda.

---

### User Story 2 - Encerrar ao atingir a cota (Priority: P1)

O estudo precisa de cem respostas. O autor define a cota; a pesquisa sai do ar sozinha na
centésima conclusão, e o histórico mostra que ela foi encerrada pela cota.

**Why this priority**: cota é como se controla custo de atenção do usuário e tamanho de amostra.
Sem encerramento automático, alguém precisa vigiar o painel.

**Independent Test**: definir cota 2, concluir duas exibições e conferir que a pesquisa ficou
encerrada, com uma transição de motivo de cota, e que uma sessão aberta antes ainda conclui.

**Acceptance Scenarios**:

1. **Given** cota 2 e uma conclusão, **When** chega a segunda conclusão, **Then** a pesquisa é
   encerrada na mesma transação, e o histórico ganha uma transição com motivo de cota.
2. **Given** uma exibição aberta antes do encerramento, **When** ela conclui depois, **Then** a
   conclusão é aceita normalmente.
3. **Given** duas conclusões simultâneas que atingem a cota, **When** gravadas, **Then** a pesquisa
   é encerrada uma vez só, com uma única transição de cota.
4. **Given** uma pausa manual e uma conclusão que atinge a cota ao mesmo tempo, **When** as duas
   terminam, **Then** a pesquisa fica sempre encerrada.
5. **Given** uma pesquisa no ar com 5 concluídas, **When** reduzo a cota para 5, **Then** ela é
   encerrada na hora, com motivo de cota.
6. **Given** uma cota acima do total concluído, **When** a defino, **Then** ela só é gravada e a
   pesquisa segue no ar.
7. **Given** um rascunho, **When** defino cota já alcançável, **Then** nada encerra: ainda não foi
   publicado.
8. **Given** a janela já fechada, **When** a cota seria atingida, **Then** nada é registrado: a
   janela encerrou antes.
9. **Given** uma cota definida, **When** a envio como nula, **Then** ela é removida e os demais
   campos ficam como estavam.

---

### User Story 3 - Respeitar o descanso da aplicação (Priority: P2)

A aplicação tem descanso de 15 dias. Quem viu uma pesquisa não recebe outra da mesma aplicação
antes disso — a não ser a que o autor marcou como isenta (por exemplo, uma pesquisa de erro
crítico).

**Why this priority**: protege o usuário final do excesso de pesquisas, que é a maior causa de
rejeição. Vem depois de prioridade e cota porque só tem efeito com mais de uma pesquisa no ar.

**Independent Test**: com descanso configurado, exibir a pesquisa A para um respondente; consultar
um evento da pesquisa B e receber silêncio; marcar B como isenta e recebê-la.

**Acceptance Scenarios**:

1. **Given** descanso de 15 dias e uma exibição há 3 dias, **When** o respondente dispara o evento
   de outra pesquisa, **Then** recebe silêncio.
2. **Given** a mesma situação e a outra pesquisa isenta do descanso, **When** ele dispara o
   evento, **Then** recebe a pesquisa isenta.
3. **Given** o instante exato em que o intervalo se completa, **When** ele consulta, **Then** já é
   elegível.
4. **Given** uma aplicação sem descanso configurado, **When** ele consulta, **Then** o descanso
   não barra ninguém.
5. **Given** um respondente que nunca viu pesquisa, **When** consulta, **Then** o descanso não se
   aplica.

---

### User Story 4 - Acompanhar a cota e o histórico (Priority: P2)

No cabeçalho da pesquisa, o painel mostra "42 de 100" e o histórico de transições com o motivo de
cada uma.

**Why this priority**: sem progresso, a cota é invisível até encerrar.

**Independent Test**: consultar o progresso de uma pesquisa com cota e duas concluídas e de uma
sem cota.

**Acceptance Scenarios**:

1. **Given** uma pesquisa com cota 100 e 42 concluídas, **When** consulto o progresso, **Then**
   recebo cota 100 e 42 concluídas, contando todas as versões.
2. **Given** uma pesquisa sem cota, **When** consulto, **Then** a cota vem ausente e a contagem
   continua valendo.
3. **Given** uma pesquisa encerrada pela cota, **When** consulto o histórico, **Then** a transição
   aparece com o motivo de cota, distinto do encerramento manual.
4. **Given** uma pesquisa de outra aplicação, **When** consulto o progresso, **Then** recebo
   pesquisa não encontrada.

---

### User Story 5 - Escolher evento e atributo entre os já vistos (Priority: P3)

Ao configurar disparo e segmentação, a autoria lista os eventos e os atributos que o app já enviou,
com os valores já vistos de cada atributo.

**Why this priority**: evita a pesquisa que nunca dispara por erro de digitação, mas a autoria
funciona sem ele.

**Independent Test**: consultar a elegibilidade com atributos `plano=pro` e `pais=BR`, listar os
atributos da aplicação e conferir nomes e valores.

**Acceptance Scenarios**:

1. **Given** consultas com eventos diferentes, **When** listo os eventos, **Then** recebo nome e
   instantes, do visto mais recentemente para o mais antigo, com desempate pelo nome.
2. **Given** consultas com atributos, **When** listo os atributos, **Then** recebo cada nome com
   seus valores em ordem alfabética.
3. **Given** um atributo com 200 valores distintos, **When** chega um valor novo, **Then** ele não
   entra; um valor já conhecido continua avançando.
4. **Given** uma aplicação inativa, **When** listo, **Then** o catálogo continua legível.
5. **Given** uma aplicação sem consultas, **When** listo, **Then** recebo página vazia com total 0.
6. **Given** uma falha ao gravar atributos, **When** a consulta acontece, **Then** a entrega da
   pesquisa não é afetada.

---

### Edge Cases

- **Corpo vazio na edição de exposição**: aceito, devolve a pesquisa como está.
- **Editar outro campo** (nome, aviso de texto livre) não reconfere a cota.
- **Pesquisa já encerrada atingindo a cota de novo**: nada é registrado; o encerramento é uma vez.
- **Encerramento por cota de pesquisa de outra aplicação ou inexistente**: ignorado em silêncio —
  quem chega ali é um respondente, não um operador.
- **Todos os candidatos isentos**: o descanso nem é consultado.
- **Atributo de alta cardinalidade** (identificador por usuário enviado por engano): o catálogo para
  de crescer no limite, e o painel não vira log.
- **Rajada do mesmo evento ou atributo**: a escrita é amortizada em alguns minutos.

## Requirements *(mandatory)*

### Functional Requirements

**Exposição**

- **FR-001**: Toda pesquisa MUST ter prioridade inteira entre −100 e 100, padrão 0.
- **FR-002**: Toda pesquisa MAY ter cota de respostas concluídas, inteira ≥ 1; sem cota, não há
  encerramento automático.
- **FR-003**: Toda pesquisa MUST ter a indicação de isenção do descanso, padrão falso.
- **FR-004**: Prioridade, cota e isenção MUST ser editáveis parcialmente, a qualquer momento, sem
  abrir versão nova; a cota enviada como nula MUST ser removida.
- **FR-005**: Prioridade fora da faixa e cota abaixo de 1 MUST ser recusadas por validação, sem
  mudar nada.

**Elegibilidade**

- **FR-006**: Entre as candidatas que passam em todas as camadas, o sistema MUST escolher a de
  maior prioridade; no empate, a publicada há mais tempo; persistindo o empate, a de menor
  identificador.
- **FR-007**: A escolha MUST ser determinística: a mesma consulta, sem mudança de estado, devolve a
  mesma pesquisa.
- **FR-008**: Com descanso configurado, o sistema MUST NOT entregar pesquisa não isenta a quem teve
  exibição de qualquer pesquisa da aplicação dentro do intervalo.
- **FR-009**: O intervalo MUST contar da última exibição aberta do respondente, qualquer que seja o
  desfecho, e o instante exato em que se completa MUST liberar.
- **FR-010**: Pesquisa isenta MUST ignorar o descanso.
- **FR-011**: O descanso MUST NOT ser consultado quando o respondente é desconhecido, quando todas
  as candidatas são isentas ou quando a aplicação não tem intervalo.

**Cota**

- **FR-012**: A conclusão que leva o total de concluídas (todas as versões) à cota MUST encerrar a
  pesquisa na mesma transação que grava a conclusão.
- **FR-013**: Conclusões simultâneas MUST ser serializadas na contagem, e o encerramento MUST
  acontecer uma única vez.
- **FR-014**: O encerramento por cota MUST registrar uma transição com motivo de cota, distinto do
  encerramento manual.
- **FR-015**: Pesquisa no ar ou pausada MUST ser encerrada pela cota; rascunho ou já encerrada
  MUST ficar como está, sem erro.
- **FR-016**: Se a janela já fechou, o encerramento por cota MUST NOT registrar nova transição.
- **FR-017**: Reduzir a cota de pesquisa publicada e não encerrada até o total já concluído MUST
  encerrá-la imediatamente, com motivo de cota.
- **FR-018**: Exibição aberta antes do encerramento MUST poder concluir normalmente.

**Progresso e histórico**

- **FR-019**: O sistema MUST permitir consultar o progresso da cota: a cota (ausente quando não há)
  e o total de concluídas em todas as versões.
- **FR-020**: O histórico de transições MUST apresentar o motivo de cada transição, incluindo o de
  cota, com instante; o autor MUST ser apresentado como ausente enquanto não houver autenticação.

**Catálogo observado**

- **FR-021**: Toda consulta de elegibilidade de aplicação ativa com atributos MUST registrar cada
  nome de atributo e cada valor no catálogo da aplicação, sem ligação com o respondente.
- **FR-022**: O catálogo MUST guardar no máximo 200 valores distintos por atributo; valor novo
  acima do limite MUST NOT entrar, e valor conhecido MUST continuar avançando.
- **FR-023**: Falha ao gravar o catálogo de atributos MUST NOT afetar a entrega, nem desfazer o
  catálogo de eventos.
- **FR-024**: O sistema MUST permitir listar os eventos observados de uma aplicação, paginados, do
  visto mais recentemente para o mais antigo, com desempate pelo nome.
- **FR-025**: O sistema MUST permitir listar os atributos observados de uma aplicação, paginados,
  na mesma ordem, cada um com seus valores em ordem alfabética.
- **FR-026**: As listagens do catálogo MUST funcionar com a aplicação inativa.

**Transversais**

- **FR-027**: Pesquisa e aplicação de outro escopo MUST recusar como inexistentes, com os códigos
  estáveis `survey.not_found` e `application.not_found`.
- **FR-028**: As leituras e a edição desta fatia MUST pertencer à superfície administrativa.
- **FR-029**: Nenhuma leitura desta fatia MUST alterar estado.

### Key Entities

- **Exposição**: prioridade, cota opcional e isenção do descanso de uma pesquisa.
- **Transição de estado**: origem, destino, motivo (publicação, pausa, retomada, encerramento
  manual, cota; abertura e fechamento de janela derivados), autor reservado e instante.
- **Atributo observado**: nome de um atributo que o app já enviou, com primeira e última
  ocorrência e até 200 valores, cada um com a última ocorrência.
- **Evento observado**: definido na `007`; aqui ganha leitura.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com duas pesquisas no mesmo evento, 100% das consultas repetidas devolvem a mesma
  pesquisa, e a de maior prioridade.
- **SC-002**: Uma pesquisa com cota N nunca recebe uma (N+1)-ésima exibição nova depois da
  conclusão que a atinge; o excesso possível é só de sessões já abertas.
- **SC-003**: Conclusões simultâneas que atingem a cota produzem exatamente uma transição de
  encerramento por cota.
- **SC-004**: Nenhum respondente recebe duas pesquisas não isentas da mesma aplicação dentro do
  intervalo de descanso.
- **SC-005**: Um atributo nunca acumula mais de 200 valores no catálogo, qualquer que seja o
  tráfego.
- **SC-006**: A indisponibilidade dos catálogos não muda nenhuma resposta da elegibilidade.

## Assumptions

- O autor das transições fica vazio até a autenticação do painel existir.
- O total de concluídas é lido do banco a cada conclusão de pesquisa com cota; pesquisa sem cota
  não paga essa conta.
- O catálogo de atributos não é perfil: não guarda quem enviou o valor.
- A prioridade só desempata candidatas que já passaram por janela, segmentação, amostragem,
  histórico e descanso; ela não fura nenhuma dessas regras.

## Dependencies

- `backend 003-survey-authoring`: estados, transições e histórico; a cota acrescenta um motivo.
- `backend 004-response-collection`: camadas da elegibilidade, conclusão de exibição e histórico do
  respondente.
- `backend 007-application-management`: intervalo de descanso da aplicação e catálogo de eventos.
- `backend 009-advanced-authoring`: usa o catálogo de atributos e a concorrência entre pesquisas
  nos avisos de publicação.
