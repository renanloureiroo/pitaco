# Phase 0 — Pesquisa e decisões: autoria de pesquisa

**Feature**: `003-survey-authoring` · **Data**: 2026-09-08

Cada decisão abaixo fecha uma incerteza do [plan.md](./plan.md) ou uma tensão da
[spec](./spec.md). O que não está aqui foi decidido em
[001-api-key-management/research.md](../001-api-key-management/research.md) e em
[002-api-key-read-list/research.md](../002-api-key-read-list/research.md), e continua valendo.

A spec não deixou nenhum `[NEEDS CLARIFICATION]` em aberto. O que este documento resolve são as
**três tensões internas** que só aparecem quando se tenta escrever o código — onde mora o
disparo, o que é invariante e o que é regra de publicação, e como o estado derivado convive com
o registro de transição — mais as escolhas estruturais que a chegada do segundo módulo força.

---

## D-01 — A pesquisa nasce em módulo próprio: `modules/survey`

**Decisão**: `modules/survey`, com as três camadas (`domain`, `application`, `infra`), ao lado de
`modules/app`. Nada de pendurar `Survey` dentro de `modules/app`.

**Rationale**: `modules/app` responde "quem é o cliente e como ele se autentica". A pesquisa
responde "o que se pergunta e quando". São vocabulários que não se cruzam: nenhuma invariante de
`Application` fala de pergunta, nenhuma invariante de `Survey` fala de chave. O precedente
contrário — a `ApiKey`, que ficou em `modules/app` pela decisão D-01 de 001 — se sustentava
porque a chave **não tem vida** fora da aplicação: ela é credencial da aplicação. A pesquisa tem
vida própria: ela vai ganhar entrega, coleta e resultado, cada uma com seu módulo, todas
apontando para cá.

**Alternativas descartadas**:
- *Dentro de `modules/app`* — sem custo hoje, com custo alto em três features: o módulo viraria
  o depósito de tudo que tem `application_id`, que é tudo.
- *Um módulo por agregado (`survey`, `version`, `question`)* — `Question` e `SurveyVersion` não
  têm ciclo de vida fora de `Survey`; separá-los produziria três módulos que só se chamam.

---

## D-02 — `ApplicationId` sobe para `core/identity`

**Decisão**: `ApplicationId` sai de `modules/app/domain/entities` e passa a
`core/identity/ApplicationId.java`, junto com seu teste. Os imports de `modules/app` são
atualizados; nenhum comportamento muda, o `code` `application.id_invalid` permanece.

**Rationale**: a regra do projeto é literal, e a constituição 1.1.0 a apertou — `core` guarda
"só o que **dois ou mais** módulos compartilham e o que inverte dependência de fato".
`ApplicationId` atende ao primeiro critério e é justamente o contraste com o presenter, que saiu
de `core` por não atender a nenhum dos dois (D-19): identificador compartilhado é vocabulário de
dois módulos, tradução entre camadas é assunto interno de um. `ApplicationId` nasceu no módulo
por não haver segundo caso. Ele existe agora: toda pesquisa pertence a uma aplicação, e o escopo por aplicação (FR-006) atravessa
cada leitura e cada escrita desta feature. O identificador da aplicação é o identificador do
inquilino do sistema inteiro, não um detalhe de `modules/app`.

A alternativa de deixá-lo onde está e importá-lo de `modules/survey` não viola a letra da
constituição — o Princípio I proíbe importação de *framework* no núcleo, não de outro módulo —
mas viola o que ele protege: dois módulos que se conhecem pelo domínio deixam de ser dois
módulos. E o custo da mudança é mecânico e acontece uma vez só; adiá-lo significa pagá-lo mais
tarde com três módulos apontando em vez de um.

**Alternativas descartadas**:
- *`modules/survey` importa `modules.app.domain.entities.ApplicationId`* — acoplamento de domínio
  entre módulos, que só cresce.
- *`modules/survey` declara o próprio `OwnerApplicationId`* — dois tipos para a mesma chave
  estrangeira, com conversão em toda borda e nenhuma garantia de que continuem iguais.

