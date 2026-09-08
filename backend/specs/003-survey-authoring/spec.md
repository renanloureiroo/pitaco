# Feature Specification: Autoria de pesquisa — rascunho, perguntas, disparo, regras e versões

**Feature Branch**: `003-survey-authoring`

**Created**: 2026-09-08

**Status**: Draft

**Input**: User description: "Vamos começar a pensar na nossa pesquisa, temos a documentação no obsidian para ser usada como base. Obsidian: Pitaco. Quero criar um pesquisa em uma aplicação, criar versões, o trigger as perguntas e as rules. Importamente mantermos em /docs uma documentação do projeto para uso, quase como um run book para usuários, fui claro?"

## Contexto

O Pitaco já sabe cadastrar aplicações e emitir chaves de acesso. O que ele ainda não sabe
é a razão de existir: **a pesquisa**. Esta feature abre o caminho da autoria — quem
pesquisa cria a pesquisa dentro de uma aplicação, escreve as perguntas, decide em que
momento ela dispara e para quem, e publica. Publicar congela o conteúdo em uma versão
imutável, e é essa versão — não a pesquisa — que cada resposta futura vai referenciar.

O que esta feature **não** faz: não entrega pesquisa a ninguém, não coleta resposta e não
agrega resultado. Ela produz o artefato que a entrega vai consumir. Um observador externo
consegue verificar o valor entregue inteiramente pela superfície administrativa: criar,
montar, configurar, publicar, controlar o que está no ar e ler a versão publicada.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Criar e manter o rascunho de uma pesquisa (Priority: P1)

Quem pesquisa cria uma pesquisa dentro de uma aplicação existente, dando a ela um nome.
A pesquisa nasce como **rascunho** e aceita conteúdo incompleto: dá para criar hoje o
esqueleto, sair, e voltar amanhã para terminar sem perder nada. O rascunho pode ser
renomeado, consultado e listado junto com as outras pesquisas da mesma aplicação, e pode
ser descartado enquanto nunca tiver sido publicado.

O rascunho é a válvula de segurança do sistema: não existe ambiente de homologação, então
é ele — somado a uma aplicação de teste dedicada — que substitui um.

**Why this priority**: é o recipiente. Sem ele não há onde pendurar pergunta, disparo nem
regra. Entrega valor sozinha porque já permite organizar o trabalho de pesquisa dentro de
cada aplicação, mesmo antes de existir uma pergunta escrita.

**Independent Test**: criar duas pesquisas em uma aplicação e uma em outra, listar as da
primeira, conferir que as duas aparecem como rascunho e que a da outra aplicação não
aparece; renomear uma, consultá-la e conferir o novo nome; descartar a outra e conferir
que ela some da listagem.

**Acceptance Scenarios**:

1. **Given** uma aplicação ativa, **When** crio uma pesquisa informando o nome, **Then**
   ela é criada em estado de rascunho, sem perguntas, sem disparo e sem versão publicada,
   e recebo o identificador dela.
2. **Given** uma pesquisa em rascunho, **When** a consulto, **Then** recebo o nome, o
   estado, a aplicação dona, o instante de criação e o conteúdo montado até aqui.
3. **Given** duas aplicações, cada uma com suas pesquisas, **When** listo as pesquisas de
   uma delas, **Then** nenhuma pesquisa da outra aparece.
4. **Given** uma aplicação com mais pesquisas do que cabe em uma página, **When** listo as
   pesquisas, **Then** recebo a primeira página com o total e a forma de pedir a próxima,
   ordenada da mais recente para a mais antiga.
5. **Given** uma aplicação sem nenhuma pesquisa, **When** listo, **Then** recebo um
   resultado vazio — não um erro.
6. **Given** uma pesquisa em rascunho, **When** altero o nome, **Then** o novo nome é
   guardado e a pesquisa continua em rascunho.
7. **Given** uma pesquisa em rascunho, **When** a descarto, **Then** ela deixa de existir
   junto com o conteúdo montado nela.
8. **Given** um identificador de aplicação que não existe, **When** crio uma pesquisa nela,
   **Then** a operação é recusada informando que a aplicação não foi encontrada.
