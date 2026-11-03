# Feature Specification: Operação — implantação auto-hospedada, observabilidade e backup

**Feature Branch**: `001-deploy-observabilidade-backup`

**Created**: 2026-09-08

**Status**: Implemented

**Input**: User description: "Quero colocar o Pitaco no ar no meu próprio servidor, tudo em container, sem abrir porta, conseguindo ver o que tá acontecendo e sem correr o risco de perder os dados."

## Contexto

O Pitaco é três partes — a API (backend), o painel e o SDK — e até aqui só existiam como
código e como ambiente de desenvolvimento. Nada dizia como a API e o painel rodam de verdade,
onde o banco guarda os dados, como se enxerga o que acontece em produção, nem o que fazer quando
o disco morre.

O alvo é quem **auto-hospeda**: um servidor próprio com Docker, sem orquestrador, sem registro de
imagens e sem pipeline de entrega contínua. O tráfego chega por um **túnel reverso** (ex.:
Cloudflare Tunnel), e nenhuma porta do servidor fica aberta para a internet. A observabilidade e
o túnel são infraestrutura compartilhada da máquina, que outros projetos também usam; o Pitaco se
conecta a elas, não as traz.

Esta feature entrega a operação, e só ela. Quatro fronteiras desenham o escopo:

- **Não é orquestração nem entrega contínua.** As imagens são construídas no próprio servidor, a
  partir do código clonado. Publicar é atualizar o clone e reconstruir.
- **Não é autenticação de usuário.** A superfície administrativa da API e o painel ainda não têm
  login; quem protege essa superfície é a camada do túnel, configurada por quem hospeda.
- **Não inclui o túnel nem a stack de observabilidade.** Os dois vivem fora da composição de
  produção, numa rede compartilhada; a composição só se pluga nela.
- **Não é funcionalidade de produto.** Nenhuma regra de negócio muda. O que muda é como o sistema
  sobe, se expõe, se observa, se protege contra perda de dados e se valida antes do merge.

## Clarifications

### Session 2026-09-08

- Q: Onde a produção roda e como uma versão nova chega lá? → A: num servidor próprio com Docker
  Compose. As imagens são construídas no próprio servidor a partir do clone do repositório; não
  há registro de imagens nem entrega contínua.
- Q: Como o tráfego chega à API e ao painel? → A: só por um túnel reverso ligado a uma rede Docker
  compartilhada, criada uma vez por máquina. Nenhum serviço publica porta no host.
- Q: A stack de observabilidade faz parte da composição de produção? → A: não. É infraestrutura
  compartilhada na mesma rede do túnel, alcançada por nome. Para rodar tudo numa máquina de
  desenvolvimento, um arquivo de sobreposição sobe uma instância própria.
- Q: A documentação interativa da API fica ligada em produção? → A: não. Nasce desligada e liga
  por uma variável de ambiente, de propósito.
- Q: Para onde vai o backup e quanto se guarda? → A: para object storage fora da máquina
  (S3 e compatíveis), com retenção por número de cópias — 14 por padrão, uma por dia.
- Q: O backup sobe junto com a stack? → A: não por padrão. É opcional e só sobe quando pedido
  explicitamente, depois de configurado o destino: backup ligado sem destino tem que falhar alto.
- Q: Qual a amostragem de traces? → A: 100%, enquanto o volume for de dezenas ou centenas de
  requisições por dia.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Subir a stack de produção num servidor próprio (Priority: P1)

Quem hospeda precisa de um caminho único e repetível para pôr o Pitaco no ar: clonar o
repositório, preencher um arquivo de ambiente com as senhas, criar uma vez a rede do túnel e
subir. O banco sobe primeiro e só é dado como pronto quando aceita conexão; a API sobe depois,
aplica as migrations e confere o schema; o painel sobe falando com a API por dentro. Nada fica
exposto no host: o túnel encontra a API e o painel por nomes próprios na rede compartilhada.

