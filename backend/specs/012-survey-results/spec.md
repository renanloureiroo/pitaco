# Feature Specification: Resultados de pesquisa — agregados, recortes, respostas abertas e export

**Feature Branch**: `012-survey-results`

**Created**: 2026-09-12

**Status**: Implemented

**Input**: User description: "Agora os resultados: por pergunta, com contagem e proporção, NPS por faixa, média e histograma, recortes por período, atributo e versão, as respostas abertas com busca, export em CSV e os avisos que impedem leitura errada. E o painel precisa montar a visão geral da aplicação a partir disso."

## Contexto

A coleta grava exibições e respostas (`004`), e a leitura crua permite abrir cada uma (`006`).
Ninguém, porém, lê dez mil exibições uma a uma. Quem publicou uma pesquisa quer saber o que ela
disse: quantos viram, quantos responderam, como se distribuíram as respostas, o NPS — e com que
confiança pode ler aqueles números.

Esta fatia entrega a leitura agregada por pesquisa, num módulo próprio (`results`), estritamente de
leitura:

- **Resultado por pergunta**: contagem e proporção sempre juntas, na forma de cada tipo — opções,
  média e distribuição, NPS por faixa, texto livre sinalizado.
- **Taxa de resposta com definição única** e desfechos: concluídas, dispensadas, abandonadas e em
  andamento, com tendência diária.
- **Recortes** por período, por atributo (com valor ou ausente) e por versão, os mesmos para tudo.
- **Respostas abertas** com busca, paginação e o contexto da mesma exibição.
- **Export CSV** de uma linha por exibição, respeitando o recorte, com aviso de dado pessoal.
- **Avisos de leitura**: amostra pequena, versões incomparáveis por pergunta, agregado de retenção
  aplicado ou não.

Três fronteiras:

- **Não há leitura analítica por aplicação no backend.** A visão geral do painel (indicadores com
  variação, tendência, desfechos, NPS consolidado, análise por segmento, desempenho por pesquisa) é
  composta pelo próprio painel a partir desta leitura por pesquisa, chamada para o período atual e o
  anterior, e uma vez por valor de atributo na análise por segmento. Nenhuma rota de aplicação foi
  criada para isso.
- **Não é comportamento.** Funil, tempo ativo e atrito vêm dos eventos de interação
  (`013-interaction-events`), na rota irmã `…/results/behavior`.
- **Não é escrita.** Nenhuma leitura desta fatia altera estado.

## Clarifications

### Session 2026-09-12

- Q: O que é taxa de resposta? → A: Concluídas ÷ exibidas. Exibidas conta toda abertura, inclusive
  dispensadas, abandonadas e em andamento. A definição volta junto do número.
- Q: Quando uma exibição é abandonada? → A: Sem desfecho depois do prazo de abandono (30 minutos),
  a mesma regra da coleta. Antes do prazo, está em andamento.
- Q: Proporção é sobre o quê? → A: Sobre quem respondeu a pergunta. Em múltipla escolha as fatias
  somam mais de 100%, e é assim mesmo.
- Q: Pergunta sem nenhuma resposta mostra zero em tudo? → A: Não. Mostra que não há agregado: "sem
  resposta" e "zero em cada opção" são coisas diferentes.
- Q: O que é amostra pequena? → A: Menos de 10 exibições com ao menos uma resposta dada
  (configurável). A proporção aparece com aviso.
- Q: Somar versões diferentes é seguro? → A: Pergunta a pergunta. No consolidado, a pergunta cuja
  forma mudou entre versões que receberam exibição no recorte vem marcada como incomparável; as
  outras somam sem aviso. Lida uma versão por vez, não há marca.
- Q: O agregado congelado pela retenção entra em qualquer recorte? → A: Só no recorte que é apenas
  de versão (ou nenhum). Ele não sabe dia nem atributo; nos outros recortes a resposta diz que não
  entrou e por quê.
- Q: Recorte por atributo sem valor? → A: É o recorte "sem o atributo" — quem não enviou aquele
  atributo.
- Q: O export traz quem respondeu? → A: Só a identificação opaca do respondente, como o app
  enviou. Texto livre vencido sai vazio; pulada sai vazia; não aplicável sai `n/a`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver o resultado de cada pergunta (Priority: P1)

