# Phase 1 — Modelo de dados: autoria de pesquisa

**Feature**: `003-survey-authoring` · **Data**: 2026-09-08

Deriva das *Key Entities* da [spec](./spec.md) e das decisões de [research.md](./research.md).
O contrato HTTP está em [contracts/surveys.openapi.yaml](./contracts/surveys.openapi.yaml).

Tudo abaixo é Java puro: `modules/survey/domain` e `modules/survey/application` não importam
Spring, JPA, Bean Validation nem Swagger.

---

## Visão geral

```
Survey ──1:N── SurveyVersion ──1:N── Question ──1:N── QuestionOption
   │                 │
   │                 ├──0:1── Trigger  (embutido, D-14)
   │                 └──0:N── SegmentationRule
   │
   └──1:N── SurveyStateTransition
```

`Survey` é o agregado. `SurveyVersion` é o recipiente de todo o conteúdo (D-04): a versão em
`DRAFT` é o rascunho, as em `PUBLISHED` são imutáveis.

---

## Domínio

### `Survey` — entidade (`domain/entities/Survey.java`)

Estende `Entity<SurveyId>`.

| Campo | Tipo | Nota |
| --- | --- | --- |
| `id` | `SurveyId` | |
| `applicationId` | `ApplicationId` | de `core/identity` (D-02) |
| `name` | `SurveyName` | renomeável enquanto houver rascunho |
| `lifecycle` | `SurveyLifecycle` | `DRAFT` \| `PUBLISHED` \| `PAUSED` \| `ENDED` |
| `publishedVersionNumber` | `Optional<Integer>` | versão publicada corrente |
| `draftVersionNumber` | `Optional<Integer>` | rascunho aberto, no máximo um (D-17) |
| `createdAt` | `Instant` | UTC |

**Factories**: `create(applicationId, name)` nasce `DRAFT` com `draftVersionNumber = 1`;
`restore(...)` volta do banco (usada pelo mapper, nunca `create`).

**Comportamento**:

```java
SurveyState stateAt(Instant now, Optional<TriggerWindow> publishedWindow)   // D-05
void markPublished(int versionNumber)
void pause()      // recusa se não estiver PUBLISHED
void resume()     // recusa se não estiver PAUSED
void end()        // recusa se DRAFT ou já ENDED
void rename(SurveyName newName)
void openDraft(int versionNumber)    // recusa se já houver rascunho
void discardDraft()
```

Toda recusa lança de dentro do domínio, com `ErrorType.BUSINESS_RULE`.

**Invariantes**: sem nome não existe; `publishedVersionNumber` só é preenchido uma vez por
publicação e nunca diminui; `draftVersionNumber` nunca coexiste com um segundo rascunho.

---

### `SurveyState` — enum derivado (`domain/entities/SurveyState.java`)

`DRAFT`, `SCHEDULED`, `ACTIVE`, `PAUSED`, `ENDED`. **Não é persistido** (D-05) — é calculado em
`Survey.stateAt(...)`. JSON em minúsculas, convertido no presenter.

A tabela de derivação está em [research.md, D-05](./research.md).

### `SurveyLifecycle` — enum persistido (`domain/entities/SurveyLifecycle.java`)

`DRAFT`, `PUBLISHED`, `PAUSED`, `ENDED`. É o que a coluna guarda: só muda por comando.

---

### `SurveyVersion` — entidade (`domain/entities/SurveyVersion.java`)

Estende `Entity<SurveyVersionId>`.

| Campo | Tipo | Nota |
| --- | --- | --- |
| `id` | `SurveyVersionId` | |
| `surveyId` | `SurveyId` | |
| `number` | `int` | 1..N, único dentro da pesquisa |
| `status` | `SurveyVersionStatus` | `DRAFT` \| `PUBLISHED` |
| `questions` | `List<Question>` | sempre na ordem de exibição (FR-015) |
| `trigger` | `Optional<Trigger>` | |
| `rules` | `List<SegmentationRule>` | vinculadas ao disparo (FR-019) |
| `changeKind` | `Optional<ChangeKind>` | ausente na versão 1 |
| `changeSummary` | `Optional<String>` | idem |
| `comparabilityGroup` | `int` | gravado na publicação (D-12) |
| `publishedAt` | `Optional<Instant>` | presente sse `PUBLISHED` |

**Comportamento de edição** — todos recusam com `SurveyContentFrozen` se `status = PUBLISHED`:

