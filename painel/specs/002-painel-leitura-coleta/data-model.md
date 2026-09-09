# Phase 1 — Modelo de dados do painel

O painel não tem banco. "Modelo de dados" aqui é o conjunto de tipos que atravessam a fronteira
da API e viram domínio da feature `collect`, mais as regras de validação que essa travessia
aplica. Todo tipo nasce de um schema Zod: a constituição exige validação de fronteira, e o
cliente HTTP só devolve dado que passou pelo schema ([R1 de 001](../001-painel-operacao-pesquisas/research.md)).

Convenção herdada de 001: campo **ausente** no backend vira `undefined` (via `.optional()`), nunca
`null` e nunca um valor de preenchimento. É o que permite exibir ausência como ausência.

---

## Desfecho (`DisplayOutcome`)

Em que a exibição parou.

| Valor | Rótulo no painel | Significado |
|---|---|---|
| `STARTED` | Em andamento | Foi aberta e ainda não terminou |
| `COMPLETED` | Concluída | A pessoa respondeu até o fim |
| `DISMISSED` | Dispensada | A pessoa fechou sem responder |

**Regra**: `closedAt` existe **se e somente se** o desfecho é final (`COMPLETED` ou `DISMISSED`).
O painel não recalcula isso — exibe o que veio — mas a distinção governa o texto de ausência:
exibição com desfecho `STARTED` e sem `closedAt` é "ainda aberta", não "sem data".

**Filtro**: os três valores acima são exatamente os que o backend aceita filtrar. `ABANDONED`
existe no domínio do backend mas nunca é gravado, e por isso não é oferecido — FR-006 fixa que o
painel não inventa opções que sempre devolveriam vazio.

---

## Exibição — resumo (`DisplaySummary`)

O que a listagem mostra. Projeção sem o instantâneo de atributos.

