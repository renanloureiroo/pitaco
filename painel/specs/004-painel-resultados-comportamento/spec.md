# Feature Specification: Painel de resultados e comportamento — números por pergunta, recorte, respostas abertas, export e interação

**Feature Branch**: `004-painel-resultados-comportamento`

**Created**: 2026-09-12

**Status**: Implemented

**Input**: User description: "o backend já apura os resultados. bora mostrar no painel: número por pergunta, recorte por período, atributo e versão, as respostas abertas com busca, export em CSV e o que acontece dentro da pesquisa (onde o povo desiste, quanto tempo leva)."

## Contexto

A `002-painel-leitura-coleta` mostrou a coleta item a item, e disse por que parava ali: o backend
não tinha agregação. A regra era explícita — nenhum número que a API não devolva, nenhuma
consulta só para produzir um número.

O backend passou a apurar. Para cada pesquisa ele devolve a taxa de resposta com a definição do
cálculo e a linha do tempo, o agregado de cada pergunta no formato do tipo (contagem e proporção
por opção, distribuição e média nas escalas, grupos e pontuação no NPS), a contagem de puladas e
de não aplicáveis, a comparabilidade entre versões somadas, o catálogo de atributos vistos nas
exibições, a amostra e o recorte aplicado. Lista respostas abertas com busca e o contexto da
mesma exibição, gera o CSV do recorte e, a partir dos eventos de interação do SDK, apura o
comportamento dentro da pesquisa — funil por pergunta, tempo ativo, vias de dispensa e sinais de
atrito, cada métrica com a sua definição.

Esta feature leva essa leitura ao painel, em duas abas novas da pesquisa: **Resultados** e
**Comportamento**. A regra de 002 continua de pé com outro alcance: o painel não inventa número.
Tudo que aparece foi apurado pelo backend; o painel escolhe a forma, põe a contagem ao lado da
proporção e diz quando o número pode enganar.

Ficam fora: a visão consolidada da aplicação (`006-painel-design-system-v2`), os avisos de saúde
do SDK e a nota de retenção, que aparecem na tela de resultados mas são especificados em
`005-painel-saude-privacidade`.

## Clarifications

### Session 2026-09-12

- Q: Proporção sozinha ou com contagem? → A: Sempre as duas juntas. Proporção sozinha esconde
  amostra pequena.
- Q: Como fica uma pergunta que ninguém respondeu, ou uma taxa sem exibição? → A: Estados
  próprios ("Ninguém respondeu esta pergunta ainda", taxa "—"). Nunca zero.
- Q: O recorte vale para quê? → A: Para tudo na tela ao mesmo tempo — taxa, agregados,
  respostas abertas, export e comportamento. Um número sob um recorte e outro sob outro seria
  pior do que nenhum.
- Q: Somar versões diferentes é permitido? → A: É o padrão (consolidado), mas cada pergunta que
  mudou entre as versões somadas avisa que somar pode enganar e sugere ler versão a versão.
- Q: O export pode ter dado pessoal? → A: Pode, porque texto livre é livre. A mitigação é de
  processo: o painel lembra disso antes de baixar, e só baixa depois da confirmação.
- Q: O que é "comportamento" se o SDK é antigo e não manda eventos? → A: A base é a exibição
  instrumentada (ao menos um evento). Sem nenhuma, a tela diz isso em vez de mostrar zeros.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ler o resultado de uma pesquisa (Priority: P1)

Na aba Resultados da pesquisa, quem pesquisa vê primeiro a taxa de resposta — exibidas,
concluídas, dispensadas, abandonadas e em andamento, a taxa e a frase que define o cálculo — com
a linha do tempo diária de exibições e conclusões. Em pesquisa criada pelo modelo NPS, o NPS
aparece no topo com os três grupos. Depois, cada pergunta no formato do seu tipo: barras por
opção, com cor fixa por opção; histograma das notas com a média nas escalas; NPS por faixa
(detratores 0–6, neutros 7–8, promotores 9–10) com a pontuação; e, no texto livre, o vínculo
para as respostas abertas. Contagem e proporção aparecem sempre juntas.

