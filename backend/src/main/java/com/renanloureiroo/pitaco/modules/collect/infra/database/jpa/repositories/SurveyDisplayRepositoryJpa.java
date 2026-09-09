package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.mappers.SurveyDisplayMapper;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.projections.DisplaySummaryProjection;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.projections.RespondentDisplayProjection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class SurveyDisplayRepositoryJpa implements SurveyDisplayRepository {

  private final SurveyDisplayJpaRepository repository;

  public SurveyDisplayRepositoryJpa(SurveyDisplayJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<SurveyDisplay> findById(DisplayId id, ApplicationId applicationId) {
    return repository
        .findByIdAndApplicationId(id.value(), applicationId.value())
        .map(SurveyDisplayMapper::toDomain);
  }

  @Override
  public SurveyDisplay create(SurveyDisplay display) {
    return save(display);
  }

  @Override
  public SurveyDisplay update(SurveyDisplay display) {
    return save(display);
  }

  @Override
  public List<DisplayHistoryEntry> historyOf(RespondentId respondentId, List<SurveyId> surveyIds) {
    if (surveyIds.isEmpty()) {
      return List.of();
    }

    return repository
        .findHistory(respondentId.value(), surveyIds.stream().map(SurveyId::value).toList())
        .stream()
        .map(
            row ->
                new DisplayHistoryEntry(
                    SurveyId.of((String) row[0]),
                    (Integer) row[1],
                    DisplayOutcome.valueOf((String) row[2]),
                    (Instant) row[3]))
        .toList();
  }

  @Override
  public Page<DisplaySummary> findPage(ListDisplaysQuery query) {
    var page =
        repository.findSummaryPage(
            query.applicationId().value(),
            query.surveyId().value(),
            query.versionId().map(SurveyVersionId::value).orElse(null),
            query.outcome().map(DisplayOutcome::name).orElse(null),
            query.openedFrom().orElse(null),
            query.openedTo().orElse(null),
            PageRequest.of(query.page(), query.size()));

    return new Page<>(
        page.getContent().stream().map(SurveyDisplayRepositoryJpa::summaryOf).toList(),
        page.getTotalElements());
  }

  @Override
  public Page<RespondentDisplaySummary> findPageByRespondent(ListRespondentDisplaysQuery query) {
    var page =
        repository.findRespondentSummaryPage(
            query.applicationId().value(),
            query.respondentId().value(),
            query.outcome().map(DisplayOutcome::name).orElse(null),
            query.openedFrom().orElse(null),
            query.openedTo().orElse(null),
            PageRequest.of(query.page(), query.size()));

    return new Page<>(
        page.getContent().stream()
            .map(
                projection ->
                    new RespondentDisplaySummary(
                        SurveyId.of(projection.surveyId()), summaryOf(projection)))
            .toList(),
        page.getTotalElements());
  }

  private static DisplaySummary summaryOf(DisplaySummaryProjection projection) {
    return new DisplaySummary(
        DisplayId.of(projection.id()),
        SurveyVersionId.of(projection.versionId()),
        projection.versionNumber(),
        projection.comparabilityGroup(),
        DisplayOutcome.valueOf(projection.outcome()),
        Optional.ofNullable(projection.sdkVersion()),
        projection.openedAt(),
        Optional.ofNullable(projection.closedAt()));
  }

  private static DisplaySummary summaryOf(RespondentDisplayProjection projection) {
    return new DisplaySummary(
        DisplayId.of(projection.id()),
        SurveyVersionId.of(projection.versionId()),
        projection.versionNumber(),
        projection.comparabilityGroup(),
        DisplayOutcome.valueOf(projection.outcome()),
        Optional.ofNullable(projection.sdkVersion()),
        projection.openedAt(),
        Optional.ofNullable(projection.closedAt()));
  }

  private SurveyDisplay save(SurveyDisplay display) {
    repository.save(SurveyDisplayMapper.toJpa(display));
    return display;
  }
}