9. **Given** uma aplicação inativa, **When** crio uma pesquisa nela, **Then** a operação é
   recusada informando que a aplicação está inativa.
10. **Given** uma pesquisa de uma aplicação, **When** a consulto informando outra
    aplicação como dona, **Then** ela não é encontrada.

---

### User Story 2 - Montar as perguntas (Priority: P1)

Dentro do rascunho, quem pesquisa escreve as perguntas. Cada pergunta tem um enunciado, um
tipo entre os seis do catálogo — escolha única, múltipla escolha, avaliação, NPS, escala e
texto livre —, a indicação de obrigatoriedade e, nos tipos que exigem, suas opções. A ordem
das perguntas é a ordem que o respondente vê, e pode ser mudada. Pergunta pode ser
reescrita e removida enquanto a pesquisa for rascunho.

Cada pergunta carrega também uma **chave estável**, que nasce com ela e sobrevive a todas
as versões seguintes. É essa chave que torna possível, mais tarde, dizer "essa pergunta é a
mesma da versão anterior" sem comparar texto.

**Why this priority**: é o conteúdo. Uma pesquisa sem perguntas não é pesquisa. Junto com a
história 1 forma o MVP: um rascunho montável ponta a ponta.

**Independent Test**: montar num rascunho uma pergunta de cada um dos seis tipos, reordenar
duas delas, remover uma, consultar a pesquisa e conferir que as cinco restantes voltam na
ordem definida, com enunciado, tipo, obrigatoriedade e opções íntegros.

**Acceptance Scenarios**:

1. **Given** uma pesquisa em rascunho, **When** acrescento uma pergunta de escolha única
   com enunciado e duas opções, **Then** ela é guardada na última posição, com uma chave
   estável própria.
2. **Given** uma pesquisa em rascunho, **When** acrescento uma pergunta de cada um dos seis
   tipos do catálogo, **Then** todas são aceitas e voltam na consulta na ordem em que foram
   criadas.
3. **Given** uma pergunta de tipo que exige opções, **When** a crio sem nenhuma opção,
   **Then** a pergunta é aceita no rascunho e a pendência fica registrada como impedimento
   de publicação — o rascunho aceita incompleto.
4. **Given** uma pergunta de texto livre, **When** informo opções para ela, **Then** a
   operação é recusada informando que o tipo não aceita opções.
5. **Given** uma pesquisa com quatro perguntas, **When** movo a última para a primeira
   posição, **Then** as quatro voltam na nova ordem, sem buraco e sem posição repetida.
6. **Given** uma pergunta existente, **When** reescrevo o enunciado, o tipo, a
   obrigatoriedade ou as opções, **Then** as mudanças são guardadas e a chave estável da
   pergunta permanece a mesma.
7. **Given** uma pesquisa com três perguntas, **When** removo a do meio, **Then** as duas
   restantes ficam em posições consecutivas, sem buraco.
8. **Given** uma pesquisa em rascunho, **When** tento acrescentar uma pergunta com
   enunciado vazio, **Then** a operação é recusada apontando o campo.
9. **Given** uma pergunta de escala ou avaliação, **When** informo limites incoerentes —
   mínimo maior que o máximo, ou faixa de tamanho zero —, **Then** a operação é recusada
   apontando o que está incoerente.
10. **Given** uma pesquisa já publicada, **When** tento acrescentar, alterar, reordenar ou
    remover pergunta nela, **Then** a operação é recusada informando que o conteúdo
    publicado está congelado.

---

### User Story 3 - Configurar o disparo e as regras de segmentação (Priority: P2)

Quem pesquisa define **quando** a pesquisa aparece e **para quem**. O disparo tem três
partes: o nome do evento do app que a aciona, a janela de início e fim, e a proporção de
quem vê. Uma pesquisa escuta um evento — não dois.

Sobre esse disparo se penduram as **regras de segmentação**: exigências sobre os atributos
que o app hospedeiro manda junto da consulta, com quatro operações — igual, diferente,
presente e ausente. Todas as regras precisam casar para a pesquisa ser considerada. Atributo
ausente nunca satisfaz regra que o exige: a avaliação falha fechado.

