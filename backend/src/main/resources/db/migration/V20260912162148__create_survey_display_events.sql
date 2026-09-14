-- Eventos de interação do SDK: o que aconteceu dentro de uma exibição, pergunta a pergunta, no
-- catálogo fechado do Pitaco. Nunca conteúdo de texto livre: evento de texto guarda só o tamanho.
create table survey_display_events (
    id              varchar(64) primary key,
    -- A cascata é o que faz a exclusão do respondente e a da pesquisa levarem os eventos junto.
    display_id      varchar(64) not null references survey_displays (id) on delete cascade,
    -- Monotônico por exibição, gerado no dispositivo: com a exibição, é a chave de idempotência.
    seq             int         not null,
    catalog_version int         not null,
    -- Sem check: o catálogo cresce por versão, e o servidor é quem decide o que reconhece.
    type            varchar(40) not null,
    question_key    varchar(64),
    -- Relógio do dispositivo; elapsed_ms é o monotônico desde a apresentação.
    occurred_at     timestamptz not null,
    elapsed_ms      bigint      not null,
    received_at     timestamptz not null,
    -- jsonb porque cada tipo tem o próprio payload fechado; só os campos do catálogo chegam aqui.
    data            jsonb       not null default '{}'::jsonb
);

alter table survey_display_events add constraint uq_survey_display_events_seq
    unique (display_id, seq);

-- A leitura de comportamento parte das exibições do recorte e busca os eventos de cada uma por
-- tipo, na ordem de seq.
create index idx_survey_display_events_type
    on survey_display_events (display_id, type, seq) include (question_key);

-- A retenção procura eventos vencidos pelo recebimento.
create index idx_survey_display_events_received on survey_display_events (received_at);
