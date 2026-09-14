package com.renanloureiroo.pitaco.modules.results.infra.database.jpa;

// O recorte é o mesmo de ResultsSql, em CTE: as exibições do recorte primeiro, e os eventos só
// delas. Os campos do payload saem do jsonb com ->>, já normalizados pela ingestão.
final class BehaviorSql {

  private BehaviorSql() {}

  private static final String SCOPED =
      "with scoped as (\n select d.id, d.outcome, d.opened_at\n"
          + ResultsSql.FROM_DISPLAYS
          + ResultsSql.WHERE_FILTER
          + ")\n";

  static final String TOTALS =
      SCOPED
          + """
          select count(*),
                 count(*) filter (where exists (select 1 from survey_display_events e
                                                 where e.display_id = s.id))
            from scoped s
          """;

  static final String FUNNEL =
      SCOPED
          + """
          , per_question as (
            select e.display_id, e.question_key,
                   bool_or(e.type = 'question_viewed') as viewed,
                   bool_or(e.type = 'question_viewed'
                           and cast(e.data ->> 'visit' as integer) >= 2) as revisited,
                   bool_or(e.type = 'answer_selected') as selected,
                   bool_or(e.type in ('answer_changed', 'answer_deselected')) as changed,
                   count(*) filter (where e.type = 'validation_blocked') as blocks
              from survey_display_events e
              join scoped s on s.id = e.display_id
             where e.question_key is not null
             group by e.display_id, e.question_key),
          last_left as (
            select distinct on (e.display_id, e.question_key)
                   e.display_id, e.question_key,
                   e.data ->> 'to' as destination,
                   e.data ->> 'answered' as answered
              from survey_display_events e
              join scoped s on s.id = e.display_id
             where e.type = 'question_left' and e.question_key is not null
             order by e.display_id, e.question_key, e.seq desc)
          select p.question_key,
                 count(*) filter (where p.viewed),
                 count(*) filter (where p.revisited),
                 count(*) filter (where l.answered = 'true'),
                 count(*) filter (where l.answered = 'false'
                                    and l.destination in ('next', 'complete')),
                 count(*) filter (where p.selected),
                 count(*) filter (where p.selected and p.changed),
                 count(*) filter (where p.blocks > 0),
                 coalesce(sum(p.blocks), 0)
            from per_question p
            left join last_left l
              on l.display_id = p.display_id and l.question_key = p.question_key
           group by p.question_key
          """;

  static final String ABANDONMENTS =
      SCOPED
          + """
          , last_viewed as (
            select distinct on (e.display_id) e.display_id, e.question_key
              from survey_display_events e
              join scoped s on s.id = e.display_id
             where e.type = 'question_viewed' and e.question_key is not null
             order by e.display_id, e.seq desc)
          select lv.question_key, count(*)
            from last_viewed lv
            join scoped s on s.id = lv.display_id
           where (s.outcome = 'DISMISSED' and exists (select 1 from survey_answers x where x.display_id = s.id and x.status = 'ANSWERED'))
              or (s.outcome = 'STARTED' and s.opened_at < cast(:abandonedBefore as timestamptz))
           group by lv.question_key
          """;

  static final String ACTIVE_TIMES =
      SCOPED
          + """
          , per_display as (
            select e.question_key, e.display_id,
                   sum(cast(e.data ->> 'activeMs' as bigint)) as active_ms
              from survey_display_events e
              join scoped s on s.id = e.display_id
             where e.type = 'question_left'
               and e.question_key is not null
               and e.data ->> 'activeMs' is not null
             group by e.question_key, e.display_id)
          select question_key, count(*),
                 percentile_cont(0.5) within group (order by active_ms),
                 percentile_cont(0.9) within group (order by active_ms)
            from per_display
           group by question_key
          """;

  static final String DISMISSALS =
      SCOPED
          + """
          , last_dismissal as (
            select distinct on (e.display_id) e.display_id, e.data ->> 'via' as via
              from survey_display_events e
              join scoped s on s.id = e.display_id
             where e.type = 'survey_dismissed'
             order by e.display_id, e.seq desc)
          select via, count(*)
            from last_dismissal
           group by via
          """;
}