**Why this priority**: sem disparo a pesquisa é conteúdo sem gatilho, e publicar não faz
sentido. Vem depois das perguntas porque um rascunho com perguntas e sem disparo já tem
valor demonstrável; o contrário não tem.

**Independent Test**: configurar o disparo de um rascunho com evento, janela e proporção,
acrescentar duas regras de segmentação, consultar a pesquisa e conferir que tudo volta
íntegro; depois tentar uma janela com fim antes do início e conferir a recusa.

**Acceptance Scenarios**:

1. **Given** uma pesquisa em rascunho sem disparo, **When** defino evento, janela e
   proporção, **Then** o disparo é guardado e volta na consulta da pesquisa.
2. **Given** uma pesquisa com disparo definido, **When** redefino o evento ou a proporção,
   **Then** o disparo é substituído e continua sendo um só.
3. **Given** um disparo, **When** informo janela sem data de fim, **Then** é aceita — a
   pesquisa roda por tempo indeterminado.
4. **Given** um disparo, **When** informo fim anterior ou igual ao início, **Then** a
   operação é recusada informando que a janela é incoerente.
5. **Given** um disparo, **When** informo proporção fora do intervalo de zero a um,
   **Then** a operação é recusada apontando o campo.
6. **Given** um disparo, **When** informo nome de evento vazio ou fora do formato aceito,
   **Then** a operação é recusada apontando o campo.
7. **Given** um disparo definido, **When** acrescento uma regra exigindo que um atributo
   seja igual a um valor, **Then** a regra é guardada vinculada ao disparo.
8. **Given** um disparo com regras, **When** consulto a pesquisa, **Then** as regras voltam
   com atributo, operação e valor.
9. **Given** uma regra de presença ou ausência, **When** informo um valor para ela,
   **Then** a operação é recusada — essas operações não comparam valor.
10. **Given** uma regra de igualdade ou diferença, **When** a crio sem valor, **Then** a
    operação é recusada apontando o campo.
11. **Given** um disparo com regras, **When** removo uma delas, **Then** as demais
    permanecem intactas.
12. **Given** uma pesquisa já publicada, **When** tento alterar o disparo ou as regras,
    **Then** a operação é recusada informando que o conteúdo publicado está congelado.

---

### User Story 4 - Publicar, congelando o conteúdo em uma versão (Priority: P2)

Publicar é o ponto de não-retorno. A publicação primeiro **valida** — exige ao menos uma
pergunta, todo enunciado preenchido, opção em todo tipo que exige opção, evento definido e
janela coerente — e, quando recusa, diz **o que** falta e **onde**, nunca "inválido".
Passando, o conteúdo é congelado na **versão 1** da pesquisa, e a pesquisa passa a
**agendada** se a janela ainda não abriu ou **ativa** se já abriu.

A versão congelada é o artefato que a entrega vai ler e que cada resposta futura vai
referenciar. Ela é imutável: nem o texto de uma pergunta muda depois.

**Why this priority**: é o que transforma trabalho de autoria em algo que o resto do
sistema pode consumir. Depende das histórias 2 e 3 existirem para ter o que validar.

**Independent Test**: montar um rascunho completo, publicar, conferir que a versão 1 nasceu
com as perguntas na ordem e que a pesquisa ficou agendada ou ativa conforme a janela; e
tentar publicar um rascunho sem perguntas, conferindo que a recusa aponta o motivo.

**Acceptance Scenarios**:

1. **Given** um rascunho com perguntas válidas, disparo e janela já aberta, **When**
   publico, **Then** nasce a versão 1 com o instante de publicação, e a pesquisa fica
   ativa.
2. **Given** o mesmo rascunho com janela que só abre amanhã, **When** publico, **Then** a
   pesquisa fica agendada, não ativa.
3. **Given** um rascunho sem nenhuma pergunta, **When** publico, **Then** a publicação é
   recusada informando que falta pergunta.
4. **Given** um rascunho com uma pergunta de escolha única sem opções, **When** publico,
   **Then** a publicação é recusada apontando aquela pergunta.
5. **Given** um rascunho sem disparo configurado, **When** publico, **Then** a publicação é
   recusada informando que falta o evento de disparo.
