create table applications (
    -- O identificador é texto opaco no domínio: hoje um UUID, mas a coluna não
    -- prende o formato para que trocá-lo não custe uma migration de tipo.
    id                       varchar(64)  primary key,
    slug                     varchar(50)  not null unique,
    name                     varchar(120) not null,
    status                   varchar(20)  not null,
    quiet_period_days        integer,
    retention_days           integer,
    open_text_retention_days integer,
    created_at               timestamptz  not null,
    updated_at               timestamptz  not null
);
