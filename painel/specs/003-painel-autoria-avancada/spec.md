# Feature Specification: Painel de autoria avançada — modelos, condição, reordenação, pré-visualização e exposição

**Feature Branch**: `003-painel-autoria-avancada`

**Created**: 2026-09-12

**Status**: Implemented

**Input**: User description: "a montagem funciona, mas tá crua. quero começar de modelo pronto (NPS, CSAT, CES), pergunta condicional, arrastar pra reordenar, ver a pesquisa do jeito que vai aparecer no app e controlar quanto ela aparece. e publicar sabendo o que tá fazendo."

## Contexto

A entrega `001-painel-operacao-pesquisas` levou uma pesquisa do cadastro à publicação: nome,
perguntas, disparo, regras, versões e ciclo de vida. Era o mínimo que a API administrativa
oferecia naquele momento. A `002-painel-leitura-coleta` abriu a leitura do que o SDK coletou.

O backend cresceu na autoria desde então. Hoje ele sabe criar uma pesquisa a partir de um formato
consagrado, guardar uma condição de exibição por pergunta, duplicar uma pesquisa para outra
aplicação, controlar a exposição de cada pesquisa (prioridade no desempate, cota que a encerra
sozinha, isenção do intervalo de descanso), guardar o aviso de dado pessoal que acompanha o
texto livre, listar os eventos e atributos que o app já enviou e avisar — sem bloquear — o que
pode surpreender quem publica. E o SDK passou a ter um renderizador que roda na web.

Esta feature leva essa autoria para o painel. O valor é verificável sem tocar em código: quem
pesquisa monta uma pesquisa de NPS em segundos, condiciona a pergunta de motivo à nota baixa, vê
a pesquisa exatamente como o app vai mostrá-la, decide quanto ela disputa a atenção do usuário e
publica sabendo o que muda nos resultados.

Fica fora o que não é autoria: a leitura dos resultados (`004-painel-resultados-comportamento`)
e a saúde do SDK (`005-painel-saude-privacidade`). O aviso de comparabilidade que aparece nos
resultados é consequência da classificação feita aqui, e por isso é citado, mas é exibido lá.

## Clarifications

### Session 2026-09-12

- Q: O modelo é só um ponto de partida ou trava a pesquisa no formato? → A: Ponto de partida. A
  pesquisa nasce com a pergunta oficial do formato e continua editável como qualquer rascunho; o
  painel só guarda de que modelo ela veio (`templateKind`), que os resultados usam para o NPS.
- Q: Quem valida se a condição faz sentido (valor dentro da escala, opção existente)? → A: O
  backend. O painel valida só a forma (origem, operador, ao menos um valor, mínimo e máximo na
  faixa) e mostra a recusa do backend junto do campo que ela aponta.
- Q: A pré-visualização usa um desenho próprio do painel ou o SDK? → A: O renderizador real do
  SDK (`@pitaco/react-native/preview`) sobre react-native-web, carregado só no navegador. Um
  desenho próprio divergiria do app na primeira mudança de layout do SDK.
- Q: Prioridade, cota, isenção do descanso e aviso de texto livre abrem versão nova? → A: Não.
  São da pesquisa, não da versão: mudam com a pesquisa no ar, sem rascunho, e só a encerrada fica
  somente leitura.
- Q: Os avisos de publicação bloqueiam? → A: Não. Impedimento bloqueia; aviso informa. A tela diz
  isso com todas as letras.
- Q: Duplicar para outra aplicação leva o quê? → A: Perguntas, disparo, segmentação e exposição.
  Respostas e versões anteriores ficam com a original; a cópia nasce em rascunho e só pode ir
  para aplicação ativa.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Criar a pesquisa a partir de um modelo (Priority: P1)