6. **Given** um rascunho com mais de um impedimento, **When** publico, **Then** a recusa
   relata todos os impedimentos de uma vez, cada um apontando o campo que o causou.
7. **Given** uma pesquisa publicada, **When** consulto a versão publicada dela, **Then**
   recebo as perguntas com enunciado, tipo, ordem, obrigatoriedade, opções e chave estável.
8. **Given** uma pesquisa publicada, **When** publico de novo, **Then** a operação é
   recusada informando que a pesquisa já está publicada.
9. **Given** um rascunho, **When** consulto os impedimentos de publicação sem publicar,
   **Then** recebo a mesma lista que a publicação usaria — descobrir pendência não exige
   tentar publicar.
10. **Given** uma pesquisa publicada, **When** a descarto, **Then** a operação é recusada —
    publicada não se apaga, se encerra.

---

### User Story 5 - Controlar o que está no ar (Priority: P3)

Publicada, a pesquisa precisa poder ser desligada sem depender de ninguém. **Pausar** é
imediato e reversível; **encerrar** é definitivo. Uma pesquisa agendada vira ativa quando a
janela abre. Cada transição de estado fica registrada com o instante e o motivo, e o motivo
distingue o que foi manual do que foi automático.

**Why this priority**: é a alavanca de emergência, e só faz sentido depois que existe
publicação. Sem ela, a única forma de parar uma pesquisa ruim é no banco.

**Independent Test**: publicar uma pesquisa, pausá-la, conferir o estado e o registro da
transição, retomá-la, encerrá-la, e conferir que retomar depois de encerrada é recusado.

**Acceptance Scenarios**:

1. **Given** uma pesquisa ativa, **When** a pauso, **Then** ela fica pausada e a transição
   fica registrada com instante e motivo manual.
2. **Given** uma pesquisa pausada, **When** a retomo, **Then** ela volta a ativa — ou a
   agendada, se a janela ainda não abriu.
3. **Given** uma pesquisa ativa ou pausada, **When** a encerro, **Then** ela fica encerrada
   e a transição fica registrada.
4. **Given** uma pesquisa encerrada, **When** tento retomá-la ou pausá-la, **Then** a
   operação é recusada informando que o encerramento é definitivo.
5. **Given** uma pesquisa em rascunho, **When** tento pausá-la ou encerrá-la, **Then** a
   operação é recusada informando que ela ainda não foi publicada.
6. **Given** uma pesquisa agendada cuja janela abriu, **When** a consulto, **Then** ela
   aparece como ativa.
7. **Given** uma pesquisa ativa cuja janela fechou, **When** a consulto, **Then** ela
   aparece como encerrada.
8. **Given** uma pesquisa com histórico de transições, **When** consulto esse histórico,
   **Then** recebo cada transição com estado de origem, estado de destino, motivo e
   instante.

---

### User Story 6 - Criar uma nova versão de pesquisa publicada (Priority: P3)

Um erro de digitação no enunciado de uma pesquisa que já está coletando não deveria custar
uma pesquisa nova e um resultado partido ao meio. Quem pesquisa abre uma **nova versão** a
partir da versão publicada, corrige o que precisa e publica de novo. A versão anterior
continua existindo intacta, e as respostas já dadas continuam apontando para ela.

As perguntas mantidas conservam a chave estável — é isso que torna possível somar depois o
que é somável. Pergunta acrescentada nasce com chave nova.

E toda versão nova carrega uma **classificação da mudança**: **cosmética** quando o
sentido do que se pergunta não mudou — uma vírgula corrigida, uma palavra trocada por
sinônimo —, e **semântica** quando mudou. A distinção não é burocracia: ela é o que
autoriza, ou proíbe, somar as respostas das duas versões numa leitura só. Corrigir
"Qual sua avaliaçao?" para "Qual sua avaliação?" não invalida nada; trocar "Você
recomendaria?" por "Você recomendaria a um colega de trabalho?" cria outra pergunta com o
mesmo rosto, e somar as duas produz um número que não significa nada.

Quem publica a versão declara a classificação, e o sistema recusa a declaração que
contradiz o que ele consegue verificar: acrescentar, remover ou retipar pergunta é
semântico por construção, e nenhuma declaração de cosmético sobrevive a isso.

