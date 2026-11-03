# Phase 0 — Pesquisa e decisões

Cada item fixa uma decisão que o desenho assume. As decisões de
[001](../001-painel-operacao-pesquisas/research.md) continuam valendo — Server Components para
leitura, Server Actions com `useActionState` para escrita, resultado tipado, `refresh()` para
realinhar a tela — e não são redecididas aqui.

---

## R1 — Tudo cresce em `surveys`; o catálogo fica em `applications`

**Decisão**: modelo, condição, exposição, aviso de texto livre, avisos de publicação e
duplicação entram em `src/features/surveys/`. O catálogo observado (eventos e atributos) entra em
`src/features/applications/`, exportado por `index.ts`. A página de disparo, em `app/`, lê as duas
features e passa o catálogo para `TriggerPanel` como lista de nomes e de `{ name, values }`.

**Rationale**: o critério do Princípio I é o agregado. Condição pertence à pergunta, exposição e
aviso pertencem à pesquisa, avisos são calculados sobre a pesquisa. O catálogo, ao contrário, é
da aplicação: um atributo visto não pertence a pesquisa nenhuma, e o backend o expõe sob
`/applications/{id}/events` e `/attributes`. `surveys` declara só o tipo de entrada
(`ObservedAttributeSuggestion`), sem importar `applications` — a ponte é feita em `app/`.

**Alternativas consideradas**:
- *Feature `catalog` própria*: rejeitada. Duas leituras sem tela própria não justificam fronteira
  nova.
- *`surveys` importar `listObservedEvents`*: rejeitada. Criaria dependência `surveys →
  applications` só para uma página; a composição em `app/` é o lugar previsto pela constituição.

---

## R2 — Condição: forma no painel, invariante no backend, erro no campo

**Decisão**: `questionFormSchema` valida apenas a forma da condição — operador escolhido, ao menos
um valor, um único valor em igual e diferente, mínimo e máximo em `between`. Se o valor cabe nas
opções ou na escala da origem é decidido pelo backend. A recusa dele traz `field`
(`condition.sourceKey`, `condition.operator`, `condition.values`, `condition`), e esses mesmos
nomes, reunidos em `CONDITION_FIELDS`, são as chaves de erro do formulário — por isso o erro do
backend aparece no mesmo `FieldMessage` que o erro local (`field-error-condition.values`).

**Envio**: os controles visíveis da condição (`Select` do Radix) editam um rascunho em estado; o
que viaja no `FormData` são campos ocultos com os nomes que `readQuestionInput` lê
(`conditionSourceKey`, `conditionOperator`, `conditionValues` repetido, `conditionMin`,
`conditionMax`). Condição desligada não renderiza os campos — e campo não renderizado não vai.

**Recusa sem perda**: depois de uma action o React reseta o formulário e os `Select` do Radix
voltam ao valor inicial. A condição enviada é guardada num `ref` no `onSubmit` e restaurada num
efeito quando o estado volta com erro; `values` não serve, porque guarda só o último de cada
campo repetido.

**Origens e operadores** (`lib/condition.ts`): `conditionSources` devolve as perguntas anteriores
pela posição, sem a própria e sem texto livre — texto livre não tem valor com que comparar.
`operatorsFor` oferece faixa só a origens numéricas. `scaleOf` dá ao NPS a escala fixa 0–10.

**Resumo legível**: `describeCondition` nomeia a origem por `P{posição}` e a opção pelo rótulo
que o autor escreveu; múltipla escolha fala em "incluir"; origem que sumiu vira "a pergunta de
origem" em vez de quebrar.

**Alternativas consideradas**:
- *Validar a faixa contra a escala no painel*: rejeitada. Duas fontes de verdade que divergem em
  silêncio — a mesma razão pela qual 001 não replica quantidade mínima de opções.
- *Erro geral no topo do formulário*: rejeitada. Com origem, operador e valores na tela, a pessoa
  não saberia qual campo corrigir.

---

## R3 — Arrastar e soltar com dnd-kit