Quem publicou abre os resultados e vê, por pergunta, quantos responderam, pularam ou não se
aplicavam, e o agregado na forma do tipo: barras por opção, média e distribuição de nota, NPS com
promotores, neutros e detratores.

**Why this priority**: é a razão de coletar.

**Independent Test**: semear exibições com respostas conhecidas e conferir cada contagem, proporção,
média, distribuição e NPS.

**Acceptance Scenarios**:

1. **Given** respostas de escolha, **When** leio, **Then** cada opção traz contagem e proporção sobre
   quem respondeu, na ordem da definição, com zero onde ninguém marcou.
2. **Given** uma opção que a definição atual não tem mas alguma versão teve, **Then** ela aparece pelo
   valor, no fim, sem sumir da conta.
3. **Given** respostas de avaliação ou escala, **Then** recebo a média e a faixa inteira, com zero
   onde ninguém marcou.
4. **Given** respostas NPS, **Then** recebo promotores (9–10), neutros (7–8), detratores (0–6) e a
   pontuação de −100 a 100, mais a distribuição.
5. **Given** texto livre respondido, **Then** o agregado só sinaliza que há texto; a listagem vem das
   respostas abertas.
6. **Given** uma pergunta sem resposta, **Then** ela vem sem agregado, distinta de zero.
7. **Given** respostas não aplicáveis, **Then** são contadas à parte e ficam fora do denominador.
8. **Given** uma pesquisa criada pelo modelo NPS, **Then** o NPS da pergunta do modelo vem no topo;
   sem modelo, não vem.

---

### User Story 2 - Taxa de resposta, desfechos e tendência (Priority: P1)

Os resultados abrem com exibidas, concluídas, dispensadas, abandonadas e em andamento, a taxa e a
definição dela, e a série diária de exibidas e concluídas.

**Why this priority**: sem taxa e desfecho, o agregado não tem contexto.

**Independent Test**: semear uma de cada desfecho e conferir as contagens e a taxa.

**Acceptance Scenarios**:

1. **Given** exibições em cada desfecho, **Then** recebo as cinco contagens e taxa = concluídas ÷
   exibidas.
2. **Given** nenhuma exibição, **Then** a taxa é ausente, não zero.
3. **Given** uma exibição iniciada antes do prazo de abandono e outra depois, **Then** a primeira é
   abandonada e a segunda em andamento.
4. **Given** exibições em dias diferentes, **Then** a série diária traz exibidas e concluídas por dia
   (UTC).
5. **Given** uma pesquisa publicada sem exibição, **Then** recebo zeros e amostra pequena; nunca
   publicada, estado vazio sem ir ao banco.

---

### User Story 3 - Recortar por período, atributo e versão (Priority: P1)

O operador olha só a última semana, só o plano `pro`, só quem não enviou `plano`, só a versão 3. O
recorte vale para tudo ao mesmo tempo e volta descrito na resposta.

**Why this priority**: o número consolidado esconde a diferença entre segmentos.

**Independent Test**: recortar por atributo com valor e por ausência, e conferir que todos os números
mudam juntos.

**Acceptance Scenarios**:

1. **Given** um período, **Then** só as exibições abertas dentro dele, inclusive nos extremos, contam.
2. **Given** atributo e valor, **Then** só as exibições com aquele valor; atributo sem valor, só as
   exibições sem aquele atributo.
3. **Given** uma versão, **Then** só as perguntas e exibições daquela versão; sem versão, consolida.
4. **Given** valor sem atributo, atributo em branco, período invertido ou versão menor que 1, **Then**
   recebo `400` apontando o campo.
5. **Given** o catálogo de atributos da pesquisa, **Then** a resposta lista cada atributo com seus
   valores e quantas exibições têm cada um.

---

### User Story 4 - Ler com a confiança certa (Priority: P2)

Os resultados avisam o que impede leitura ingênua: amostra pequena, pergunta que mudou entre versões
somadas, e o agregado da retenção aplicado (ou não, e por quê).

**Why this priority**: número sem aviso é número mal lido.

