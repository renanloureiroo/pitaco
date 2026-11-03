create table surveys (
    id                       varchar(64) primary key,
    application_id           varchar(64) not null references applications (id),
    name                     varchar(120) not null,
    lifecycle                varchar(16) not null,
    -- Ponteiros para as versões que interessam: a publicada corrente e o rascunho aberto.
    -- Nulos enquanto a pesquisa não passou por cada um desses estados.
    published_version_number int,
    draft_version_number     int,
    created_at               timestamptz not null
);

-- Recorte por aplicação e ordenação da listagem sem sort em memória; o id desempata para que
-- a travessia de páginas seja determinística.
create index idx_surveys_application_listing
    on surveys (application_id, created_at desc, id desc);

create table survey_versions (
    id                    varchar(64) primary key,
    survey_id             varchar(64) not null references surveys (id) on delete cascade,
    number                int not null,
    status                varchar(16) not null,
    -- As quatro colunas do disparo: ou todas preenchidas, ou nenhuma.
    trigger_event_name    varchar(80),
    trigger_window_start  timestamptz,
    trigger_window_end    timestamptz,
    trigger_sampling_rate numeric(5, 4),
    change_kind           varchar(16),
    change_summary        varchar(500),
    comparability_group   int not null,
    published_at          timestamptz
);

alter table survey_versions add constraint uq_survey_versions_number unique (survey_id, number);

-- No máximo um rascunho por pesquisa, garantido no banco e não só na checagem do caso de uso.
create unique index uq_survey_versions_single_draft
    on survey_versions (survey_id) where status = 'DRAFT';

create index idx_survey_versions_survey_number on survey_versions (survey_id, number desc);

create table questions (
    id           varchar(64) primary key,
    version_id   varchar(64) not null references survey_versions (id) on delete cascade,
    question_key varchar(64) not null,
    statement    varchar(500) not null,
    type         varchar(24) not null,
    position     int not null,
    required     boolean not null,
    range_min    int,
    range_max    int
);

-- Deferrable: a reordenação troca várias posições na mesma transação e passa por estados
-- intermediários repetidos; o que precisa ser íntegro é o resultado final.
alter table questions add constraint uq_questions_position unique (version_id, position)
    deferrable initially deferred;
alter table questions add constraint uq_questions_key unique (version_id, question_key);
create index idx_questions_version_position on questions (version_id, position);

create table question_options (
    id          varchar(64) primary key,
    question_id varchar(64) not null references questions (id) on delete cascade,
    position    int not null,
    label       varchar(200) not null,
    value       varchar(120) not null
);

alter table question_options add constraint uq_question_options_value unique (question_id, value);
create index idx_question_options_question on question_options (question_id, position);

create table segmentation_rules (
    id         varchar(64) primary key,
    version_id varchar(64) not null references survey_versions (id) on delete cascade,
    attribute  varchar(80) not null,
    operation  varchar(16) not null,
    value      varchar(200)
);

create index idx_segmentation_rules_version on segmentation_rules (version_id);

create table survey_state_transitions (
    id          varchar(64) primary key,
    survey_id   varchar(64) not null references surveys (id) on delete cascade,
    from_state  varchar(16) not null,
    to_state    varchar(16) not null,
    reason      varchar(24) not null,
    -- Reservado para quando houver autenticação; sempre nulo por enquanto.
    actor       varchar(120),
    occurred_at timestamptz not null
);

create index idx_survey_transitions_survey on survey_state_transitions (survey_id, occurred_at);