**Why this priority**: é a razão de existir da pesquisa. Sem esta tela, o resultado continua
exigindo exportar e somar à mão.

**Independent Test**: com uma pesquisa publicada e coleta semeada (duas concluídas, uma
dispensada, uma abandonada), conferir 4 exibidas, 2 concluídas, 1 dispensada, 1 abandonada, taxa
50%, a definição "concluídas ÷ exibidas", o aviso de amostra pequena e o NPS com 1 promotor, 1
detrator e pontuação 0.

**Acceptance Scenarios**:

1. **Given** uma pesquisa com exibições, **When** abro Resultados, **Then** vejo as cinco
   contagens, a taxa e a definição do cálculo junto dos números.
2. **Given** poucas respostas, **When** vejo os números, **Then** um aviso diz que a amostra é
   pequena e as proporções podem enganar.
3. **Given** uma pergunta de escolha, **When** vejo o agregado, **Then** cada opção mostra
   contagem e proporção, inclusive a opção com zero, e cada opção mantém a mesma cor em qualquer
   recorte; em múltipla escolha, uma nota explica por que as proporções somam mais de 100%.
4. **Given** uma pergunta de avaliação ou escala, **When** vejo o agregado, **Then** vejo a média
   e o histograma das notas.
5. **Given** uma pergunta NPS, **When** vejo o agregado, **Then** vejo a pontuação com sinal, os
   três grupos com suas faixas e o histograma colorido por grupo.
6. **Given** uma pergunta que ninguém respondeu, **When** a vejo, **Then** ela diz "Ninguém
   respondeu esta pergunta ainda" — não barras zeradas.
7. **Given** uma pergunta condicionada que parte do público não viu, **When** a vejo, **Then** os
   não aplicáveis aparecem contados à parte das puladas, com a explicação.
8. **Given** a pesquisa nunca publicada, **When** abro Resultados, **Then** vejo "Esta pesquisa
   ainda não foi publicada" com vínculo para a publicação, sem filtros nem export.
9. **Given** a pesquisa publicada sem exibição, **When** abro Resultados, **Then** vejo "Nenhuma
   exibição ainda" com vínculo para o disparo.
10. **Given** a tela aberta, **When** clico em Atualizar ou passam 30 segundos com a aba visível,
    **Then** os números se atualizam sem recarregar a página, e a hora da atualização aparece.

---

### User Story 2 - Recortar por período, atributo e versão (Priority: P1)