**Decisão**: `SortableQuestionsList` usa `@dnd-kit/core` e `@dnd-kit/sortable` com
`PointerSensor` (distância mínima de 4px, para o clique em botões do cartão não virar arrasto) e
`KeyboardSensor` com `sortableKeyboardCoordinates`. A alça é um `Button` com `aria-label`
"Arrastar pergunta …"; anúncios e instruções para leitor de tela são escritos em português.

**Testabilidade**: a lógica de soltar é separada do componente. `draggedOrder` (pura, em
`lib/question-order.ts`) calcula a permutação ou devolve `undefined` quando não há o que enviar;
`reorderOnDrop` recebe o evento e funções de efeito, e é testada sem simular ponteiro.

**Ordem pretendida**: ao soltar, a lista passa a mostrar a ordem pedida (`pendingIds`) enquanto a
action roda, com as alças desabilitadas. A resposta realinha pelo `refresh()`; em recusa, o
estado pendente é limpo e a ordem volta à do servidor, com a mensagem.

**Hidratação**: o `DndContext` recebe `id` estável (`questions-{surveyId}`). Sem ele, o
`aria-describedby` gerado pelo dnd-kit diverge entre servidor e cliente.

**Uma ação, dois gestos**: arrastar e as setas de 001 enviam a mesma permutação completa. As setas
continuam, porque são o caminho mais curto para mover uma posição.

**Alternativas consideradas**:
- *HTML5 drag-and-drop nativo*: rejeitada. Não funciona por teclado nem em toque, e a
  acessibilidade teria de ser reconstruída à mão.
- *Reordenação otimista sem estado pendente*: rejeitada. A lista "pularia" de volta enquanto a
  resposta não chega.

---

## R4 — Pré-visualização: o renderizador do SDK, só no navegador

**Decisão**: `SurveyPreview` importa `PitacoPreview` de `@pitaco/react-native/preview` com
`next/dynamic` e `ssr: false`. O `next.config.ts` declara `transpilePackages:
["@pitaco/react-native"]` e o alias `react-native → react-native-web` (Turbopack e webpack), com
as extensões `.web.*` primeiro. O SDK entra como dependência local (`file:../sdk`).

**Mapeamento**: cada `Question` vira o formato que o SDK recebe na entrega (`type` em maiúsculas,
`options`, `range`, `condition`), num esquema de rascunho (`surveyId: "draft"`). `resetKey` é a
serialização das perguntas: mudou o conteúdo, a pré-visualização recomeça. O tema segue o
`resolvedTheme` do painel.

**Atualização ao vivo**: `QuestionForm` aceita `onLiveUpdate` e monta um rascunho de `Question` a
partir do próprio `FormData` a cada `onChange` do formulário e a cada mudança de estado
controlado (tipo, opções, condição). Enquanto um formulário está aberto, a pré-visualização mostra
só esse rascunho; fechado, a pesquisa inteira. "Visualizar" isola uma pergunta.

**Eventos**: o `onEvent` do SDK alimenta uma lista local, mostrada do mais recente para o mais
antigo, com o `data` resumido numa linha e o evento completo ao expandir. Nada vai ao backend.

**Alternativas consideradas**:
- *Desenhar a pesquisa com componentes do painel*: rejeitada. Duplicaria o layout do SDK e
  mentiria na primeira mudança dele.
- *Renderizar no servidor*: rejeitada. react-native-web no servidor exige configuração de estilo
  que não traz ganho para um quadro interativo.
- *Formulário inteiramente controlado para atualizar a cada tecla*: rejeitada. O `onChange` do
  formulário já entrega cada alteração sem reescrever os campos como controlados.

---

## R5 — Edição no lugar da lista

**Decisão**: "Editar" guarda o identificador da pergunta em estado de tela (`editingId`) e a
coluna da montagem passa a mostrar o `QuestionForm` preenchido, num `Card`, no lugar da lista
arrastável e do botão de adicionar. Salvar (sucesso da action) ou concluir limpa o estado e a
lista volta, já realinhada pelo `refresh()`.

**Rationale**: com a pré-visualização ao lado, a coluna da esquerda é o espaço de trabalho de uma
pergunta por vez. Manter a lista arrastável ativa enquanto um formulário está aberto permitiria
reordenar no meio de uma edição, com a pré-visualização mostrando o rascunho de uma pergunta que
mudou de posição.

