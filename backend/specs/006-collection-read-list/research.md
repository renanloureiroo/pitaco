# Research: Coleta — leitura das exibições, respostas e respondentes

**Feature**: `006-collection-read-list` · **Date**: 2026-09-09 · **Spec**: [spec.md](./spec.md)

Cada decisão abaixo resolveu uma incógnita antes do desenho. O que já estava resolvido pelo
código existente está registrado como *herdado* — não é decisão nova, é constatação que dispensa
trabalho.

---

## D-01 — A superfície administrativa já existe e não custa nada (FR-032)

**Decisão**: as quatro leituras nascem sob `/applications/**`, e o `AdminSurfaceInterceptor` já
registrado para tudo que não é `/collect/**` recusa com `403 api_key.forbidden_surface` qualquer
requisição que apresente `X-Pitaco-Key`. Nenhum código novo de segurança.

**Rationale**: herdado da D-03 da feature 004. A separação é por prefixo de rota, e as rotas
novas caem no lado certo por construção. O custo de FR-032 é uma asserção de E2E por endpoint.

**Alternativas consideradas**: nenhuma — reabrir a decisão de autenticação está fora do escopo
desta fatia, como estava na 005.

---

## D-02 — A listagem devolve projeção, não a entidade de domínio (FR-002, FR-033)

**Decisão**: `SurveyDisplayRepository` ganha `Page<DisplaySummary> findPage(...)`, onde
`DisplaySummary` é um `record` de projeção declarado na própria porta — no mesmo molde do
`DisplayHistoryEntry` que já mora lá. A entidade `SurveyDisplay` continua sendo devolvida apenas
por `findById`, que serve a consulta individual.

