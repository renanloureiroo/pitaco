# Phase 0 — Pesquisa e decisões: listagem e consulta de chaves de API

**Feature**: `002-api-key-read-list` · **Data**: 2026-09-08

Cada decisão abaixo fecha uma incerteza do [plan.md](./plan.md) ou um marcador da
[spec](./spec.md). O que não está aqui foi decidido em
[001-api-key-management/research.md](../001-api-key-management/research.md) e continua valendo.

---

## D-01 — Sem filtro, a listagem devolve válidas e revogadas

**Resolve**: o `[NEEDS CLARIFICATION]` de FR-005.

**Decisão**: a listagem sem parâmetro `status` devolve todas as chaves da aplicação, válidas e
revogadas, distinguíveis pelo campo `status` de cada item. O filtro restringe; ele não é o que
revela.

**Rationale**: os próprios cenários de aceitação da spec já assumiam isso — a User Story 1,
cenário 2, diz que com uma chave válida e uma revogada "as duas aparecem". Escondê-las por
padrão contradiria o cenário e, pior, criaria um default que mente por omissão: quem lista e vê
três chaves concluiria que a aplicação tem três chaves. A trilha de revogação é justamente o
que SC-007 de 001 prometeu preservar; um default que a esconde entrega a preservação e nega o
acesso.

**Alternativas descartadas**:
- *Só as válidas por padrão* — alinha com o uso mais frequente, mas faz uma chave sumir da
  listagem no instante em que é revogada, que é exatamente o momento em que alguém está olhando
  para ela. O ganho de ergonomia não paga a surpresa.
- *Filtro obrigatório* — elimina a ambiguidade forçando a intenção, mas custa o caso trivial
  ("me mostra as chaves") e não tem precedente na API atual, onde nenhum parâmetro é obrigatório
  fora do caminho.

---

## D-02 — Paginação por deslocamento, padrão 20, teto 100

**Decisão**: `page` (base 0) e `size`, com padrão `page=0&size=20` e `size` limitado a 100. A
resposta traz `page`, `size`, `total` e `totalPages`.

**Rationale**: FR-008 pede o total de chaves que atendem ao pedido, e total é precisamente o que
a paginação por cursor não dá de graça. O volume é administrativo — dezenas de chaves por
aplicação, não milhões —, então o custo clássico do deslocamento (`offset` grande varrendo linhas
descartadas) não se materializa: com o índice de D-05, chegar à página 5 de 20 itens custa pular
100 entradas de índice. Base 0 acompanha o `Pageable` do Spring Data, evitando uma conversão que
só existiria para agradar a estética.

**Alternativas descartadas**:
- *Cursor (keyset)* — melhor para páginas profundas e para conjuntos que mudam durante a
  travessia, mas não entrega `total` e obriga a inventar um formato de cursor opaco. Entra se
  alguma aplicação chegar a um volume que torne o deslocamento visível.
- *Sem teto no `size`* — proibido pelo Princípio V: um endpoint de coleção nunca devolve
  resultado ilimitado, e `size=1000000` é um `findAll` disfarçado.

---

## D-03 — `Page<T>` e `PageQuery` genéricos em `core`

