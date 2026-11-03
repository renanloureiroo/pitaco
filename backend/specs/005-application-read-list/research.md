# Phase 0 — Pesquisa e decisões: Aplicações — listagem e consulta

**Feature**: `005-application-read-list` · **Data**: 2026-09-08

Nenhum `NEEDS CLARIFICATION` sobrou da Technical Context: a fatia é gêmea da
`002-api-key-read-list`, e cada peça dela tem um precedente vivo no repositório. A pesquisa
aqui é sobre **onde o precedente serve e onde ele não serve**.

---

## D-01 — Duas rotas novas sob `/applications`, fechando o contrato que o `Location` já promete

**Decisão**: `GET /applications` (listagem paginada) e `GET /applications/{applicationId}`
(consulta individual), no `ApplicationController` que já existe.

**Motivo**: `CreateApplicationUseCase` já responde `201` com
`Location: /api/applications/{id}` — e hoje esse `Location` aponta para uma rota que **não
existe**. A criação promete um recurso que nunca pôde ser buscado. Esta fatia paga essa dívida.

**Alternativas descartadas**:
- *Controller novo (`ApplicationReadController`)*: a rota é a mesma e o agregado é o mesmo;
  separar leitura de escrita em dois controllers duplicaria `@RequestMapping` e a interface
  Swagger sem nenhum ganho. `ApiKeyController` já reúne as quatro operações.
- *Consulta por slug (`GET /applications/by-slug/{slug}`)*: registrada como fora de escopo na
  spec. O `Location` da criação endereça por identificador, e é o identificador que todas as
  rotas aninhadas (`/applications/{id}/api-keys`, `/surveys`) já usam.

---

## D-02 — Porta paginada `findPage(Query)`, no mesmo molde de `ApiKeyRepository`

**Decisão**: `ApplicationRepository` ganha

```
Page<Application> findPage(Query query);

record Query(Optional<Status> status, int page, int size) implements PageQuery {}
```

**Motivo**: `core.pagination` já existe exatamente para isso, e a `Query` aninhada
implementando `PageQuery` é o formato que `ApiKeyRepository` estabeleceu. Reusar o formato faz
o `ApplicationRepositoryJpa` sair quase idêntico ao `ApiKeyRepositoryJpa` — quem leu um lê o
outro sem esforço.

**Diferença em relação à chave**: a `Query` da chave carrega `applicationId` porque toda
leitura de chave acontece dentro de uma aplicação. Aqui não há escopo acima da aplicação
(registrado como premissa na spec), então a `Query` carrega só o filtro de estado e o recorte.

---

## D-03 — `findAll()` sai da porta

**Decisão**: remover `List<Application> findAll()` de `ApplicationRepository` e de
`ApplicationRepositoryJpa`. O método permanece em `InMemoryApplicationRepository`, sem
`@Override`, como o que sempre foi de fato: um espião de teste.

**Motivo**: uma varredura mostrou que `findAll()` da porta **não tem nenhum chamador em
produção** — os únicos usos estão em `CreateApplicationUseCaseTest`, inspecionando o fake.
Deixá-lo ao lado do `findPage` novo colocaria uma coleção ilimitada na porta pública do
agregado, contra o Princípio V ("um endpoint de coleção NUNCA devolve resultado ilimitado"),
e ofereceria a quem escrever o próximo caso de uso o atalho errado à mão.

**Alternativa descartada**: *deixar como está, já que não é superfície pública*. A
constituição tolera `findAll` de uso interno, mas aqui não há uso interno nenhum — é código
morto, e código morto que parece uma opção legítima acaba sendo escolhido.

---

## D-04 — Dois índices, criados na migration desta fatia

**Decisão**: uma migration só, com dois índices:

```
create index idx_applications_created_at on applications (created_at desc, id desc);
create index idx_applications_status_created_at on applications (status, created_at desc, id desc);
```

**Motivo**: o Princípio V exige índice para todo campo usado em filtro ou ordenação, criado na
mesma migration que introduz a consulta. São duas consultas de formas diferentes: a listagem
sem filtro ordena por `created_at desc, id desc`, e a filtrada por estado precisa de `status`
como coluna líder para não varrer o índice inteiro. Um índice só não atende as duas — `status`
na frente não serve a consulta sem filtro, e `created_at` na frente obriga a filtrar depois de
ordenar.

**Sobre o desempate por `id`**: é o que torna a paginação determinística entre aplicações
criadas no mesmo instante (FR-007). Sem ele, duas linhas empatadas podem trocar de posição
entre a página 0 e a página 1, repetindo uma e omitindo a outra. É a mesma razão pela qual
`V20260908160000__index_api_keys_listing.sql` levou `id desc` no fim.

**Alternativa descartada**: *só o índice composto com `status`*. Postgres consegue usar um
índice `(status, created_at, id)` para a consulta sem filtro via skip scan, mas a garantia
depende de plano e de versão; um índice a mais numa tabela que terá dezenas de linhas custa
praticamente nada.

---

## D-05 — Dois derivados no Spring Data, não um JPQL com predicado opcional

**Decisão**: `ApplicationJpaRepository` ganha `Page<ApplicationJpaEntity> findByStatus(String
status, Pageable pageable)`; a listagem sem filtro usa o `findAll(Pageable)` herdado do
`JpaRepository`.