**Independent Test**: no consolidado, trocar uma opção entre v1 e v2 exibidas e conferir que só
aquela pergunta vem marcada como incomparável.

**Acceptance Scenarios**:

1. **Given** menos de 10 exibições com resposta, **Then** a amostra vem marcada como pequena.
2. **Given** a forma de uma pergunta mudou entre versões exibidas no recorte, **When** leio o
   consolidado, **Then** só ela vem incomparável, com as versões consideradas; versão sem exibição no
   recorte não entra.
3. **Given** um recorte de uma versão, **Then** não há marca de comparabilidade.
4. **Given** respostas descartadas pela retenção, **When** leio sem recorte, **Then** o agregado
   congelado é somado ao vivo (contagens, distribuição e amostra) e a nota diz isso.
5. **Given** um recorte de período ou atributo, **Then** o congelado não é somado e a nota explica
   por quê; com recorte de versão, soma só o congelado daquela versão.
6. **Given** nada congelado, **Then** a resposta não fala de retenção.

---

### User Story 5 - Ler as respostas abertas (Priority: P2)

O operador lê as respostas de texto livre, da mais recente para a mais antiga, busca por termo e vê,
junto de cada uma, o que a mesma pessoa respondeu nas outras perguntas daquela exibição.

**Why this priority**: o porquê do número está no texto.

**Independent Test**: buscar por um termo e conferir que só os textos que o contêm voltam, cada um
com o contexto legível.

**Acceptance Scenarios**:

1. **Given** respostas abertas, **Then** recebo-as paginadas, mais recentes primeiro, com o enunciado.
2. **Given** um termo, **Then** só os textos que o contêm, sem diferenciar maiúscula; termo só com
   espaço é ignorado.
3. **Given** texto vencido pela retenção, **Then** ele não aparece.
4. **Given** uma resposta aberta, **Then** o contexto traz as outras perguntas respondidas na mesma
   exibição, com opção pelo rótulo.
5. **Given** paginação fora dos limites, termo acima de 200 ou recorte inválido, **Then** `400`.

---

### User Story 6 - Exportar para planilha (Priority: P2)

O operador exporta um CSV com uma linha por exibição, respeitando o recorte, para análise fora do
painel. O arquivo avisa que pode conter dado pessoal quando a pesquisa tem texto livre.

**Why this priority**: análise que o painel não faz.

**Independent Test**: exportar uma pesquisa com vírgula, aspas e quebra de linha nas respostas e
abrir o arquivo íntegro.

**Acceptance Scenarios**:

1. **Given** exibições concluídas, dispensadas e abandonadas, **Then** há uma linha por exibição, com
   desfecho, instantes, versão, versão do SDK, atributos e respostas.
2. **Given** vírgula, aspas ou quebra de linha numa resposta, **Then** o CSV continua íntegro (RFC
   4180), com BOM para abrir com acento certo.
3. **Given** múltipla escolha, **Then** as opções saem separadas por ponto e vírgula; não aplicável
   sai `n/a`; pulada sai vazia.
4. **Given** um recorte de período, **Then** o export o respeita.
5. **Given** nenhuma exibição, **Then** sai só o cabeçalho.
6. **Given** volume maior que um lote, **Then** sai inteiro, em ordem de abertura.
7. **Given** texto livre em alguma versão publicada, **Then** a resposta traz o cabeçalho de aviso de
   dado pessoal; sem texto livre, não traz.
8. **Given** recorte inválido, chave do SDK ou pesquisa inexistente, **Then** `400`, `403` ou `404`
   antes de qualquer byte do arquivo.

---

### User Story 7 - Alimentar a visão geral da aplicação (Priority: P3)

O painel monta a visão geral — indicadores com variação contra o período anterior, tendência diária,
desfechos, NPS consolidado das pesquisas de modelo NPS, análise por segmento e desempenho por
pesquisa — chamando esta leitura por pesquisa com os recortes certos.

**Why this priority**: é a tela de entrada do painel, mas não exige nada novo do backend além do que
as histórias acima entregam.

**Independent Test**: para duas pesquisas, ler os resultados no período atual e no anterior e
conferir que contagens, série diária, NPS e catálogo de atributos bastam para compor os indicadores.

