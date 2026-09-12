package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemorySurveyRepository implements SurveyRepository {

  private final Map<SurveyId, Survey> surveys = new LinkedHashMap<>();
  private final InMemorySurveyVersionRepository versions;

  public InMemorySurveyRepository() {
    this(null);
  }

  // A consulta de concorrentes cruza pesquisa e versão publicada, que no banco é uma junção;
  // aqui o fake precisa enxergar o repositório de versões para responder.
  public InMemorySurveyRepository(InMemorySurveyVersionRepository versions) {
    this.versions = versions;
  }

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
  public Optional<Survey> lockByIdAndApplicationId(SurveyId id, ApplicationId applicationId) {
    return findByIdAndApplicationId(id, applicationId);
  }

  @Override
  public boolean endIfLive(SurveyId id) {
    var stored = surveys.get(id);
    if (stored == null
        || (stored.getLifecycle() != SurveyLifecycle.PUBLISHED
            && stored.getLifecycle() != SurveyLifecycle.PAUSED)) {
      return false;
    }

    surveys.put(id, copyOf(stored, SurveyLifecycle.ENDED));
    return true;
  }

  @Override
  public List<Survey> findLiveListeningTo(
      ApplicationId applicationId, EventName event, Instant now) {
    if (versions == null) {
      throw new IllegalStateException("Fake criado sem o repositório de versões");
    }

    return surveys.values().stream()
        .filter(survey -> survey.getApplicationId().equals(applicationId))
        .filter(survey -> survey.getLifecycle() == SurveyLifecycle.PUBLISHED)
        .filter(
            survey ->
                versions
                    .findPublished(survey.id())
                    .flatMap(SurveyVersion::trigger)
                    .filter(trigger -> trigger.event().equals(event))
                    .filter(trigger -> !trigger.window().hasClosedAt(now))
                    .isPresent())
        .sorted(
            Comparator.comparing(Survey::getCreatedAt).thenComparing(survey -> survey.id().value()))
        .map(InMemorySurveyRepository::copyOf)
        .toList();
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
    return copyOf(survey, survey.getLifecycle());
  }

  private static Survey copyOf(Survey survey, SurveyLifecycle lifecycle) {
    return Survey.restore(
        survey.id(),
        survey.getApplicationId(),
        survey.getName(),
        lifecycle,
        survey.publishedVersionNumber().orElse(null),
        survey.draftVersionNumber().orElse(null),
        survey.getExposure(),
        survey.template(),
        survey.getFreeTextNotice(),
        survey.getCreatedAt());
  }
}
