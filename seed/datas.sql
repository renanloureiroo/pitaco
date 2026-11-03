-- Traz a demo para perto de agora: desloca todo instante das aplicações demo-completo* pelo
-- mesmo intervalo, de modo que a exibição mais recente fique uma hora atrás. As distâncias entre
-- os eventos não mudam; só o "quando". Pode rodar quantas vezes quiser.
create temp table demo_apps on commit drop as
  select id from public.applications where slug like 'demo-completo%';

create temp table demo_delta on commit drop as
  select (now() - interval '1 hour') - max(d.opened_at) as delta
    from public.survey_displays d where d.application_id in (select id from demo_apps);

update public.applications set created_at = created_at + (select delta from demo_delta),
       updated_at = updated_at + (select delta from demo_delta)
 where id in (select id from demo_apps);
update public.api_keys set created_at = created_at + (select delta from demo_delta),
       revoked_at = revoked_at + (select delta from demo_delta),
       last_used_at = last_used_at + (select delta from demo_delta)
 where application_id in (select id from demo_apps);
update public.surveys set created_at = created_at + (select delta from demo_delta)
 where application_id in (select id from demo_apps);
update public.survey_versions set published_at = published_at + (select delta from demo_delta),
       trigger_window_start = trigger_window_start + (select delta from demo_delta),
       trigger_window_end = trigger_window_end + (select delta from demo_delta)
 where survey_id in (select id from public.surveys where application_id in (select id from demo_apps));
update public.survey_state_transitions set occurred_at = occurred_at + (select delta from demo_delta)
 where survey_id in (select id from public.surveys where application_id in (select id from demo_apps));
update public.respondents set first_seen_at = first_seen_at + (select delta from demo_delta),
       last_seen_at = last_seen_at + (select delta from demo_delta)
 where application_id in (select id from demo_apps);
update public.survey_displays set opened_at = opened_at + (select delta from demo_delta),
       closed_at = closed_at + (select delta from demo_delta)
 where application_id in (select id from demo_apps);
update public.survey_answers set answered_at = answered_at + (select delta from demo_delta)
 where display_id in (select id from public.survey_displays where application_id in (select id from demo_apps));
update public.survey_display_events set occurred_at = occurred_at + (select delta from demo_delta),
       received_at = received_at + (select delta from demo_delta)
 where display_id in (select id from public.survey_displays where application_id in (select id from demo_apps));
update public.application_events set first_seen_at = first_seen_at + (select delta from demo_delta),
       last_seen_at = last_seen_at + (select delta from demo_delta)
 where application_id in (select id from demo_apps);
update public.application_attributes set first_seen_at = first_seen_at + (select delta from demo_delta),
       last_seen_at = last_seen_at + (select delta from demo_delta)
 where application_id in (select id from demo_apps);
update public.application_attribute_values set last_seen_at = last_seen_at + (select delta from demo_delta)
 where attribute_id in (select id from public.application_attributes where application_id in (select id from demo_apps));
update public.sdk_version_usage set first_seen_at = first_seen_at + (select delta from demo_delta),
       last_seen_at = last_seen_at + (select delta from demo_delta)
 where application_id in (select id from demo_apps);
update public.suppression_events set occurred_at = occurred_at + (select delta from demo_delta)
 where application_id in (select id from demo_apps);
update public.sdk_error_reports set occurred_at = occurred_at + (select delta from demo_delta),
       received_at = received_at + (select delta from demo_delta)
 where application_id in (select id from demo_apps);
update public.deletion_audits set performed_at = performed_at + (select delta from demo_delta)
 where application_id in (select id from demo_apps);
update public.retention_runs set ran_at = ran_at + (select delta from demo_delta)
 where application_id in (select id from demo_apps);
update public.aggregate_snapshots set discarded_before = discarded_before + (select delta from demo_delta),
       computed_at = computed_at + (select delta from demo_delta)
 where survey_id in (select id from public.surveys where application_id in (select id from demo_apps));

-- O uso diário do SDK é por dia: troca a tabela inteira da demo pelos mesmos dias deslocados.
create temp table demo_daily on commit drop as
  select application_id, version,
         (day + extract(day from (select delta from demo_delta))::int) as day, request_count
    from public.sdk_version_daily_usage where application_id in (select id from demo_apps);
delete from public.sdk_version_daily_usage where application_id in (select id from demo_apps);
insert into public.sdk_version_daily_usage (application_id, version, day, request_count)
  select application_id, version, day, request_count from demo_daily;
