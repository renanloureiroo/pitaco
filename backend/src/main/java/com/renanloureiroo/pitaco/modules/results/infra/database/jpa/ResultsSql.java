package com.renanloureiroo.pitaco.modules.results.infra.database.jpa;

// O recorte é um só, repetido em toda consulta: período de abertura, versão pelo número e
// atributo do instantâneo — valor nulo no atributo é o recorte "sem o atributo". Os casts
// existem porque o parâmetro pode chegar nulo, e o Postgres precisa saber o tipo mesmo assim.
final class ResultsSql {

  private ResultsSql() {}

  static final String FROM_DISPLAYS =
      """
        from survey_displays d
        join survey_versions v on v.id = d.version_id
      """;

  static final String WHERE_FILTER =
      """
       where d.application_id = :applicationId
         and d.survey_id = :surveyId
         and (cast(:versionNumber as integer) is null or v.number = cast(:versionNumber as integer))
         and (cast(:openedFrom as timestamptz) is null or d.opened_at >= cast(:openedFrom as timestamptz))
         and (cast(:openedTo as timestamptz) is null or d.opened_at <= cast(:openedTo as timestamptz))
         and (cast(:attribute as varchar) is null
              or (cast(:attributeValue as varchar) is null
                  and not exists (select 1 from survey_display_attributes x
                                   where x.display_id = d.id and x.name = cast(:attribute as varchar)))
              or (cast(:attributeValue as varchar) is not null
                  and exists (select 1 from survey_display_attributes x
                               where x.display_id = d.id
                                 and x.name = cast(:attribute as varchar)
                                 and x.value = cast(:attributeValue as varchar))))
      """;

  static final String DISPLAY_COUNTS =
      """
      select count(distinct d.id),
             count(distinct case when d.outcome = 'COMPLETED' then d.id end),
             count(distinct case when d.outcome = 'DISMISSED' then d.id end),
             count(distinct case when d.outcome = 'STARTED'
                                  and d.opened_at < cast(:abandonedBefore as timestamptz) then d.id end),
             count(distinct case when d.outcome = 'STARTED'
                                  and d.opened_at >= cast(:abandonedBefore as timestamptz) then d.id end),
             count(distinct case when a.status = 'ANSWERED' then d.id end)
      """
          + FROM_DISPLAYS
          + "  left join survey_answers a on a.display_id = d.id\n"
          + WHERE_FILTER;

  static final String TIMELINE =
      """
      select to_char(d.opened_at at time zone 'UTC', 'YYYY-MM-DD'),
             count(*),
             count(case when d.outcome = 'COMPLETED' then 1 end)
      """
          + FROM_DISPLAYS
          + WHERE_FILTER
          + " group by 1 order by 1";

  static final String ANSWER_COUNTS =
      """
      select a.question_key,
             count(case when a.status = 'ANSWERED' then 1 end),
             count(case when a.status = 'SKIPPED' then 1 end),
             count(case when a.status = 'NOT_APPLICABLE' then 1 end)
        from survey_answers a
        join survey_displays d on d.id = a.display_id
        join survey_versions v on v.id = d.version_id
      """
          + WHERE_FILTER
          + " group by a.question_key";

  static final String OPTION_COUNTS =
      """
      select a.question_key, o.option_value, count(*)
        from survey_answer_options o
        join survey_answers a on a.id = o.answer_id
        join survey_displays d on d.id = a.display_id
        join survey_versions v on v.id = d.version_id
      """
          + WHERE_FILTER
          + " and a.status = 'ANSWERED' group by a.question_key, o.option_value";

  static final String NUMERIC_COUNTS =
      """
      select a.question_key, a.numeric_value, count(*)
        from survey_answers a
        join survey_displays d on d.id = a.display_id
        join survey_versions v on v.id = d.version_id
      """
          + WHERE_FILTER
          + " and a.status = 'ANSWERED' and a.numeric_value is not null"
          + " group by a.question_key, a.numeric_value";

  static final String ATTRIBUTE_CATALOG =
      """
      select x.name, x.value, count(*)
        from survey_display_attributes x
        join survey_displays d on d.id = x.display_id
       where d.application_id = :applicationId
         and d.survey_id = :surveyId
       group by x.name, x.value
       order by x.name, x.value
      """;

