# Feature Specification: Demo completa com dados fictícios

**Feature Branch**: `002-demo-seed`

**Created**: 2026-10-01

**Status**: Implemented

**Input**: User description: "Quero uma demo completa pra mostrar o painel funcionando de verdade, com dado fictício, que eu carregue em segundos no servidor sem ficar martelando a API."

## Contexto

O painel do Pitaco mostra muita coisa: visão geral com tendência e segmentos, resultados de todos
os tipos de pergunta, comportamento (funil, tempo, dispensas, atrito), versões, ciclo de vida das
pesquisas, saúde do SDK e privacidade. Numa instalação nova, tudo isso está vazio — e um painel
vazio não demonstra nada. Para mostrar o produto, avaliar uma mudança de tela ou tirar uma
captura, é preciso um conjunto de dados grande, variado e plausível, com padrões que a análise
consiga encontrar.

Esta feature entrega esse conjunto e o jeito de carregá-lo. Três fronteiras desenham o escopo:

- **Não é o seed do app de exemplo do SDK.** Aquele cria, pela API, uma aplicação mínima com
  chave e pesquisas para o app de exemplo disparar de verdade. Esta demo é para o painel ler, não
  para o SDK coletar.
- **Não é schema nem migration.** A demo é só dados; o schema continua nascendo das migrations da
  API.
- **Não é dado real.** Toda aplicação, pessoa, resposta e identificador é fictício.

## Clarifications

### Session 2026-10-01

- Q: A demo é gerada na hora, pela API, ou carregada pronta? → A: carregada pronta. Um dump só de
  dados, versionado no repositório, carregado direto no banco. Gerar pela API levava minutos,
  dependia da API estar de pé e esbarrava no limite de requisições por origem fora da máquina
  local.
- Q: O que a carga pode tocar no banco? → A: só as aplicações cujo identificador legível começa
  com `demo-completo`, e tudo o que pertence a elas. Nada mais.
- Q: E se a demo já estiver carregada? → A: a carga recusa. Substituir é um pedido explícito, que
  apaga a demo anterior e carrega de novo.
- Q: As datas da demo são fixas? → A: não. São relativas: a cada carga, a exibição mais recente
  fica a uma hora de agora, e há um modo só para renovar as datas sem recarregar.
- Q: A API precisa estar no ar para carregar? → A: não. Precisa ter subido ao menos uma vez, para
  as migrations terem criado o schema.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Carregar a demo em segundos (Priority: P1)

Quem hospeda ou desenvolve quer, com um comando na raiz do repositório e a stack no ar, ver o
painel cheio. A carga vai direto no banco da stack, numa transação só, sem passar pela API e sem
porta publicada; termina dizendo quantas aplicações e exibições da demo existem e onde abrir.

**Why this priority**: é a razão de existir da feature. Sem a carga, o conjunto de dados é um
arquivo inerte.

**Independent Test**: com a stack no ar e as migrations aplicadas, rodar a carga e conferir o
resumo (três aplicações, cerca de quatro mil exibições) e, no painel, a aplicação **Loja Aurora**
com dados.

**Acceptance Scenarios**:

1. **Given** a stack no ar com o schema criado e sem demo, **When** rodo a carga, **Then** a demo
   é carregada em poucos segundos e o resumo informa as aplicações e exibições carregadas.
2. **Given** um banco sem o schema do Pitaco, **When** rodo a carga, **Then** ela recusa sem
   alterar nada e diz para subir a API uma vez.
3. **Given** a demo já carregada, **When** rodo a carga sem pedir substituição, **Then** ela recusa,
   informa quantas aplicações da demo existem e indica como recarregar.
4. **Given** um erro no meio da carga, **When** ela falha, **Then** nada da carga fica no banco.
5. **Given** aplicações reais no mesmo banco, **When** carrego a demo, **Then** nenhuma linha delas
   é alterada.
6. **Given** um argumento desconhecido, **When** rodo o comando, **Then** recebo a forma de uso e
   nada é executado.

---

### User Story 2 - Ver todas as telas do painel com dados plausíveis (Priority: P1)

Quem abre o painel depois da carga precisa encontrar dado em cada tela, nos estados que a tela
sabe mostrar: aplicação ativa e desativada, chave ativa e revogada, pesquisa em rascunho,
publicada, pausada, encerrada manualmente e encerrada por cota, pesquisa com duas versões, todos
os tipos de pergunta, exibições respondidas, dispensadas e abandonadas, eventos de interação,
segmentos com diferenças que a análise consegue achar, saúde do SDK com versões, supressões e
erros, e privacidade com exclusões auditadas.