Acima dos números, um recorte: período (todo, últimos 7, 30 ou 90 dias, ou personalizado com
início e fim), atributo (qualquer atributo visto nas exibições da pesquisa, com um valor ou "Sem
o atributo") e visão (consolidado ou uma versão). Aplicar muda todos os números ao mesmo tempo e
fica na URL, compartilhável. O recorte ativo aparece escrito ao lado dos números. No consolidado,
cada pergunta que mudou entre as versões somadas avisa que somar pode enganar.

**Why this priority**: o número global raramente responde a pergunta de quem pesquisa ("e no
plano pro?", "e depois da mudança?"). Sem recorte, cada pergunta vira um export.

**Independent Test**: aplicar período personalizado de setembro e conferir que exibidas caem de 4
para 3, uma resposta aberta some e a URL tem `de=`; aplicar "Sem o atributo" para `plano` e
conferir 2 exibidas e `ausente=1`; limpar e voltar a 4.

**Acceptance Scenarios**:

1. **Given** a tela de resultados, **When** escolho um atalho de período e aplico, **Then** taxa,
   agregados e respostas abertas mudam juntos, e a URL guarda `periodo`.
2. **Given** período personalizado com início depois do fim, **When** aplico, **Then** a recusa
   aparece no formulário e nada navega.
3. **Given** um atributo e um valor, **When** aplico, **Then** só exibições com aquele valor
   entram; **Given** "Sem o atributo", **Then** só exibições que não enviaram o atributo entram.
4. **Given** uma versão escolhida na visão, **When** aplico, **Then** só exibições daquela versão
   entram, e nenhuma pergunta mostra aviso de comparabilidade.
5. **Given** o consolidado com duas versões em que uma pergunta mudou, **When** a vejo, **Then**
   ela avisa "mudou entre as versões 1 e 2; somar as respostas pode enganar"; **Given** que nada
   mudou, **Then** diz que é seguro somar.
6. **Given** um recorte aplicado, **When** olho os números, **Then** vejo o recorte escrito em
   português ("Últimos 30 dias", "plano = pro", "Sem o atributo \"plano\"", "Versão 2").
7. **Given** um recorte sem exibição, **When** aplico, **Then** vejo "Nenhuma exibição neste
   recorte" com ação de limpar.
8. **Given** a URL com valores inválidos, **When** a abro, **Then** o valor inválido é ignorado e
   a tela abre.
9. **Given** um recorte aplicado, **When** limpo, **Then** todos os parâmetros do recorte saem da
   URL e a página volta à primeira.

---

### User Story 3 - Ler as respostas abertas (Priority: P2)

Abaixo dos agregados, as respostas de texto livre do recorte, paginadas, cada uma com o
enunciado e o momento. Uma busca filtra por termo, sem diferenciar maiúsculas de minúsculas. Cada
resposta tem, recolhido, o que a mesma pessoa respondeu nas outras perguntas daquela exibição.
Um lembrete diz que texto livre pode conter dado pessoal.

**Why this priority**: o texto explica o número ("achei confuso" de quem deu 9 é diferente de
quem deu 2), mas os agregados já entregam valor sem ele.

**Independent Test**: buscar "ÓTIMO" e conferir que só "Ficou ótimo" aparece, que a URL tem `q=`
e que os números do topo não mudaram; limpar a busca e conferir as duas respostas.

**Acceptance Scenarios**:

1. **Given** respostas abertas no recorte, **When** abro Resultados, **Then** vejo cada uma com
   texto, enunciado e momento, paginadas.
2. **Given** a busca, **When** procuro um termo, **Then** só respostas que o contêm aparecem, a
   URL guarda `q`, a página volta à primeira e os agregados não mudam.
3. **Given** uma resposta, **When** abro o contexto, **Then** vejo as outras respostas daquela
   exibição com seus enunciados.
4. **Given** nenhuma resposta, **When** vejo a seção, **Then** ela diz "Nenhuma resposta de texto
   neste recorte" ou "Nenhuma resposta contém \"termo\"".

---

### User Story 4 - Exportar o recorte em CSV (Priority: P2)

Um botão Exportar CSV, presente em pesquisa já publicada, pede confirmação lembrando que
respostas abertas podem conter dado pessoal. Confirmado, o navegador baixa o arquivo do recorte
atual — o mesmo período, atributo e versão da tela.

**Why this priority**: análise fora do painel continua necessária, mas não é o primeiro uso.

**Independent Test**: com `?periodo=90`, exportar, cancelar uma vez (nada baixa), exportar e
confirmar; conferir o nome `resultados-{id}.csv`, o cabeçalho e que a resposta fora do período
não está no arquivo.

**Acceptance Scenarios**:

1. **Given** uma pesquisa publicada, **When** clico em Exportar CSV, **Then** um diálogo lembra do
   dado pessoal antes de qualquer download.
2. **Given** o diálogo, **When** cancelo, **Then** nada é baixado.
3. **Given** o diálogo, **When** confirmo, **Then** o arquivo baixado tem o nome dado pelo
   backend e só o recorte aplicado.
4. **Given** uma recusa do backend, **When** o download é pedido, **Then** a recusa volta com o
   status do backend, sem o painel inventar arquivo.
5. **Given** uma pesquisa nunca publicada, **When** abro Resultados, **Then** o export não é
   oferecido.

---

### User Story 5 - Ver o que acontece dentro da pesquisa (Priority: P2)

A aba Comportamento mostra, sobre as exibições instrumentadas do recorte: quantas são e que
fração das exibições cobrem; o tempo ativo (soma das medianas por pergunta); a taxa de troca de
resposta; quantas dispensas e de que forma. Abaixo, o funil por pergunta (viram, responderam,
pularam, pararam ali, com a perda entre perguntas), o tempo ativo por pergunta (mediana e
percentil 90), as vias de dispensa (botão fechar, arrastar para baixo, toque fora, voltar do
Android, navegação do app, pelo app) e os sinais de atrito (taxa de volta, taxa de troca de
resposta, bloqueios de validação). As definições de cada métrica, como o backend as calcula,
ficam ao alcance. Na aba Resultados, um cartão resume o comportamento e leva a esta aba.

**Why this priority**: explica a taxa de conclusão — onde as pessoas desistem e por quê — mas só
faz sentido depois de ler o resultado.

**Independent Test**: com eventos de interação semeados, abrir Comportamento e conferir o funil,
o tempo e as vias; numa pesquisa sem eventos, conferir o vazio "Nenhuma exibição instrumentada".

**Acceptance Scenarios**:

1. **Given** exibições instrumentadas, **When** abro Comportamento, **Then** vejo os quatro
   indicadores, o funil, o tempo por pergunta, as vias de dispensa e os sinais de atrito.
2. **Given** o funil, **When** o leio, **Then** cada pergunta mostra quantos a viram, a proporção
   sobre as instrumentadas, a perda em relação à anterior e quantos responderam, pularam e
   pararam ali.
3. **Given** dispensas sem via informada, **When** vejo as vias, **Then** elas aparecem contadas
   à parte.
4. **Given** as definições, **When** as abro, **Then** vejo, para cada métrica, a definição que o
   backend devolveu.
5. **Given** exibições que nenhuma enviou evento, **When** abro a aba, **Then** vejo "Nenhuma
   exibição instrumentada neste recorte" explicando que versões antigas do SDK não enviam eventos.
6. **Given** a pesquisa nunca publicada, **When** abro a aba, **Then** vejo o mesmo vazio de
   Resultados, com vínculo para a publicação.
7. **Given** a aba, **When** aplico um recorte, **Then** ele usa os mesmos parâmetros de URL dos
   resultados.
8. **Given** Resultados, **When** vejo o cartão de comportamento, **Then** ele mostra onde mais
   desistem, o tempo ativo e a troca de resposta, com vínculo "Ver comportamento".

---

### Edge Cases

- **Taxa sem exibição**: "—", nunca 0%.
- **NPS sem resposta**: pontuação ausente, nunca zero.
- **Agregado ausente vs. contagens zeradas**: ausente é "ninguém respondeu"; presente com zeros
  mostra as opções com zero.
- **Período personalizado invertido na URL**: descartado na leitura, não enviado.
- **Atributo sem valor e sem `ausente=1` na URL**: não é recorte nenhum.
- **Busca longa demais** (mais de 200 caracteres): ignorada.
- **Versão inválida na URL**: cai no consolidado.
- **Duas leituras com nomes diferentes de campo**: a taxa de troca de resposta e os bloqueios de
  validação aceitam o nome atual e o anterior do backend.
- **Pesquisa suprimida sem exibição**: o vazio "Nenhuma exibição ainda" não aparece quando há
  aviso de supressão — quem não viu não teve como responder (ver `005-painel-saude-privacidade`).
- **Falha de leitura**: resultado, respostas abertas, comportamento ou versões indisponíveis
  levam ao estado de falha do segmento, com nova tentativa; a leitura de saúde, complementar, não
  derruba a tela.
- **Pesquisa inexistente**: tela de não encontrado.

## Requirements *(mandatory)*

### Functional Requirements

**Abas**

- **FR-001**: A pesquisa MUST ganhar as abas Resultados e Comportamento, após Exibições.

**Resultado**

- **FR-002**: Resultados MUST exibir exibidas, concluídas, dispensadas, abandonadas, em
  andamento, a taxa de resposta e a definição do cálculo devolvida pelo backend, juntas.
- **FR-003**: Resultados MUST exibir a linha do tempo diária de exibições e conclusões do recorte.
- **FR-004**: Resultados MUST avisar amostra pequena quando o backend a sinalizar.
- **FR-005**: Em pesquisa com resumo de NPS, Resultados MUST exibir a pontuação e os três grupos
  no topo.
- **FR-006**: Cada pergunta MUST exibir o tipo, respondidas, puladas e, quando houver, não
  aplicáveis com explicação.
- **FR-007**: Pergunta de escolha MUST exibir barras por opção com contagem e proporção, com cor
  determinada pela posição da opção; múltipla escolha MUST explicar que as proporções somam mais
  de 100%.
- **FR-008**: Pergunta de avaliação ou escala MUST exibir a média e o histograma das notas; NPS
  MUST exibir pontuação com sinal, grupos com suas faixas e histograma colorido por grupo.
- **FR-009**: Pergunta sem resposta MUST exibir estado próprio, e taxa ou pontuação ausentes MUST
  aparecer como ausentes, nunca como zero.
- **FR-010**: Resultados MUST distinguir três vazios: nunca publicada (com vínculo para a
  publicação), sem exibição (com vínculo para o disparo) e sem exibição no recorte (com limpar).
- **FR-011**: Resultados MUST oferecer atualização manual e automática a cada 30 segundos enquanto
  a aba estiver visível, sem recarregar a página, mostrando a hora da última atualização.
- **FR-012**: Os instantes MUST ser exibidos no fuso de referência, rotulado na tela.

**Recorte**

- **FR-013**: Resultados e Comportamento MUST oferecer recorte por período (todo, 7, 30, 90 dias
  ou personalizado), atributo (com valor ou ausente) e visão (consolidado ou versão).
- **FR-014**: O recorte MUST viver na URL (`periodo`, `de`, `ate`, `atributo`, `valor`, `ausente`,
  `versao`) e valer para todas as leituras da tela ao mesmo tempo.
- **FR-015**: Atalho de período MUST vencer o período personalizado; período personalizado
  invertido MUST ser recusado no formulário sem navegar e descartado na leitura da URL.
- **FR-016**: Os atributos oferecidos MUST ser os vistos nas exibições da pesquisa, com seus
  valores e contagens; as versões oferecidas MUST ser as dos grupos de comparabilidade.
- **FR-017**: O recorte ativo MUST aparecer escrito em português junto dos números.
- **FR-018**: No consolidado, cada pergunta que o backend marcar como incomparável entre as
  versões somadas MUST avisar quais versões e sugerir ler versão a versão; comparável entre mais
  de uma versão MUST dizer que é seguro somar.
- **FR-019**: Aplicar ou limpar o recorte MUST voltar à primeira página.

**Respostas abertas**

- **FR-020**: Resultados MUST listar as respostas de texto livre do recorte, paginadas, com texto,
  enunciado e momento.
- **FR-021**: A lista MUST oferecer busca por termo, refletida na URL (`q`), sem afetar os
  agregados.
- **FR-022**: Cada resposta MUST oferecer, recolhido, o contexto das outras respostas da mesma
  exibição.
- **FR-023**: A seção MUST lembrar que texto livre pode conter dado pessoal.

**Export**

- **FR-024**: Resultados de pesquisa publicada MUST oferecer exportar o recorte em CSV, só depois
  de confirmação que lembra do dado pessoal.
- **FR-025**: O download MUST passar pelo servidor do painel, que repassa o fluxo do backend com
  tipo e nome de arquivo preservados e status de recusa como veio; o navegador MUST NOT falar com
  o backend.

**Comportamento**

- **FR-026**: Comportamento MUST exibir exibições instrumentadas e sua cobertura, tempo ativo
  (soma das medianas), taxa de troca de resposta e dispensas com sua proporção.
- **FR-027**: Comportamento MUST exibir o funil por pergunta (viram, proporção, perda em relação à
  anterior, respondidas, puladas, pararam aqui), o tempo ativo por pergunta (mediana e p90), as
  vias de dispensa em português (com as sem via à parte) e os sinais de atrito.
- **FR-028**: Comportamento MUST exibir as definições das métricas devolvidas pelo backend.
- **FR-029**: Comportamento MUST distinguir pesquisa nunca publicada e recorte sem exibição
  instrumentada, cada um com texto próprio.
- **FR-030**: Resultados MUST exibir um resumo do comportamento com vínculo para a aba.

### Key Entities

- **Resultado da pesquisa**: se já foi publicada, taxa de resposta, perguntas com agregado,
  amostra e aviso de amostra pequena, recorte aplicado, catálogo de atributos, resumo de NPS e
  nota de retenção.
- **Taxa de resposta**: contagens por desfecho, taxa (ausente sem exibição), definição e linha do
  tempo diária.
- **Resultado por pergunta**: chave, enunciado, tipo, posição, respondidas, puladas, não
  aplicáveis, agregado e comparabilidade (só no consolidado).
- **Agregado**: escolha (opções com contagem e proporção), numérico (média e distribuição), NPS
  (grupos, pontuação, distribuição) ou texto.
- **Recorte**: período, atributo e valor (ou ausência), versão.
- **Resposta aberta**: texto, enunciado, momento e contexto da mesma exibição.
- **Comportamento**: exibidas, instrumentadas, por pergunta (vista, respondida, pulada,
  abandonada, tempo ativo, voltas, trocas, bloqueios), dispensas por via e definições.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Os números da tela de resultados conferem com a coleta semeada em 100% dos campos
  verificados pelo teste ponta a ponta (exibidas, concluídas, dispensadas, abandonadas, taxa, NPS).
- **SC-002**: Nenhuma proporção aparece sem a contagem ao lado, e nenhuma ausência aparece como
  zero — verificável em revisão de tela e por teste de componente.
- **SC-003**: Trocar o recorte muda todos os números da tela numa única navegação, e a URL
  resultante reproduz a mesma tela quando aberta por outra pessoa.
- **SC-004**: Nenhum arquivo é baixado sem a confirmação sobre dado pessoal, verificado por teste
  de componente e ponta a ponta.
- **SC-005**: O CSV exportado contém só o recorte aplicado, verificado ponta a ponta.
- **SC-006**: Quem pesquisa encontra a pergunta onde mais pessoas desistem em no máximo 1 clique a
  partir dos resultados.
- **SC-007**: Os fluxos de ler o resultado, os três vazios, recortar, buscar e exportar estão
  cobertos por teste ponta a ponta.

## Assumptions

- Toda agregação é do backend: o painel soma nada, recalcula nada e só escolhe a forma. Os
  limiares (amostra pequena, abandono) e as definições das métricas vêm dele.
- O SDK com eventos de interação é a fonte do comportamento; exibições de SDKs que não enviam
  eventos ficam fora da base, por decisão do backend.
- O catálogo de atributos oferecido no recorte é o que veio nas exibições da pesquisa, não o
  catálogo da aplicação.
- O export é gerado inteiro pelo backend; o painel só repassa.
- As convenções de 001 e 002 continuam valendo: fuso de referência rotulado, recorte na URL com
  valor inválido caindo no padrão, paginação compartilhada.

## Dependencies

- `001-painel-operacao-pesquisas`: navegação da pesquisa, versões e comparabilidade.
- `002-painel-leitura-coleta`: semeadura de coleta no servidor de API simulado, fuso de
  referência, paginação.
- `003-painel-autoria-avancada`: modelo NPS (resumo no topo), condição (não aplicáveis) e
  natureza da mudança (comparabilidade entre versões).
- API administrativa de resultados do backend (módulo `results`), apoiada pela coleta de
  `backend 004-response-collection` e pela leitura de `backend 006-collection-read-list`.