**Custo registrado**: se a feature 002 ainda não estiver em `main` quando esta começar, o
`plan.md` de 002 referencia o caminho antigo — texto, não código, e sem impacto de merge.

---

## D-03 — O acesso à aplicação é uma porta do módulo `survey`, não uma chamada ao módulo `app`

**Decisão**: `modules/survey/application/gateways/ApplicationScope`, porta Java pura com um
método:

```java
Optional<ApplicationScopeState> stateOf(ApplicationId applicationId);   // ACTIVE | INACTIVE
```

O adaptador `ApplicationScopeGateway` vive em `modules/survey/infra/gateways` e delega para a
porta `ApplicationRepository` de `modules/app`, injetada como bean. O fake
`InMemoryApplicationScope` vive em `testsupport/gateways`.

**Rationale**: o caso de uso precisa de exatamente duas informações sobre a aplicação — ela
existe? está ativa? (FR-002). Uma porta com essa forma diz isso e nada mais, e é o que permite
testar `CreateSurveyUseCase` sem mock e sem arrastar `modules/app` para dentro do teste. A
travessia entre módulos acontece na infra, que é onde o cabeamento mora.

Não é abstração antecipada: é a porta de saída que o caso de uso já precisaria ter para ser
testável sobre fake, como manda o Princípio III.

**Alternativas descartadas**:
- *Injetar `ApplicationRepository` direto no caso de uso de `survey`* — o caso de uso passaria a
  depender do agregado inteiro de outro módulo para perguntar um booleano.
- *Consulta SQL direta a `applications` a partir de `survey`* — duas tabelas, um dono; a primeira
  mudança de schema em `applications` quebraria `survey` sem aviso.

---

## D-04 — O rascunho **é** uma versão: pergunta, disparo e regra moram na versão