**Why this priority**: sem isso não há produção. Todas as outras histórias operam sobre esta
stack. Entrega valor sozinha: o Pitaco no ar, acessível pelo domínio do túnel.

**Independent Test**: num servidor com Docker, criar a rede compartilhada, copiar o modelo de
ambiente, preencher a senha do banco, subir a composição e conferir, de dentro da rede interna,
que a saúde da API responde `UP` e o painel responde `200` — e que nenhum serviço publicou porta.

**Acceptance Scenarios**:

1. **Given** um servidor com Docker e a rede compartilhada criada, **When** subo a composição com
   o arquivo de ambiente preenchido, **Then** banco, API e painel ficam de pé e a saúde da API
   responde `UP`.
2. **Given** a stack no ar, **When** listo as portas dos serviços, **Then** nenhum publica porta
   no host.
3. **Given** o banco ainda subindo, **When** a composição inicia, **Then** a API só sobe depois
   que o banco aceita conexão.
4. **Given** uma versão nova com migration, **When** reconstruo e subo a API, **Then** a migration
   é aplicada na subida e o schema é validado contra as entidades; divergência impede a partida.
5. **Given** o túnel na mesma rede compartilhada, **When** ele procura os serviços, **Then**
   encontra a API e o painel por nomes com o prefixo do projeto, sem colidir com outros projetos
   da mesma máquina.
6. **Given** o túnel terminando o TLS, **When** a API monta uma URL externa (servidor anunciado
   no contrato público, cabeçalho `Location`), **Then** usa o esquema e o host que o cliente usou.
7. **Given** o banco caindo repetidamente com reinício automático, **When** olho a pasta de
   dados, **Then** nenhum arquivo de core foi gravado.
8. **Given** uma máquina de desenvolvimento sem túnel nem observabilidade compartilhada, **When**
   subo a composição com o arquivo de sobreposição local, **Then** API, painel e uma instância
   própria do Grafana LGTM ficam acessíveis em portas locais, com a documentação da API ligada.

---

### User Story 2 - Não perder os dados (Priority: P1)

Quem hospeda precisa de um backup que saia da máquina, que seja conferido antes de ocupar espaço
no destino, que não acumule para sempre e — principalmente — que se prove restaurável. Um backup
cujo restore nunca foi ensaiado não é backup.

**Why this priority**: o banco é o único estado do Pitaco que não se reconstrói a partir do
repositório. Perder o disco sem backup fora da máquina é perder todas as respostas coletadas.
Está no mesmo nível da história 1 porque produção sem backup é um risco que se aceita sem saber.

**Independent Test**: configurar o destino, rodar um backup, conferir no destino o arquivo com
nome datado e, em seguida, rodar o ensaio de restore: ele sobe um banco descartável, restaura o
dump mais recente, confere o histórico de migrations e as contagens, e remove o banco descartável.

**Acceptance Scenarios**:

1. **Given** um destino configurado, **When** o backup roda, **Then** um dump nomeado com o
   instante UTC chega ao destino, e o log registra o arquivo e o tamanho.
2. **Given** um dump que não pode ser listado, **When** o backup o confere, **Then** ele não é
   enviado e a execução falha.
3. **Given** mais cópias no destino do que o limite configurado, **When** um backup novo é
   enviado, **Then** as cópias mais antigas além do limite são removidas.
4. **Given** o backup em modo periódico, **When** uma execução falha, **Then** a falha é
   registrada e a próxima execução acontece no intervalo seguinte.
5. **Given** nenhum destino configurado, **When** o backup é iniciado, **Then** ele falha com
   mensagem explícita, sem rodar em silêncio.
6. **Given** um pedido de restore sem confirmação explícita, **When** executado, **Then** é
   recusado sem tocar no banco, informando qual banco seria substituído.