**Why this priority**: carregar dados que deixam meia tela vazia não demonstra o produto. Está no
mesmo nível da história 1 porque as duas juntas são o mínimo que entrega valor.

**Independent Test**: depois da carga, percorrer as telas da aplicação **Loja Aurora** e de suas
pesquisas e conferir que nenhuma está vazia e que os padrões declarados aparecem.

**Acceptance Scenarios**:

1. **Given** a demo carregada, **When** abro a lista de aplicações, **Then** vejo **Loja Aurora**,
   **Aurora Entregas** e **Aurora Legado**, esta última desativada.
2. **Given** a Loja Aurora, **When** abro as chaves, **Then** vejo chaves ativas e uma revogada.
3. **Given** a Loja Aurora, **When** abro as pesquisas, **Then** encontro uma em cada estado do
   ciclo de vida — rascunho, publicada, pausada, encerrada manualmente e encerrada por cota.
4. **Given** a pesquisa de NPS, **When** abro as versões, **Then** ela tem duas versões
   publicadas e perguntas condicionais.
5. **Given** a pesquisa de checkout, **When** abro os resultados, **Then** há dados para todos os
   tipos de pergunta, com amostragem parcial e segmentação.
6. **Given** a visão geral e a análise por segmento, **When** comparo planos e plataformas,
   **Then** o NPS é maior no plano premium e menor no gratuito, a plataforma Android concentra
   reclamações de estabilidade e o plano gratuito, de frete.
7. **Given** a saúde do SDK, **When** a abro, **Then** vejo três versões do SDK, supressões e
   erros de cinco tipos.
8. **Given** a privacidade, **When** a abro, **Then** vejo três exclusões de titular auditadas.
9. **Given** a pesquisa de checkout, **When** abro o comportamento, **Then** o funil, os tempos,
   as dispensas e o atrito têm dados.

---

### User Story 3 - Manter os gráficos recentes cheios (Priority: P2)

Os gráficos do painel olham para "últimos 7 dias" e "últimos 30 dias". Com o tempo, uma demo de
datas fixas envelhece e os gráficos esvaziam. A cada carga, as datas da demo são deslocadas para
perto de agora, e um modo próprio renova as datas de uma demo já carregada, sem recarregá-la.

**Why this priority**: sem isso, a demo funciona por algumas semanas e depois parece quebrada.
Vem depois das duas primeiras porque a demo recém-carregada já está com as datas certas.

**Independent Test**: carregar a demo, conferir que a exibição mais recente está a cerca de uma
hora de agora; mais tarde, rodar a renovação e conferir de novo, com as distâncias entre os
eventos preservadas.

**Acceptance Scenarios**:

1. **Given** uma carga nova, **When** ela termina, **Then** a exibição mais recente da demo está a
   uma hora de agora e os gráficos de 7 e 30 dias têm dados.
2. **Given** a demo carregada há semanas, **When** rodo a renovação das datas, **Then** todos os
   instantes da demo andam o mesmo intervalo e a exibição mais recente volta a ficar a uma hora de
   agora.
3. **Given** uma exibição que durou três minutos, **When** as datas são renovadas, **Then** ela
   continua durando três minutos.
4. **Given** nenhuma demo carregada, **When** peço a renovação, **Then** ela recusa dizendo que
   não há demo.
5. **Given** a renovação rodada duas vezes seguidas, **When** confiro, **Then** o resultado é o
   mesmo de rodar uma vez.

---

### User Story 4 - Recarregar a demo do zero (Priority: P3)

Depois de mexer na demo pelo painel — encerrar uma pesquisa, revogar uma chave, excluir um
titular —, quem demonstra quer voltar ao estado original sem tocar em mais nada.

**Why this priority**: é conveniência. A demo já é útil sem ela, e quem não mexe nos dados não
precisa.

**Independent Test**: carregar, alterar algo da demo pelo painel, recarregar com substituição e
conferir que o estado original voltou, com as mesmas contagens, e que aplicações fora da demo não
mudaram.

**Acceptance Scenarios**:

1. **Given** a demo carregada e alterada, **When** recarrego com substituição, **Then** a demo
   anterior é apagada por inteiro e a original é carregada, na mesma transação.
