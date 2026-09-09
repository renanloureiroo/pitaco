# Data Model — 004-response-collection

Três agregados novos em `modules/collect`, uma alteração em `modules/app` e o vocabulário
promovido para `core`. As decisões que sustentam cada escolha estão em [research.md](./research.md).

---

## 1. Vocabulário compartilhado (`core`)

Movido de `modules/survey`, sem mudança de comportamento (D-16).

| Tipo | Pacote | Papel |
| --- | --- | --- |
| `SurveyId`, `SurveyVersionId` | `core/identity` | identificadores que os dois módulos referenciam |
| `QuestionKey` | `core/catalog` | linhagem da pergunta; é o que a resposta aponta (FR-029) |
| `QuestionType` | `core/catalog` | catálogo dos seis tipos, com `acceptsOptions`/`requiresOptions`/`requiresRange`/`fixedRange` |
| `QuestionOption`, `ScaleRange` | `core/catalog` | o que o tipo aceita como valor |
| `EventName` | `core/catalog` | nome do evento, comparado por igualdade exata (FR-013) |
| `SamplingRate` | `core/catalog` | proporção em [0,1] |
| `RuleOperation` | `core/catalog` | `EQUALS`, `NOT_EQUALS`, `PRESENT`, `ABSENT` |
| `SegmentationCriterion` | `core/catalog` | **novo**: `(attribute, operation, value)` — a regra sem o id, que é assunto da autoria |

`modules/survey.SegmentationRule` permanece onde está, guardando o `SegmentationRuleId` e
passando a expor `criterion()`.

---

## 2. Agregado Respondente

**Entidade** `Respondent extends Entity<RespondentId>`

| Campo | Tipo | Regra |
| --- | --- | --- |
| `id` | `RespondentId` | `generate()`/`of()` sobre UUID |
| `applicationId` | `ApplicationId` | obrigatório |
| `identity` | `RespondentIdentity` | obrigatório |
| `firstSeenAt` | `Instant` | gravado na criação |
| `lastSeenAt` | `Instant` | atualizado a cada abertura de exibição (D-10) |

**Value object** `RespondentIdentity(RespondentIdentityKind kind, String value)`

- `kind` ∈ `APP_REFERENCE`, `DEVICE`.
- `of(Optional<String> appReference, Optional<String> deviceId)` — a referência do app prevalece
  quando presente e não vazia; na falta dela vale o dispositivo; sem nenhum dos dois, lança
  `DomainException` `respondent.identity_required`.
- `value` com no máximo 200 caracteres, sem espaços nas pontas, nunca vazio.
- Nunca vai para log (FR-008, SC-013).

**Comportamento**: `create(applicationId, identity, now)`, `restore(...)`, `seenAt(now)`.

**Ciclo de vida**: nasce na abertura da primeira exibição, nunca na consulta de elegibilidade
(D-10). Não guarda atributos — eles vivem no instantâneo da exibição (FR-009).

---

## 3. Agregado Exibição de pesquisa

**Entidade** `SurveyDisplay extends Entity<DisplayId>`

| Campo | Tipo | Regra |
| --- | --- | --- |
| `id` | `DisplayId` | **gerado no dispositivo**; só `of(String)`, validando UUID; é a chave de idempotência (FR-023) |
| `applicationId` | `ApplicationId` | obrigatório; o recorte de toda leitura (SC-012) |
| `respondentId` | `RespondentId` | obrigatório |
| `surveyId` | `SurveyId` | obrigatório |
| `versionId` | `SurveyVersionId` | a versão **exibida**, congelada (FR-022, SC-014) |
| `comparabilityGroup` | `int` | denormalizado da versão exibida (D-12) |
| `outcome` | `DisplayOutcome` | `STARTED` → `COMPLETED` \| `DISMISSED`; avança numa direção só |
| `sdkVersion` | `String` (≤ 40) | opcional |
| `attributes` | `AttributeSnapshot` | instantâneo do que a consulta informou (FR-009) |
| `openedAt` | `Instant` | obrigatório |
| `closedAt` | `Instant` | presente se e somente se o desfecho é final |

**Enum** `DisplayOutcome`: `STARTED`, `COMPLETED`, `DISMISSED`, `ABANDONED`.
`ABANDONED` **nunca é persistido** — é derivado por `outcomeAt(now, timeout)` a partir de
`STARTED` + `openedAt` (D-11), no mesmo molde de `SurveyState` na autoria.