**Why this priority**: é o que evita que a única saída para um erro de texto seja duplicar
a pesquisa e perder a série. Mas uma pesquisa publicada sem erro nenhum não precisa disso —
por isso vem por último entre as histórias de autoria.

**Independent Test**: publicar uma pesquisa, abrir uma nova versão, corrigir o enunciado de
uma pergunta declarando a mudança como cosmética, publicar, e conferir que existem duas
versões, que a versão 1 continua com o texto original, que a pergunta corrigida manteve a
chave estável e que a v2 está marcada como cosmética; depois abrir uma v3 removendo uma
pergunta e conferir que a declaração de cosmética é recusada.

**Acceptance Scenarios**:

1. **Given** uma pesquisa publicada, **When** abro uma nova versão, **Then** nasce um
   rascunho de versão com cópia integral das perguntas da versão publicada, cada uma
   conservando sua chave estável.
2. **Given** um rascunho de versão, **When** corrijo o enunciado de uma pergunta e publico
   declarando a mudança como cosmética, **Then** nasce a versão 2 marcada como cosmética e
   a versão 1 continua com o texto original.
3. **Given** um rascunho de versão em que acrescentei uma pergunta, **When** publico
   declarando cosmética, **Then** a publicação é recusada informando que acrescentar
   pergunta é mudança semântica.
4. **Given** um rascunho de versão em que removi uma pergunta, **When** publico declarando
   cosmética, **Then** a publicação é recusada pelo mesmo motivo.
5. **Given** um rascunho de versão em que troquei o tipo de uma pergunta mantendo o
   enunciado, **When** publico declarando cosmética, **Then** a publicação é recusada —
   retipar é semântico por construção.
6. **Given** um rascunho de versão em que só corrigi um enunciado, **When** publico
   declarando semântica, **Then** a publicação é aceita — declarar semântica é sempre
   permitido; a classificação restritiva é a cosmética.
7. **Given** um rascunho de versão em que alterei as opções de uma pergunta de escolha,
   **When** publico declarando cosmética, **Then** a publicação é recusada — mudar o
   conjunto de opções muda o que se pode responder.
8. **Given** uma pesquisa com três versões, **When** listo as versões, **Then** recebo as
   três com número, instante de publicação, classificação e resumo da mudança.
9. **Given** uma pesquisa com uma v2 cosmética sobre a v1 e uma v3 semântica sobre a v2,
   **When** consulto quais versões são comparáveis entre si, **Then** v1 e v2 aparecem no
   mesmo grupo e v3 em outro.
10. **Given** um rascunho de versão aberto, **When** abro outro, **Then** a operação é
    recusada — existe no máximo um rascunho de versão por pesquisa.
11. **Given** um rascunho de versão, **When** o descarto, **Then** a pesquisa volta a ter
    apenas a versão publicada, intacta.
12. **Given** um rascunho de versão, **When** acrescento uma pergunta nova, **Then** ela
    nasce com chave estável própria, distinta das que vieram da versão anterior.
13. **Given** uma pesquisa encerrada, **When** tento abrir nova versão, **Then** a operação
    é recusada.
14. **Given** um rascunho de versão idêntico à versão publicada, **When** publico, **Then**
    a operação é recusada informando que não há mudança a versionar.

---

### Edge Cases

- **Publicar exatamente no instante em que a janela abre**: a pesquisa nasce ativa, não
  agendada. O limite é inclusivo no início.
- **Reordenação que passa por um estado intermediário inválido**: mover uma pergunta é uma
  operação só, e a integridade da ordem é verificada no resultado final, não a cada passo.
- **Duas alterações concorrentes no mesmo rascunho**: a última escrita vence; não há
  bloqueio nem resolução de conflito na v1.
- **Aplicação desativada depois de ter pesquisas publicadas**: as pesquisas continuam
  existindo e consultáveis; desativar a aplicação impede criar pesquisa nova, não apaga o
  que já existe.
- **Pesquisa com janela que fecha enquanto está pausada**: continua pausada até alguém
  encerrá-la; a janela fechada só encerra pesquisa ativa.
- **Duas pesquisas da mesma aplicação escutando o mesmo evento**: aceito nesta feature. A
  disputa entre elas é decidida na entrega, não na autoria.