**Rationale**: `SurveyDisplay` exige `AttributeSnapshot` no construtor, e o instantâneo de
atributos vive em `survey_display_attributes` como `@ElementCollection(fetch = EAGER)`. Montar
uma página de entidades de domínio significaria uma consulta de atributos por linha — N+1 exato,
proibido pelo Princípio V. `join fetch` de coleção com paginação é pior: o Hibernate passa a
paginar em memória. A projeção é a saída que o próprio Princípio V nomeia ("`join fetch` ou
projeção explícita"), e a listagem não pede atributo nenhum (FR-002).

**Alternativas consideradas**: `join fetch` dos atributos na consulta paginada — rejeitada pela
paginação em memória; tornar a coleção `LAZY` — rejeitada porque mudaria o caminho quente da
coleta para servir uma leitura de painel.

---

## D-03 — A projeção junta `survey_versions` para trazer o número da versão (FR-002)

**Decisão**: `DisplaySummary` carrega `versionId` **e** `versionNumber`, obtido por junção com
`survey_versions` na mesma consulta da página.

**Rationale**: a exibição grava `version_id`; o número é o que identifica a versão para quem
opera o painel ("versão 3"), e devolver só o identificador opaco obrigaria o cliente a uma
segunda chamada por linha. Uma junção por página, por chave primária, custa menos que isso.

**Alternativas consideradas**: devolver só `versionId` — rejeitada por empurrar N chamadas para o
cliente; desnormalizar o número na exibição — rejeitada porque exigiria migration de dados para
resolver um problema de leitura que uma junção resolve.

---

## D-04 — Existência da pesquisa é porta nova, e não `PublishedSurveyCatalog` (FR-010)

**Decisão**: `collect/application/gateways` ganha `SurveyScopeGateway` com
`boolean existsInApplication(SurveyId, ApplicationId)`, e o adaptador
`SurveyScopeGatewaySurvey` em `collect/infra/gateways` consulta a tabela `surveys` por uma
`JpaRepository` própria — mesmo molde de `PublishedSurveyJpaRepository`.

**Rationale**: FR-010 distingue "pesquisa não existe" (recusa) de "pesquisa existe e nunca foi
exibida" (página vazia). Essa distinção só é possível contra a tabela de pesquisas: uma pesquisa
que nunca publicou existe, não tem exibição, e deve devolver vazio — `PublishedSurveyCatalog`
responderia "não" e produziria 404 errado. Semanticamente o catálogo é sobre versão publicada;
existência de pesquisa não pertence a ele.

**Alternativas consideradas**: um método a mais em `PublishedSurveyCatalog` — rejeitada porque o
nome passaria a mentir e a porta responderia por dois assuntos; deduzir a existência do resultado
vazio — rejeitada por contrariar FR-010 e por revelar menos do que a spec pede.

---

## D-05 — O código de erro é o mesmo, a classe é do módulo que a lança (FR-010, FR-026)

**Decisão**: `collect/application/errors` ganha `SurveyNotFoundInApplication`, carregando o
`code` estável **`survey.not_found`** — o mesmo que `survey/application/errors/SurveyNotFound`
já usa — e `RespondentNotFound` com o `code` novo **`respondent.not_found`**.
`display.not_found` é reaproveitado como está.

**Rationale**: o `code` é contrato público e precisa ser o mesmo para o cliente; a classe não
pode ser compartilhada, porque dois módulos nunca se conhecem pelo domínio. É a mesma conduta já
registrada na memória do projeto: o que sobe para `core` é a invariante, não o tipo, e cada
módulo mantém seu `code`.

**Alternativas consideradas**: importar `SurveyNotFound` de `survey` — rejeitada por violar o
Princípio I; inventar `collect.survey_not_found` — rejeitada por quebrar o contrato que o cliente
já trata.

---

## D-06 — A ordem das respostas vem de `PublishedSurveyCatalog.contentOf` (FR-015)

**Decisão**: a consulta individual busca o conteúdo da versão exibida por
`contentOf(versionId)`, já existente, e ordena as respostas pela posição da pergunta. Resposta
cuja chave não está no conteúdo da versão vai para o fim, com desempate estável pela chave.

**Rationale**: a ordem é propriedade da versão exibida, não da resposta — `survey_answers` não
grava posição, e gravá-la duplicaria um dado que a versão congelada já tem. `contentOf` é uma
consulta indexada por chave primária, executada uma vez numa leitura de item único. A cláusula do
resto é para o estado inconsistente que a spec manda apresentar sem corrigir.

**Alternativas consideradas**: método novo no catálogo devolvendo só as chaves ordenadas —
rejeitada porque economizaria bytes de uma consulta que já é única e acrescentaria superfície de
porta sem segundo caso que a exija; ordenar por `question_key` alfabética — rejeitada por não ser
a ordem que a pessoa viu.

---

## D-07 — Texto livre expirado é suprimido na leitura, com situação própria (FR-028)

**Decisão**: a supressão acontece na leitura. `collect`'s `ApplicationScopeGateway` ganha
`Optional<Integer> effectiveOpenTextRetentionDaysOf(ApplicationId)`, servida por
`Application.effectiveOpenTextRetentionDays()` — que já existe e nunca foi usada. Uma resposta
cujo valor é `TextValue` e cujo `answeredAt` é mais antigo que o prazo é apresentada **sem
valor**, com a situação de leitura `EXPIRED`. Prazo ausente significa sem expiração.

**Rationale**: não existe rotina de expurgo no projeto, e criar uma aqui seria feature própria
(precisa de agendamento, trilha e decisão sobre apagar de fato). A supressão na leitura entrega
FR-028 com uma consulta a mais numa leitura de item único e sem nada de novo em escrita.
`EXPIRED` é situação de **saída**, declarada no `Output`, não em `AnswerStatus` — o domínio
gravou `ANSWERED`, e a expiração é uma decisão de apresentação sobre um dado que continua lá.
É o que torna FR-028 verificável: expirado é distinguível de pulado.

**Alternativas consideradas**: rotina de expurgo apagando o texto — rejeitada por ser feature de
retenção, com escopo, agendamento e irreversibilidade próprios; acrescentar `EXPIRED` a
`AnswerStatus` — rejeitada porque mentiria sobre o que foi gravado e obrigaria o mapper a
inventar um estado que o banco não tem; não implementar — rejeitada por contrariar FR-028.

---

## D-08 — Um método a mais no gateway, não um gateway novo (FR-028)

**Decisão**: a retenção efetiva entra como método novo em `ApplicationScopeGateway`, e não como
porta separada nem como campo dentro de `ApplicationScopeState`.

**Rationale**: `ApplicationScopeState` é enum consumido pela elegibilidade, que é o caminho
quente da coleta; enriquecê-lo mudaria esse caminho para servir uma leitura de painel. Uma porta
nova para um método só seria máquina sem segundo caso concreto. O gateway já é *a travessia até
`app`*, e a retenção é dado de `app`.

**Alternativas consideradas**: `OpenTextRetentionGateway` dedicado — rejeitada por antecipação de
abstração; trocar o retorno de `stateOf` por um `record` — rejeitada por tocar o caminho quente.

---

## D-09 — Consulta individual é rota plana sob a aplicação (FR-011, FR-018)

**Decisão**: quatro rotas, três controllers:

| Rota | Controller |
| --- | --- |
| `GET /applications/{applicationId}/surveys/{surveyId}/displays` | `SurveyDisplayController` |
| `GET /applications/{applicationId}/displays/{displayId}` | `DisplayController` |
| `GET /applications/{applicationId}/respondents` | `RespondentController` |
| `GET /applications/{applicationId}/respondents/{respondentId}/displays` | `RespondentController` |

**Rationale**: o identificador de exibição é chave primária global — gerado no dispositivo, é a
chave de idempotência da fila do SDK. Aninhá-lo sob a pesquisa obrigaria o cliente a carregar um
`surveyId` que a exibição já determina, e criaria um segundo caminho para o mesmo recurso.
`findById(id, applicationId)` já fecha FR-018: exibição de outra aplicação não é encontrada.
`RespondentController` hospeda duas rotas do mesmo prefixo, como `SurveyLifecycleController` já
faz.

**Alternativas consideradas**: `/surveys/{surveyId}/displays/{displayId}` — rejeitada pelo
parâmetro redundante e pela dupla identidade do recurso; `/displays/{displayId}` fora da
aplicação — rejeitada porque todo eixo de acesso administrativo do projeto passa pela aplicação.

---

## D-10 — Dois índices, com os filtros residuais no `include` (FR-033, Princípio V)

**Decisão**: uma migration nova cria

```
idx_survey_displays_survey_listing     (survey_id, opened_at desc, id desc)
                                       include (version_id, outcome, closed_at,
                                                comparability_group, sdk_version, respondent_id)
idx_survey_displays_respondent_listing (respondent_id, opened_at desc, id desc)
                                       include (survey_id, version_id, outcome, closed_at,
                                                comparability_group, sdk_version)
idx_respondents_application_listing    (application_id, last_seen_at desc, id desc)
```

e derruba `idx_survey_displays_survey (survey_id, opened_at desc)`, que passa a ser prefixo
redundante do primeiro.

**Rationale**: a coluna líder recorta o conjunto, `opened_at desc, id desc` entrega a ordenação
de FR-007 já ordenada e o desempate determinístico da paginação, e `version_id`/`outcome` — os
filtros de FR-004 — ficam **no índice**, como o Princípio V exige, sem custar índice adicional
numa tabela que é escrita a cada abertura e a cada fechamento de exibição. Como payload, eles são
avaliados antes de qualquer acesso ao heap. `respondents` só tinha o índice de unicidade por
identidade, que não serve a ordenação por último contato.

**Alternativas consideradas**: um índice por combinação de filtro — rejeitada por multiplicar o
custo de escrita no caminho quente da coleta sem problema medido; deixar `outcome` e `version_id`
fora do índice — rejeitada por contrariar a regra literal do Princípio V quando o `include`
custava nada.

---

## D-11 — O filtro de desfecho tem enum próprio na borda (FR-009)

**Decisão**: o parâmetro de consulta usa `DisplayOutcomeFilter { STARTED, COMPLETED, DISMISSED }`
declarado em `infra/http/dtos`, que traduz para `DisplayOutcome`. `ABANDONED` não é oferecido.

**Rationale**: `ABANDONED` nunca é gravado — é derivado na elegibilidade. Publicá-lo no OpenAPI
como valor de filtro documentaria um filtro que sempre devolve vazio. O enum da borda mantém o
contrato honesto, e valor desconhecido continua sendo 400 pela conversão do próprio Spring.

**Alternativas consideradas**: aceitar `DisplayOutcome` inteiro — rejeitada por documentar uma
opção inútil; recusar `ABANDONED` com validação própria — rejeitada por gastar código para
proibir o que o tipo já não permite expressar.

---

## D-12 — Período com início depois do fim é `@AssertTrue` no DTO (FR-009)

**Decisão**: a ordenação do período é verificada por um método `@AssertTrue` no `record` do
query DTO, produzindo 400 com o campo apontado. Não nasce value object de período.

**Rationale**: é restrição de consulta, não invariante de domínio — nada no domínio possui um
período de leitura, e um value object faria a recusa sair como 422 pelo `ErrorType.VALIDATION` do
`DomainException`, quando FR-009 e a conduta da 005 pedem recusa de validação de entrada. A regra
"a mensagem do DTO espelha a do value object" não se aplica onde não há value object
correspondente.

**Alternativas consideradas**: value object `ReadPeriod` em `collect/domain` — rejeitada por
inventar domínio para uma restrição de borda; validar no caso de uso — rejeitada porque a recusa
perderia o nome do campo.

---

## D-13 — Nenhuma transação, nenhuma escrita (FR-027)

**Decisão**: nenhum dos quatro casos de uso declara `@Transactional`, e nenhum chama método de
escrita de porta alguma.

**Rationale**: a anotação do projeto entra quando há duas escritas ou quando leitura e escrita
precisam do mesmo instante do banco. Aqui não há escrita. FR-027 e SC-005 são verificados por
E2E que relê o estado antes e depois — inclusive nos caminhos de recusa.

**Alternativas consideradas**: `@Transactional(readOnly)` — o projeto não tem essa variante na
sua anotação, e adicioná-la para leitura de uma consulta só seria máquina sem ganho.