**Resolve**: a contradição entre as *Key Entities* da spec ("Pergunta pertence a uma versão";
"Disparo: um por pesquisa") e FR-025 ("impedir alteração de pergunta, **disparo ou regra** de
pesquisa cujo conteúdo esteja congelado").

**Decisão**: `SurveyVersion` é o recipiente de todo o conteúdo — perguntas, disparo e regras de
segmentação. Ela tem número e estado (`DRAFT` ou `PUBLISHED`). Criar uma pesquisa cria, na mesma
transação, a versão 1 em `DRAFT`. Publicar troca o estado dessa versão para `PUBLISHED` e a torna
imutável. Abrir nova versão cria a versão N+1 em `DRAFT`, com cópia integral do conteúdo da
versão publicada corrente — perguntas **e** disparo **e** regras.

`Survey` fica com o que atravessa versões: aplicação dona, nome, ciclo de vida, ponteiro para a
versão publicada corrente e para o rascunho aberto.

**Rationale**: FR-025 congela os três juntos, e só existe um jeito de congelar três coisas com um
único ato: elas estarem no mesmo recipiente. "Um disparo por pesquisa" continua verdadeiro do
ponto de vista de quem usa — em qualquer instante existe um disparo vigente, o da versão
publicada —, e passa a ser verificável como invariante local: um disparo por versão.

O ganho concreto está em US6: sem isso, corrigir a janela de uma pesquisa no ar mudaria
retroativamente o significado da versão 1, que é justamente o que a imutabilidade da versão
promete impedir.

**Alternativas descartadas**:
- *Disparo na pesquisa, perguntas na versão* — o disparo mutável depois da publicação contradiz
  FR-025 diretamente, e faria a versão 1 mentir sobre as condições em que foi coletada.
- *Rascunho como entidade à parte da versão* — duplicaria pergunta, disparo e regra em dois
  formatos, com um conversor entre eles e duas oportunidades de divergir.

**Consequência registrada**: a classificação da mudança (FR-034) compara **apenas perguntas**,
como a spec manda. Mudar disparo ou regra entre versões não força semântica — o que se pergunta
não mudou, mudou a quem se pergunta.

---

## D-05 — Estado exposto é derivado; o armazenado é o ciclo de vida

**Decisão**: a coluna guarda `SurveyLifecycle` ∈ {`DRAFT`, `PUBLISHED`, `PAUSED`, `ENDED`} — o que
depende de um comando. O estado que a API devolve é `SurveyState` ∈ {`draft`, `scheduled`,
`active`, `paused`, `ended`}, derivado na leitura a partir do ciclo de vida, da janela da versão
publicada e do instante atual:

| Ciclo de vida | Janela | Estado exposto |
| --- | --- | --- |
| `DRAFT` | — | `draft` |
| `PUBLISHED` | ainda não abriu | `scheduled` |
| `PUBLISHED` | aberta (início inclusivo) | `active` |
| `PUBLISHED` | fechada | `ended` |
| `PAUSED` | qualquer | `paused` |
| `ENDED` | qualquer | `ended` |

**Rationale**: é a premissa explícita da spec — a transição pela janela é "observada na leitura,
sem depender de rotina agendada" — e é o mesmo padrão que a D-04 de 002 usou para `ApiKeyStatus`
sobre `revoked_at`: estado que já está expresso por outro dado não ganha coluna. Sem isso seria
preciso um scheduler, e um scheduler que atrasa faz a API mentir.

A tabela também resolve dois edge cases da spec de graça: pausada com janela fechada continua
`paused` (a linha de `PAUSED` ignora a janela), e publicar no instante exato da abertura dá
`active` porque o início é inclusivo.

**Alternativas descartadas**:
- *Coluna `state` atualizada por job* — janela de mentira entre o instante do limite e a execução
  do job, e um componente novo para manter.
- *Derivar tudo, inclusive `PAUSED`* — impossível: pausar é comando, não tem dado que o expresse.

---

## D-06 — O histórico é a união do que foi comandado com o que a janela produziu

**Resolve**: FR-029 exige registrar toda transição "distinguindo manual de automática", mas D-05
diz que a transição pela janela não acontece em lugar nenhum — ela é observada.

**Decisão**: a tabela `survey_state_transitions` grava apenas as transições **comandadas**
(publicação, pausa, retomada, encerramento), com `reason` ∈ {`PUBLICATION`, `MANUAL_PAUSE`,
`MANUAL_RESUME`, `MANUAL_END`} e o instante do comando. A consulta de histórico devolve essas
linhas **mais** as transições derivadas da janela, calculadas na leitura a partir dos limites já
passados: `scheduled → active` no início da janela, `active → ended` no fim, com
`reason: WINDOW_OPENED` / `WINDOW_CLOSED`. Tudo ordenado por instante.

**Rationale**: quem lê o histórico quer a linha do tempo da pesquisa, não o extrato de uma tabela.
A transição pela janela tem instante conhecido e exato — é o limite da janela —, então não há
nada a inventar ao derivá-la. E manter a tabela só com o que foi comandado preserva a propriedade
que faz a derivação valer: nenhuma linha gravada depende do relógio de leitura.

O campo `actor` existe na tabela, sempre nulo, reservado para quando houver autenticação —
conforme a premissa da spec.

**Alternativas descartadas**:
- *Gravar a transição da janela na primeira leitura que a observa* — escrita em caminho de leitura,
  e o instante gravado seria o da leitura, não o do limite: o histórico ficaria dependente de
  alguém ter olhado.
- *Não expor as transições da janela* — FR-029 pede as automáticas explicitamente.

---

## D-07 — `QuestionKey` é um tipo, distinto de `QuestionId`

**Decisão**: dois identificadores por pergunta.

- `QuestionId` — identidade da linha **dentro de uma versão**. Nova a cada cópia de versão.
- `QuestionKey` — a chave estável (FR-011). Nasce na criação da pergunta, é copiada literalmente
  para as versões seguintes, e é única dentro da pesquisa.

**Rationale**: SC-008 pede que uma pergunta atravessando três versões seja reconhecível como a
mesma "sem comparar o texto". Um único identificador não consegue servir aos dois papéis: ou ele
se repete entre versões — e aí duas linhas distintas têm a mesma identidade, quebrando `Entity` —
ou ele muda, e a continuidade se perde. Dois tipos separam identidade de linhagem, e é a
linhagem que FR-034 (classificação) e a leitura consolidada futura vão consumir.

`QuestionKey` é `record` (value object), não `Id`: ele não identifica um agregado, marca uma
linhagem.

---

## D-08 — Coerência é invariante de construção; completude é regra de publicação

**Resolve**: a tensão entre o Princípio II ("um tipo de domínio em mãos é sempre válido") e o
cenário 3 de US2 — pergunta de escolha **sem opção** é aceita no rascunho.

**Decisão**: a linha é traçada entre o que está **errado** e o que está **faltando**.

*Invariante — recusada na construção, `DomainException`:*
- enunciado vazio (FR-012)
- opção em tipo que não aceita opção (FR-012)
- opções repetidas na mesma pergunta (edge case da spec)
- faixa de escala incoerente: mínimo ≥ máximo (FR-012)
- janela com fim não posterior ao início (FR-017)
- proporção fora de `[0, 1]` (FR-018)
- regra de presença/ausência com valor, ou de igualdade/diferença sem valor (FR-021)

*Completude — aceita no rascunho, impedimento na publicação (FR-023):*
- pesquisa sem nenhuma pergunta
- pergunta de tipo que exige opção, sem nenhuma opção
- disparo não definido

**Rationale**: o rascunho é a válvula de segurança que a spec descreve — ele substitui o ambiente
de homologação que não existe, e um rascunho que recusa incompleto não substitui nada. Mas isso
não relaxa o Princípio II: uma `Question` em mãos continua sendo sempre uma pergunta coerente. O
que ela pode ser é uma pergunta coerente e **inacabada**, e "inacabado" é uma propriedade da
pesquisa como um todo, verificada quando ela tenta sair do rascunho.

**Alternativas descartadas**:
- *Tudo invariante* — mataria o rascunho e contradiria US2 cenário 3.
- *Tudo regra de publicação* — deixaria uma pergunta de texto livre com opções circular pelo
  sistema até a publicação, e o Princípio II existe exatamente para impedir isso.

---

## D-09 — O catálogo de tipos carrega as próprias exigências

**Decisão**: `QuestionType` é enum com uma tabela de exigências embutida, não uma hierarquia de
subtipos de pergunta.

| Tipo | JSON | Opções | Faixa numérica |
| --- | --- | --- | --- |
| Escolha única | `single_choice` | exige | não aceita |
| Múltipla escolha | `multiple_choice` | exige | não aceita |
| Avaliação | `rating` | não aceita | exige |
| Escala | `scale` | não aceita | exige |
| NPS | `nps` | não aceita | fixa em 0–10 |
| Texto livre | `free_text` | não aceita | não aceita |

Cada valor responde `requiresOptions()`, `acceptsOptions()` e `requiresRange()`. `Question` é uma
única classe com `List<QuestionOption>` (vazia quando o tipo não aceita) e `Optional<ScaleRange>`.

**Rationale**: seis tipos, três atributos variáveis e nenhum comportamento polimórfico — a
diferença entre eles é de *dados aceitos*, não de conduta. Uma hierarquia de seis classes com
factory e visitor custaria seis arquivos para expressar uma tabela de três colunas, e a
constituição é explícita: abstração entra no segundo caso concreto que a exige. Se a autoria
condicional (fora de escopo) trouxer comportamento por tipo, a hierarquia entra então, com o caso
concreto na mão.

NPS tem faixa fixa 0–10 imposta na construção: informar outra é recusado.

---

## D-10 — Um único objeto serve à consulta de impedimentos e à recusa de publicação

**Decisão**: `PublicationImpediment` é `record` de domínio — `code`, `field` e, quando o
impedimento é de uma pergunta, `questionKey`. `SurveyVersion.publicationImpediments()` devolve a
lista completa, sempre. Dois consumidores:

- `CheckSurveyPublicationUseCase` (FR-022) devolve a lista, sem publicar. Status 200.
- `PublishSurveyUseCase` (FR-023) lança `SurveyNotPublishable` carregando a mesma lista quando ela
  não está vazia. `ErrorType.BUSINESS_RULE` → 422, com a lista na extensão `impediments` do corpo
  RFC 9457.

**Rationale**: SC-002 exige que 100% das recusas apontem o campo, e o cenário 9 de US4 exige que
consultar sem publicar dê "a mesma lista que a publicação usaria". "A mesma lista" é uma promessa
que só se sustenta se for literalmente o mesmo código — duas implementações divergem na primeira
regra nova. O acúmulo (relatar todos de uma vez, cenário 6) sai de graça: a coleção é o retorno
natural, e o `fail fast` é que precisaria de esforço.

**Alternativas descartadas**:
- *Uma exceção por impedimento, lançada na primeira falha* — contradiz o cenário 6 e faz quem
  monta a pesquisa descobrir os problemas um por requisição.
- *`errors: Map<String,String>` como no erro de validação* — não comporta dois impedimentos no
  mesmo campo (duas perguntas sem opção), que é o caso mais comum.

---

## D-11 — A classificação é declarada e verificada por diferença estrutural, comparando por chave

**Decisão**: publicar a partir da versão 2 exige `changeKind` ∈ {`cosmetic`, `semantic`} e um
`changeSummary` textual (FR-033). A verificação compara a versão nova com a publicada anterior,
casando perguntas **por `QuestionKey`**, e recusa `cosmetic` quando encontra:

- chave presente na nova e ausente na anterior → pergunta acrescentada
- chave presente na anterior e ausente na nova → pergunta removida
- mesma chave com `QuestionType` diferente → pergunta retipada
- mesma chave com conjunto de opções diferente (valor ou quantidade, ignorando ordem) → opções
  alteradas

Diferença de enunciado, de obrigatoriedade e de posição **não** força semântica. `semantic` é
sempre aceita (FR-035). A recusa nomeia a chave da pergunta e qual das quatro diferenças a
motivou (FR-034).

**Rationale**: a spec é explícita em que o sistema "não julga se um enunciado reescrito mudou de
sentido" — ele verifica o que consegue enxergar. As quatro diferenças acima são exatamente as que
tornam duas respostas não somáveis: mudou a pergunta, ou mudou o conjunto de respostas possíveis.
Comparar por chave, e não por posição, é o que faz uma reordenação continuar cosmética.

Comparar conjunto de opções ignorando a ordem é deliberado: reordenar as alternativas de uma
escolha não muda o que se pode responder.

---

## D-12 — O grupo de comparabilidade é gravado no ato da publicação

**Decisão**: `survey_versions` tem a coluna `comparability_group` (inteiro). A versão 1 recebe 1.
A versão N recebe o grupo da anterior quando `changeKind = COSMETIC`, e o grupo da anterior mais
um quando `SEMANTIC`. FR-037 é atendido lendo a coluna e agrupando.

**Rationale**: o valor é decidido no instante da publicação a partir de dados que nunca mais
mudam — versão publicada é imutável —, então gravá-lo não é desnormalização arriscada, é registrar
um fato. A alternativa, recalcular percorrendo a cadeia a cada leitura, custa uma varredura de
todas as versões para responder uma pergunta cuja resposta já era conhecida quando a versão
nasceu. E a transitividade que a spec descreve (v1 → v2 cosmética → v3 semântica → v4 cosmética
produz `{v1,v2}` e `{v3,v4}`) cai naturalmente da regra de incremento.

---

## D-13 — A paginação reusa inteira o que 002 entregou

**Decisão**: nada de paginação nasce nesta feature. As duas listagens — pesquisas (FR-005) e
versões (FR-037) — usam os tipos que a feature 002 já colocou no repositório:

```java
core.pagination.Page<T>        // record (List<T> items, long total), com map e totalPages(size)
core.pagination.PageQuery      // interface (page, size, offset) que a consulta de cada porta implementa
infra.http.dtos.PageResponseDTO<T>   // envelope único da borda: items, page, size, total, totalPages
```

`ListSurveysQuery` e `ListSurveyVersionsQuery` são `record` do módulo que **implementam**
`PageQuery` e acrescentam os próprios filtros — é o padrão que `ApiKeyRepository` estabeleceu.
Os presenters de listagem devolvem `PageResponseDTO<SurveyResponseDTO>` e
`PageResponseDTO<SurveyVersionResponseDTO>`, sem envelope próprio.

**Rationale**: a D-03 de 002 tinha adiado esses tipos até haver o segundo caso concreto, e a
versão anterior deste plano os promovia aqui. O adiamento acabou: 002 os entregou em
`core/pagination` antes desta feature começar, e `PageResponseDTO` já é compartilhado por todos
os módulos em `infra/http/dtos`. O que era tarefa desta fatia virou dependência dela.

Os parâmetros seguem o que 002 fixou em D-02: `page` base 0, `size` padrão 20 e teto 100, e a
resposta com `page`, `size`, `total` e `totalPages`.

**Consequência para o contrato**: as duas páginas desta feature não ganham schema próprio no
OpenAPI. O que o springdoc gera é a materialização de `PageResponseDTO<T>` para cada `T`, e o
controller fixa o media type com `produces` porque `@Schema(implementation)` não expressa o tipo
genérico — o mesmo detalhe que `ApiKeyController` já registra em comentário.

---

## D-14 — Opções em tabela própria; disparo embutido na versão

**Decisão**:

- `question_options` é tabela relacional (`id`, `question_id`, `position`, `label`, `value`), lida
  com `join fetch` junto das perguntas.
- O disparo **não** tem tabela: suas quatro partes são colunas anuláveis de `survey_versions`
  (`trigger_event_name`, `trigger_window_start`, `trigger_window_end`, `trigger_sampling_rate`).
  Ou as quatro estão preenchidas, ou o disparo não existe — invariante da entidade JPA verificada
  no mapper.
- `segmentation_rules` é tabela própria, referenciando a versão.

**Rationale**: o disparo é exatamente um por versão e nunca é consultado sozinho; uma tabela para
uma linha obrigatória seria um `join` permanente para nada. As opções são muitas por pergunta e
precisam de ordem, o que uma coluna `jsonb` também resolveria — mas o projeto não tem nenhum
`jsonb` hoje, e introduzir um idioma novo de persistência para economizar uma tabela é caro no
lugar errado. O N+1 que a tabela ameaça é resolvido por construção: a leitura da versão traz
perguntas e opções em uma consulta com `join fetch`, nunca em laço (Princípio V).

**Alternativa registrada**: se o volume de opções por pesquisa crescer a ponto de o `join fetch`
pesar, `jsonb` entra — com o problema medido na mão.

---

## D-15 — Rotas aninhadas na aplicação; comando de ciclo de vida como sub-recurso

**Decisão**: tudo sob `/applications/{applicationId}/surveys`, seguindo o aninhamento que 001 já
usa para as chaves. As três transições manuais são sub-recursos de comando —
`POST .../pause`, `POST .../resume`, `POST .../end` — e não um `PUT .../state`.

**Rationale**: o aninhamento torna o escopo por aplicação (FR-006) parte da rota, e não um
parâmetro que se pode esquecer de aplicar. Quanto às transições: cada uma tem seu próprio conjunto
de erros — pausar recusa em rascunho e em encerrada, retomar recusa em encerrada e em ativa,
encerrar recusa em rascunho — e o Princípio IV exige um `@ApiResponse` por status possível. Um
único `PUT .../state` obrigaria a documentar a união dos três conjuntos em uma operação só, onde
o cliente não consegue saber qual erro pertence a qual comando.

`POST .../publication` publica. `POST .../versions` abre o rascunho de versão;
`DELETE .../versions/draft` o descarta.

---

## D-16 — Fora do escopo da aplicação dona é "não encontrado"

**Decisão**: identificador malformado, pesquisa inexistente e pesquisa de outra aplicação
respondem os três `survey.not_found`, 404. Vale igualmente para pergunta, regra e versão.

**Rationale**: é o precedente de 001, e o cenário de borda da spec pede exatamente isso — "nunca
'sem permissão', o escopo por aplicação não vaza a existência do que está fora dele". Distinguir
entregaria um oráculo de existência a quem sonda a API, e SC-004 exige o contrário em 100% das
operações.

---

## D-17 — A versão editável é o rascunho; sem rascunho, o conteúdo está congelado

**Decisão**: toda escrita de conteúdo (pergunta, disparo, regra) resolve a "versão editável" da
pesquisa: a única versão em `DRAFT`. Não havendo nenhuma, a operação é recusada com
`survey.content_frozen` (422). A rota não muda entre editar o rascunho da v1 e o da v3.

**Rationale**: FR-025 e o cenário 10 de US2 pedem a mesma recusa para "pesquisa publicada", e o
cenário 10 de US6 limita a um rascunho de versão por pesquisa. As duas regras juntas dizem que
existe, em qualquer instante, no máximo uma versão editável — então "onde escrever" nunca é
ambíguo e não precisa entrar na rota. Um cliente que edita uma pesquisa publicada com rascunho
aberto usa o mesmo caminho de sempre; o que muda é o que ele precisa ter feito antes.

A unicidade é garantida no banco por índice único parcial sobre `(survey_id)` onde
`status = 'DRAFT'` — não só pela checagem do caso de uso, como manda o Princípio V.

---

## D-18 — Transação onde há duas escritas

**Decisão**: `@Transactional` (a anotação do projeto, `core.transaction`) em
`CreateSurveyUseCase` (pesquisa + versão 1), `PublishSurveyUseCase` (versão + pesquisa +
transição), `OpenSurveyVersionUseCase` (versão + cópia das perguntas e regras),
`DiscardSurveyUseCase` (versão + conteúdo + pesquisa), `AddQuestionUseCase` e
`RemoveQuestionUseCase` (pergunta + reposicionamento das irmãs), `ReorderQuestionsUseCase`, e nos
três comandos de ciclo de vida (pesquisa + transição).

Ficam **sem** transação: as leituras, o `RenameSurveyUseCase` (uma escrita) e
`UpdateQuestionUseCase` (uma escrita).

**Rationale**: a regra do projeto é literal — entra quando há duas escritas, ou quando uma leitura
e uma escrita precisam do mesmo instante do banco. A maior parte desta feature cai no primeiro
caso porque quase toda operação de autoria toca a versão e algo dentro dela.

---

## D-19 — Presenter é função estática, e o controller nunca monta DTO

**Decisão**: cada presenter desta feature é `final class` com construtor privado e um único
`public static R present(O output)`, em `modules/survey/infra/http/presenters`. Não há interface,
não há bean, não há injeção. Os presenters de listagem devolvem `PageResponseDTO<T>` (D-13).

**Rationale**: é o que a constituição 1.1.0 passou a exigir, e o repositório já está assim —
`IssueApiKeyPresenter` e `ListApiKeysPresenter` são exatamente esse molde, e
`core/presenter/Presenter.java` não existe mais. A versão anterior deste plano descrevia o
presenter como porta em `core`; a descrição está corrigida.

O que a mudança custa a esta feature é zero: nenhum presenter daqui precisa de estado ou
dependência. O que ela evita é um bean por tradução — são 13 presenters nesta fatia, e treze
`@Component` sem dependência nenhuma seriam treze objetos no contexto para executar treze
funções puras.

Os 13 saem de 23 endpoints porque quatro respondem `204` sem corpo e seis reusam o presenter de
um irmão: renomear, pausar, retomar e encerrar devolvem o mesmo `SurveyResponseDTO` que a
criação; publicar e abrir versão devolvem o mesmo `SurveyVersionResponseDTO`.

**Registrado para o futuro**: presenter que um dia precise de dependência real — um formatador
com locale, um resolvedor de URL — vira `@Component` injetado, quando o caso concreto existir.
Nenhum dos desta feature é esse caso: os três que mais fazem trabalho (`GetSurveyPresenter`,
`GetSurveyVersionPresenter`, `ListStateTransitionsPresenter`) só traduzem enum para minúsculas e
achatam coleções.
