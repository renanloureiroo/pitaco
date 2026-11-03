-- Controle de exposição: o que cada pesquisa pode disputar da atenção do respondente, e o que
-- o app já enviou como atributo para a autoria montar regra sem digitar de memória.

-- Da pesquisa, não da versão: mudar prioridade ou cota não muda o instrumento de medida.
alter table surveys add column priority int not null default 0;
alter table surveys add column response_quota int;
alter table surveys add column ignores_quiet_period boolean not null default false;

alter table surveys add constraint ck_surveys_priority check (priority between -100 and 100);
alter table surveys add constraint ck_surveys_response_quota
    check (response_quota is null or response_quota >= 1);

-- A contagem que decide o encerramento por cota roda a cada conclusão. Parcial porque só as
-- concluídas contam; o intervalo de descanso usa o idx_survey_displays_respondent_listing, que
-- já tem (respondent_id, opened_at desc) na frente.
create index idx_survey_displays_completed on survey_displays (survey_id)
    where outcome = 'COMPLETED';

-- Catálogo, não perfil: um nome por aplicação e um valor por nome, sem ligação com respondente.
create table application_attributes (
    id             varchar(64) primary key,
    application_id varchar(64) not null references applications (id),
    name           varchar(80) not null,
    first_seen_at  timestamptz not null,
    last_seen_at   timestamptz not null
);

alter table application_attributes add constraint uq_application_attributes_name
    unique (application_id, name);

create index idx_application_attributes_listing
    on application_attributes (application_id, last_seen_at desc, name);

create table application_attribute_values (
    id           varchar(64) primary key,
    attribute_id varchar(64) not null references application_attributes (id) on delete cascade,
    value        varchar(200) not null,
    last_seen_at timestamptz not null
);

-- Alvo do upsert e da contagem que limita os valores distintos por atributo.
alter table application_attribute_values add constraint uq_application_attribute_values_value
    unique (attribute_id, value);
