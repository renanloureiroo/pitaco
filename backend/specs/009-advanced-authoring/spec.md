# Feature Specification: Autoria avançada — modelos, condição, duplicação e publicação consciente

**Feature Branch**: `009-advanced-authoring`

**Created**: 2026-09-12

**Status**: Implemented

**Input**: User description: "A montagem de pesquisa precisa ficar madura: começar de um modelo de NPS, CSAT ou CES, pergunta que só aparece dependendo da resposta anterior, duplicar pesquisa (até pra outra aplicação), renomear e descartar, aviso pra não escrever dado pessoal no texto livre, e na hora de publicar saber se a mudança é cosmética mesmo, o que impede e o que merece atenção."

## Contexto

A `003-survey-authoring` entregou o essencial da autoria: rascunho, perguntas, disparo,
segmentação, publicação em versões congeladas, classificação da mudança, grupos de
comparabilidade, impedimentos e ciclo de vida. Com pesquisas reais no ar, apareceram as lacunas que
separam uma ferramenta de laboratório de uma de uso diário:

- **Ninguém deveria redigir um NPS do zero.** O formato tem pergunta e escala consagradas; errar a
  escala invalida a comparação com o mercado.
- **Perguntas condicionais.** "Por que você deu essa nota?" só faz sentido para quem deu nota baixa.
  Sem condição, o autor ou pergunta a todos, ou cria outra pesquisa.
- **Reaproveitar uma pesquisa** — inclusive entre aplicações (app iOS e Android cadastrados à parte)
  — exigia remontar tudo à mão.
- **Texto livre é por onde dado pessoal entra sem ninguém pedir.** Um lembrete curto junto do campo
  reduz isso sem assustar quem responde.
- **Publicar às cegas.** A classificação cosmética precisava enxergar condição e faixa; e faltava
  avisar o que não impede a publicação mas deveria ser uma escolha consciente: pesquisas
  disputando o mesmo evento, regra que não alcança ninguém, SDK antigo que não desenharia a
  pesquisa.

Esta fatia entrega isso tudo e reafirma, com as extensões, as regras de classificação,
comparabilidade e impedimentos que vieram da `003`.

## Clarifications

### Session 2026-09-12

- Q: O modelo prende a pesquisa ao formato? → A: Não. É ponto de partida: o rascunho nasce com a
  pergunta oficial e segue editável. A pesquisa lembra de qual modelo partiu (usado pelo NPS no
  topo dos resultados).
- Q: Quais operadores a condição aceita? → A: `equals`, `not_equals`, `in` e `between`. Em múltipla
  escolha, igual significa "contém". A origem é sempre uma pergunta anterior, nunca de texto livre.
- Q: O que acontece com a pergunta pulada pela condição? → A: O SDK a envia como não aplicável.
  Não aplicável só é aceito em pergunta com condição, e uma obrigatória pulada pela condição não
  impede concluir.
- Q: Duplicar copia o quê? → A: O conteúdo que está valendo (a versão publicada, ou o rascunho de
  quem nunca publicou), exposição, modelo e aviso de texto livre. Respostas, versões anteriores e
  estado ficam com a original. A cópia nasce rascunho, versão 1, com chaves novas.
- Q: Janela já encerrada na original vai para a cópia? → A: Vira janela aberta a partir de agora e
  sem fim; evento e amostragem ficam. Janela que ainda vale é copiada como está.
- Q: Renomear exige rascunho? → A: Não. O nome é da pesquisa, não da versão; muda a qualquer
  momento, inclusive no ar.
- Q: O aviso de texto livre nasce ligado? → A: Sim, com um texto padrão curto e humano. O autor pode
  desligar ou trocar o texto; texto nulo volta ao padrão.
- Q: Trocar o rótulo dos extremos da escala é mudança semântica? → A: Não, é cosmético. Mudar a
  faixa é semântico. Mudar a condição é semântico, porque muda quem vê a pergunta.
- Q: Avisos bloqueiam a publicação? → A: Não. Impedimento bloqueia; aviso transforma uma escolha
  invisível em escolha consciente.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Começar de um modelo consagrado (Priority: P1)

O autor cria uma pesquisa escolhendo NPS, CSAT ou CES 2.0. O rascunho nasce com a pergunta oficial,
a escala certa e os rótulos dos extremos, pronto para publicar assim que houver disparo.

**Why this priority**: é o caminho mais comum de criação e o que mais erra quando feito à mão.