7. **Given** a confirmação explícita, **When** peço o restore sem informar arquivo, **Then** o
   dump mais recente do destino é restaurado; **When** informo um nome ou um arquivo local,
   **Then** é esse que é restaurado.
8. **Given** backups no destino, **When** rodo o ensaio de restore, **Then** ele relata as
   migrations aplicadas, a última versão e as contagens de aplicações e exibições, sem escrever no
   banco de produção, e remove o banco descartável ao terminar.
9. **Given** dumps num diretório local e nenhum object storage, **When** rodo o ensaio apontando
   o destino para esse diretório, **Then** o ensaio funciona igual.

---

### User Story 3 - Ver o que acontece em produção (Priority: P2)

Quando algo dá errado, quem opera precisa ir da resposta de erro que alguém viu ao trace daquela
requisição, e do trace aos logs dela. A API envia traces, logs e métricas para a stack de
observabilidade da máquina por um único endereço, e cada log leva o identificador do trace.

**Why this priority**: sem observabilidade, um erro em produção é um palpite. Vem depois das
duas primeiras porque a stack funciona e protege os dados sem ela.

**Independent Test**: com a stack no ar e a observabilidade alcançável, provocar uma resposta de
erro, pegar o `traceId` do corpo e encontrar, no Grafana, o trace e os logs dessa requisição.

**Acceptance Scenarios**:

1. **Given** a API no ar com o endereço de exportação configurado, **When** ela atende
   requisições, **Then** traces, logs e métricas chegam à stack de observabilidade.
2. **Given** uma resposta de erro com `traceId`, **When** busco esse identificador, **Then**
   encontro o trace e os logs daquela requisição.
3. **Given** uma requisição que nunca chega a um controller (rota inexistente, método errado,
   corpo malformado), **When** ela é atendida, **Then** uma linha de log de acesso registra método,
   caminho, status, bytes e duração.
4. **Given** volume baixo de tráfego, **When** olho os traces, **Then** todas as requisições
   foram registradas, sem amostragem.

---

### User Story 4 - Expor só o necessário (Priority: P2)

O que passa pelo túnel é público. Quem hospeda precisa que a API não publique, por padrão, mais
do que o necessário: sem mapa da API, sem endpoints de gestão além da saúde, sem porta no host.

**Why this priority**: cada superfície a mais atrás de um túnel público é um risco gratuito. Vem
depois das duas primeiras porque o padrão seguro já está no lugar sem nenhuma ação de quem
hospeda; esta história documenta e garante esse padrão.

**Independent Test**: com a configuração padrão, pedir a documentação interativa e a
especificação da API e não receber documentação; ligar a variável de documentação, reiniciar e
receber as duas, com a raiz da API levando à documentação.

**Acceptance Scenarios**:

1. **Given** a configuração padrão de produção, **When** peço a documentação interativa ou a
   especificação da API, **Then** nenhuma documentação é servida.
2. **Given** a variável de documentação ligada, **When** peço as duas, **Then** ambas respondem.
3. **Given** a documentação ligada, **When** abro a raiz do host ou a raiz da API com `GET` ou
   `HEAD`, **Then** sou redirecionado para a documentação interativa por um endereço relativo.
4. **Given** a documentação desligada, **When** abro a raiz, **Then** nenhum redirecionamento
   acontece e as demais rotas respondem como sempre.
5. **Given** a API no ar, **When** peço endpoints de gestão, **Then** só saúde e informações
   estão expostos.

---

### User Story 5 - Saber antes do merge que cada parte continua de pé (Priority: P3)

Quem contribui precisa que cada pull request rode o portão de qualidade da parte que ele toca —
e só dela —, que a composição de produção e os scripts de backup também sejam validados, e que
uma falha deixe evidência para investigar.

**Why this priority**: protege a produção de quebras evitáveis, mas não impede o Pitaco de rodar.
É a última camada.

