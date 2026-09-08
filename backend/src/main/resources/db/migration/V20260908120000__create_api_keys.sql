create table api_keys (
    -- Mesmo raciocínio de applications.id: identificador é texto opaco no domínio.
    id             varchar(64) primary key,
    application_id varchar(64) not null references applications (id),
    label          varchar(80) not null,
    -- O prefixo é dica pública e pode repetir: quem identifica a chave é o id, e quem
    -- garante que dois segredos não se confundem é o unique do hash.
    prefix         varchar(16) not null,
    secret_hash    varchar(64) not null unique,
    created_at     timestamptz not null,
    revoked_at     timestamptz
);

create index idx_api_keys_application_id on api_keys (application_id);
create index idx_api_keys_prefix on api_keys (prefix);
