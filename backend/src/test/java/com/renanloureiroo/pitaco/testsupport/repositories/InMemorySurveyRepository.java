package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemorySurveyRepository implements SurveyRepository {

  private final Map<SurveyId, Survey> surveys = new LinkedHashMap<>();

  @Override
  public Survey create(Survey survey) {
    surveys.put(survey.id(), copyOf(survey));
    return survey;
  }

  @Override
  public Optional<Survey> findByIdAndApplicationId(SurveyId id, ApplicationId applicationId) {
    return Optional.ofNullable(surveys.get(id))
        .filter(survey -> survey.getApplicationId().equals(applicationId))
        .map(InMemorySurveyRepository::copyOf);
  }

  @Override
  public Page<Survey> findPage(ListSurveysQuery query) {
    var matching =
        surveys.values().stream()
            .filter(survey -> survey.getApplicationId().equals(query.applicationId()))
            .sorted(
                Comparator.comparing(Survey::getCreatedAt)
                    .thenComparing(survey -> survey.id().value())
                    .reversed())
            .toList();

    var items =
        matching.stream()
            .skip(query.offset())
            .limit(query.size())
            .map(InMemorySurveyRepository::copyOf)
            .toList();

    return new Page<>(items, matching.size());
  }

  @Override
  public Survey update(Survey survey) {
    surveys.put(survey.id(), copyOf(survey));
    return survey;
  }

  @Override
  public void delete(SurveyId id) {
    surveys.remove(id);
  }

  public List<Survey> findAll() {
    return List.copyOf(surveys.values());
  }

  public boolean isEmpty() {
    return surveys.isEmpty();
  }

  private static Survey copyOf(Survey survey) {
    return Survey.restore(
        survey.id(),
        survey.getApplicationId(),
        survey.getName(),
        survey.getLifecycle(),
        survey.publishedVersionNumber().orElse(null),
        survey.draftVersionNumber().orElse(null),
        survey.getCreatedAt());
  }
}