- **Nome de pesquisa repetido dentro da mesma aplicação**: aceito. O nome é rótulo humano,
  não identificador.
- **Regra de segmentação sobre atributo que nenhum app jamais enviou**: aceita. O catálogo
  de atributos observados ainda não existe, e a regra simplesmente não vai casar com
  ninguém até o atributo aparecer.
- **Opções repetidas dentro da mesma pergunta**: recusadas — duas opções idênticas tornam a
  resposta impossível de interpretar.
- **Versão nova que só reordena as perguntas**: pode ser declarada cosmética. A ordem muda
  a experiência de responder, mas não muda o que cada pergunta pergunta, e as respostas
  continuam somáveis por chave estável.
- **Versão nova que só troca a obrigatoriedade de uma pergunta**: pode ser declarada
  cosmética. A pergunta é a mesma; o que muda é quanto se insiste nela.
- **Sequência de versões cosméticas sobre uma semântica**: v1 → v2 cosmética → v3
  semântica → v4 cosmética produz dois grupos comparáveis — {v1, v2} e {v3, v4}. A
  comparabilidade é transitiva dentro do grupo e se rompe na semântica.
- **Pesquisa consultada por quem informa uma aplicação que não é a dona**: não encontrada,
  nunca "sem permissão" — o escopo por aplicação não vaza a existência do que está fora
  dele.

## Requirements *(mandatory)*

### Functional Requirements

**Pesquisa e rascunho**

- **FR-001**: O sistema DEVE permitir criar uma pesquisa dentro de uma aplicação ativa
  existente, com nome, nascendo em estado de rascunho.
- **FR-002**: O sistema DEVE recusar a criação de pesquisa em aplicação inexistente ou
  inativa, informando a causa.
- **FR-003**: O sistema DEVE aceitar rascunho com conteúdo incompleto e preservá-lo entre
  sessões de trabalho.
- **FR-004**: O sistema DEVE permitir consultar uma pesquisa pelo identificador, no escopo
  da aplicação dona, devolvendo nome, estado, conteúdo montado, disparo, regras e versões.
- **FR-005**: O sistema DEVE permitir listar as pesquisas de uma aplicação de forma
  paginada, ordenada da mais recente para a mais antiga, com o total.
- **FR-006**: O sistema DEVE escopar toda leitura e escrita de pesquisa pela aplicação
  dona, tratando o que está fora do escopo como inexistente.
- **FR-007**: O sistema DEVE permitir renomear pesquisa em rascunho.
- **FR-008**: O sistema DEVE permitir descartar pesquisa que nunca foi publicada, e recusar
  o descarte de pesquisa publicada.

**Perguntas**

- **FR-009**: O sistema DEVE suportar os seis tipos de pergunta do catálogo: escolha única,
  múltipla escolha, avaliação, escala, NPS e texto livre.
- **FR-010**: O sistema DEVE guardar, por pergunta, enunciado, tipo, posição,
  obrigatoriedade, chave estável e — nos tipos que exigem — opções.
- **FR-011**: O sistema DEVE atribuir a cada pergunta uma chave estável no momento da
  criação, imutável dali em diante.
- **FR-012**: O sistema DEVE recusar pergunta com enunciado vazio, com opções em tipo que
  não as aceita, com opções repetidas ou com limites de escala incoerentes.
- **FR-013**: O sistema DEVE permitir acrescentar, reescrever, reordenar e remover pergunta
  enquanto o conteúdo não estiver congelado.
- **FR-014**: O sistema DEVE manter as posições das perguntas consecutivas e sem repetição
  após qualquer reordenação ou remoção.
- **FR-015**: O sistema DEVE devolver as perguntas sempre na ordem de exibição.

**Disparo e regras de segmentação**

- **FR-016**: O sistema DEVE permitir definir, por pesquisa, um único disparo com nome do
  evento, janela de início e fim, e proporção de exibição.
- **FR-017**: O sistema DEVE aceitar janela sem data de fim e recusar janela cujo fim não
  seja posterior ao início.
