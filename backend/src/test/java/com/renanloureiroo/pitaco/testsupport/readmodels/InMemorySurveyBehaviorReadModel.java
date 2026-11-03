package com.renanloureiroo.pitaco.testsupport.readmodels;

import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyBehaviorReadModel;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.ResultsFilter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// As contagens chegam prontas, como da consulta agrupada: quem prova o SQL é o E2E, e aqui o
// caso de uso é testado sobre o que ele faz com os números.
public class InMemorySurveyBehaviorReadModel implements SurveyBehaviorReadModel {

  private BehaviorTotals totals = new BehaviorTotals(0, 0);
  private final List<QuestionFunnel> funnels = new ArrayList<>();
  private final List<QuestionAbandonment> abandonments = new ArrayList<>();
  private final List<QuestionActiveTime> activeTimes = new ArrayList<>();
  private final List<DismissalCount> dismissals = new ArrayList<>();
  private final List<ResultsFilter> filters = new ArrayList<>();
  private Instant lastAbandonedBefore;

  public InMemorySurveyBehaviorReadModel withTotals(long displayed, long instrumented) {
    totals = new BehaviorTotals(displayed, instrumented);
    return this;
  }

  public InMemorySurveyBehaviorReadModel withFunnel(QuestionFunnel funnel) {
    funnels.add(funnel);
    return this;
  }

  public InMemorySurveyBehaviorReadModel withAbandonment(QuestionAbandonment abandonment) {
    abandonments.add(abandonment);
    return this;
  }

  public InMemorySurveyBehaviorReadModel withActiveTime(QuestionActiveTime activeTime) {
    activeTimes.add(activeTime);
    return this;
  }

  public InMemorySurveyBehaviorReadModel withDismissals(Optional<String> via, long count) {
    dismissals.add(new DismissalCount(via, count));
    return this;
  }

  public List<ResultsFilter> filters() {
    return List.copyOf(filters);
  }

  public Optional<Instant> lastAbandonedBefore() {
    return Optional.ofNullable(lastAbandonedBefore);
  }

  @Override
  public BehaviorTotals totalsOf(ResultsFilter filter) {
    filters.add(filter);
    return totals;
  }

  @Override
  public List<QuestionFunnel> funnelsOf(ResultsFilter filter) {
    return List.copyOf(funnels);
  }

  @Override
  public List<QuestionAbandonment> abandonmentsOf(ResultsFilter filter, Instant abandonedBefore) {
    lastAbandonedBefore = abandonedBefore;
    return List.copyOf(abandonments);
  }

  @Override
  public List<QuestionActiveTime> activeTimesOf(ResultsFilter filter) {
    return List.copyOf(activeTimes);
  }

  @Override
  public List<DismissalCount> dismissalsOf(ResultsFilter filter) {
    return List.copyOf(dismissals);
  }
}