**Independent Test**: abrir um pull request que mexe só no painel e conferir que só o portão do
painel roda; abrir outro que mexe na composição e conferir que o portão de composição valida o
arquivo, os scripts e a imagem de backup.

**Acceptance Scenarios**:

1. **Given** uma mudança só no backend, **When** o pull request é aberto, **Then** só o portão do
   backend roda, com a suíte completa sobre banco e observabilidade em container.
2. **Given** uma mudança no código-fonte do SDK, **When** o pull request é aberto, **Then** rodam
   o portão do SDK e o do painel, porque o painel embute o SDK.
3. **Given** uma mudança na composição ou em `ops/`, **When** o pull request é aberto, **Then** a
   composição é validada com um ambiente fictício — sozinha, com o backup ligado e com a
   sobreposição local —, os scripts de backup passam pela análise estática e a imagem de backup é
   construída.
4. **Given** um push novo na mesma branch, **When** uma execução anterior ainda roda, **Then** ela
   é cancelada.
5. **Given** uma falha de teste no backend ou no painel, **When** o portão termina, **Then** os
   resultados dos testes ou os rastros do E2E ficam disponíveis por sete dias.

---

### Edge Cases

- **Rede compartilhada inexistente**: a composição não sobe enquanto a rede não for criada; é um
  passo único por máquina, documentado.
- **Observabilidade fora do ar**: a API sobe e atende normalmente; os sinais do período se perdem.
  As primeiras linhas do boot só aparecem no console, porque a ponte de log é instalada quando o
  contexto da aplicação já está de pé.
- **Variável de exportação com o nome errado**: uma propriedade inexistente é ignorada em
  silêncio, e só as métricas continuariam saindo. A variável correta está fixada na composição e
  explicada no research.
- **Build sem memória**: construir API e painel em paralelo pode estourar a memória de um
  servidor pequeno; construir um serviço por vez resolve, e os containers antigos seguem
  servindo durante o build.
- **Migration nova e volta de versão**: migrations não voltam sozinhas. Voltar para um commit
  anterior cujo schema não bate com o banco impede a partida; o caminho é restaurar o backup
  feito antes da publicação.
- **Arquivos de core de volumes antigos**: a proteção vale para quedas futuras; arquivos de core
  já gravados num volume antigo não fazem parte do banco e podem ser apagados.
- **Backup ligado sem destino**: falha na subida e, com reinício automático, reinicia em laço — é
  proposital, e é por isso que o backup só sobe quando pedido.
- **Dump ilegível**: não é enviado nem ocupa uma cópia da retenção.
- **Arquivos estranhos no destino**: a retenção só considera arquivos com o padrão de nome do
  backup; nada mais é apagado.
- **Restore interrompido por erro**: o restore para no primeiro erro. Como ele limpa os objetos
  antes de recriá-los, pode ser repetido do começo.
- **Banco descartável que não fica pronto**: o ensaio desiste em 60 segundos, com mensagem, e
  remove o que criou.
- **Documentação ligada por engano em produção**: o mapa inteiro da API fica acessível a quem
  alcançar o túnel; é por isso que nasce desligada e liga só por variável explícita.

## Requirements *(mandatory)*

### Functional Requirements

**Stack de produção**

- **FR-001**: A stack de produção MUST ser descrita num único arquivo de composição na raiz do
  repositório, com três serviços sempre presentes — banco PostgreSQL 16, API e painel — e um
  serviço de backup opcional.
- **FR-002**: Nenhum serviço da stack de produção MUST publicar porta no host; a única entrada é
  um túnel reverso ligado a uma rede compartilhada externa, criada uma vez por máquina.
- **FR-003**: O banco MUST ficar só na rede interna da stack; API e painel MUST estar na rede
  interna e na compartilhada, nesta com nomes prefixados pelo projeto (`pitaco-api`,
  `pitaco-painel`).
- **FR-004**: O painel MUST falar com a API pela rede interna, pelo nome do serviço, com o prefixo
  de contexto da API incluído.