Ao criar uma pesquisa, quem pesquisa escolhe de onde começar: em branco ou um dos três formatos
consagrados — NPS (0 a 10, recomendação), CSAT (satisfação de 1 a 5) e CES (esforço de 1 a 7).
Cada opção diz em uma linha o que coloca no rascunho. Escolhido um modelo, a pesquisa nasce com a
pergunta oficial daquele formato, já com a escala e os rótulos dos extremos.

**Why this priority**: NPS, CSAT e CES são a maior parte das pesquisas de produto. Montar a
pergunta oficial à mão é lento e erra a redação ou a escala, o que torna o número incomparável
com o mercado. Entrega valor sozinha.

**Independent Test**: criar uma pesquisa escolhendo o modelo NPS e conferir que a montagem abre
com uma pergunta de 0 a 10 e os rótulos "Nada provável … Extremamente provável".

**Acceptance Scenarios**:

1. **Given** a tela de nova pesquisa, **When** a abro, **Then** vejo as opções Em branco, NPS,
   CSAT e CES, cada uma com sua descrição, e Em branco vem marcada.
2. **Given** o modelo NPS escolhido, **When** crio a pesquisa, **Then** sou levado à montagem e
   ela tem exatamente uma pergunta, de 0 a 10, com os rótulos dos extremos.
3. **Given** Em branco escolhido, **When** crio a pesquisa, **Then** ela nasce sem perguntas,
   como em `001-painel-operacao-pesquisas`.
4. **Given** uma recusa do backend, **When** ela volta, **Then** o nome digitado e o modelo
   escolhido continuam no formulário.

---

### User Story 2 - Exibir uma pergunta só quando a resposta anterior pede (Priority: P1)

Ao montar uma pergunta, quem pesquisa pode ligar uma condição de exibição: escolhe uma pergunta
anterior como origem, como comparar (for igual a, for diferente de, for um destes, estiver na
faixa) e com o quê. Na lista, a pergunta condicionada mostra a condição em linguagem de gente:
"Exibida se P1 for "Não"". Uma condição que o backend considera impossível volta com o erro
junto do campo que a causou.

**Why this priority**: é o que transforma uma pesquisa genérica em conversa — "por que nota
baixa?" só para quem deu nota baixa. Sem condição, a pergunta de motivo aparece para todos e
derruba a taxa de conclusão.

**Independent Test**: com uma pergunta de escolha única "Gostou?" (Sim, Não), adicionar "O que
faltou?" condicionada a "Não" e conferir o resumo na lista; com uma pergunta NPS, tentar a faixa
de 0 a 11 e conferir o erro junto do campo de valores.

**Acceptance Scenarios**:

1. **Given** uma pesquisa sem pergunta anterior que sirva de origem, **When** abro o formulário,
   **Then** a condição explica por que não está disponível em vez de oferecer campos vazios.
2. **Given** perguntas anteriores, **When** ligo a condição, **Then** vejo origem, operador e
   valor; só perguntas anteriores e que não são de texto livre aparecem como origem.
3. **Given** uma origem de escolha, **When** escolho o valor, **Then** escolho entre as opções
   dela pelo rótulo; **Given** uma origem numérica (avaliação, escala, NPS), **Then** a faixa
   (mínimo e máximo) também é oferecida.
4. **Given** a condição montada, **When** salvo, **Then** a pergunta aparece na lista com o
   resumo legível da condição, nomeando a origem pela posição (P1, P2…).
5. **Given** uma faixa fora da escala da origem, **When** salvo, **Then** o backend recusa e o
   erro aparece junto do campo de valores, sem criar a pergunta e sem perder o que foi montado.
6. **Given** a condição ligada, **When** a desligo, **Then** nenhum campo dela é enviado.
7. **Given** uma pergunta condicionada, **When** a edito, **Then** a condição existente vem
   montada.

---

### User Story 3 - Reordenar arrastando e editar no lugar (Priority: P2)

Cada pergunta da lista tem uma alça de arrastar, que também funciona pelo teclado (espaço pega,
setas movem, espaço solta, Escape cancela), com anúncios para leitor de tela. As setas de subir e
descer continuam disponíveis. Editar uma pergunta abre o formulário dela no lugar da lista, já
preenchido; salvar ou cancelar devolve a lista.

