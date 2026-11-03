# Phase 0 — Pesquisa e decisões

Cada item resolve uma incógnita do Technical Context ou fixa uma decisão que o desenho da
Phase 1 assume. As decisões de 001 continuam valendo e **não** são redecididas aqui; quando
esta entrega apenas as aplica, o item diz isso e aponta para lá.

Referências a 001: [research de 001](../001-painel-operacao-pesquisas/research.md).

---

## R1 — Feature nova `collect`, e não extensão de `surveys`

**Decisão**: criar `src/features/collect/`, espelhando o módulo `collect` do backend. A
dependência `collect → surveys` existe, é unidirecional, e passa pelo `index.ts` público de
`surveys`.

**Rationale**: o critério do Princípio I é o agregado, não a tela. O respondente pertence à
**aplicação**, não a uma pesquisa, e seu histórico atravessa pesquisas diferentes — uma feature
`surveys` que lesse respondentes estaria lendo fora do próprio agregado. A exibição, por sua vez,
tem identificador global e é alcançada por dois eixos. O backend chegou à mesma separação e pelo
mesmo motivo, o que é evidência, não coincidência.

`collect` precisa de duas coisas de `surveys`: `getVersion` (para dar enunciado às respostas,
[R2](#r2)) e `QUESTION_TYPE_LABELS`. Ambas já estão exportadas na fronteira pública de `surveys`.
`surveys` não passa a conhecer `collect` — a aba "Exibições" é composta em `app/`, não dentro da
feature. Sem ciclo.

**Alternativas consideradas**:
- *Tudo dentro de `surveys`*: rejeitada. Colocaria respondentes — que não têm pesquisa — dentro da
  feature de pesquisas, e a fronteira deixaria de significar coisa alguma.
- *Duas features, `displays` e `respondents`*: rejeitada. Compartilham a exibição como entidade
  central e os mesmos filtros; separá-las criaria exatamente o import cruzado que o Princípio I
  proíbe, ou uma terceira feature só para o que sobrasse.

---

## R2 — Enunciado das respostas: ler a versão exibida

**Problema**: o detalhe da exibição devolve cada resposta identificada por `questionKey` — uma
chave estável, sem texto. FR-011 exige que a resposta apareça junto do enunciado da pergunta que a
originou, **na versão exibida** (não na atual).

**Decisão**: a página de detalhe faz duas leituras em paralelo — o detalhe da exibição e
`getVersion(applicationId, surveyId, versionNumber)`, usando o `versionNumber` que o próprio
detalhe carrega. O casamento é por `key`, em uma função pura `matchAnswersToQuestions` em
`lib/answers.ts`, testada isoladamente.

**Ordem**: a fonte da ordem é a **lista de respostas**, que a API já devolve na ordem das
perguntas da versão. A versão entra só como enriquecimento de texto. Isso evita que uma
divergência entre as duas leituras reordene ou esconda respostas.

**Degradação**: se a leitura da versão falhar, o detalhe **ainda renderiza** — as respostas
aparecem identificadas pela chave, com aviso de que os enunciados não puderam ser carregados. Uma
resposta cuja chave não exista na versão lida também aparece, marcada como pergunta não
encontrada. O princípio: nenhuma resposta some da tela por falha de enriquecimento.

**Alternativas consideradas**:
- *Pedir ao backend que devolva o enunciado no detalhe da exibição*: rejeitada. Duplicaria o
  conteúdo congelado da versão dentro da exibição e cresceria a resposta por linha, exatamente o
  que a decisão D-02 do backend evitou na listagem.
- *Ler a versão e usá-la como fonte da ordem*: rejeitada, pelo motivo de ordem acima.

---

## R3 — Fuso horário: manter o fuso de referência, e dizê-lo

**Tensão**: FR-027 pede "fuso local de quem lê, com o fuso indicado". O painel, desde 001, formata
com `Intl.DateTimeFormat("pt-BR", { timeZone: "America/Sao_Paulo" })` em `shared/lib/format.ts`.

**Decisão**: manter o fuso de referência fixo e **rotulá-lo na tela**. `format.ts` ganha uma
constante exportada com o rótulo do fuso (ex.: `Horários em Brasília (UTC−3)`), exibida uma vez
por tela de coleta. O filtro de período interpreta o que a pessoa digita **no mesmo fuso**, para
que filtrar por "8 de setembro" case com o que a tela mostra como 8 de setembro.

**Rationale**: as telas são Server Components. O fuso do navegador não existe no servidor; obtê-lo
exigiria `"use client"` em toda célula de data (contra o "server first" da constituição) ou
aceitar divergência de hidratação. Além disso, um painel operado por um time em um fuso ganha mais
com um fuso comum e explícito do que com cada pessoa vendo um horário diferente do colega.

**Consequência para a spec**: FR-027 deve ser reescrito. O que a decisão preserva é a intenção
real do requisito — nenhum instante é exibido sem que se saiba em que fuso ele está.

**Alternativas consideradas**:
- *Formatar no cliente*: rejeitada pelo custo de `"use client"` espalhado e pela inconsistência com
  as telas de 001.
- *Exibir em UTC cru*: rejeitada. Empurra a conversão para a cabeça de quem lê.

---

## R4 — Filtros de período e de desfecho sem dependência nova

**Decisão**: o filtro de período usa `<Input type="datetime-local">`, o primitivo `Input` que já
está instalado. O filtro de desfecho usa `Select`, do mesmo jeito que
`ApplicationStatusFilter` já faz.

**Rationale**: o date-picker do shadcn traz `Calendar`, que traz `react-day-picker` — uma
dependência de runtime nova, que a constituição exige justificar. Para um filtro de dois campos
em uma tela administrativa, o controle nativo resolve, é acessível por padrão e não custa bundle.

**Comportamento do formulário**: é `"use client"`, porque escreve na URL a partir de uma
interação — mesma justificativa do filtro de situação de 001. Trocar qualquer filtro **volta para
a primeira página**, pelo mesmo motivo que já vale lá.

**Alternativas consideradas**:
- *Date-picker do shadcn*: rejeitada pelo custo acima.
- *Filtro por formulário com `GET` nativo*: rejeitada. Perderia os demais `searchParams` que não
  estivessem no formulário.

---

## R5 — Rota do detalhe de exibição: plana sob a aplicação

**Decisão**: `/aplicacoes/[applicationId]/exibicoes/[displayId]`, e não aninhada na pesquisa.

**Rationale**: a exibição é alcançada de dois eixos — da pesquisa e do respondente. Aninhá-la na
pesquisa obrigaria a uma segunda rota para o caminho vindo do respondente, ou a um caminho que
mente sobre de onde se veio. O identificador de exibição é global e a própria exibição já
determina sua pesquisa. É a mesma conclusão que o backend registrou na decisão D-09, e vale aqui
pelo mesmo motivo.

**Consequência**: a rota fica sob o layout da aplicação, herdando breadcrumb e navegação de seção,
mas **fora** do layout da pesquisa — o cabeçalho de pesquisa não aparece. O vínculo para a
pesquisa é explícito no corpo da tela (FR-016), o que é mais honesto do que fingir contexto.

---

## R6 — Três vazios diferentes, e como distingui-los

FR-025 exige distinguir "nunca houve coleta" de "o filtro não encontrou nada". Na listagem de
exibições de uma pesquisa há um terceiro caso, que a spec traz nos edge cases: a pesquisa **nunca
foi publicada**, e portanto nunca poderia ter sido exibida.

**Decisão**: a página de exibições lê também a pesquisa (`getSurvey`), em paralelo, e decide entre
três vazios com uma função pura testável:

| Condição | Vazio exibido |
|---|---|
| `publishedVersionNumber` ausente | "Esta pesquisa ainda não foi publicada" + vínculo para a publicação |
| Nenhum filtro aplicado, zero itens | "Nenhuma exibição ainda" — a pesquisa está no ar e ainda não foi vista |
| Algum filtro aplicado, zero itens | "Nenhuma exibição neste recorte" + ação de limpar filtros |

**Nota sobre SC-009**: a leitura de `getSurvey` não viola "nenhuma leitura só para produzir um
número" — ela não produz número nenhum, e sim a diferença entre dois vazios que dizem coisas
opostas a quem lê. Nas outras três telas, o vazio tem só duas variantes e não exige leitura extra.

---

## R7 — Filtro por versão: corrigir o contrato no backend

**Problema encontrado no planejamento**: `ListDisplaysQueryDTO` filtra por `versionId`, um UUID
validado por regex. Mas `SurveyVersionResponseDTO` — a única superfície de versão que o painel
tem — **não expõe identificador algum**, só `number`. O painel só descobre um `versionId` lendo as
próprias exibições, isto é, só conhece as versões que já apareceram na página atual. Um filtro
construído sobre isso seria incorreto por construção.

**Decisão**: o backend passa a filtrar por **número** da versão. O parâmetro `versionId` sai do
DTO de consulta e entra `versionNumber` (inteiro, mínimo 1). O `versionId` **permanece nas
respostas** — é identidade legítima, já publicada, e não custa nada.

**Rationale**: o número já é o identificador da versão no painel — o próprio backend documenta
isso, na descrição de `DisplaySummaryResponseDTO.versionNumber`: *"Número da versão exibida, o que
identifica a versão no painel"*. E o número é inequívoco no contexto: a rota já é
`/applications/{applicationId}/surveys/{surveyId}/displays`, então a versão está escopada por
pesquisa. Filtrar por número é filtrar pelo que a pessoa vê na tela.

**Tamanho da mudança** (cinco pontos, nenhum estrutural):

1. `ListDisplaysQueryDTO` — trocar o campo e sua validação; `toInput` passa o número.
2. `ListSurveyDisplaysUseCase.Input` — `Optional<Integer> versionNumber` no lugar de
   `Optional<SurveyVersionId> versionId`.
3. `SurveyDisplayRepository.ListDisplaysQuery` — mesma troca.
4. `SurveyDisplayJpaRepository.findSummaryPage` — a consulta principal **já junta**
   `SurveyVersionJpaEntity v`, então o predicado vira `(:versionNumber is null or v.number =
   :versionNumber)`. A `countQuery` ainda não junta `v` e passa a juntar.
5. Documentação Swagger do parâmetro e testes do backend que exercitam o filtro.

**Ponto de atenção na implementação**: hoje a `countQuery` não faz junção; ao adicioná-la, é
preciso garantir que a junção seja interna e por igualdade de identificador, para que a contagem
continue idêntica à da consulta principal. Um teste que compare `total` com o tamanho do conjunto
sem paginação cobre isso.

**Alternativas consideradas**:
- *Expor `id` em `SurveyVersionResponseDTO`*: rejeitada. Publicaria um identificador que nenhuma
  tela mostra, só para servir de token de filtro, e obrigaria o painel a carregar a lista completa
  de versões para montar o seletor.
- *Deixar o filtro de fora desta entrega*: era o encaminhamento antes da autorização para mexer no
  backend. Rejeitada agora: o custo da correção é menor que o de entregar uma listagem que não
  filtra pelo eixo mais útil.

---

## R8 — Uma tabela de exibições, dois eixos

**Decisão**: um único componente `DisplaysTable`, com uma coluna opcional de pesquisa, serve as
duas listagens — a da pesquisa (que não mostra a coluna, pois a pesquisa é o contexto) e a do
respondente (que mostra).

**Rationale**: a cláusula de simplicidade da constituição permite antecipar abstração "quando a
forma é óbvia e a superfície mínima". Aqui não há antecipação alguma: os dois usos existem na
mesma entrega, as colunas coincidem em tudo menos uma, e a alternativa seria duas tabelas
divergindo na primeira manutenção. Não é um `DataTable` genérico — R14 de 001 continua valendo:
nada de abstração de tabela para "qualquer dado".

**Fronteira**: a diferença entra como propriedade explícita (`showSurvey`), não como inferência a
partir dos dados.

---

## R9 — Servidor de API simulado para o E2E

**Decisão**: `e2e/stub-api/routes/` ganha `displays.ts` e `respondents.ts`, registrados em
`index.ts`, seguindo o que R9 de 001 estabeleceu. O `store.ts` passa a guardar exibições,
respostas e respondentes semeados pelo teste.

**Rationale**: nada novo — é a aplicação do padrão existente. O ponto que exige cuidado é que o
teste E2E de coleta precisa de dados que o painel **não sabe criar** (o painel é somente leitura,
e quem cria exibição é o SDK). O `stub-api` semeia esses dados diretamente, o que mantém a regra
de cada teste criar e limpar os próprios dados sem inventar uma tela de escrita que não existe.

---

## R10 — Nada de agregação, e o que isso significa no desenho

**Decisão** (resolvendo FR-030, FR-031 e SC-009): nenhuma tela emite consulta cuja única
finalidade seja produzir um número. A única quantidade exibida é o `total` que a paginação já
devolve junto com os itens, mostrado pelo componente `Pagination` que já existe.

**Consequência prática**: quem quiser saber quantas exibições foram concluídas aplica o filtro de
desfecho e lê `pagination-info`. Isso é uma consulta, a mesma que já traz os itens — não uma
consulta extra.

**Verificação**: a revisão desta feature deve conseguir contar as chamadas de API por tela e casar
com a tabela de leituras do [contrato de rotas](./contracts/ui-routes.md#leituras-por-tela).