**Independent Test**: criar pelo modelo NPS e conferir pergunta, tipo NPS, escala 0–10, rótulos e
que a pesquisa registra o modelo.

**Acceptance Scenarios**:

1. **Given** uma aplicação ativa, **When** crio com modelo `nps`, **Then** o rascunho nasce com uma
   pergunta NPS 0–10, obrigatória, "Nada provável" a "Extremamente provável", e a pesquisa registra
   o modelo.
2. **Given** o modelo `csat`, **Then** a pergunta é avaliação de 1 a 5, "Muito insatisfeito" a
   "Muito satisfeito".
3. **Given** o modelo `ces`, **Then** a pergunta é escala de concordância de 1 a 7 com a afirmação
   de facilidade.
4. **Given** nenhum modelo, **When** crio, **Then** o rascunho nasce vazio e sem modelo registrado.
5. **Given** um modelo desconhecido, **When** crio, **Then** recebo recusa por validação.
6. **Given** uma pergunta de escala, **When** defino rótulos dos extremos de até 60 caracteres,
   **Then** eles são gravados; em pergunta sem faixa, rótulo é recusado.

---

### User Story 2 - Pergunta que depende da resposta anterior (Priority: P1)

O autor condiciona "Por que você deu essa nota?" a `between 0 e 6` na pergunta de NPS. O SDK recebe a
condição e a avalia localmente; quem deu 9 nem vê a pergunta.

**Why this priority**: muda o que se consegue perguntar sem multiplicar pesquisas.

**Independent Test**: criar a condição, publicar, conferir que a entrega ao SDK leva a condição e
que a submissão com a pergunta como não aplicável é aceita.

**Acceptance Scenarios**:

1. **Given** uma pergunta de escolha anterior, **When** condiciono outra a `equals`, `not_equals` ou
   `in` com valores das opções da origem, **Then** a condição é gravada e aparece no detalhe.
2. **Given** uma pergunta NPS anterior, **When** condiciono a `between 0 e 6`, **Then** a condição é
   aceita; os extremos da escala valem como valor.
3. **Given** valor que não é opção da origem, número fora da escala, texto em escala, `between` em
   escolha, origem posterior, a própria pergunta ou origem de texto livre, **When** salvo, **Then**
   recebo recusa por regra de negócio com o código e o campo que a causou.
4. **Given** uma pergunta que é origem de outra, **When** a removo, **Then** a remoção é recusada
   apontando a dependente, e a versão fica como estava.
5. **Given** uma origem, **When** a altero de um jeito que invalida a condição (remover a opção usada),
   **Then** a alteração é recusada apontando a dependente.
6. **Given** uma condicionada, **When** reordeno pondo-a antes da origem, **Then** a reordenação é
   recusada e a ordem fica como estava.
7. **Given** uma pesquisa publicada com condição, **When** o SDK consulta a elegibilidade, **Then** a
   pergunta condicionada chega com a condição.
8. **Given** uma obrigatória condicionada pulada pela condição, **When** o SDK envia não aplicável,
   **Then** a conclusão é aceita; não aplicável em pergunta sem condição é recusado e nada é gravado.

---

### User Story 3 - Duplicar, renomear e descartar (Priority: P2)

O autor duplica a pesquisa do app iOS para a aplicação Android, dá um nome novo e ajusta o disparo.
Renomeia a original no ar e descarta um rascunho que nunca publicou.

**Why this priority**: economia de trabalho, não capacidade nova.

**Independent Test**: duplicar para outra aplicação e conferir que nasce um rascunho com chaves
novas, sem histórico, e que a original não mudou.

**Acceptance Scenarios**:

1. **Given** uma pesquisa publicada com rascunho de versão aberto, **When** duplico, **Then** a cópia
   leva a versão publicada, não o rascunho, numa pesquisa nova em rascunho.
2. **Given** um destino e um nome, **When** duplico, **Then** a cópia vai para a aplicação de destino
   com o nome dado, e a resposta aponta onde ela pode ser consultada.
3. **Given** nenhum nome, **When** duplico, **Then** a cópia se chama "Cópia de <nome>", cortada no
   limite de 120 caracteres.
4. **Given** perguntas com condição, **When** duplico, **Then** as perguntas ganham chaves novas e
   a condição passa a apontar para a chave nova da origem.
5. **Given** janela já encerrada, **When** duplico, **Then** a cópia tem janela aberta a partir de
   agora e sem fim, mantendo evento e amostragem.
6. **Given** uma pesquisa no ar, **When** a renomeio, **Then** o nome muda sem abrir versão nem mudar
   o estado.