**Why this priority**: em `001-painel-operacao-pesquisas` só havia setas, uma posição por clique.
Com cinco ou mais perguntas, reordenar virava uma sequência de cliques. Não bloqueia publicar, por
isso P2.

**Independent Test**: com três perguntas, arrastar a terceira para o topo pela alça operada por
teclado, recarregar e conferir a nova ordem; editar o enunciado de uma pergunta e conferir a
lista atualizada.

**Acceptance Scenarios**:

1. **Given** duas ou mais perguntas, **When** solto uma sobre outra, **Then** a lista mostra a
   ordem pretendida na hora e envia a permutação completa; recarregar mantém a ordem.
2. **Given** a alça focada, **When** opero pelo teclado, **Then** o leitor de tela anuncia o que
   foi pego, a posição corrente e onde foi solto.
3. **Given** uma reordenação em curso, **When** ela ainda não terminou, **Then** as alças ficam
   desabilitadas.
4. **Given** uma recusa do backend, **When** ela volta, **Then** a mensagem aparece e a lista volta
   à ordem do servidor.
5. **Given** soltar fora da lista ou no mesmo lugar, **When** solto, **Then** nada é enviado.
6. **Given** uma única pergunta, **When** vejo a lista, **Then** as setas não são oferecidas.
7. **Given** a ação Editar de uma pergunta, **When** a aciono, **Then** o formulário dela aparece
   preenchido; ao salvar, a lista volta com a alteração.

---

### User Story 4 - Ver a pesquisa como o app vai mostrá-la (Priority: P2)

Ao lado da montagem, uma pré-visualização mostra a pesquisa com o renderizador real do SDK, no
tema do painel (claro ou escuro). Dá para responder ali mesmo, e uma lista mostra os eventos de
interação que o SDK emitiria, cada um com seu detalhe. Enquanto uma pergunta é adicionada ou
editada, a pré-visualização mostra o rascunho dela, atualizado a cada alteração do formulário.
Cada pergunta da lista pode ser visualizada isoladamente.

**Why this priority**: elimina o ciclo "publicar, abrir o app, achar o erro, abrir rascunho". Não
é pré-requisito de publicar.

**Independent Test**: montar duas perguntas, interagir com a pré-visualização e conferir a lista
de eventos; começar a editar uma pergunta e conferir que a pré-visualização mostra o rascunho.

**Acceptance Scenarios**:

1. **Given** perguntas montadas, **When** abro a montagem, **Then** a pré-visualização mostra a
   pesquisa inteira na ordem da lista.
2. **Given** o formulário de pergunta aberto, **When** altero tipo, opções, condição ou texto,
   **Then** a pré-visualização mostra o rascunho daquela pergunta.
3. **Given** a pré-visualização, **When** interajo, **Then** cada evento emitido aparece na lista
   de eventos, do mais recente para o mais antigo, com tipo e dados; **When** limpo, **Then** a
   lista esvazia.
4. **Given** a ação Visualizar de uma pergunta, **When** a aciono, **Then** só ela aparece na
   pré-visualização, e "Ver todas no preview" restaura a pesquisa inteira.
5. **Given** a troca de tema do painel, **When** ela acontece, **Then** a pré-visualização segue o
   tema.
6. **Given** uma pesquisa sem perguntas, **When** abro a montagem, **Then** não há
   pré-visualização — só o convite a adicionar; **When** começo a adicionar, **Then** ela aparece.

---

### User Story 5 - Duplicar, renomear e descartar pelo cabeçalho (Priority: P2)

O cabeçalho da pesquisa reúne as ações sobre ela: renomear, duplicar e descartar (esta só
enquanto nunca publicada, como em `001-painel-operacao-pesquisas`). Duplicar abre um diálogo em
que se escolhe a aplicação de destino — a própria vem marcada como "(esta)" — e, opcionalmente,
o nome da cópia. A cópia nasce em rascunho e a tela seguinte é a montagem dela, sob a aplicação
de destino.

