-- Catálogo dos eventos que cada aplicação já disparou: uma linha por nome, não um log. É o que
-- deixa a autoria escolher o evento de disparo entre os já vistos em vez de digitá-lo de memória.
create table application_events (
    id             varchar(64) primary key,
    application_id varchar(64) not null references applications (id),
    name           varchar(80) not null,
    first_seen_at  timestamptz not null,
    last_seen_at   timestamptz not null
);

-- É o alvo do upsert do caminho quente: o mesmo evento repetido é uma linha só.
alter table application_events add constraint uq_application_events_name
    unique (application_id, name);

-- Listagem do painel: os vistos mais recentemente primeiro, com o nome desempatando.
create index idx_application_events_listing
    on application_events (application_id, last_seen_at desc, name);
