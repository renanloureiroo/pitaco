-- Privacidade e retenção: a cascata que a exclusão precisa, o registro de que ela aconteceu, o
-- agregado que sobrevive ao descarte e o aviso de dado pessoal no texto livre.

-- As chaves nasceram sem cascata. O nome gerado pelo Postgres não é contrato, então a restrição
-- antiga é achada pelo catálogo (tabela + coluna) em vez de escrita à mão. survey_displays.version_id
-- fica como está: apagar uma versão com exibição apontando continua bloqueado, e apagar a
-- pesquisa leva as duas no mesmo comando.
do $$
declare
    target record;
    existing text;
begin
    for target in
        select *
          from (values
                  ('respondents', 'application_id', 'applications'),
                  ('surveys', 'application_id', 'applications'),
                  ('api_keys', 'application_id', 'applications'),
                  ('survey_displays', 'application_id', 'applications'),
                  ('survey_displays', 'respondent_id', 'respondents'),
                  ('survey_displays', 'survey_id', 'surveys'),
                  ('application_events', 'application_id', 'applications'),
                  ('application_attributes', 'application_id', 'applications'),
                  ('sdk_version_usage', 'application_id', 'applications'),
                  ('sdk_version_daily_usage', 'application_id', 'applications'),
                  ('suppression_events', 'application_id', 'applications'),
                  ('sdk_error_reports', 'application_id', 'applications'))
               as t(table_name, column_name, referenced)
    loop
        select c.conname
          into existing
          from pg_constraint c
          join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
         where c.conrelid = target.table_name::regclass
           and c.contype = 'f'
           and array_length(c.conkey, 1) = 1
           and a.attname = target.column_name;

        if existing is not null then
            execute format('alter table %I drop constraint %I', target.table_name, existing);
        end if;

        execute format(
            'alter table %I add constraint %I foreign key (%I) references %I (id) on delete cascade',
            target.table_name,
            'fk_' || target.table_name || '_' || target.column_name,
            target.column_name,
            target.referenced);
    end loop;
end
$$;

-- A exclusão de respondente apaga as exibições dele; sem este índice, a cascata varreria a tabela.
create index if not exists idx_survey_displays_respondent on survey_displays (respondent_id);

-- Que houve exclusão, quando e quantas linhas saíram. Nunca quem foi excluído: guardar a
-- referência seria o mesmo dado num lugar diferente.
create table deletion_audits (
    id               varchar(64) primary key,
    application_id   varchar(64) not null references applications (id) on delete cascade,
    displays_deleted int         not null,
    answers_deleted  int         not null,
    -- Reservado para quando houver login por SSO; sempre nulo por enquanto.
    performed_by     varchar(120),
    performed_at     timestamptz not null
);

create index idx_deletion_audits_listing
    on deletion_audits (application_id, performed_at desc, id desc);

-- O agregado das respostas que a retenção descartou. Contagens aditivas, e não um blob: somar
-- ao que continua vivo é um group by, e a forma não varia — status, opção ou valor numérico.
create table aggregate_snapshots (
    id                  varchar(64) primary key,
    survey_id           varchar(64) not null references surveys (id) on delete cascade,
    version_id          varchar(64) not null references survey_versions (id) on delete cascade,
    reason              varchar(16) not null,
    -- Tudo que entrou aqui foi respondido antes deste instante.
    discarded_before    timestamptz not null,
    -- Exibições com ao menos uma resposta dada entre as descartadas: o tamanho da amostra
    -- continua certo depois que as linhas somem.
    responding_displays int         not null,
    computed_at         timestamptz not null
);

alter table aggregate_snapshots add constraint ck_aggregate_snapshots_reason
    check (reason in ('RETENTION'));

create index idx_aggregate_snapshots_survey on aggregate_snapshots (survey_id, version_id);

create table aggregate_snapshot_counts (
    snapshot_id  varchar(64)  not null references aggregate_snapshots (id) on delete cascade,
    question_key varchar(64)  not null,
    -- STATUS conta ANSWERED/SKIPPED/NOT_APPLICABLE; OPTION conta cada valor marcado; NUMBER conta
    -- cada valor numérico.
    dimension    varchar(8)   not null,
    value        varchar(120) not null,
    count        bigint       not null,
    primary key (snapshot_id, question_key, dimension, value)
);

alter table aggregate_snapshot_counts add constraint ck_aggregate_snapshot_counts_dimension
    check (dimension in ('STATUS', 'OPTION', 'NUMBER'));

-- Só as execuções que descartaram algo: é o que responde "já houve descarte?" no aviso do painel.
create table retention_runs (
    id              varchar(64) primary key,
    application_id  varchar(64) not null references applications (id) on delete cascade,
    answers_deleted int         not null,
    texts_cleared   int         not null,
    ran_at          timestamptz not null
);

create index idx_retention_runs_application on retention_runs (application_id, ran_at desc);

-- O descarte procura respostas vencidas por aplicação; answered_at é o critério.
create index idx_survey_answers_answered_at on survey_answers (answered_at);

-- Da pesquisa, não da versão: mudar o aviso não muda o que se pergunta.
alter table surveys add column free_text_notice_enabled boolean not null default true;
alter table surveys add column free_text_notice_text varchar(200);
