-- Autoria avançada: modelo de origem da pesquisa, rótulos dos extremos da escala e a condição
-- que decide se uma pergunta aparece.

alter table surveys add column template_kind varchar(8);
alter table surveys add constraint ck_surveys_template_kind
    check (template_kind is null or template_kind in ('NPS', 'CSAT', 'CES'));

alter table questions add column range_min_label varchar(60);
alter table questions add column range_max_label varchar(60);

-- A origem é apontada pela chave estável, e não pelo id da linha: a chave atravessa versões e é
-- única dentro de uma, então a condição copiada para a versão seguinte continua valendo sem
-- reescrita. A FK composta garante no banco que a origem é da mesma versão; deferível porque a
-- versão é regravada inteira, e a ordem das linhas no meio do flush não importa.
alter table questions add column condition_source_key varchar(64);
alter table questions add column condition_operator varchar(16);
alter table questions add column condition_min int;
alter table questions add column condition_max int;

alter table questions add constraint ck_questions_condition_operator
    check ((condition_source_key is null) = (condition_operator is null));
alter table questions add constraint ck_questions_condition_operator_values
    check (condition_operator is null
           or condition_operator in ('EQUALS', 'NOT_EQUALS', 'IN', 'BETWEEN'));
alter table questions add constraint fk_questions_condition_source
    foreign key (version_id, condition_source_key) references questions (version_id, question_key)
    deferrable initially deferred;

-- Tabela filha, não jsonb: os valores têm forma fixa — texto curto, em ordem — como as opções da
-- pergunta, que já moram numa tabela assim.
create table question_condition_values (
    question_id varchar(64)  not null references questions (id) on delete cascade,
    position    int          not null,
    value       varchar(120) not null,
    primary key (question_id, position)
);

-- As respostas não aplicáveis entram na contagem por pergunta, junto das respondidas e puladas.
-- O status continua texto livre na coluna; nada a migrar.