```java
Question addQuestion(QuestionDraft draft)          // nasce na última posição, com QuestionKey nova
void updateQuestion(QuestionId id, QuestionDraft draft)   // a QuestionKey permanece (FR-011)
void removeQuestion(QuestionId id)                 // recompacta as posições (FR-014)
void reorder(List<QuestionId> newOrder)            // exige permutação exata das existentes
void defineTrigger(Trigger trigger)                // substitui; sempre um só (FR-016)
void addRule(SegmentationRule rule)                // exige disparo definido
void removeRule(SegmentationRuleId id)
```

`reorder` verifica a integridade **no resultado final**, não a cada passo — é o edge case da spec.
Uma lista que não seja permutação exata das perguntas existentes é recusada com
`question.order_invalid`.

**Comportamento de publicação**:

```java
List<PublicationImpediment> publicationImpediments()          // D-10
SurveyVersion publish(Instant now, Optional<SurveyVersion> previous,
                      Optional<ChangeKind> declared, String summary)
SurveyVersion copyAsDraft(int newNumber)   // perguntas com a mesma QuestionKey, id novo (D-07)
boolean sameContentAs(SurveyVersion other) // FR-036
```

`publish` é o único ponto que grava `publishedAt` e `comparabilityGroup`, e o único que muda
`status` para `PUBLISHED`. Depois disso a instância não aceita mais nenhuma escrita.

---

### `Question` — entidade (`domain/entities/Question.java`)

Estende `Entity<QuestionId>`.

| Campo | Tipo | Nota |
| --- | --- | --- |
| `id` | `QuestionId` | identidade da linha nesta versão |
| `key` | `QuestionKey` | linhagem entre versões (D-07), imutável |
| `statement` | `QuestionStatement` | enunciado |
| `type` | `QuestionType` | um dos seis (D-09) |
| `position` | `int` | 1..N, consecutiva e sem repetição (FR-014) |
| `required` | `boolean` | |
| `options` | `List<QuestionOption>` | vazia quando o tipo não aceita |
| `range` | `Optional<ScaleRange>` | presente nos tipos que exigem |

**Invariantes na construção** (D-08): opção em tipo que não aceita → `question.options_not_allowed`;
duas opções com o mesmo valor → `question.options_duplicated`; faixa em tipo que não aceita, ou
ausente em tipo que exige, ou incoerente → `question.scale_range_invalid`. Enunciado vazio é
recusado pelo próprio `QuestionStatement`.

**Não é invariante**: pergunta de escolha **sem** opção. É aceita e vira impedimento de
publicação — o rascunho aceita incompleto (US2, cenário 3).

---

### Value objects (`domain/valueobjects/`)

| Tipo | Forma | Invariante |
| --- | --- | --- |
| `SurveyName` | `record(String value)` | não vazio, ≤ 120 caracteres, `strip()` aplicado |
| `QuestionStatement` | `record(String value)` | não vazio, ≤ 500 caracteres, `strip()` aplicado |
| `QuestionKey` | `record(String value)` | UUID; `generate()` e `of(...)` |
| `QuestionOption` | `record(String label, String value, int position)` | ambos não vazios |
| `ScaleRange` | `record(int min, int max)` | `min < max`; NPS fixo em 0–10 |
| `Trigger` | `record(EventName event, TriggerWindow window, SamplingRate rate)` | as três presentes |
| `EventName` | `record(String value)` | `^[a-z][a-z0-9_.]{1,79}$` |
| `TriggerWindow` | `record(Instant start, Optional<Instant> end)` | `end`, quando presente, > `start` |
| `SamplingRate` | `record(double value)` | `0.0 ≤ value ≤ 1.0` |
| `SegmentationRule` | `record(SegmentationRuleId id, String attribute, RuleOperation op, Optional<String> value)` | ver abaixo |

`SegmentationRule`: `attribute` não vazio; `EQUALS`/`NOT_EQUALS` **exigem** valor;
`PRESENT`/`ABSENT` **recusam** valor (FR-021). A avaliação da regra não acontece nesta feature —
ela é da entrega —, mas a semântica de falhar fechado com atributo ausente fica registrada aqui.

`ScaleRange` de `NPS` é imposta: informar outra faixa é recusado.

---

### Enums de domínio

**`QuestionType`** (D-09) — a tabela de exigências mora no próprio enum:

| Valor | JSON | `acceptsOptions` | `requiresOptions` | `requiresRange` |
| --- | --- | --- | --- | --- |
| `SINGLE_CHOICE` | `single_choice` | sim | sim | não |
| `MULTIPLE_CHOICE` | `multiple_choice` | sim | sim | não |
| `RATING` | `rating` | não | não | sim |
| `SCALE` | `scale` | não | não | sim |
| `NPS` | `nps` | não | não | sim (fixa 0–10) |
| `FREE_TEXT` | `free_text` | não | não | não |

**`RuleOperation`**: `EQUALS` (`equals`), `NOT_EQUALS` (`not_equals`), `PRESENT` (`present`),
`ABSENT` (`absent`).

**`ChangeKind`**: `COSMETIC` (`cosmetic`), `SEMANTIC` (`semantic`).

**`SurveyVersionStatus`**: `DRAFT`, `PUBLISHED`.

**`TransitionReason`**: `PUBLICATION`, `MANUAL_PAUSE`, `MANUAL_RESUME`, `MANUAL_END` (persistidas);
`WINDOW_OPENED`, `WINDOW_CLOSED` (derivadas na leitura, D-06).

---

### `PublicationImpediment` — record de domínio (`domain/publication/PublicationImpediment.java`)

```java
record PublicationImpediment(String code, String field, Optional<QuestionKey> questionKey) {}
```

Catálogo fechado:

| `code` | `field` | Quando |
| --- | --- | --- |
| `survey.no_questions` | `questions` | nenhuma pergunta (FR-023) |
| `question.statement_missing` | `questions[].statement` | enunciado vazio sobrevivente |
| `question.options_missing` | `questions[].options` | tipo exige opção e não há nenhuma |
| `trigger.missing` | `trigger` | disparo não definido |
| `trigger.window_invalid` | `trigger.window` | janela incoerente |

Sempre a lista completa, nunca a primeira falha (D-10).

---

### `SurveyStateTransition` — entidade (`domain/entities/SurveyStateTransition.java`)

| Campo | Tipo |
| --- | --- |
| `id` | `SurveyStateTransitionId` |
| `surveyId` | `SurveyId` |
| `from` | `SurveyState` |
| `to` | `SurveyState` |
| `reason` | `TransitionReason` |
| `actor` | `Optional<String>` — sempre vazio até haver autenticação |
| `occurredAt` | `Instant` |

Persistidas só as comandadas; as da janela são derivadas na leitura (D-06).

---

### Identificadores

`SurveyId`, `SurveyVersionId`, `QuestionId`, `SegmentationRuleId`, `SurveyStateTransitionId` —
todos estendem `Id` de `core.identity`, com `generate()`, `of(String)` e validação de UUID, no
mesmo molde de `ApplicationId`. Cada um com seu `code`: `survey.id_invalid`,
`survey_version.id_invalid`, `question.id_invalid`, `segmentation_rule.id_invalid`,
`survey_state_transition.id_invalid`.

`ApplicationId` **move** de `modules/app/domain/entities` para `core/identity` (D-02).

---

## Erros nomeados (`application/errors/`)

| Classe | `ErrorType` | HTTP | `code` |
| --- | --- | --- | --- |
| `SurveyNotFound` | `NOT_FOUND` | 404 | `survey.not_found` |
| `SurveyVersionNotFound` | `NOT_FOUND` | 404 | `survey_version.not_found` |
| `QuestionNotFound` | `NOT_FOUND` | 404 | `question.not_found` |
| `SegmentationRuleNotFound` | `NOT_FOUND` | 404 | `segmentation_rule.not_found` |
| `SurveyContentFrozen` | `BUSINESS_RULE` | 422 | `survey.content_frozen` |
| `SurveyNotPublishable` | `BUSINESS_RULE` | 422 | `survey.not_publishable` — carrega os impedimentos |
| `SurveyAlreadyPublished` | `CONFLICT` | 409 | `survey.already_published` |
| `PublishedSurveyCannotBeDiscarded` | `BUSINESS_RULE` | 422 | `survey.published_cannot_be_discarded` |
| `SurveyNotPublished` | `BUSINESS_RULE` | 422 | `survey.not_published` |
| `SurveyTransitionNotAllowed` | `BUSINESS_RULE` | 422 | `survey.transition_not_allowed` |
| `SurveyDraftVersionAlreadyOpen` | `CONFLICT` | 409 | `survey_version.draft_already_open` |
| `SurveyVersionHasNoChanges` | `BUSINESS_RULE` | 422 | `survey_version.no_changes` |
| `CosmeticDeclarationRefused` | `BUSINESS_RULE` | 422 | `survey_version.cosmetic_refused` — carrega chave e diferença |
| `TriggerNotDefined` | `BUSINESS_RULE` | 422 | `trigger.not_defined` |