**Why this priority**: o caso comum é rodar em outro app a pesquisa que deu certo neste. Sem
duplicar, a pesquisa é refeita à mão, com risco de divergir.

**Independent Test**: com duas aplicações, duplicar uma pesquisa com uma pergunta para a outra e
conferir que a montagem aberta é da aplicação de destino, com "Cópia de …", estado Rascunho e a
mesma pergunta.

**Acceptance Scenarios**:

1. **Given** uma pesquisa, **When** abro Duplicar, **Then** vejo as aplicações ativas como
   destino e o aviso de que respostas e versões anteriores ficam com a original.
2. **Given** o nome em branco, **When** duplico, **Then** a cópia se chama "Cópia de" seguido do
   nome atual.
3. **Given** outra aplicação escolhida, **When** duplico, **Then** sou levado à montagem da cópia
   sob a aplicação de destino, em rascunho, com as mesmas perguntas.
4. **Given** uma recusa, **When** ela volta, **Then** o destino escolhido e o nome digitado
   continuam no diálogo, e nada navega.
5. **Given** uma pesquisa encerrada, **When** vejo o cabeçalho, **Then** renomear não é oferecido.

---

### User Story 6 - Controlar a exposição e o aviso de texto livre (Priority: P2)

Na tela de disparo, um painel de Exposição mostra e edita a prioridade (de −100 a 100, maior
vence o desempate), a cota de respostas (atingida, a pesquisa encerra sozinha) com o progresso
"Concluídas: X de N", e a isenção do intervalo de descanso da aplicação, com a explicação do que
ela custa. A política de descanso da aplicação e a regra de desempate ficam escritas ali. Um
painel de Aviso de dado pessoal liga, desliga e personaliza o lembrete que o SDK mostra junto dos
campos de texto livre.

**Why this priority**: sem prioridade e cota, duas pesquisas no mesmo evento disputam às cegas e
uma pesquisa com amostra suficiente continua incomodando usuários. Não bloqueia publicar.

**Independent Test**: definir prioridade 7, cota 50 e isenção, salvar, recarregar e conferir os
valores e "Concluídas: 0 de 50"; limpar a cota e conferir "Sem cota"; personalizar e depois
desligar o aviso de texto livre, recarregar e conferir.

**Acceptance Scenarios**:

1. **Given** uma pesquisa sem cota, **When** abro o disparo, **Then** vejo "Sem cota", nunca zero,
   e a regra de desempate escrita.
2. **Given** prioridade, cota e isenção informadas, **When** salvo, **Then** o painel mostra os
   novos valores e o progresso rumo à cota.
3. **Given** a cota apagada, **When** salvo, **Then** a cota é removida (não mantida).
4. **Given** prioridade fora de −100 a 100 ou cota menor que 1, **When** salvo, **Then** a recusa
   aparece no campo, sem chamar a API.
5. **Given** a isenção marcada, **When** a vejo, **Then** a explicação diz que a pesquisa gasta a
   paciência reservada às demais; desmarcada, diz que respeita o intervalo.
6. **Given** a cota atingida pela coleta, **When** abro a pesquisa, **Then** ela está Encerrada e
   o histórico registra "Encerrada por cota".
7. **Given** o aviso de texto livre, **When** o abro, **Then** vejo o texto que o respondente vê;
   **When** personalizo e salvo, **Then** o novo texto passa a ser o exibido; **When** deixo o
   texto em branco, **Then** volta ao padrão; **When** desligo, **Then** a tela diz que os campos
   aparecem sem aviso.
8. **Given** uma pesquisa sem pergunta de texto livre, **When** vejo o aviso, **Then** a tela
   explica que ele só aparece quando houver uma.
9. **Given** uma pesquisa encerrada, **When** abro o disparo, **Then** exposição e aviso aparecem
   sem formulário.