**Value object** `AttributeSnapshot(Map<String,String> values)`

- No máximo 50 atributos; nome ≤ 80 caracteres (espelha `SegmentationCriterion.attribute`);
  valor ≤ 200; nomes normalizados com `strip()`; ordem de inserção preservada.
- Atributo que nenhuma regra usa é aceito e guardado (edge case da spec).

**Transições**

```
STARTED ──complete(now)──▶ COMPLETED       (exige toda obrigatória respondida — FR-032)
STARTED ──dismiss(now)──▶ DISMISSED        (sempre aceito, preserva o que já foi respondido)
COMPLETED / DISMISSED ──qualquer──▶ recusa `display.already_closed` (FR-025, US3.3)
STARTED (openedAt + timeout < now) ──derivado──▶ ABANDONED  (não resolve, conta tentativa — FR-027)
```

---

## 4. Agregado Resposta

**Entidade** `Answer extends Entity<AnswerId>`

| Campo | Tipo | Regra |
| --- | --- | --- |
| `id` | `AnswerId` | gerado |
| `displayId` | `DisplayId` | obrigatório |
| `questionKey` | `QuestionKey` | pertence à versão da exibição (FR-033); única por exibição |
| `status` | `AnswerStatus` | `ANSWERED` \| `SKIPPED` (FR-032) |
| `value` | `Optional<AnswerValue>` | presente se e somente se `ANSWERED` |
| `answeredAt` | `Instant` | obrigatório |

**Sealed interface** `AnswerValue`

| Forma | Serve a | Validação |
| --- | --- | --- |
| `TextValue(AnswerText text)` | `FREE_TEXT` | ≤ 2000 caracteres, não vazia (D-13) |
| `NumericValue(int value)` | `RATING`, `SCALE`, `NPS` | dentro do `ScaleRange` da pergunta |
| `ChoiceValue(List<String> options)` | `SINGLE_CHOICE`, `MULTIPLE_CHOICE` | não vazia, sem repetição, cada valor declarado na pergunta; exatamente um para escolha única |

A resposta é **crua e imutável**: não há edição nem exclusão nesta feature.

---

## 5. Validação da submissão (`domain/collection`)

`SubmissionValidation.check(List<DeliverableQuestion> questions, DisplayOutcome outcome,
List<AnswerDraft> answers)` devolve `List<SubmissionProblem>` — **sempre a lista inteira**, no
molde de `PublicationImpediment` (D-14). `SubmissionProblem(String code, Optional<QuestionKey>
questionKey)`.

Regras verificadas, na ordem em que aparecem no relatório:

| Código | Quando |
| --- | --- |
| `answer.question_unknown` | chave não pertence à versão da exibição |
| `answer.question_duplicated` | mesma chave repetida no envio |
| `answer.required_missing` | `outcome = COMPLETED` e pergunta obrigatória ausente ou `SKIPPED` |
| `answer.value_missing` | `status = ANSWERED` sem valor |
| `answer.value_type_mismatch` | forma do valor incompatível com o tipo da pergunta |
| `answer.option_unknown` | opção não declarada na pergunta |
| `answer.options_empty` | escolha múltipla com conjunto vazio |
| `answer.options_duplicated` | opção repetida no mesmo valor |
| `answer.value_out_of_range` | inteiro fora do `ScaleRange` (NPS `11`, por exemplo) |
| `answer.text_too_long` | texto livre acima do limite |

A validação usa **a versão da exibição**, nunca a publicada corrente (premissa da spec).

---

## 6. Elegibilidade (`domain/eligibility`)

Três peças puras, sem I/O, testáveis em JUnit puro:

- `SegmentationEvaluation.satisfies(List<SegmentationCriterion>, AttributeSnapshot)` — conjunção
  de todos os critérios (FR-015). Atributo ausente ou vazio **não casa** com `EQUALS`; `ABSENT`
  casa com ausente ou vazio; `NOT_EQUALS` sobre atributo ausente **não casa** (falha fechado,
  FR-016).
- `SamplingDecision.accepts(SurveyId, RespondentIdentity, SamplingRate)` — hash determinístico
  (D-05).
- `ResolvedHistory.of(List<DisplayHistoryEntry>, Instant now, Duration timeout, int maxAttempts)`
  — responde `isResolved(surveyId, group)` e `exhaustedAttempts(surveyId, group)` (D-11, D-12).

