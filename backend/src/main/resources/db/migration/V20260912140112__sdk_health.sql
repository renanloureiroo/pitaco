-- Saúde e compatibilidade: o que acontece do outro lado, em apps que atualizam no ritmo dos
-- outros. Versões do SDK em uso, pesquisas que o SDK não soube desenhar e falhas do próprio SDK.

-- Rollup, não log: uma linha por versão por aplicação, com o acumulado e o último contato.
create table sdk_version_usage (
    id             varchar(64) primary key,
    application_id varchar(64) not null references applications (id),
    version        varchar(40) not null,
    request_count  bigint      not null,
    first_seen_at  timestamptz not null,
    last_seen_at   timestamptz not null
);

alter table sdk_version_usage add constraint uq_sdk_version_usage_version
    unique (application_id, version);

-- O total por dia é o que dá o tráfego recente. Sem ele, uma versão que foi maioria no ano
-- passado continuaria pesando na conta de hoje pelo acumulado.
create table sdk_version_daily_usage (
    application_id varchar(64) not null references applications (id),
    version        varchar(40) not null,
    day            date        not null,
    request_count  bigint      not null,
    primary key (application_id, version, day)
);

create index idx_sdk_version_daily_usage_recent on sdk_version_daily_usage (application_id, day);
create index idx_sdk_version_daily_usage_day on sdk_version_daily_usage (day);

-- Não é exibição: a pesquisa suprimida nunca foi mostrada, e contá-la em survey_displays
-- estragaria a taxa de resposta que justifica aquela tabela.
create table suppression_events (
    id                   varchar(64) primary key,
    application_id       varchar(64) not null references applications (id),
    survey_id            varchar(64) not null references surveys (id) on delete cascade,
    version_id           varchar(64) not null references survey_versions (id) on delete cascade,
    -- Só quando o respondente já existe: a supressão não cria respondente.
    respondent_id        varchar(64) references respondents (id) on delete cascade,
    -- Resumo da identidade, não a identidade: serve só para não contar duas vezes o mesmo
    -- dispositivo na mesma versão.
    dedup_key            varchar(64),
    sdk_version          varchar(40),
    reason               varchar(32) not null,
    min_required_version varchar(40) not null,
    occurred_at          timestamptz not null
);

alter table suppression_events add constraint ck_suppression_events_reason
    check (reason in ('UNKNOWN_QUESTION_TYPE', 'UNSUPPORTED_FEATURE'));

create index idx_suppression_events_survey on suppression_events (survey_id, occurred_at);
create index idx_suppression_events_dedup
    on suppression_events (version_id, dedup_key, occurred_at desc) where dedup_key is not null;
create index idx_suppression_events_respondent
    on suppression_events (respondent_id) where respondent_id is not null;

-- jsonb porque o contexto é genuinamente variável: é o estado que o SDK achou relevante, e a
-- forma muda com o tipo de falha.
create table sdk_error_reports (
    id             varchar(64)  primary key,
    application_id varchar(64)  not null references applications (id),
    sdk_version    varchar(40),
    kind           varchar(32)  not null,
    message        varchar(500) not null,
    context        jsonb        not null default '{}'::jsonb,
    occurred_at    timestamptz  not null,
    received_at    timestamptz  not null
);

alter table sdk_error_reports add constraint ck_sdk_error_reports_kind
    check (kind in ('RENDER_ERROR', 'NETWORK_ERROR', 'MALFORMED_RESPONSE', 'STORAGE_ERROR',
                    'UNKNOWN'));

create index idx_sdk_error_reports_listing
    on sdk_error_reports (application_id, received_at desc, id desc);
create index idx_sdk_error_reports_kind
    on sdk_error_reports (application_id, kind, received_at desc);
create index idx_sdk_error_reports_version
    on sdk_error_reports (application_id, sdk_version, received_at desc);
create index idx_sdk_error_reports_received on sdk_error_reports (received_at);