| Campo | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `id` | `string` | sim | identificador global; a chave da rota de detalhe |
| `versionId` | `string` | sim | identidade da versão; o painel não a exibe |
| `versionNumber` | `number` | sim | **é o que a tela mostra e o que o filtro usa** ([R7](./research.md#r7)) |
| `comparabilityGroup` | `number` | sim | versões do mesmo grupo têm respostas somáveis |
| `outcome` | `DisplayOutcome` | sim | |
| `sdkVersion` | `string` | **não** | ausente vira texto de ausência, nunca traço |
| `openedAt` | `string` (ISO UTC) | sim | |
| `closedAt` | `string` (ISO UTC) | **não** | ausente ⇒ ainda aberta |

**Ordenação**: a API já devolve da mais recente para a mais antiga (`openedAt` desc, desempate por
`id` desc). O painel **não reordena** — FR-004 é satisfeito pela API, e reordenar no cliente
quebraria a paginação.

---

## Exibição no eixo do respondente (`RespondentDisplay`)

`DisplaySummary` mais `surveyId`. É a mesma exibição vista de outro eixo: no histórico do
respondente a pesquisa varia, então precisa aparecer.

| Campo adicional | Tipo | Observação |
|---|---|---|
| `surveyId` | `string` | permite o vínculo para a pesquisa e a coluna extra da tabela ([R8](./research.md#r8)) |

---

## Exibição — detalhe (`DisplayDetail`)

`DisplaySummary` mais o que a listagem omite de propósito.

| Campo adicional | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `respondentId` | `string` | sim | vínculo para o histórico (FR-016) |
| `surveyId` | `string` | sim | vínculo para a pesquisa (FR-016) |
| `attributes` | `Record<string, string>` | sim | pode vir **vazio**; vazio é ausência exibível, não erro |
| `answers` | `Answer[]` | sim | vazio na exibição aberta e na dispensada (FR-015) |

**Regra de leitura**: `attributes` pertence à **exibição**, não ao respondente. A tela precisa
dizer isso em texto (FR-014) — o mesmo respondente pode ter instantâneos diferentes em exibições
diferentes, e tratá-los como perfil seria erro de interpretação, não de layout.

---

## Resposta (`Answer`)

O que foi respondido a uma pergunta dentro de uma exibição.

| Campo | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `questionKey` | `string` | sim | chave estável que atravessa versões |
| `status` | `AnswerStatus` | sim | `ANSWERED` \| `SKIPPED` \| `EXPIRED` |
| `text` | `string` | **não** | só em pergunta de texto livre |
| `number` | `number` | **não** | só em pergunta numérica |
| `options` | `string[]` | sim | vazio fora das perguntas de escolha |

### Situação da resposta — a distinção que FR-012 e SC-006 exigem

| Situação | Rótulo | Texto que a tela precisa dar |
|---|---|---|
| `ANSWERED` | — | mostra o valor conforme o tipo |
| `SKIPPED` | Pulada | a pessoa viu a pergunta e escolheu não responder |
| `EXPIRED` | Texto expirado | havia resposta; o prazo de retenção de texto livre da aplicação venceu e o conteúdo foi descartado |

Estas três **nunca** podem colapsar em "resposta vazia". É a regra mais fácil de quebrar desta
feature e por isso vira teste, não convenção: `EXPIRED` fala de dado que existiu e foi apagado por
política, `SKIPPED` fala de escolha de quem respondeu.

### Resposta enriquecida (`ResolvedAnswer`) — tipo interno

Produto de `matchAnswersToQuestions` ([R2](./research.md#r2)). Não atravessa a fronteira da API:
é montado no painel juntando a resposta com a pergunta da versão exibida.

| Campo | Origem | Quando falta |
|---|---|---|
| `answer` | detalhe da exibição | sempre presente — é a fonte da ordem |
| `question` | versão exibida, casada por `key` | `undefined` se a leitura da versão falhou ou a chave não existe naquela versão |

**Invariante testada**: `matchAnswersToQuestions(answers, questions).length === answers.length`,
em qualquer combinação. Nenhuma resposta desaparece por falha de enriquecimento.

---

## Respondente (`Respondent`)

Quem a aplicação já viu.

| Campo | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `id` | `string` | sim | chave da rota de histórico |
| `identityKind` | `RespondentIdentityKind` | sim | `APP_REFERENCE` \| `DEVICE` |
| `identityValue` | `string` | sim | opaco para o Pitaco; exibido como veio |
| `firstSeenAt` | `string` (ISO UTC) | sim | |
| `lastSeenAt` | `string` (ISO UTC) | sim | |

### Forma de identificação — FR-020

| Valor | Rótulo no painel |
|---|---|
| `APP_REFERENCE` | Referência da aplicação |
| `DEVICE` | Dispositivo |

O código cru nunca aparece na tela. O **valor** (`identityValue`), sim: é opaco para o Pitaco e
exibi-lo transformado seria mentir sobre o que a aplicação enviou.

---

## Filtros de listagem

Vivem em `searchParams` ([R12 de 001](../001-painel-operacao-pesquisas/research.md)). Nomes em
português, como as rotas.

| Param na URL | Query da API | Telas | Regra de leitura |
|---|---|---|---|
| `versao` | `versionNumber` | exibições da pesquisa | inteiro ≥ 1; inválido é ignorado |
| `desfecho` | `outcome` | ambas as listagens de exibição | só os três valores conhecidos; qualquer outro vira "todos" |
| `de` | `openedFrom` | ambas | data-hora local no fuso de referência → ISO UTC ([R3](./research.md#r3)) |
| `ate` | `openedTo` | ambas | idem |
| `page`, `size` | `page`, `size` | todas | `parsePaginationParams` já existente |

**Duas validações, propósitos diferentes** — a aparente contradição entre FR-007 ("recusar com
mensagem apontando o campo") e a regra de 001 ("URL inválida cai no padrão") se resolve porque são
momentos distintos:

| Momento | Comportamento | Por quê |
|---|---|---|
| Formulário de filtro, antes de navegar | Início posterior ao fim é **recusado** com mensagem no campo; não navega; preserva o digitado e a listagem (FR-007) | é entrada de pessoa, e a pessoa pode corrigir |
| Leitura do `searchParams` no servidor | Par incoerente ou valor malformado é **ignorado** e cai no padrão | URL é entrada de usuário, não contrato; nunca deve quebrar a tela |

**Filtro de versão**: o seletor lista os números de versão vindos de `getVersionComparability`,
que devolve todos os grupos com seus números sem paginação — evita carregar a listagem paginada de
versões só para montar um seletor.

---

## Dependência de `surveys`

| O que `collect` usa | De onde | Para quê |
|---|---|---|
| `getVersion` | `@/features/surveys` | enunciados das respostas ([R2](./research.md#r2)) |
| `getSurvey` | `@/features/surveys` | distinguir "nunca publicada" de "nunca exibida" ([R6](./research.md#r6)) |
| `getVersionComparability` | `@/features/surveys` | povoar o seletor de versão |
| `QUESTION_TYPE_LABELS`, `type Question` | `@/features/surveys` | rotular o tipo da pergunta no detalhe |

Todas já exportadas na fronteira pública de `surveys`. Nenhum import de caminho interno.