7. **Given** um rascunho nunca publicado, **When** o descarto, **Then** ele deixa de existir junto
   do conteúdo montado; pesquisa publicada (com ou sem rascunho aberto) não se apaga — se encerra.
8. **Given** um destino inexistente ou inativo, **When** duplico, **Then** recebo a recusa da
   aplicação de destino.

---

### User Story 4 - Lembrar de não escrever dado pessoal (Priority: P2)

Junto de cada campo de texto livre, o SDK mostra "Evite escrever dados pessoais, como nome,
telefone ou e-mail." O autor pode desligar ou trocar o texto.

**Why this priority**: reduz dado pessoal na fonte, onde é mais barato evitá-lo.

**Independent Test**: sem configurar nada, consultar a elegibilidade de uma pesquisa com texto
livre e receber o aviso ligado com o texto padrão.

**Acceptance Scenarios**:

1. **Given** uma pesquisa nova, **When** consulto, **Then** o aviso está ligado com o texto padrão.
2. **Given** um texto próprio, **When** o defino, **Then** ele é gravado, devolvido e entregue ao SDK.
3. **Given** o aviso desligado, **When** o SDK consulta, **Then** recebe o aviso desligado e sem texto.
4. **Given** texto nulo, **When** edito, **Then** volta ao padrão sem mudar se está ligado.
5. **Given** texto em branco, acima de 200 caracteres ou aviso ligado nulo, **When** edito, **Then**
   recebo recusa por validação e nada muda.
6. **Given** uma pesquisa com aviso configurado, **When** a duplico, **Then** a cópia leva o aviso.

---

### User Story 5 - Publicar sabendo o que muda (Priority: P1)

Ao publicar a versão 2, o autor declara se a mudança é cosmética ou semântica, com um resumo. O
sistema confere a declaração cosmética contra a versão anterior e recusa quando a estrutura mudou.
Versões cosméticas somam com a anterior; semânticas abrem grupo novo.

**Why this priority**: é o que protege a soma dos resultados entre versões.

**Independent Test**: publicar v1, trocar o rótulo da escala e publicar como cosmética (aceito);
trocar a condição e publicar como cosmética (recusado, apontando a pergunta e a diferença).

**Acceptance Scenarios**:

1. **Given** só rótulo, enunciado, obrigatoriedade ou ordem alterados, **When** publico como
   cosmética, **Then** a versão nasce no mesmo grupo da anterior.
2. **Given** pergunta acrescentada, removida, tipo, opções, faixa ou condição alterados, **When**
   publico como cosmética, **Then** a publicação é recusada, listando cada pergunta e diferença.
3. **Given** qualquer mudança, **When** publico como semântica, **Then** é aceita e abre o grupo
   seguinte.
4. **Given** a segunda versão em diante, **When** publico sem declarar, **Then** a publicação é
   recusada pedindo a classificação.
5. **Given** um rascunho idêntico à publicada, **When** publico, **Then** é recusado: não há mudança.
6. **Given** v1 → v2 cosmética → v3 semântica → v4 cosmética, **When** consulto a comparabilidade,
   **Then** recebo os grupos {v1, v2} e {v3, v4}.

---

### User Story 6 - Saber o que impede e o que merece atenção (Priority: P2)

Antes de publicar, o autor vê os impedimentos (sem pergunta, sem disparo, …) e os avisos:
pesquisas no ar no mesmo evento, regra de segmentação que não alcança ninguém, regras que se
contradizem e tráfego de SDK que não desenharia a pesquisa.

**Why this priority**: evita a pesquisa que publica e nunca aparece.

**Independent Test**: publicar duas pesquisas no mesmo evento e consultar os avisos de uma delas.

**Acceptance Scenarios**:

1. **Given** um rascunho sem pergunta ou sem disparo, **When** consulto os impedimentos, **Then**
   recebo cada um com o campo que o causou.
2. **Given** outra pesquisa no ar ou agendada no mesmo evento, **When** consulto os avisos, **Then**
   recebo a concorrência com nome e prioridade de cada uma; pausada fica de fora, e a pesquisa não
   disputa consigo mesma.
3. **Given** uma regra que exige atributo nunca enviado, ou igualdade a valor nunca visto, **When**
   consulto, **Then** recebo aviso com a regra e o atributo; se o catálogo está saturado, não se
   conclui que o valor nunca foi visto.
4. **Given** duas regras exigindo valores diferentes do mesmo atributo, ou exigindo e proibindo o
   mesmo valor, ou a presença e a ausência, **When** consulto, **Then** recebo aviso de contradição.
