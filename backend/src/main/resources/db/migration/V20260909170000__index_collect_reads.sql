-- Índices das leituras de coleta. Nenhuma coluna nova: o filtro é a coluna líder, a ordenação e
-- o desempate já vêm ordenados do índice, e os demais campos da projeção viajam no include,
-- avaliados antes do heap.

-- Prefixo redundante do idx_survey_displays_survey_listing abaixo.
drop index idx_survey_displays_survey;

create index idx_survey_displays_survey_listing
    on survey_displays (survey_id, opened_at desc, id desc)
    include (version_id, outcome, closed_at, comparability_group, sdk_version, respondent_id);

create index idx_survey_displays_respondent_listing
    on survey_displays (respondent_id, opened_at desc, id desc)
    include (survey_id, version_id, outcome, closed_at, comparability_group, sdk_version);

create index idx_respondents_application_listing
    on respondents (application_id, last_seen_at desc, id desc);