- **FR-005**: Senhas e credenciais MUST vir de um arquivo de ambiente local, fora do controle de
  versão; o repositório MUST versionar apenas o modelo desse arquivo.
- **FR-006**: A API MUST subir só depois que o banco responder à verificação de saúde; as
  migrations MUST rodar na subida da API, e a API MUST recusar a partida se o schema divergir das
  entidades.
- **FR-007**: Todos os serviços MUST reiniciar automaticamente após queda, exceto quando parados
  de propósito.
- **FR-008**: O banco MUST rodar com gravação de core dump desligada.
- **FR-009**: Banco, API e JVM MUST operar em UTC.
- **FR-010**: A imagem da API MUST ser construída em dois estágios, com cache de dependências no
  build, rodar sem privilégio de root, em base com glibc, e limitar a heap da JVM a uma fração da
  memória do container.
- **FR-011**: A imagem do painel MUST compilar o SDK do próprio repositório antes do painel,
  instalá-lo como cópia e não como link, gerar a saída autônoma do servidor e rodar sem
  privilégio de root, com build e execução na mesma base com glibc.
- **FR-012**: O contexto de build das imagens MUST excluir testes, documentação, specs, arquivos
  de ambiente e configuração local com senha.
- **FR-013**: A API MUST montar URLs externas — servidor anunciado no contrato público e cabeçalho
  `Location` — com o esquema e o host informados pelo túnel nos cabeçalhos `X-Forwarded-*`.
- **FR-014**: O repositório MUST oferecer uma sobreposição local que, somada à composição de
  produção, publica portas da API e do painel, liga a documentação da API e sobe uma instância
  própria do Grafana LGTM no lugar da observabilidade compartilhada.

**Superfície exposta**

- **FR-015**: A documentação interativa e a especificação da API MUST nascer desligadas; uma única
  variável de ambiente MUST ligar as duas juntas.
- **FR-016**: Com a documentação ligada, `GET` e `HEAD` na raiz do host, na raiz da API e na raiz
  da API com barra final MUST redirecionar (`302`, `Location` relativo) para a documentação
  interativa; com ela desligada, esse redirecionamento MUST NOT existir.
- **FR-017**: Os endpoints de gestão MUST expor apenas saúde e informações.

**Observabilidade**

- **FR-018**: A API MUST exportar traces, logs e métricas por OTLP sobre HTTP para um único
  endereço configurado por ambiente, que na produção aponta para a stack de observabilidade na
  rede compartilhada.
- **FR-019**: A amostragem de traces MUST ser de 100%.
- **FR-020**: Todo log exportado MUST carregar os identificadores de trace e de span da requisição
  em que foi emitido.
- **FR-021**: Toda requisição MUST produzir uma linha de log de acesso na saída padrão do
  processo — método, caminho, protocolo, status, bytes e duração em milissegundos —, inclusive as
  que não chegam a um controller.
- **FR-022**: Toda resposta de erro MUST carregar o `traceId` da requisição quando o tracing está
  ligado.

**Backup e restore**

- **FR-023**: A imagem de backup MUST trazer as ferramentas de dump e restore da mesma versão
  principal do banco de produção, e o cliente de object storage.
- **FR-024**: O backup MUST gerar um dump em formato próprio do PostgreSQL, sem dono, nomeado
  `pitaco-<instante UTC>.dump`.
- **FR-025**: O backup MUST conferir que o dump é legível antes de enviá-lo; dump ilegível MUST
  NOT ser enviado.
- **FR-026**: O destino MUST ser configurado só por variáveis de ambiente, sem arquivo de
  configuração, e MUST aceitar S3 e compatíveis e, para ensaio, um diretório local.
- **FR-027**: Depois de cada envio, o backup MUST manter no destino apenas as cópias mais recentes,
  até o limite configurado (14 por padrão), sem tocar em arquivos fora do padrão de nome.
