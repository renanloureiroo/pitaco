package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersionStatus;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class InMemorySurveyVersionRepository implements SurveyVersionRepository {

  private final Map<SurveyVersionId, SurveyVersion> versions = new LinkedHashMap<>();

  @Override
  public SurveyVersion create(SurveyVersion version) {
    versions.put(version.id(), copyOf(version));
    return version;
  }

  @Override
  public Optional<SurveyVersion> findDraft(SurveyId surveyId) {
    return of(surveyId)
        .filter(version -> version.getStatus() == SurveyVersionStatus.DRAFT)
        .findFirst()
        .map(InMemorySurveyVersionRepository::copyOf);
  }

  @Override
  public Optional<SurveyVersion> findByNumber(SurveyId surveyId, int number) {
    return of(surveyId)
        .filter(version -> version.getNumber() == number)
        .findFirst()
        .map(InMemorySurveyVersionRepository::copyOf);
  }

  @Override
  public Optional<SurveyVersion> findPublished(SurveyId surveyId) {
    return publishedOf(surveyId).findFirst().map(InMemorySurveyVersionRepository::copyOf);
  }

  @Override
  public Page<SurveyVersion> findPage(ListSurveyVersionsQuery query) {
    var matching =
        of(query.surveyId())
            .sorted(Comparator.comparingInt(SurveyVersion::getNumber).reversed())
            .toList();

    var items =
        matching.stream()
            .skip(query.offset())
            .limit(query.size())
            .map(InMemorySurveyVersionRepository::copyOf)
            .toList();

    return new Page<>(items, matching.size());
  }

  @Override
  public List<SurveyVersion> findAllPublished(SurveyId surveyId) {
    return publishedOf(surveyId).map(InMemorySurveyVersionRepository::copyOf).toList();
  }

  @Override
  public Map<SurveyId, TriggerWindow> findPublishedWindows(List<SurveyId> surveyIds) {
    var wanted = Set.copyOf(surveyIds);
    var windows = new LinkedHashMap<SurveyId, TriggerWindow>();

    versions.values().stream()
        .filter(version -> version.getStatus() == SurveyVersionStatus.PUBLISHED)
        .filter(version -> wanted.contains(version.getSurveyId()))
        .forEach(
            version ->
                version
                    .trigger()
                    .ifPresent(trigger -> windows.put(version.getSurveyId(), trigger.window())));

    return Map.copyOf(windows);
  }

  @Override
  public SurveyVersion update(SurveyVersion version) {
    versions.put(version.id(), copyOf(version));
    return version;
  }

  @Override
  public void delete(SurveyVersionId id) {
    versions.remove(id);
  }

  @Override
  public void deleteBySurveyId(SurveyId surveyId) {
    versions.values().removeIf(version -> version.getSurveyId().equals(surveyId));
  }

  public List<SurveyVersion> findAll() {
    return List.copyOf(versions.values());
  }

  public boolean isEmpty() {
    return versions.isEmpty();
  }

  private java.util.stream.Stream<SurveyVersion> of(SurveyId surveyId) {
    return versions.values().stream().filter(version -> version.getSurveyId().equals(surveyId));
  }

  private java.util.stream.Stream<SurveyVersion> publishedOf(SurveyId surveyId) {
    return of(surveyId)
        .filter(version -> version.getStatus() == SurveyVersionStatus.PUBLISHED)
        .sorted(Comparator.comparingInt(SurveyVersion::getNumber).reversed());
  }

  private static SurveyVersion copyOf(SurveyVersion version) {
    return SurveyVersion.restore(
        version.id(),
        version.getSurveyId(),
        version.getNumber(),
        version.getStatus(),
        version.getQuestions(),
        version.trigger(),
        version.getRules(),
        version.changeKind(),
        version.changeSummary(),
        version.getComparabilityGroup(),
        version.publishedAt());
  }
}