> **Revisada durante a implementação.** A decisão original era adiar o tipo genérico até haver a
> segunda listagem, conforme a constituição ("abstração nova entra quando existe o segundo caso
> concreto que a exige"). O dono do projeto optou por antecipar, para que a segunda listagem já
> nasça sobre a base pronta. O registro da decisão original fica abaixo, em *Alternativas
> descartadas*.

**Decisão**: `core/pagination` recebe dois tipos, ambos Java puro:

- `Page<T>(List<T> items, long total)` — o que toda porta paginada devolve, com `map` para
  traduzir os itens sem tocar no total e `totalPages(size)` para a contagem de páginas. `items`
  é copiado na construção, então a página é imutável.
- `PageQuery` — interface com `page()`, `size()` e `offset()` derivado. A `Query` de cada porta
  a implementa e acrescenta os próprios filtros: é aí que a extensão acontece.

Na borda, `infra/http/dtos/PageResponseDTO<T>` é o envelope único da resposta paginada. Como o
tipo é genérico, `@Schema(implementation = ...)` não consegue expressá-lo: o schema vem do tipo
de retorno do handler, e o media type é fixado pelo `produces` do `@GetMapping`. O springdoc
gera `PageResponseDTOApiKeyResponseDTO`, com `items` referenciando `ApiKeyResponseDTO`.

**Rationale**: o custo de errar a forma é baixo porque a superfície é mínima — itens e total na
resposta, página e tamanho na consulta. Nada de cursor, metadados ou links foi antecipado: se a
segunda listagem precisar, entra então. `Page<T>` não sabe qual página é nem de que tamanho, e
por isso não duplica o que a `Query` já disse; quem cruza os dois é o caso de uso.

**Alternativas descartadas**:
- *Adiar o tipo genérico até a segunda listagem* (decisão original desta fatia) — o resultado
  paginado atravessaria a porta como `record` nomeado do próprio módulo. É o que a constituição
  pede ao pé da letra, e o custo de adiar seria pequeno: uma refatoração mecânica com os testes
  já no lugar. Descartada por decisão do dono do projeto.
- *`Page<T>` carregando também `page` e `size`* — ecoaria de volta o que a `Query` já informou,
  e obrigaria todo adaptador a repassá-los sem necessidade.
- *Herança de um record base para as consultas* — `record` é final. A interface `PageQuery` dá a
  mesma extensão sem o acoplamento.
- *Expor `org.springframework.data.domain.Page` na porta* — violação direta do Princípio I:
  `application` passaria a importar Spring. `Pageable`/`Page` do Spring Data seguem confinados a
  `ApiKeyRepositoryJpa` e `ApiKeyJpaRepository`.

---

## D-04 — `ApiKeyStatus` derivado, sem coluna e sem campo

**Decisão**: enum `ApiKeyStatus { ACTIVE, REVOKED }` em `modules/app/domain/entities`, exposto por
`ApiKey.status()`, calculado de `revokedAt`. Nenhuma coluna nova, nenhum campo novo na entidade,
nenhuma migration de dados. O filtro da listagem é `Optional<ApiKeyStatus>` — ausente significa
todas (D-01).

**Rationale**: 001 decidiu que o estado é derivado da presença de `revokedAt`, e essa decisão
está certa: uma coluna `status` ao lado de `revoked_at` seria uma segunda fonte de verdade para o
mesmo fato, com a possibilidade permanente de discordarem. O que faltava era **nome** para esse
estado — sem nome não há como filtrar por ele nem apresentá-lo. O enum dá o nome sem dar
armazenamento. No banco, o filtro continua sendo `revoked_at is null` / `is not null`.

**Alternativas descartadas**:
- *Coluna `status` persistida* — desnormalização sem ganho: as consultas sobre `revoked_at`
  usam o mesmo índice, e a coluna abriria caminho para inconsistência.
- *Booleano `revoked` na API* — cabe hoje, mas fecha a porta para um terceiro estado (expirada,
  suspensa) sem quebra de contrato. O enum textual custa o mesmo e permanece extensível.

---

## D-05 — Ordenação `created_at desc, id desc` sobre índice composto

**Decisão**: ordenação padrão e única `created_at desc, id desc`. Migration
`V20260908160000__index_api_keys_listing.sql` cria
`(application_id, created_at desc, id desc)` e remove `idx_api_keys_application_id`.

**Rationale**: `created_at` sozinho não é determinístico — duas chaves emitidas no mesmo instante
poderiam trocar de posição entre uma página e a seguinte, fazendo uma aparecer duas vezes e a
outra nunca, que é exatamente o que SC-003 proíbe. O desempate por `id` (chave primária, único)
fecha isso sem custo. O índice composto entrega ordenação e recorte já ordenados, sem `sort` em
memória, e torna `idx_api_keys_application_id` redundante: `application_id` é a coluna líder do
composto, então toda consulta que usava o índice antigo usa o novo. Manter os dois custaria
escrita em toda emissão em troca de nada.

Remover um índice é migration nova, não edição de migration aplicada — a regra da constituição
segue respeitada.

**Alternativas descartadas**:
- *Índice parcial por `revoked_at is null`* — aceleraria o filtro `status=active`, mas o recorte
  por aplicação já reduz o conjunto a dezenas de linhas; um segundo índice é custo de escrita sem
  problema medido para resolver.
- *Manter `idx_api_keys_application_id`* — só se alguma consulta futura precisasse do índice mais
  estreito, o que não é o caso de nenhuma consulta existente.

---

## D-06 — Dois casos de uso, não um com filtro opcional

**Decisão**: `ListApiKeysUseCase` e `GetApiKeyUseCase`, separados.

**Rationale**: a constituição fixa um `execute` por caso de uso e `Input`/`Output` próprios.
Consultar uma chave devolve uma chave ou falha com `api_key.not_found`; listar devolve uma página
que pode estar vazia sem falhar. São contratos de erro diferentes, e espremer os dois em um
`Input` com campo opcional produziria um `Output` que às vezes tem item e às vezes tem página.

**Alternativas descartadas**:
- *Um caso de uso com `Optional<ApiKeyId>`* — economiza uma classe e cobra o preço em cada
  chamada e em cada teste.

---

## D-07 — Aplicação inexistente recusa; aplicação inativa lista

**Decisão**: os dois casos de uso verificam a existência da aplicação antes de tocar nas chaves e
lançam `ApplicationNotFound` se ela não existir. Aplicação **inativa** lista e consulta
normalmente — `ApplicationIsInactive` não aparece nesta fatia.

**Rationale**: FR-010 e o cenário 5 da User Story 2 pedem `application.not_found` explicitamente,
e sem a verificação uma aplicação inexistente responderia 200 com lista vazia — indistinguível de
uma aplicação real sem chaves, e um oráculo às avessas. Já a inatividade impede emitir porque
emitir cria uma credencial nova; ler não cria nada, e é justamente numa aplicação desativada que
alguém precisa enxergar as chaves para revogá-las (FR-011).

O custo é uma consulta a mais por requisição, por chave primária. Não é N+1: é uma consulta fixa,
fora de qualquer laço.

**Alternativas descartadas**:
- *Não verificar a aplicação* — economiza a consulta e devolve lista vazia para aplicação
  inexistente, contrariando FR-010.
- *Recusar leitura em aplicação inativa* — trancaria a porta justamente para quem precisa entrar.

---

## D-08 — Chave de outra aplicação e identificador malformado são "não encontrada"

**Decisão**: a consulta busca por `id` **e** `applicationId` juntos. Chave inexistente, chave de
outra aplicação e identificador em formato inválido produzem o mesmo `api_key.not_found`.

**Rationale**: é a decisão D-06 de 001, aplicada ao caminho de leitura por coerência — distinguir
entregaria um oráculo. A diferença aqui é que a leitura é o caminho mais barato de sondar: quem
quisesse enumerar chaves alheias tentaria por aqui. Mesma resposta para os três casos, sem
vazamento de existência (FR-016).

**Alternativas descartadas**:
- *403 para chave de outra aplicação* — confirma que a chave existe, que é a informação a
  proteger.

---

## D-09 — Parâmetros de query em um `record` DTO com Bean Validation

**Decisão**: `ListApiKeysQueryDTO(String status, Integer page, Integer size)`, anotado com
`@ParameterObject`, ligado com `@Valid`, com `@Min`/`@Max` em `page`/`size` e `@Pattern` em
`status`. Converte para o `Input` do caso de uso por `toInput(applicationId)`.

**Rationale**: é o mesmo caminho que o corpo de requisição já percorre — a falha chega ao
`ApiExceptionHandler` como `MethodArgumentNotValidException` e sai como `request.invalid` com o
mapa de campos, sem nenhuma linha nova no handler. Ligar os parâmetros soltos com `@RequestParam`
levaria uma falha de tipo a `MethodArgumentTypeMismatchException`, tratada pelo caminho genérico
do `ResponseEntityExceptionHandler`, que responde 400 **sem** a propriedade `code` — quebra do
Princípio IV pela porta dos fundos.

`status` é ligado como `String` com `@Pattern`, e não como enum: um valor inválido vira erro de
validação com mensagem própria, em vez de erro de conversão antes de qualquer validação rodar.
`@ParameterObject` do springdoc garante que os três parâmetros continuem documentados
individualmente no OpenAPI, e não como um objeto aninhado.

**Alternativas descartadas**:
- *`@RequestParam` com `@Min`/`@Max` direto* — exigiria `@Validated` na classe do controller e
  um handler novo para `ConstraintViolationException`, que hoje não existe. Mais código na borda
  para um resultado pior.
- *Aceitar qualquer `size` e limitar em silêncio* — esconde do cliente que o pedido dele não foi
  atendido; FR-013 pede recusa explícita.

---

## D-10 — Um DTO de item, compartilhado pelos dois endpoints

**Decisão**: `ApiKeyResponseDTO` (id, applicationId, label, prefix, status, createdAt, revokedAt)
serve tanto como item da listagem quanto como corpo da consulta individual. A listagem o envolve
em `PageResponseDTO<ApiKeyResponseDTO>`, o envelope genérico de D-03.

**Rationale**: FR-015 exige que a consulta exponha exatamente os mesmos campos de um item da
lista. Dois DTOs com o mesmo conteúdo seriam duas chances de divergirem na próxima mudança; um só
faz o compilador garantir o que o requisito pede. Nenhum dos dois tem campo para `secret` ou para
o hash — a ausência do segredo é estrutural, não uma disciplina a manter (FR-004).

**Alternativas descartadas**:
- *DTO próprio por endpoint* — simetria aparente, divergência garantida.
- *Reusar `IssueApiKeyResponseDTO`* — ele carrega `secret`. Reusá-lo seria criar o caminho pelo
  qual o segredo poderia vazar num campo esquecido.
