package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.mappers.SurveyDisplayMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

  private SurveyDisplay save(SurveyDisplay display) {
    repository.save(SurveyDisplayMapper.toJpa(display));
    return display;
  }
}