5. **Given** tráfego recente em que as versões que suportam a pesquisa não são maioria estrita,
   **When** consulto, **Then** recebo aviso de compatibilidade com a versão mínima e a proporção que
   não suporta; sem tráfego registrado, nada a dizer.
6. **Given** pesquisa fora do escopo, **When** consulto, **Then** recebo pesquisa não encontrada.

---

### Edge Cases

- **Condição com valores repetidos** ou valor vazio/acima de 120 caracteres: recusada.
- **`in` com mais de 50 valores**: recusada.
- **`between` com mínimo acima do máximo**: recusada; mínimo igual ao máximo é aceito.
- **A ordem dos valores da condição** não muda o sentido: `in [a, b]` e `in [b, a]` são a mesma
  condição na classificação.
- **Opção que deixou de existir** numa versão posterior: classificação semântica obrigatória.
- **Pesquisa publicada com rascunho aberto** não se descarta.
- **Duplicar pesquisa nunca publicada** copia o rascunho.
- **Aviso de texto livre ligado sem nenhuma pergunta de texto livre**: não exige recurso do SDK.

## Requirements *(mandatory)*

### Functional Requirements

**Modelos**

- **FR-001**: A criação de pesquisa MUST aceitar opcionalmente um modelo entre NPS, CSAT e CES.
- **FR-002**: Com modelo, o rascunho MUST nascer com uma pergunta obrigatória na forma oficial: NPS
  0–10 ("Nada provável"/"Extremamente provável"); CSAT avaliação 1–5 ("Muito
  insatisfeito"/"Muito satisfeito"); CES 2.0 escala 1–7 ("Discordo totalmente"/"Concordo
  totalmente"); sem disparo e sem condição.
- **FR-003**: A pesquisa MUST registrar o modelo de origem.
- **FR-004**: Perguntas com faixa MUST aceitar rótulos opcionais dos extremos, de até 60 caracteres;
  perguntas sem faixa MUST recusar rótulo.

**Condição**

- **FR-005**: Uma pergunta MAY ter uma condição de exibição com origem, operador (`equals`,
  `not_equals`, `in`, `between`) e valores ou faixa.
- **FR-006**: `equals` e `not_equals` MUST ter exatamente um valor; `in` de 1 a 50 valores distintos;
  `between` mínimo e máximo, sem valores, com mínimo ≤ máximo.
- **FR-007**: A origem MUST existir na mesma versão, vir antes da condicionada e não ser de texto
  livre.
- **FR-008**: Em origem de escolha, todo valor MUST ser opção da origem, e `between` MUST ser
  recusado; em origem numérica, todo valor MUST ser inteiro dentro da escala, e a faixa MUST caber
  nela.
- **FR-009**: Remover uma origem, alterá-la invalidando a condição, ou reordenar deixando a
  condicionada antes da origem MUST ser recusado, apontando a pergunta dependente, sem alterar nada.
- **FR-010**: Toda recusa de condição MUST trazer o código estável e o campo que a causou.
- **FR-011**: A condição MUST ser entregue ao SDK junto da pergunta condicionada.
- **FR-012**: A submissão MUST aceitar a situação "não aplicável" apenas em pergunta condicionada;
  obrigatória não aplicável MUST NOT impedir a conclusão.
- **FR-013**: A cópia de versão (nova versão ou duplicação) MUST manter a condição apontando pela
  chave estável da origem.

**Duplicação, nome e descarte**

- **FR-014**: O sistema MUST permitir duplicar uma pesquisa, opcionalmente para outra aplicação ativa
  e com outro nome.
- **FR-015**: A cópia MUST levar a versão publicada (ou o rascunho, se nunca publicou), exposição,
  modelo e aviso de texto livre; MUST nascer rascunho, versão 1, grupo 1, com perguntas e regras de
  identidade e chave novas.
- **FR-016**: Janela já encerrada MUST virar janela aberta a partir do instante da cópia e sem fim.
- **FR-017**: Sem nome informado, a cópia MUST se chamar "Cópia de <nome>", cortada em 120.
- **FR-018**: A original MUST ficar inalterada.
- **FR-019**: O nome MUST ser editável em qualquer estado, sem abrir versão.
- **FR-020**: Descartar MUST apagar a pesquisa nunca publicada e todo o conteúdo dela, e MUST ser
  recusado para pesquisa publicada.

**Aviso de texto livre**

- **FR-021**: Toda pesquisa MUST ter o aviso de texto livre, ligado por padrão, com texto padrão.
- **FR-022**: O autor MUST poder ligar/desligar e definir texto próprio de até 200 caracteres, não
  vazio; texto nulo MUST voltar ao padrão.
- **FR-023**: O SDK MUST receber o aviso com o texto já resolvido quando ligado, e desligado sem
  texto quando desligado.

**Publicação**

- **FR-024**: A partir da segunda versão, a publicação MUST exigir a classificação `cosmetic` ou
  `semantic`, com resumo opcional de até 500 caracteres.
- **FR-025**: A declaração `cosmetic` MUST ser recusada quando, em relação à publicada anterior,
  houver pergunta acrescentada ou removida, tipo, conjunto de opções, faixa ou condição alterados;
  a recusa MUST listar cada pergunta e diferença.
- **FR-026**: Rótulos, enunciado, obrigatoriedade e ordem MUST NOT impedir a declaração cosmética.
- **FR-027**: Rascunho idêntico ao publicado MUST ser recusado.
- **FR-028**: Versão cosmética MUST herdar o grupo de comparabilidade da anterior; semântica MUST
  abrir o seguinte; a comparabilidade MUST ser consultável como grupos de versões.
- **FR-029**: O sistema MUST expor os impedimentos de publicação de uma vez, cada um com o campo.
- **FR-030**: O sistema MUST expor avisos de publicação, sem bloquear: concorrência no mesmo evento
  (pesquisas no ar ou agendadas, com nome e prioridade), regra que não alcança ninguém pelo catálogo
  observado, regras contraditórias no mesmo atributo, e compatibilidade quando as versões de SDK que
  suportam a pesquisa não são maioria estrita do tráfego recente.
- **FR-031**: A versão mínima de SDK de uma pesquisa MUST considerar os tipos de pergunta e os
  recursos usados (condição, rótulos de escala, aviso de texto livre quando ligado e havendo texto
  livre).

**Transversais**

- **FR-032**: Pesquisa de outra aplicação MUST recusar como inexistente (`survey.not_found`).
- **FR-033**: As operações desta fatia MUST pertencer à superfície administrativa.

### Key Entities

- **Modelo de pesquisa**: NPS, CSAT ou CES — de onde a pesquisa partiu.
- **Condição de exibição**: origem (chave estável), operador, valores ou faixa.
- **Rótulos da escala**: texto opcional dos extremos; cosmético.
- **Aviso de texto livre**: ligado/desligado e texto próprio opcional; da pesquisa, não da versão.
- **Diferença estrutural**: pergunta e tipo de diferença entre duas versões.
- **Aviso de publicação**: código, regra ou atributo envolvido, pesquisas concorrentes, versão
  mínima e proporção sem suporte.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma pesquisa de NPS na forma oficial fica pronta para publicar com duas chamadas
  (criar pelo modelo e definir o disparo).
- **SC-002**: Nenhuma condição que nunca poderia ser satisfeita — valor fora das opções ou da
  escala da origem — chega a ser gravada.
- **SC-003**: Nenhuma versão declarada cosmética difere estruturalmente da anterior; 100% das
  recusas apontam a pergunta e a diferença.
- **SC-004**: Duplicar não altera nenhuma linha da pesquisa original.
- **SC-005**: Toda pesquisa com texto livre entregue ao SDK leva o estado do aviso, sem
  configuração prévia.
- **SC-006**: Avisos de publicação nunca impedem a publicação.

## Assumptions

- A condição é avaliada pelo SDK, sem nova consulta; a regra de "origem pulada não satisfaz" é do
  SDK.
- A pergunta de modelo não é travada: depois de editada, a pesquisa continua lembrando do modelo.
- O aviso de compatibilidade usa a janela de tráfego recente da saúde do SDK (14 dias por padrão).
- Pesquisa duplicada não herda respostas nem é comparável com a original.

## Dependencies

- `backend 003-survey-authoring`: rascunho, perguntas, versões, classificação, comparabilidade,
  impedimentos — estendidos aqui.
- `backend 004-response-collection`: entrega ao SDK e validação da submissão.
- `backend 007-application-management`: aplicação de destino ativa.
- `backend 008-exposure-control`: catálogo de atributos e prioridade usados nos avisos.
- `backend 010-sdk-health`: tráfego recente por versão e catálogo de capacidades do SDK usados no
  aviso de compatibilidade.
- `backend 012-survey-results`: conta o não aplicável à parte e usa o modelo NPS.