- **FR-028**: O backup MUST oferecer execução única e modo periódico (um dia por padrão); no modo
  periódico, uma execução que falha MUST ser registrada e MUST NOT impedir a seguinte.
- **FR-029**: Sem destino configurado, o backup MUST falhar com mensagem explícita.
- **FR-030**: O serviço de backup MUST ser opcional na composição e só subir quando pedido
  explicitamente.
- **FR-031**: O restore MUST exigir confirmação explícita; sem ela, MUST recusar sem tocar no banco
  e informar qual banco e qual servidor seriam substituídos.
- **FR-032**: O restore MUST aceitar o dump mais recente do destino, um dump do destino pelo nome
  ou um arquivo local; MUST conferir a legibilidade antes, substituir os objetos existentes e
  parar no primeiro erro.
- **FR-033**: O ensaio de restore MUST restaurar o dump mais recente num banco descartável e
  isolado, conferir a presença do histórico de migrations e a ausência de migration com falha,
  relatar a quantidade de migrations, a última versão e as contagens de aplicações e exibições, e
  remover o banco descartável ao terminar, com sucesso ou falha.
- **FR-034**: O ensaio de restore MUST NOT escrever no ambiente real; dele, só lê o destino dos
  backups.
- **FR-035**: Backup e restore MUST registrar cada ação numa linha com instante UTC, operação e
  pares `chave=valor`.

**Documentação operacional**

- **FR-036**: O repositório MUST trazer um runbook com subir, backup, backup manual, restaurar,
  ensaiar o restore, chave de acesso comprometida, desativar uma aplicação em incidente e o que
  olhar na saúde do SDK, e um guia de publicação com atualizar, reconstruir, conferir e voltar de
  versão.

**Integração contínua**

- **FR-037**: Cada parte — backend, painel, SDK e composição — MUST ter um portão próprio,
  disparado em push na `main` e em pull request somente por mudança nos caminhos daquela parte ou
  no próprio portão, com permissão apenas de leitura sobre o repositório.
- **FR-038**: O portão do backend MUST rodar a suíte completa, com banco e observabilidade em
  container, e publicar os resultados dos testes em caso de falha, retidos por sete dias.
- **FR-039**: O portão do painel MUST compilar o SDK e instalá-lo pelo mesmo caminho da imagem,
  rodar lint, tipos, testes de unidade e E2E contra o simulador de API, e publicar os rastros do
  E2E em caso de falha; MUST disparar também por mudança no código-fonte ou no manifesto do SDK.
- **FR-040**: O portão do SDK MUST rodar lint, tipos, testes (incluindo o contrato do catálogo
  contra a especificação da API), build e lint/tipos do app de exemplo, e exportar o app de
  exemplo para iOS e Android.
- **FR-041**: O portão da composição MUST validar, com um ambiente fictício, a composição de
  produção sozinha, com o serviço de backup ligado e somada à sobreposição local; analisar
  estaticamente os scripts de backup e construir a imagem de backup.
- **FR-042**: Nos portões de backend, painel e SDK, uma execução nova na mesma ref MUST cancelar a
  anterior ainda em andamento.

### Key Entities

- **Stack de produção**: o conjunto de serviços que roda o Pitaco num servidor — banco, API,
  painel e, opcionalmente, backup —, descrito num único arquivo de composição.
- **Rede interna**: a rede privada da stack; o único lugar onde o banco é alcançável.
- **Rede compartilhada**: a rede externa da máquina onde vivem o túnel reverso e a stack de
  observabilidade; API e painel entram nela com nomes prefixados.
- **Arquivo de ambiente**: as senhas e o destino do backup, só no servidor; o repositório guarda o
  modelo.
- **Volume de dados**: o diretório persistente do banco; sobrevive a reconstrução de imagens.
- **Dump**: uma cópia do banco num instante, em formato próprio do PostgreSQL, nomeada pelo
  instante UTC.