---

### User Story 7 - Escolher evento e atributo entre os que o app já enviou (Priority: P3)

Ao definir o evento de disparo, o painel sugere os eventos que o SDK da aplicação já consultou;
ao montar uma regra, sugere os atributos que o app já enviou e, escolhido um atributo, os valores
vistos dele. Clicar numa sugestão preenche o campo; digitar livremente continua valendo, porque o
evento pode ainda não ter sido visto.

**Why this priority**: evita o erro mais comum — `checkout_completed` no painel e
`checkout.completed` no app — mas a digitação livre já resolvia o caso sem catálogo.

**Independent Test**: com eventos e atributos já vistos, clicar numa sugestão de evento, depois
num atributo e num valor, e conferir que os campos foram preenchidos e a regra criada.

**Acceptance Scenarios**:

1. **Given** eventos já vistos, **When** abro o formulário de disparo, **Then** vejo as sugestões
   e clicar numa preenche o nome do evento.
2. **Given** nenhum evento visto, **When** abro o formulário, **Then** uma frase explica a ausência
   e o campo aceita o nome digitado.
3. **Given** atributos já vistos, **When** clico num atributo, **Then** o campo é preenchido e os
   valores vistos dele aparecem como sugestão.
4. **Given** nenhum atributo visto, **When** monto uma regra, **Then** uma frase explica e a
   digitação continua aceita.

---

### User Story 8 - Publicar sabendo o que muda (Priority: P2)

A tela de publicação mostra, além dos impedimentos de `001-painel-operacao-pesquisas`, os avisos
que não bloqueiam: outras pesquisas no ar escutam o mesmo evento (com nome, prioridade e
vínculo para cada uma), uma regra exige atributo ou valor que o app nunca enviou, regras se
contradizem, ou a pesquisa exige um SDK mais novo que o da maior parte do tráfego. Os mesmos
avisos aparecem na tela de disparo. A partir da versão 2, quem publica classifica a mudança —
cosmética (respostas continuam comparáveis) ou semântica (abre um novo grupo de
comparabilidade) — e pode resumi-la; cada opção diz a sua consequência. A listagem de versões
mostra a natureza e o grupo de cada uma. Abrir um rascunho de versão leva direto à montagem.

**Why this priority**: publicar sem ver a concorrência ou sem classificar a mudança produz
resultado enganoso depois. É o que torna a soma de versões nos resultados honesta.

**Independent Test**: publicar a versão 1, abrir rascunho, mudar uma opção, tentar publicar sem
natureza (recusa no campo), publicar como semântica e conferir as duas versões, a natureza na
listagem e o Grupo 2 na comparabilidade.

**Acceptance Scenarios**:

1. **Given** outra pesquisa no ar no mesmo evento, **When** abro a publicação, **Then** vejo o
   aviso com a pesquisa concorrente, sua prioridade, a regra de desempate, e publicar continua
   liberado.
2. **Given** uma regra sobre valor nunca visto, **When** abro a publicação, **Then** vejo o aviso
   nomeando o atributo, e nenhum impedimento novo.
3. **Given** a maior parte do tráfego recente em SDK anterior à versão mínima da pesquisa,
   **When** abro a publicação, **Then** o aviso diz a versão mínima e a proporção sem suporte.
4. **Given** a versão 2 ou seguinte, **When** abro a escolha da natureza, **Then** cada opção diz
   o que acontece com a comparabilidade ("Cosmética — respostas continuam comparáveis",
   "Semântica — abre um novo grupo de comparabilidade").
5. **Given** a natureza não escolhida, **When** publico, **Then** a recusa aparece no campo e o
   resumo digitado é preservado.
6. **Given** a natureza semântica, **When** publico, **Then** a versão aparece na listagem com a
   natureza e o grupo, e o painel de comparabilidade mostra o Grupo 2.