Reusados de `modules/app`, via a porta `ApplicationScope`: `application.not_found` e
`application.inactive` (FR-002). Os erros de invariante saem do domínio como `DomainException`
com `ErrorType.VALIDATION` → 400, com os `code` já listados nos value objects.

**Contrato**: o `code` é público e estável; a mensagem é livre. Nenhum teste afirma sobre
mensagem, exceto os de DTO e os E2E.

---

## Portas

### `SurveyRepository` (`application/repositories/SurveyRepository.java`)

```java
Survey create(Survey survey);
Optional<Survey> findByIdAndApplicationId(SurveyId id, ApplicationId applicationId);
Page<Survey> findPage(ApplicationId applicationId, ListSurveysQuery query);
Survey update(Survey survey);
void delete(SurveyId id);
```

### `SurveyVersionRepository` (`application/repositories/SurveyVersionRepository.java`)

```java
SurveyVersion create(SurveyVersion version);
Optional<SurveyVersion> findDraft(SurveyId surveyId);
Optional<SurveyVersion> findByNumber(SurveyId surveyId, int number);
Optional<SurveyVersion> findPublished(SurveyId surveyId);      // a corrente
Page<SurveyVersion> findPublishedPage(SurveyId surveyId, ListSurveyVersionsQuery query);
List<SurveyVersion> findAllPublished(SurveyId surveyId);        // uso interno: grupos (FR-037)
SurveyVersion update(SurveyVersion version);
void delete(SurveyVersionId id);
```

Toda leitura de versão traz perguntas, opções e regras na mesma consulta (`join fetch`) — a porta
nunca devolve versão pela metade, e não há travessia preguiçosa em laço (Princípio V).

### `SurveyStateTransitionRepository`

```java
SurveyStateTransition record(SurveyStateTransition transition);
List<SurveyStateTransition> findBySurveyId(SurveyId surveyId);
```

### `ApplicationScope` (`application/gateways/ApplicationScope.java`) — D-03

```java
Optional<ApplicationScopeState> stateOf(ApplicationId applicationId);   // ACTIVE | INACTIVE
```

### Paginação — reusada de 002, nada novo (D-13)

`core.pagination.Page<T>` e `core.pagination.PageQuery` já existem no repositório:

```java
record Page<T>(List<T> items, long total) {                 // page/size não moram aqui
  <R> Page<R> map(Function<? super T, ? extends R> mapper);
  int totalPages(int size);
}

interface PageQuery {                                       // a consulta de cada porta a implementa
  int page();  int size();  default long offset();
}
```

O módulo declara duas consultas que a implementam e acrescentam o que é seu:

```java
record ListSurveysQuery(int page, int size)                     implements PageQuery {}
record ListSurveyVersionsQuery(int page, int size)              implements PageQuery {}
```

Na borda, o envelope é o `PageResponseDTO<T>` compartilhado de `infra/http/dtos` — nenhum
envelope próprio desta feature.

---

## Persistência

Migration única: `V20260908180000__create_surveys.sql`. `ddl-auto` segue `validate`; entidades JPA
são separadas das de domínio, com mapper explícito.

### `surveys`

| Coluna | Tipo | Nota |
| --- | --- | --- |
| `id` | `varchar(64) pk` | |
| `application_id` | `varchar(64) not null` → `applications(id)` | |
| `name` | `varchar(120) not null` | |
| `lifecycle` | `varchar(16) not null` | `DRAFT`\|`PUBLISHED`\|`PAUSED`\|`ENDED` |
| `published_version_number` | `int` | nulo enquanto rascunho |
| `draft_version_number` | `int` | nulo quando não há rascunho |
| `created_at` | `timestamptz not null` | |

```sql
create index idx_surveys_application_listing
    on surveys (application_id, created_at desc, id desc);
```

Sustenta a listagem paginada de FR-005, ordenada da mais recente para a mais antiga e
determinística por construção. Nome repetido é aceito (edge case da spec) — sem constraint de
unicidade.

### `survey_versions`