- **FR-018**: O sistema DEVE recusar proporção de exibição fora do intervalo de zero a um.
- **FR-019**: O sistema DEVE permitir acrescentar e remover regras de segmentação
  vinculadas ao disparo, com atributo, operação e — quando a operação exige — valor.
- **FR-020**: O sistema DEVE suportar as operações de igualdade, diferença, presença e
  ausência nas regras de segmentação.
- **FR-021**: O sistema DEVE recusar regra de presença ou ausência que carregue valor, e
  regra de igualdade ou diferença que não carregue.

**Publicação, versões e estados**

- **FR-022**: O sistema DEVE expor, para um rascunho, a lista de impedimentos de publicação
  sem exigir uma tentativa de publicar.
- **FR-023**: O sistema DEVE recusar a publicação de pesquisa sem pergunta, com enunciado
  vazio, com tipo que exige opção sem opção, sem evento de disparo ou com janela
  incoerente — relatando todos os impedimentos de uma vez, cada um apontando o campo.
- **FR-024**: O sistema DEVE, ao publicar, congelar o conteúdo em uma versão numerada e
  imutável, com o instante da publicação.
- **FR-025**: O sistema DEVE impedir alteração de pergunta, disparo ou regra de pesquisa
  cujo conteúdo esteja congelado.
- **FR-026**: O sistema DEVE colocar a pesquisa publicada em estado agendado quando a
  janela ainda não abriu, e ativo quando já abriu.
- **FR-027**: O sistema DEVE permitir pausar pesquisa ativa e retomar pesquisa pausada.
- **FR-028**: O sistema DEVE permitir encerrar pesquisa ativa ou pausada, e recusar
  qualquer transição a partir de encerrada.
- **FR-029**: O sistema DEVE registrar cada transição de estado com estado de origem, de
  destino, motivo e instante, distinguindo transição manual de automática.
- **FR-030**: O sistema DEVE permitir consultar o conteúdo de uma versão publicada.
- **FR-031**: O sistema DEVE permitir abrir um rascunho de nova versão a partir da versão
  publicada, copiando as perguntas com suas chaves estáveis, permitindo no máximo um
  rascunho de versão por pesquisa.
- **FR-032**: O sistema DEVE preservar toda versão publicada anterior, intacta, quando uma
  nova versão é publicada.
- **FR-033**: O sistema DEVE exigir, ao publicar uma versão a partir da segunda, a
  declaração da mudança como cosmética ou semântica, e guardá-la junto de um resumo textual
  da mudança.
- **FR-034**: O sistema DEVE recusar a declaração de mudança cosmética quando, em relação à
  versão anterior, houver pergunta acrescentada, removida, com tipo alterado ou com
  conjunto de opções alterado — informando qual pergunta e qual diferença motivou a recusa.
- **FR-035**: O sistema DEVE aceitar a declaração de mudança semântica em qualquer caso —
  a restrição vale só para a cosmética.
- **FR-036**: O sistema DEVE recusar a publicação de versão idêntica à anterior.
- **FR-037**: O sistema DEVE expor, para uma pesquisa, quais de suas versões são
  comparáveis entre si — versões separadas apenas por mudanças cosméticas formam um mesmo
  grupo; uma mudança semântica abre um grupo novo.

### Key Entities

- **Pesquisa**: a unidade de autoria. Pertence a uma aplicação, tem nome, estado
  (rascunho, agendada, ativa, pausada, encerrada), janela de vigência e aponta para a
  versão publicada corrente.
- **Versão de pesquisa**: o conteúdo congelado de uma pesquisa em um momento. Numerada
  dentro da pesquisa, carrega o instante de publicação, a classificação da mudança em
  relação à versão anterior — cosmética ou semântica — e um resumo textual dela. É o
  artefato que a entrega lê e que cada resposta futura referencia. A classificação é o que
  decide, mais tarde, quais versões podem ter suas respostas somadas.
- **Pergunta**: pertence a uma versão, não à pesquisa. Tem enunciado, tipo, posição,
  obrigatoriedade, opções quando o tipo exige, e uma chave estável que atravessa versões.
- **Disparo**: um por pesquisa. Nome do evento que a aciona e a proporção de quem vê.
- **Regra de segmentação**: pertence ao disparo. Exige que um atributo do respondente
  satisfaça uma operação — igual, diferente, presente ou ausente — para a pesquisa ser
  considerada.