7. **Given** uma pesquisa publicada sem rascunho, **When** abro um rascunho de versão, **Then**
   sou levado à montagem, já editável.
8. **Given** duas versões em grupos diferentes com coleta nas duas, **When** abro os resultados
   consolidados, **Then** a pergunta que mudou avisa que somar as versões pode enganar — a leitura
   é de `004-painel-resultados-comportamento`, a separação é feita aqui.

---

### Edge Cases

- **Origem que saiu da lista**: uma condição cuja origem não existe mais na lista exibida não
  quebra o resumo — ele diz "a pergunta de origem".
- **Múltipla escolha como origem**: o resumo fala em "incluir" em vez de "for".
- **Pergunta NPS com rótulo**: a faixa só viaja quando há rótulo, e sempre como 0 a 10.
- **Rótulo dos extremos**: até 60 caracteres; acima disso, recusa no campo.
- **Opção pela metade**: rótulo sem valor usa o rótulo como valor; valor sem rótulo é recusado.
- **Arrastar durante outra reordenação**: as alças ficam desabilitadas até a resposta.
- **Pré-visualização no servidor**: o renderizador não roda na renderização do servidor; a área
  aparece vazia até o navegador carregar o módulo.
- **Duplicar para aplicação inativa**: não é oferecido; a lista de destinos só traz ativas, mais a
  própria aplicação.
- **Cota ausente no progresso**: "Sem cota", nunca "0 de 0".
- **Aviso de código desconhecido**: um aviso novo do backend aparece com mensagem genérica em vez
  de quebrar a tela.
- **Leitura auxiliar do disparo indisponível**: se catálogo, aplicação, progresso de cota ou
  avisos falharem, a tela de disparo cai no estado de falha com nova tentativa.
- **Desligar o aviso**: o checkbox desmarcado precisa chegar como `false`; a isenção do descanso
  segue a mesma regra.

## Requirements *(mandatory)*

### Functional Requirements

**Modelos**

- **FR-001**: A criação de pesquisa MUST oferecer começar em branco ou por um dos modelos NPS,
  CSAT e CES, cada um com descrição de uma linha, com Em branco marcado por padrão.
- **FR-002**: Escolher um modelo MUST enviar o modelo ao backend; Em branco MUST enviar só o nome.
- **FR-003**: Modelo desconhecido MUST ser recusado antes do envio.

**Condição de exibição**

- **FR-004**: O formulário de pergunta MUST permitir ligar e desligar uma condição de exibição;
  desligada, nenhum campo da condição MUST ser enviado.
- **FR-005**: As origens oferecidas MUST ser apenas perguntas anteriores à pergunta montada, que
  não sejam de texto livre e que não sejam ela mesma; para pergunta nova, todas as existentes.
- **FR-006**: Os operadores oferecidos MUST ser igual, diferente e conjunto para toda origem, e
  faixa apenas para origens numéricas (avaliação, escala, NPS).
- **FR-007**: O painel MUST validar a forma da condição antes do envio — operador escolhido, ao
  menos um valor, um único valor em igual e diferente, mínimo e máximo na faixa — apontando o
  campo; a adequação do valor à origem MUST ser decidida pelo backend.
- **FR-008**: A recusa do backend que aponte um campo da condição MUST aparecer junto do campo
  correspondente, preservando a condição montada.
- **FR-009**: A lista de perguntas MUST mostrar, na pergunta condicionada, o resumo legível da
  condição, nomeando a origem pela posição e a opção pelo rótulo.
- **FR-010**: O formulário MUST oferecer rótulos para os extremos em avaliação, escala e NPS.

**Reordenação e edição**

- **FR-011**: A lista MUST permitir reordenar por arrastar e soltar, com alça operável por
  teclado e anúncios acessíveis, além das setas de `001-painel-operacao-pesquisas`.
- **FR-012**: Soltar MUST enviar a permutação completa e mostrar a ordem pretendida até a
  resposta; recusa MUST devolver a ordem do servidor e exibir o motivo.