**Motivo**: é o precedente explícito de `ApiKeyJpaRepository`, que documenta a escolha no
próprio arquivo — "três derivados em vez de um JPQL com predicado nulo opcional: as assinaturas
dizem o que filtram, o JPQL não diria". Aqui bastam dois porque `status` é coluna de verdade,
enquanto o estado da chave é derivado de `revoked_at is null` e precisava de dois predicados.

**Nota**: `findAll(Pageable)` do `JpaRepository` é ilimitado no nome mas não no efeito — o
`Pageable` recorta. Ele fica confinado ao adaptador, atrás da porta paginada, e não é o
`findAll()` que D-03 remove.

---

## D-06 — Resumo na listagem, detalhe na consulta: dois DTOs, não um com campos nulos

**Decisão**: `ApplicationSummaryResponseDTO` (id, slug, name, status, createdAt) para a
listagem; `ApplicationResponseDTO` (o resumo mais os três prazos e `updatedAt`) para a consulta.

**Motivo**: um DTO só, com os prazos nulos na listagem, mentiria no OpenAPI — o schema diria
"pode vir" para campos que na listagem nunca vêm e na consulta sempre podem vir, e o cliente
não teria como distinguir "prazo não configurado" de "rota que não devolve prazo". Dois
schemas dizem a verdade sobre cada rota.

**Alternativa descartada**: *`?expand=policies` na listagem*. Um parâmetro que muda a forma da
resposta é um segundo schema disfarçado de um só; entra se houver tela que precise dos prazos
em lista — e hoje não há.

---

## D-07 — Prazo ausente é campo ausente, nunca zero

**Decisão**: `Optional<Integer>` nos `Output` dos casos de uso; campo `Integer` anotado
`nullable = true` nos DTOs, com o presenter fazendo `orElse(null)`.

**Motivo**: `Application` já expõe `quietPeriodDays()`, `retentionDays()` e
`openTextRetentionDays()` como `Optional`, e o domínio recusa prazo `<= 0`. Traduzir ausência
para `0` inventaria um valor que a entidade jamais aceitaria. É o mesmo tratamento que
`ApiKeyResponseDTO.revokedAt` já recebe.

**Sobre `effectiveOpenTextRetentionDays()`**: a entidade tem esse acessor, que cai no prazo
geral quando o de texto livre não foi definido. Ele **não** entra na resposta: a consulta
devolve o que foi configurado, não o que foi derivado — misturar os dois faria a tela mostrar
um prazo que ninguém digitou, sem como saber que era herdado.

---

## D-08 — A superfície administrativa já está resolvida: FR-019 não custa código

**Decisão**: nenhum código novo de segurança. As duas rotas nascem protegidas.

**Motivo**: `SecurityWebMvcConfiguration` registra o `AdminSurfaceInterceptor` em
`/applications/**`, e ele rejeita qualquer requisição que apresente o header de chave de
aplicação com `api_key.forbidden_surface`. As rotas novas caem sob esse padrão por construção.

**Consequência para o trabalho**: FR-019 vira **cobertura de teste**, não implementação — um
cenário E2E por rota, mais o `403` declarado no OpenAPI de cada uma.

---

## D-09 — Estado no JSON é minúsculo, e valor desconhecido no filtro é 400

**Decisão**: `status` viaja como `"active"` / `"inactive"`; o `ListApplicationsQueryDTO` valida
com `@Pattern(regexp = "active|inactive")` e converte com `Status.valueOf(upper)`.

**Motivo**: é literalmente o que `ListApiKeysQueryDTO` faz com `active|revoked`. Validar no
DTO é o que transforma um valor desconhecido em `400` com campo e motivo (FR-009) em vez de um
`IllegalArgumentException` do `valueOf` virando `500`.

---

## D-10 — Sem `@Transactional` nos dois casos de uso

**Decisão**: nenhum dos dois casos de uso recebe a anotação de `core.transaction`.

**Motivo**: a regra do projeto é que a transação explícita entra com duas escritas, ou com uma
leitura e uma escrita que precisem do mesmo instante do banco. Aqui não há escrita nenhuma
(FR-016) e cada caso de uso faz **uma** consulta. `ListApiKeysUseCase` e `GetApiKeyUseCase`,
que fazem duas leituras cada, também não a têm.

---

## D-11 — `applicationIdOf` duplicado: reconhecido, e deliberadamente fora desta fatia

**Observação**: converter `String` → `ApplicationId` tratando `DomainException` como "não
encontrado" já aparece 12 vezes no repositório, em 9 arquivos dos dois módulos. `GetApplicationUseCase`
seria a décima terceira.

**Decisão**: copiar o helper privado, como fazem `ListApiKeysUseCase` e `GetApiKeyUseCase`, e
**não** extrair a abstração nesta fatia.

**Motivo**: a extração é boa e o módulo `survey` já mostra o formato dela — um
`application/services/SurveyScope`. Mas fazê-la agora tocaria seis casos de uso fora desta
fatia, misturando um refactor transversal com a entrega de dois endpoints. Fica registrada
como candidata a fatia própria; o comentário que explica a escolha ("formato inválido é
indistinguível de inexistente") continua acompanhando cada cópia.