2. **Given** a substituição rodada várias vezes, **When** confiro as contagens, **Then** são sempre
   as mesmas.
3. **Given** nenhuma demo carregada, **When** peço a substituição, **Then** ela simplesmente
   carrega.

---

### Edge Cases

- **Migration futura que muda uma tabela da demo**: a carga falha por inteiro, porque é uma
  transação só, e o banco fica como estava. O dump precisa ser regerado.
- **Pergunta condicional que referencia outra pergunta da mesma tabela**: a ordem das linhas no
  dump importaria com as restrições de integridade ativas; elas são suspensas só durante a cópia,
  dentro da transação, e voltam antes de a transação terminar. A limpeza que precede a cópia roda
  com as restrições ativas.
- **Exibições sem desfecho**: aparecem como abandonadas no painel, porque abandono é derivado na
  leitura depois de 30 minutos — a demo grava só o que a coleta grava.
- **Chaves da demo**: existem só como representação irreversível do segredo; nenhuma serve para um
  SDK coletar. A demo é para o painel ler.
- **Descarte automático por prazo**: os prazos de retenção configurados na demo são maiores que o
  período coberto, então o descarte diário não apaga a demo recém-carregada ou renovada. Uma demo
  esquecida por meses sem renovação envelhece, e os expurgos de saúde do SDK passam a apagar o que
  passou do prazo deles.
- **Uso diário do SDK**: é contado por dia; a renovação o desloca em dias inteiros, mantendo a
  série.
- **Seed do app de exemplo no mesmo banco**: usa outra aplicação, fora do prefixo da demo, e não é
  tocado pela carga nem pela substituição.
- **Banco fora da stack**: a carga fala com o banco pelo container da stack de produção. Outro
  banco exige aplicar os mesmos arquivos manualmente, na mesma ordem e numa transação.

## Requirements *(mandatory)*

### Functional Requirements

**Conjunto de dados**

- **FR-001**: A demo MUST ser um arquivo de dados pronto, versionado no repositório e comprimido,
  contendo só dados — nenhum objeto de schema — e só das aplicações da demo e do que pertence a
  elas.
- **FR-002**: A demo MUST conter três aplicações (uma desativada), cinco chaves (uma revogada) e
  nove pesquisas cobrindo rascunho, publicada, pausada, encerrada manualmente e encerrada por cota.
- **FR-003**: A demo MUST conter uma pesquisa com duas versões publicadas e perguntas
  condicionais, uma pesquisa com todos os tipos de pergunta, amostragem parcial e segmentação, e
  pesquisas segmentadas por plataforma e por país.
- **FR-004**: A demo MUST conter cerca de quatro mil exibições espalhadas por 88 dias, nos três
  desfechos gravados pela coleta, com respostas, instantâneos de atributos e cerca de quarenta mil
  eventos de interação.
- **FR-005**: As respostas MUST carregar padrões detectáveis por segmento: NPS maior no plano
  premium e menor no gratuito, reclamação de estabilidade concentrada no Android e de frete no
  plano gratuito.
- **FR-006**: A demo MUST conter dados de saúde do SDK — uso de três versões, supressões e erros
  de cinco tipos — e três exclusões de titular auditadas.
- **FR-007**: Todo dado da demo MUST ser fictício; chaves de acesso MUST aparecer só na
  representação irreversível do segredo, nunca em texto claro.

**Carga**

- **FR-008**: A carga MUST ser um comando único, executado na raiz do repositório, que fala com o
  banco pelo container da stack, sem passar pela API e sem exigir porta publicada.
- **FR-009**: Antes de qualquer escrita, a carga MUST verificar que o schema do Pitaco existe e,
  sem ele, recusar com instrução para subir a API uma vez.
- **FR-010**: Com a demo já presente, a carga MUST recusar sem alterar nada, informar quantas
  aplicações da demo existem e indicar como substituir.
- **FR-011**: Toda escrita de uma carga — limpeza, cópia e ajuste de datas — MUST acontecer numa
  única transação; qualquer falha MUST deixar o banco como estava.
- **FR-012**: Carga, substituição e renovação MUST alterar apenas linhas das aplicações cujo
  identificador legível começa com `demo-completo` e linhas que pertencem a elas.
- **FR-013**: A substituição MUST apagar a demo anterior por inteiro e carregar a original na mesma
  transação; sem demo anterior, MUST simplesmente carregar.