- **FR-013**: Soltar fora da lista ou sobre a própria pergunta MUST NOT enviar nada.
- **FR-014**: Editar uma pergunta MUST abrir o formulário preenchido na coluna da montagem, no
  lugar da lista; concluir MUST devolver a lista.

**Pré-visualização**

- **FR-015**: A montagem MUST exibir uma pré-visualização com o renderizador do SDK, no tema
  corrente do painel, carregada apenas no navegador.
- **FR-016**: A pré-visualização MUST refletir o rascunho da pergunta em edição a cada alteração
  do formulário, e a pesquisa inteira quando nada está em edição.
- **FR-017**: A pré-visualização MUST listar os eventos de interação emitidos, do mais recente
  para o mais antigo, com ação de limpar.
- **FR-018**: Cada pergunta MUST poder ser visualizada isoladamente, com ação de voltar a ver todas.

**Ações da pesquisa**

- **FR-019**: O cabeçalho MUST oferecer duplicar, com escolha da aplicação de destino entre as
  ativas e nome opcional; em branco, o backend nomeia "Cópia de …".
- **FR-020**: Duplicar com sucesso MUST levar à montagem da cópia sob a aplicação de destino.
- **FR-021**: Uma pesquisa encerrada MUST NOT oferecer renomear.

**Exposição e aviso de texto livre**

- **FR-022**: A tela de disparo MUST exibir e permitir editar prioridade (−100 a 100), cota de
  respostas (inteiro ≥ 1, em branco remove) e isenção do intervalo de descanso.
- **FR-023**: A tela MUST exibir o progresso rumo à cota como "Concluídas: X de N", e "Sem cota"
  quando não houver cota.
- **FR-024**: A tela MUST exibir a política de descanso da aplicação e a regra de desempate.
- **FR-025**: A isenção MUST ser explicada ao ser ligada e ao ser desligada, e desmarcar MUST
  chegar ao backend como `false`.
- **FR-026**: A tela de disparo MUST exibir o aviso de dado pessoal do texto livre com o texto
  exibido ao respondente, permitir ligá-lo, desligá-lo e personalizá-lo (até 200 caracteres), e
  texto em branco MUST voltar ao padrão.
- **FR-027**: Sem pergunta de texto livre, a tela MUST explicar que o aviso ainda não aparece.
- **FR-028**: Exposição e aviso MUST mudar sem abrir versão e MUST ficar somente leitura apenas na
  pesquisa encerrada.

**Catálogo observado**

- **FR-029**: O formulário de disparo MUST sugerir os eventos já vistos na aplicação, e o de regra
  MUST sugerir os atributos já vistos e os valores vistos do atributo escolhido.
- **FR-030**: Sugestão MUST preencher o campo ao ser clicada, e a digitação livre MUST continuar
  aceita; sem catálogo, a tela MUST explicar a ausência.

**Publicação**

- **FR-031**: As telas de publicação e de disparo MUST exibir os avisos de publicação sem
  bloquear a ação de publicar, dizendo isso explicitamente na publicação.
- **FR-032**: O aviso de concorrência MUST listar cada pesquisa concorrente com nome, prioridade e
  vínculo para o disparo dela, junto da regra de desempate.
- **FR-033**: O aviso de compatibilidade MUST dizer a versão mínima do SDK e a proporção do
  tráfego recente sem suporte.
- **FR-034**: A escolha da natureza da mudança, exigida a partir da versão 2 desde
  `001-painel-operacao-pesquisas`, MUST dizer a consequência de cada opção — cosmética mantém as
  respostas comparáveis, semântica abre um novo grupo de comparabilidade — e a recusa do backend
  a uma mudança declarada cosmética que não é MUST aparecer no formulário, preservando o resumo.
- **FR-035**: Abrir um rascunho de versão MUST levar diretamente à montagem, já editável.
- **FR-036**: A listagem de versões MUST mostrar a natureza da mudança e o grupo de
  comparabilidade de cada versão, para que a separação feita na publicação seja legível depois.