- **Transição de estado**: o registro de que uma pesquisa mudou de estado, com origem,
  destino, motivo e instante.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Quem pesquisa consegue levar uma pesquisa de cinco perguntas do nada até o ar
  — criar, montar, configurar o disparo e publicar — sem depender de ninguém e sem tocar no
  banco.
- **SC-002**: 100% das recusas de publicação identificam o campo ou a pergunta que as
  causou — nenhuma recusa genérica do tipo "conteúdo inválido".
- **SC-003**: Depois de publicada, nenhuma operação de autoria consegue alterar o conteúdo
  de uma versão já publicada, verificado por tentativa explícita em cada uma das operações
  de escrita.
- **SC-004**: Uma pesquisa consultada por quem informa outra aplicação como dona nunca
  revela sua existência, em 100% das operações de leitura e escrita.
- **SC-005**: Publicar uma pesquisa com 20 perguntas e consultá-la de volta devolve as 20
  na ordem definida, com conteúdo idêntico ao montado, em 100% das execuções.
- **SC-006**: Quem publica uma segunda versão consegue, olhando a listagem de versões,
  identificar o que mudou entre as duas e se elas são comparáveis, sem abrir as duas
  versões lado a lado.
- **SC-007**: Nenhuma versão declarada cosmética difere da anterior por pergunta
  acrescentada, removida, retipada ou com opções alteradas — verificado por tentativa
  explícita em cada uma dessas quatro diferenças.
- **SC-008**: Uma pergunta que atravessa três versões é reconhecível como a mesma pergunta
  nas três, sem comparar o texto do enunciado.

## Assumptions

- **A autoria é administrativa.** Estas operações pertencem à superfície administrativa do
  produto, consumida pelo painel. A superfície pública consumida pelo SDK não é tocada
  aqui.
- **Não há autenticação de usuário nesta feature.** Conforme o modelo de dados, a
  superfície administrativa segue sem autenticação até haver decisão sobre SSO; o campo que
  registra o autor de uma transição fica reservado e vazio.
- **Cota, prioridade e isenção do intervalo de descanso ficam de fora.** O disparo desta
  feature para em evento, janela, proporção e regras de segmentação. Cota de respostas,
  prioridade de desempate e isenção do intervalo de descanso pertencem à feature de
  controle de exposição.
- **Lógica condicional entre perguntas fica de fora.** Uma pergunta exibida só quando outra
  teve certa resposta é assunto de autoria avançada.
- **Preview fica de fora.** O preview é do painel, montado sobre o renderizador publicado
  pelo SDK. A API não renderiza pesquisa.
- **O catálogo de eventos e atributos já observados fica de fora.** Ele nasce da consulta de
  elegibilidade, que ainda não existe; até lá, o nome do evento e o do atributo são
  digitados.
- **A transição automática de agendada para ativa, e de ativa para encerrada pela janela, é
  observada na leitura**, derivada da janela e do instante atual, sem depender de rotina
  agendada.
- **Modelos prontos e duplicação de pesquisa ficam de fora.** Também autoria avançada.
- **A classificação da mudança é declarada, não inferida.** O sistema verifica a
  declaração contra as diferenças estruturais que ele consegue enxergar — pergunta
  acrescentada, removida, retipada, com opções alteradas — e recusa a cosmética que as
  contradiz. Ele não julga se um enunciado reescrito mudou de sentido; esse julgamento é de
  quem escreve.
- **A leitura consolidada entre versões fica de fora.** Esta feature produz a classificação
  e o agrupamento de versões comparáveis; quem soma as respostas é a feature de resultados.
- **O runbook de uso do produto não entra aqui.** A documentação para usuários é da
  aplicação como um todo — painel, SDK e API —, mora fora do repositório do backend e não
  é escopo desta entrega. O que esta feature mantém é a documentação técnica que já existe
  em `docs/`.

## Dependencies

- Aplicações e chaves de acesso, já entregues: a pesquisa pendura em uma aplicação
  existente e ativa.
- Nenhuma dependência de entrega, coleta ou resultado — essas features consomem o que esta
  produz, e não o contrário.