- **FR-014**: Ao terminar, a carga MUST informar a quantidade de aplicações e de exibições da demo
  e onde abri-la no painel.
- **FR-015**: Um argumento desconhecido MUST produzir a forma de uso, sem executar nada.

**Datas**

- **FR-016**: Toda carga MUST deslocar todos os instantes da demo pelo mesmo intervalo, de modo que
  a exibição mais recente fique uma hora antes do momento da carga.
- **FR-017**: O deslocamento MUST preservar a distância entre quaisquer dois instantes da demo.
- **FR-018**: Um modo próprio MUST renovar as datas de uma demo já carregada, numa transação, sem
  recarregá-la; sem demo, MUST recusar.
- **FR-019**: A renovação MUST ser idempotente no curto prazo: rodar duas vezes seguidas produz o
  mesmo estado que rodar uma.
- **FR-020**: Contagens por dia MUST ser deslocadas em dias inteiros.

**Documentação**

- **FR-021**: O repositório MUST trazer, ao lado dos arquivos da demo, um guia com o que a demo
  contém, como carregar, substituir e renovar, e o que fazer quando o schema mudar.

### Key Entities

- **Demo**: o conjunto de aplicações `demo-completo*` e tudo o que pertence a elas — chaves,
  pesquisas, versões, perguntas, regras, exibições, respostas, eventos, respondentes, atributos e
  eventos observados, dados de saúde do SDK e de privacidade.
- **Dump da demo**: o arquivo comprimido com os dados, uma cópia por tabela, sem schema.
- **Deslocamento**: o intervalo único somado a todos os instantes da demo numa carga ou renovação,
  calculado a partir da exibição mais recente.
- **Carga**: a execução que limpa (quando pedido), copia e desloca, numa transação.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com a stack no ar, a demo completa carrega em menos de um minuto.
- **SC-002**: Depois da carga, 100% das telas de aplicação, pesquisa, resultados, comportamento,
  versões, ciclo de vida, saúde do SDK e privacidade da Loja Aurora mostram dados.
- **SC-003**: Uma carga, substituição ou renovação altera zero linhas de aplicações fora da demo —
  verificável por contagem antes e depois.
- **SC-004**: Uma carga que falha deixa o banco idêntico ao anterior.
- **SC-005**: Logo depois de uma carga ou renovação, a exibição mais recente da demo está a uma hora
  de agora, e os gráficos de 7 e de 30 dias têm dados.
- **SC-006**: N substituições seguidas resultam sempre nas mesmas contagens de aplicações,
  pesquisas, exibições e respostas da demo.

## Assumptions

- **A stack é a da operação.** A carga usa o serviço de banco da composição da raiz; vale para a
  stack de produção e para a stack inteira na máquina local.
- **O schema é o das migrations atuais.** O dump casa com ele; uma migration que mude uma tabela
  da demo exige regerar o dump.
- **Os dados foram gerados pelas rotas reais.** O conjunto nasceu passando pelas mesmas rotas que
  o painel e o SDK usam, com as validações do backend, e foi então exportado; a carga não repete
  essas validações, porque o dado já passou por elas.
- **Uma hora antes de agora é o ponto certo.** Perto o bastante para os gráficos de 7 e 30 dias
  ficarem cheios, longe o bastante para nenhuma exibição parecer estar acontecendo agora.
- **Quem carrega tem acesso ao Docker do servidor.** A carga não tem autenticação própria; quem
  pode rodar comandos no container do banco já pode tudo.

## Dependencies

- `ops 001-deploy-observabilidade-backup`: a stack e o serviço de banco onde a demo é carregada.
- As specs do backend — em especial `backend 003-survey-authoring`, `backend 004-response-collection`,
  `backend 008-exposure-control` e `backend 009-advanced-authoring` — e as migrations do backend: o
  schema que o dump preenche e as regras pelas quais os dados foram gerados.
- As specs do painel, de `painel 001-painel-operacao-pesquisas` a
  `painel 005-painel-saude-privacidade`: as telas que a demo preenche — autoria, coleta,
  resultados e comportamento, saúde do SDK e privacidade.
- `sdk 003-sdk-eventos-interacao`: o catálogo de eventos de interação que a demo traz gravado.
- O seed do app de exemplo do SDK (`sdk/example/scripts/seed.ts`): convive com a demo no mesmo
  banco, numa aplicação fora do prefixo; não é parte desta feature.