### Key Entities

- **Modelo de pesquisa**: o formato consagrado de onde a pesquisa partiu (`nps`, `csat`, `ces`).
  Ausente é "em branco". Guardado na pesquisa, não na versão.
- **Condição de exibição**: origem (chave estável de uma pergunta anterior), operador (`equals`,
  `not_equals`, `in`, `between`), valores ou faixa. Pertence à pergunta, e portanto à versão.
- **Exposição**: prioridade, cota de respostas e isenção do descanso. Pertence à pesquisa.
- **Progresso da cota**: concluídas rumo à cota, apurado pela coleta.
- **Aviso de texto livre**: ligado ou não, texto personalizado opcional, texto exibido e texto
  padrão. Pertence à pesquisa.
- **Catálogo observado**: eventos e atributos (com valores) que o app já enviou. Sugestão, nunca
  restrição.
- **Aviso de publicação**: código, atributo ou regra envolvidos, pesquisas concorrentes, versão
  mínima e proporção sem suporte. Informa; não bloqueia.
- **Natureza da mudança**: cosmética (mesmo grupo de comparabilidade) ou semântica (grupo novo).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Uma pesquisa de NPS com a pergunta oficial fica pronta para configurar o disparo em
  no máximo 3 interações a partir da listagem de pesquisas (nova, nome e modelo, criar).
- **SC-002**: 100% das recusas de condição apontadas pelo backend aparecem junto do campo que as
  causou, verificado por teste automatizado e ponta a ponta.
- **SC-003**: Reordenar qualquer pergunta para qualquer posição custa uma única operação de
  arrastar, por ponteiro ou teclado.
- **SC-004**: A pré-visualização usa o mesmo renderizador do app: nenhuma pergunta é desenhada por
  código do painel.
- **SC-005**: Nenhuma tela desta feature exibe cota ausente como zero.
- **SC-006**: 100% dos avisos de publicação coexistem com o botão de publicar habilitado quando
  não há impedimento, verificado ponta a ponta.
- **SC-007**: Os fluxos de criar por modelo, duplicar para outra aplicação, montar condição,
  recusa de condição, exposição, catálogo, avisos, classificação da mudança e encerramento por
  cota estão cobertos por teste ponta a ponta.

## Assumptions

- Modelos, condição, duplicação, exposição, aviso de texto livre, catálogo observado, progresso
  de cota e avisos de publicação já estão disponíveis na API administrativa; o painel não
  antecipa nada além disso.
- Toda invariante de domínio (valor dentro da escala da origem, verificação de que uma mudança
  "cosmética" de fato é cosmética, limite de prioridade) é decidida pelo backend; o painel valida
  forma para encurtar o laço de feedback.
- O SDK publica um módulo de pré-visualização para a web; o painel o consome como dependência
  local do monorepo, e o tema do app real pode diferir do tema padrão mostrado.
- A lista de destinos de duplicação cabe em uma página de 100 aplicações ativas.
- O catálogo de eventos e de atributos de um app é pequeno por natureza; a maior página da API
  basta para as sugestões.
- Convenções de 001 continuam valendo: Server Components para leitura, Server Actions para
  escrita, resultado tipado, estado de tela na URL, rotas em português.

## Dependencies

- `001-painel-operacao-pesquisas`: montagem, disparo, regras, publicação, versões, ciclo de vida,
  componentes compartilhados e o modelo de formulário.
- `002-painel-leitura-coleta`: semeadura de coleta no servidor de API simulado, usada pelo E2E de
  encerramento por cota e de comparabilidade.
- `backend 003-survey-authoring` e a evolução da autoria no backend (modelos, condição,
  duplicação, exposição, aviso de texto livre, avisos de publicação, catálogo observado).
- `backend 004-response-collection`: a coleta que alimenta o progresso da cota e o catálogo.
- SDK `@pitaco/react-native`, módulo `preview`.