---

## R6 — Exposição e aviso: PATCH na pesquisa, `null` para remover

**Decisão**: `updateSurveyExposure` e `updateFreeTextNotice` usam `PATCH
/applications/{a}/surveys/{s}`. O PATCH distingue campo ausente (não mexe) de `null` (remove);
como os formulários sempre mostram cota e texto, o que ficou em branco vai como `null` de
propósito. Os checkboxes do Radix só enviam algo quando marcados, por isso isenção e aviso
viajam num `input type="hidden"` com `true`/`false` explícitos.

**Revalidação**: `refresh()`. A listagem de pesquisas não mostra prioridade, cota nem aviso;
realinhar a tela atual basta.

**Progresso da cota**: lido de `GET …/quota-progress`, separado da pesquisa, porque a contagem é
da coleta. `describeQuota` dá "Sem cota" quando nenhuma das duas fontes tem cota.

**Alternativas consideradas**:
- *Exposição como parte da versão*: rejeitada pelo backend e seguida aqui — mudar prioridade não
  muda o que o respondente vê.

---

## R7 — Catálogo observado como sugestão, na maior página

**Decisão**: a página de disparo lê eventos e atributos com `size = MAX_SIZE` (100), sem
paginação na tela. Sugestões são botões que preenchem o campo; o campo continua livre.

**Rationale**: eventos e atributos de um app são poucos por natureza. Restringir ao catálogo
impediria configurar o disparo antes de o app enviar o evento pela primeira vez — que é a ordem
normal de trabalho.

---

## R8 — Avisos de publicação: código aberto, mensagem única

**Decisão**: `publicationWarningSchema.code` é `string`, não enum. `warningMessage` traduz os
quatro códigos conhecidos (`trigger.competing_surveys`, `segmentation.no_known_match`,
`segmentation.contradictory`, `compatibility.unsupported_by_majority`) e cai numa mensagem
genérica para o resto. A regra de desempate é uma constante (`TIEBREAK_RULE`) dita igual no
painel de exposição e no aviso de concorrência.

**Onde aparecem**: na publicação, com a frase "Nenhum aviso impede a publicação" — e na tela de
disparo, onde a causa costuma estar. Em nenhuma das duas o botão de publicar depende deles.

**Rationale**: impedimento é enum fechado porque governa um botão; aviso só informa, e um aviso
novo do backend não deve derrubar a tela de quem está publicando.

---

## R9 — Duplicar: destino entre as ativas, montagem da cópia em seguida

**Decisão**: `SurveyHeader` lê, em paralelo com a pesquisa e as transições, as aplicações ativas
(`listApplications({ status: "active", size: 100 })`). O diálogo oferece essas aplicações, com a
atual marcada "(esta)" e incluída mesmo que não venha na lista. `duplicateSurveyAction` revalida
a listagem da aplicação de destino e redireciona para a montagem da cópia sob ela.

**Rationale**: uma aplicação inativa não entrega pesquisa — duplicar para ela não tem uso. O
corpo da resposta diz em que aplicação a cópia nasceu; o painel confia nele, não no que enviou.

---

## R10 — Modelo como grupo de opções, não como lista suspensa

**Decisão**: `SurveyForm` mostra as quatro opções (Em branco, NPS, CSAT, CES) como um
`radiogroup` de cartões, cada um com a descrição de uma linha de `SURVEY_TEMPLATE_OPTIONS`. O
valor vai num campo oculto; `blank` ou vazio vira ausência em `surveyCreateFormSchema`.

**Rationale**: a descrição é o que ajuda a escolher, e uma lista suspensa a esconderia.

---

## R11 — Servidor de API simulado

**Decisão**: `e2e/stub-api/routes/authoring.ts` (duplicação) e `exposure.ts` (progresso de cota,
avisos de publicação, atributos) entram no registro de rotas; `surveys.ts` passa a aceitar
`template`, condição, exposição e aviso; `seed.ts` ganha `/stub/observed-events` e
`/stub/observed-attributes`, porque só o SDK alimenta o catálogo. Os testes que dependem dessas
semeaduras se pulam com `E2E_API=real`, com o motivo escrito.