  static final String OPEN_ANSWERS_WHERE =
      WHERE_FILTER
          + """
         and a.status = 'ANSWERED'
         and a.text_value is not null
         and btrim(a.text_value) <> ''
         and (cast(:notBefore as timestamptz) is null or a.answered_at >= cast(:notBefore as timestamptz))
         and (cast(:term as varchar) is null or a.text_value ilike cast(:term as varchar) escape '\\')
      """;

  static final String OPEN_ANSWERS =
      """
      select a.display_id, a.question_key, a.text_value, a.answered_at
        from survey_answers a
        join survey_displays d on d.id = a.display_id
        join survey_versions v on v.id = d.version_id
      """
          + OPEN_ANSWERS_WHERE
          + " order by a.answered_at desc, a.id desc limit :limit offset :offset";

  static final String OPEN_ANSWERS_COUNT =
      """
      select count(*)
        from survey_answers a
        join survey_displays d on d.id = a.display_id
        join survey_versions v on v.id = d.version_id
      """
          + OPEN_ANSWERS_WHERE;

  // As opções vêm agregadas na própria linha, separadas por um caractere de controle que
  // nenhum valor de opção carrega: uma consulta para o lote inteiro.
  static final String ANSWERS_OF =
      """
      select a.display_id, a.question_key, a.status, a.text_value, a.numeric_value, a.answered_at,
             (select string_agg(o.option_value, chr(31) order by o.position)
                from survey_answer_options o where o.answer_id = a.id)
        from survey_answers a
       where a.display_id in (:displayIds)
       order by a.display_id, a.answered_at
      """;

  static final String ATTRIBUTE_NAMES =
      """
      select distinct x.name
        from survey_display_attributes x
        join survey_displays d on d.id = x.display_id
        join survey_versions v on v.id = d.version_id
      """
          + WHERE_FILTER
          + " order by x.name";

  static final String DISPLAYS_AFTER =
      """
      select d.id, r.identity_value, v.number, d.outcome, d.sdk_version, d.opened_at, d.closed_at
        from survey_displays d
        join survey_versions v on v.id = d.version_id
        join respondents r on r.id = d.respondent_id
      """
          + WHERE_FILTER
          + """
         and (cast(:afterOpenedAt as timestamptz) is null
              or (d.opened_at, d.id) > (cast(:afterOpenedAt as timestamptz), cast(:afterId as varchar)))
       order by d.opened_at asc, d.id asc
       limit :limit
      """;

  static final String ATTRIBUTES_OF =
      """
      select x.display_id, x.name, x.value
        from survey_display_attributes x
       where x.display_id in (:displayIds)
       order by x.display_id, x.name
      """;

  static final String QUESTIONS =
      """
      select q.id, q.question_key, q.statement, q.type, q.position, q.range_min, q.range_max, v.number
        from questions q
        join survey_versions v on v.id = q.version_id
       where v.survey_id = :surveyId
         and v.status = 'PUBLISHED'
         and (cast(:versionNumber as integer) is null or v.number = cast(:versionNumber as integer))
       order by v.number desc, q.position asc
      """;

  // Opções e valores da condição agregados na própria linha, em ordem de valor, pelo mesmo
  // separador de controle de ANSWERS_OF: uma consulta para todas as versões publicadas.
  static final String QUESTION_SHAPES =
      """
      select q.question_key, v.number, q.type, q.range_min, q.range_max,
             q.condition_source_key, q.condition_operator, q.condition_min, q.condition_max,
             (select string_agg(c.value, chr(31) order by c.value)
                from question_condition_values c where c.question_id = q.id),
             (select string_agg(o.value, chr(31) order by o.value)
                from question_options o where o.question_id = q.id)
        from questions q
        join survey_versions v on v.id = q.version_id
       where v.survey_id = :surveyId
         and v.status = 'PUBLISHED'
       order by v.number asc, q.position asc
      """;

  static final String DISPLAYED_VERSIONS =
      "select distinct v.number\n" + FROM_DISPLAYS + WHERE_FILTER;

  static final String QUESTION_OPTIONS =
      """
      select o.question_id, o.label, o.value, o.position
        from question_options o
       where o.question_id in (:questionIds)
       order by o.question_id, o.position
      """;
}