A ordem das sete camadas da FR-011 é aplicada por `FindEligibleSurveyUseCase` e é o que o teste
de caso de uso verifica explicitamente.

---

## 7. Esquema (migration `V20260908210000__create_collect.sql`)

```sql
create table respondents (
    id             varchar(64) primary key,
    application_id varchar(64) not null references applications (id),
    identity_kind  varchar(16) not null,
    identity_value varchar(200) not null,
    first_seen_at  timestamptz not null,
    last_seen_at   timestamptz not null
);
alter table respondents add constraint uq_respondents_identity
    unique (application_id, identity_kind, identity_value);

create table survey_displays (
    -- Gerada no dispositivo: é a chave de idempotência do reenvio da fila local do SDK.
    id                  varchar(64) primary key,
    application_id      varchar(64) not null references applications (id),
    respondent_id       varchar(64) not null references respondents (id),
    survey_id           varchar(64) not null references surveys (id),
    version_id          varchar(64) not null references survey_versions (id),
    -- Denormalizado da versão exibida, que é imutável: evita uma junção no caminho quente.
    comparability_group int not null,
    -- STARTED, COMPLETED ou DISMISSED. ABANDONED é derivado na leitura, nunca gravado.
    outcome             varchar(16) not null,
    sdk_version         varchar(40),
    opened_at           timestamptz not null,
    closed_at           timestamptz
);
create index idx_survey_displays_history
    on survey_displays (respondent_id, survey_id, comparability_group);
create index idx_survey_displays_survey on survey_displays (survey_id, opened_at desc);

create table survey_display_attributes (
    display_id varchar(64) not null references survey_displays (id) on delete cascade,
    name       varchar(80) not null,
    value      varchar(200) not null,
    primary key (display_id, name)
);

create table survey_answers (
    id            varchar(64) primary key,
    display_id    varchar(64) not null references survey_displays (id) on delete cascade,
    question_key  varchar(64) not null,
    status        varchar(16) not null,
    text_value    varchar(2000),
    numeric_value int,
    answered_at   timestamptz not null
);
-- Uma resposta por pergunta por exibição: é o banco que garante a idempotência do reenvio.
alter table survey_answers add constraint uq_survey_answers_question
    unique (display_id, question_key);

create table survey_answer_options (
    answer_id    varchar(64) not null references survey_answers (id) on delete cascade,
    option_value varchar(120) not null,
    position     int not null,
    primary key (answer_id, option_value)
);

-- Índice parcial: rascunho nunca é candidato, então não ocupa o índice do caminho quente.
create index idx_survey_versions_published_event
    on survey_versions (trigger_event_name, survey_id) where status = 'PUBLISHED';
-- Convive com idx_surveys_application_listing (application_id, created_at desc, id desc), que
-- serve a listagem do painel: aquele não filtra por lifecycle, e é ele que a consulta de
-- candidatos precisa em toda chamada da superfície pública.
create index idx_surveys_application_lifecycle on surveys (application_id, lifecycle);

alter table api_keys add column last_used_at timestamptz;
```

---

## 8. Consultas do caminho quente

**Candidatos** (camadas 2–4 da FR-011), uma consulta:

```sql
select v.id, v.survey_id, v.number, v.comparability_group, v.trigger_sampling_rate, v.published_at
  from survey_versions v
  join surveys s on s.id = v.survey_id and s.published_version_number = v.number
 where v.status = 'PUBLISHED'
   and v.trigger_event_name = :event
   and s.application_id = :applicationId
   and s.lifecycle = 'PUBLISHED'
   and v.trigger_window_start <= :now
   and (v.trigger_window_end is null or v.trigger_window_end > :now)
```

Os critérios de segmentação de cada candidato vêm na mesma travessia (`join fetch` sobre
`segmentation_rules`), nunca em laço.

**Histórico** (camada 5), uma consulta para todos os candidatos:

```sql
select survey_id, comparability_group, outcome, opened_at
  from survey_displays
 where respondent_id = :respondentId and survey_id in (:candidateIds)
```

**Conteúdo** (FR-019), uma consulta, só para a pesquisa escolhida: a versão com perguntas e
opções em `join fetch`, ordenadas por posição.

**Caminho vazio**: só a primeira consulta roda, devolve zero linhas, e nada é escrito (SC-003).