**Acceptance Scenarios**:

1. **Given** dois períodos, **When** o painel lê cada pesquisa nos dois, **Then** recebe contagens de
   desfecho e taxa para calcular a variação.
2. **Given** a série diária de cada pesquisa, **Then** o painel soma por dia.
3. **Given** pesquisas do modelo NPS, **Then** cada uma traz promotores, neutros e detratores no topo,
   somáveis entre pesquisas.
4. **Given** o catálogo de atributos de uma pesquisa, **When** o painel lê uma vez por valor com o
   recorte de atributo, **Then** obtém NPS e taxa por segmento.

---

### Edge Cases

- **Pesquisa nunca publicada**: estado vazio, `everPublished: false`, sem consulta ao banco.
- **Pesquisa de outra aplicação ou identificador malformado**: o mesmo `404`.
- **Opção removida numa versão nova**: continua aparecendo pelo valor.
- **Não aplicável e pulada**: distintas no resultado e no CSV.
- **Retenção com recorte de período**: congelado fora, nota explicando.
- **Export grande**: lido em lotes por cursor, nunca uma consulta por linha.
- **Leituras repetidas**: nenhuma altera o estado gravado.

## Requirements *(mandatory)*

### Functional Requirements

**Resultado por pergunta**

- **FR-001**: O sistema MUST devolver, para cada pergunta da versão lida (ou das versões, no
  consolidado), na ordem das perguntas, a chave, o enunciado, o tipo, a posição e as contagens de
  respondida, pulada e não aplicável.
- **FR-002**: Pergunta sem resposta MUST vir sem agregado.
- **FR-003**: Escolha MUST trazer contagem e proporção sobre os que responderam por opção, na ordem
  da definição, com zero onde ninguém marcou, e opções fora da definição atual pelo valor, no fim.
- **FR-004**: Avaliação e escala MUST trazer média e distribuição da faixa inteira, com zeros.
- **FR-005**: NPS MUST trazer promotores (9–10), neutros (7–8), detratores (0–6), pontuação (−100 a
  100) e distribuição 0–10.
- **FR-006**: Texto livre MUST apenas sinalizar que há texto.
- **FR-007**: Não aplicável MUST ficar fora do denominador da proporção.
- **FR-008**: Pesquisa criada pelo modelo NPS MUST trazer o resumo do NPS da pergunta do modelo no
  topo, com respondentes.

**Taxa e desfechos**

- **FR-009**: O sistema MUST devolver exibidas, concluídas, dispensadas, abandonadas, em andamento,
  a taxa (concluídas ÷ exibidas, ausente sem exibição) e a definição textual da taxa.
- **FR-010**: Exibição sem desfecho depois do prazo de abandono MUST ser contada como abandonada;
  antes dele, em andamento.
- **FR-011**: O sistema MUST devolver a série diária (UTC) de exibidas e concluídas.

**Recortes**

- **FR-012**: Todas as leituras desta fatia MUST aceitar o mesmo recorte: período de abertura
  (inclusivo), atributo com valor, atributo ausente, e versão; combináveis.
- **FR-013**: O recorte aplicado MUST voltar descrito na resposta, incluindo se é de ausência.
- **FR-014**: Valor sem atributo, atributo vazio ou acima de 80, valor acima de 200, versão < 1 e
  período invertido MUST ser recusados com `400`.
- **FR-015**: O sistema MUST devolver o catálogo de atributos da pesquisa, com valores e contagem de
  exibições por valor.

**Avisos de leitura**

- **FR-016**: O sistema MUST devolver o tamanho da amostra (exibições com ao menos uma resposta
  dada, incluindo as congeladas quando somadas) e marcar amostra pequena abaixo do limiar
  configurável (padrão 10).
- **FR-017**: No consolidado, cada pergunta MUST trazer se é comparável entre as versões que
  receberam exibição no recorte, e quais versões foram consideradas; com versão, sem marca.
- **FR-018**: O agregado congelado pela retenção MUST ser somado só em recorte apenas de versão (ou
  nenhum), e a resposta MUST informar se foi aplicado, o instante antes do qual as respostas foram
  descartadas e uma nota explicativa; sem congelado, nada sobre retenção.