- **Destino de backup**: um remoto de object storage, ou um diretório local no ensaio, onde os
  dumps ficam e onde a retenção é aplicada.
- **Sinal de telemetria**: trace, log ou métrica exportado pela API, correlacionável pelo
  identificador de trace.
- **Portão de CI**: a verificação automática de uma parte, disparada pelos caminhos dela.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Num servidor com Docker, a stack sai do clone para a saúde da API respondendo `UP` em
  no máximo quatro comandos, além do preenchimento do arquivo de ambiente.
- **SC-002**: A stack de produção publica zero portas no host.
- **SC-003**: 100% dos dumps que chegam ao destino passaram pela conferência de legibilidade.
- **SC-004**: Depois de cada backup bem-sucedido, o destino guarda no máximo o número de cópias
  configurado.
- **SC-005**: Em 100% das execuções do ensaio de restore, com sucesso ou falha, o banco
  descartável e a rede do ensaio são removidos e o banco de produção não recebe nenhuma escrita.
- **SC-006**: Para qualquer resposta de erro com `traceId`, o trace e os logs da requisição são
  encontráveis na stack de observabilidade pelo mesmo identificador.
- **SC-007**: Com a configuração padrão, nenhuma das rotas de documentação da API devolve
  documentação.
- **SC-008**: Um pull request que toca uma única parte dispara apenas o portão dessa parte (e, no
  caso do SDK, também o do painel).
- **SC-009**: Quedas repetidas do banco com reinício automático deixam zero arquivos de core no
  volume de dados.

## Assumptions

- **O túnel e a observabilidade são da máquina, não do Pitaco.** Quem hospeda já tem, ou cria, um
  túnel reverso e uma stack Grafana LGTM na rede compartilhada; a stack de observabilidade
  responde pelo nome `lgtm`. O Pitaco só se pluga nos dois.
- **A camada do túnel protege o que não tem login.** O painel e a superfície administrativa da API
  ficam atrás de uma política de acesso do túnel ou de rede privada; só a superfície de coleta,
  autenticada pela chave da aplicação, precisa ser pública. É a mesma orientação da política de
  segurança do repositório.
- **Docker Compose v2 no servidor.** Perfis, sobreposição de arquivos, `depends_on` com condição
  de saúde e redes externas são usados como estão.
- **Imagens construídas no servidor.** Não há registro de imagens; a reconstrução reaproveita
  camadas e o cache de dependências do build.
- **Um backup por dia é suficiente.** O intervalo e a retenção são configuráveis; o padrão de 14
  cópias diárias cobre duas semanas.
- **O ensaio de restore é manual.** Roda depois de configurar o destino e a cada mudança de schema
  relevante; não está agendado nem no CI.
- **As imagens da API e do painel não são construídas no CI.** O CI roda os portões de cada parte e
  constrói só a imagem de backup; as imagens de aplicação são construídas no servidor.
- **Desenvolvimento do backend tem ambiente próprio.** A composição do backend sobe banco e
  observabilidade com portas publicadas para o ciclo de desenvolvimento; ela não é produção.

## Dependencies

- As specs do backend, de `backend 001-api-key-management` em diante: a API que esta stack
  publica, com as superfícies administrativa e de coleta; a separação por prefixo de rota da
  `backend 004-response-collection` é o que permite expor só a coleta, e o `traceId` no corpo de
  erro nasce nela.
- As specs do painel, de `painel 001-painel-operacao-pesquisas` em diante: o painel servido pela
  stack, que lê a URL da API só no servidor; a saúde do SDK e a privacidade que o runbook manda
  olhar estão em `painel 005-painel-saude-privacidade`.
- `sdk 001-sdk-core` e o app de exemplo do SDK: o SDK é embutido na imagem do painel (pela
  pré-visualização) e os dois são cobertos pelos portões de CI.
- `ops 002-demo-seed`: carrega dados de demonstração na stack descrita aqui.
