package com.renanloureiroo.pitaco.modules.results.infra.database.jpa;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyBehaviorReadModel;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.AttributeFilter;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.ResultsFilter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SurveyBehaviorReadModelJpa implements SurveyBehaviorReadModel {

  private final SurveyBehaviorJpaRepository repository;

  public SurveyBehaviorReadModelJpa(SurveyBehaviorJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public BehaviorTotals totalsOf(ResultsFilter filter) {
    var row =
        repository
            .totals(
                filter.applicationId().value(),
                filter.surveyId().value(),
                filter.versionNumber().orElse(null),
                filter.from().orElse(null),
                filter.to().orElse(null),
                attributeOf(filter),
                attributeValueOf(filter))
            .getFirst();
    return new BehaviorTotals(count(row[0]), count(row[1]));
  }

  @Override
  public List<QuestionFunnel> funnelsOf(ResultsFilter filter) {
    return repository
        .funnels(
            filter.applicationId().value(),
            filter.surveyId().value(),
            filter.versionNumber().orElse(null),
            filter.from().orElse(null),
            filter.to().orElse(null),
            attributeOf(filter),
            attributeValueOf(filter))
        .stream()
        .map(
            row ->
                new QuestionFunnel(
                    QuestionKey.of((String) row[0]),
                    count(row[1]),
                    count(row[2]),
                    count(row[3]),
                    count(row[4]),
                    count(row[5]),
                    count(row[6]),
                    count(row[7]),
                    count(row[8])))
        .toList();
  }

  @Override
  public List<QuestionAbandonment> abandonmentsOf(ResultsFilter filter, Instant abandonedBefore) {
    return repository
        .abandonments(
            filter.applicationId().value(),
            filter.surveyId().value(),
            filter.versionNumber().orElse(null),
            filter.from().orElse(null),
            filter.to().orElse(null),
            attributeOf(filter),
            attributeValueOf(filter),
            abandonedBefore)
        .stream()
        .map(row -> new QuestionAbandonment(QuestionKey.of((String) row[0]), count(row[1])))
        .toList();
  }

  @Override
  public List<QuestionActiveTime> activeTimesOf(ResultsFilter filter) {
    return repository
        .activeTimes(
            filter.applicationId().value(),
            filter.surveyId().value(),
            filter.versionNumber().orElse(null),
            filter.from().orElse(null),
            filter.to().orElse(null),
            attributeOf(filter),
            attributeValueOf(filter))
        .stream()
        .map(
            row ->
                new QuestionActiveTime(
                    QuestionKey.of((String) row[0]),
                    count(row[1]),
                    ((Number) row[2]).doubleValue(),
                    ((Number) row[3]).doubleValue()))
        .toList();
  }

  @Override
  public List<DismissalCount> dismissalsOf(ResultsFilter filter) {
    return repository
        .dismissals(
            filter.applicationId().value(),
            filter.surveyId().value(),
            filter.versionNumber().orElse(null),
            filter.from().orElse(null),
            filter.to().orElse(null),
            attributeOf(filter),
            attributeValueOf(filter))
        .stream()
        .map(row -> new DismissalCount(Optional.ofNullable((String) row[0]), count(row[1])))
        .toList();
  }

  private static String attributeOf(ResultsFilter filter) {
    return filter.attribute().map(AttributeFilter::name).orElse(null);
  }

  private static String attributeValueOf(ResultsFilter filter) {
    return filter.attribute().flatMap(AttributeFilter::value).orElse(null);
  }

  private static long count(Object value) {
    return value == null ? 0L : ((Number) value).longValue();
  }
}