**Respostas abertas**

- **FR-019**: O sistema MUST listar as respostas de texto livre do recorte, paginadas (padrão 20,
  máximo 100), da mais recente para a mais antiga, com o enunciado.
- **FR-020**: A listagem MUST aceitar termo de busca de até 200 caracteres, sem diferenciar
  maiúsculas; termo vazio é ignorado.
- **FR-021**: Texto vencido pelo prazo de texto livre da aplicação MUST NOT aparecer.
- **FR-022**: Cada resposta MUST trazer o contexto legível das outras perguntas respondidas na mesma
  exibição, obtido numa consulta para a página inteira.

**Export**

- **FR-023**: O sistema MUST exportar CSV RFC 4180 com BOM, uma linha por exibição do recorte
  (inclusive dispensadas e abandonadas), em ordem de abertura.
- **FR-024**: Colunas: identificador da exibição, identificação opaca do respondente, número da
  versão, desfecho resolvido, abertura, fechamento, versão do SDK, uma por atributo e uma por
  pergunta (enunciado e chave).
- **FR-025**: Múltipla escolha MUST sair separada por ponto e vírgula; não aplicável `n/a`; pulada e
  texto vencido vazios.
- **FR-026**: Pesquisa com texto livre em alguma versão publicada MUST levar cabeçalho de aviso de
  dado pessoal.
- **FR-027**: Erro de recorte, de superfície ou de escopo MUST ser respondido antes de qualquer byte
  do arquivo.

**Transversais**

- **FR-028**: Nenhuma leitura MUST alterar estado.
- **FR-029**: Pesquisa inexistente, malformada ou de outra aplicação MUST responder
  `survey.not_found`; aplicação malformada, `application.not_found`.
- **FR-030**: As leituras MUST pertencer à superfície administrativa.
- **FR-031**: Nenhum número MUST ser somado linha a linha em memória: cada agregado sai de consulta
  agrupada, e o export lê em lotes por cursor.

### Key Entities

- **Resultado da pesquisa**: taxa e desfechos, perguntas com agregado, amostra, recorte, catálogo de
  atributos, NPS do modelo, nota de retenção.
- **Agregado de pergunta**: escolha, numérico, NPS ou texto.
- **Comparabilidade de pergunta**: comparável e versões consideradas.
- **Recorte**: período, atributo, valor ou ausência, versão.
- **Resposta aberta**: exibição, pergunta, texto, instante, contexto.
- **Linha de export**: uma exibição com atributos e respostas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Os números da tela conferem com o que foi semeado, em todas as formas de pergunta.
- **SC-002**: A mesma definição de taxa de resposta aparece na tela e no export.
- **SC-003**: Nenhuma pergunta incomparável entre versões somadas aparece sem marca no consolidado.
- **SC-004**: O resultado consolidado de uma versão é o mesmo antes e depois do descarte por
  retenção.
- **SC-005**: Um export de qualquer tamanho abre íntegro numa planilha comum, com acentos corretos.
- **SC-006**: Nenhuma leitura ou export altera o estado gravado.
- **SC-007**: A visão geral da aplicação é composta pelo painel sem nenhuma rota além das desta
  fatia.

## Assumptions

- O prazo de abandono é o mesmo da coleta (`pitaco.collect.display-timeout`, 30 minutos).
- O limiar de amostra pequena é `pitaco.results.small-sample-threshold` (10).
- Rótulos das opções vêm da definição atual; opções antigas aparecem pelo valor.
- A visão geral da aplicação, inclusive a variação entre períodos, é cálculo do painel.

## Dependencies

- `backend 004-response-collection` e `backend 006-collection-read-list`: exibições, respostas,
  atributos e respondentes.
- `backend 003-survey-authoring` e `backend 009-advanced-authoring`: perguntas, versões, forma da
  pergunta para comparabilidade, modelo NPS e não aplicável.
- `backend 011-privacy-retention`: agregado congelado e prazo de texto livre.
- `backend 013-interaction-events`: leitura de comportamento, que reaproveita o recorte desta fatia.