| Coluna | Tipo | Nota |
| --- | --- | --- |
| `id` | `varchar(64) pk` | |
| `survey_id` | `varchar(64) not null` → `surveys(id) on delete cascade` | |
| `number` | `int not null` | |
| `status` | `varchar(16) not null` | `DRAFT`\|`PUBLISHED` |
| `trigger_event_name` | `varchar(80)` | as quatro juntas ou nenhuma (D-14) |
| `trigger_window_start` | `timestamptz` | |
| `trigger_window_end` | `timestamptz` | nulo = tempo indeterminado (FR-017) |
| `trigger_sampling_rate` | `numeric(5,4)` | |
| `change_kind` | `varchar(16)` | nulo na versão 1 |
| `change_summary` | `varchar(500)` | |
| `comparability_group` | `int not null` | D-12 |
| `published_at` | `timestamptz` | não nulo sse `status = 'PUBLISHED'` |

```sql
alter table survey_versions add constraint uq_survey_versions_number unique (survey_id, number);
create unique index uq_survey_versions_single_draft
    on survey_versions (survey_id) where status = 'DRAFT';
create index idx_survey_versions_survey_number on survey_versions (survey_id, number desc);
```

O índice único parcial é o que garante no banco a unicidade do rascunho que D-17 descreve — a
checagem do caso de uso sozinha não basta (Princípio V).

### `questions`

| Coluna | Tipo |
| --- | --- |
| `id` | `varchar(64) pk` |
| `version_id` | `varchar(64) not null` → `survey_versions(id) on delete cascade` |
| `question_key` | `varchar(64) not null` |
| `statement` | `varchar(500) not null` |
| `type` | `varchar(24) not null` |
| `position` | `int not null` |
| `required` | `boolean not null` |
| `range_min` | `int` |
| `range_max` | `int` |

```sql
alter table questions add constraint uq_questions_position unique (version_id, position)
    deferrable initially deferred;
alter table questions add constraint uq_questions_key unique (version_id, question_key);
create index idx_questions_version_position on questions (version_id, position);
```

A unicidade de posição é **deferrable**: a reordenação troca várias posições dentro da mesma
transação e passaria por estados intermediários repetidos; o que importa é o resultado final, como
a spec diz no edge case.

### `question_options`

| Coluna | Tipo |
| --- | --- |
| `id` | `varchar(64) pk` |
| `question_id` | `varchar(64) not null` → `questions(id) on delete cascade` |
| `position` | `int not null` |
| `label` | `varchar(200) not null` |
| `value` | `varchar(120) not null` |

```sql
alter table question_options add constraint uq_question_options_value unique (question_id, value);
create index idx_question_options_question on question_options (question_id, position);
```

O `unique` no banco reforça a invariante de opções repetidas.

### `segmentation_rules`

| Coluna | Tipo |
| --- | --- |
| `id` | `varchar(64) pk` |
| `version_id` | `varchar(64) not null` → `survey_versions(id) on delete cascade` |
| `attribute` | `varchar(80) not null` |
| `operation` | `varchar(16) not null` |
| `value` | `varchar(200)` — nulo em `PRESENT`/`ABSENT` |

```sql
create index idx_segmentation_rules_version on segmentation_rules (version_id);
```

### `survey_state_transitions`

| Coluna | Tipo |
| --- | --- |
| `id` | `varchar(64) pk` |
| `survey_id` | `varchar(64) not null` → `surveys(id) on delete cascade` |
| `from_state` | `varchar(16) not null` |
| `to_state` | `varchar(16) not null` |
| `reason` | `varchar(24) not null` |
| `actor` | `varchar(120)` — reservado, sempre nulo |
| `occurred_at` | `timestamptz not null` |

```sql
create index idx_survey_transitions_survey on survey_state_transitions (survey_id, occurred_at);
```

Todo `timestamptz` é gravado e lido em UTC.

---

## Transições de estado

```
                     publish
        DRAFT ─────────────────────► PUBLISHED ──── janela ───► (exposto: scheduled│active│ended)
          │                            │   ▲
          │ discard                pause│   │resume
          ▼                            ▼   │
       (removida)                    PAUSED ┘
                                       │
        PUBLISHED│PAUSED ── end ──────►│──► ENDED   (definitivo, FR-028)
```

Recusas: pausar ou encerrar em `DRAFT` → `survey.not_published`; qualquer transição a partir de
`ENDED` → `survey.transition_not_allowed`; descartar pesquisa já publicada →
`survey.published_cannot_be_discarded`; abrir versão nova em pesquisa encerrada →
`survey.transition_not_allowed` (US6, cenário 13).

Cada transição comandada grava uma linha em `survey_state_transitions` na mesma transação do
comando (D-18).
