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

-- Convive com idx_surveys_application_listing, que serve a listagem do painel: aquele não
-- filtra por lifecycle, e é este que a consulta de candidatos precisa em toda chamada da
-- superfície pública.
create index idx_surveys_application_lifecycle on surveys (application_id, lifecycle);

alter table api_keys add column last_used_at timestamptz;
