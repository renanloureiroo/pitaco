package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.mappers.SurveyJpaMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class SurveyRepositoryJpa implements SurveyRepository {

  private final SurveyJpaRepository repository;

  public SurveyRepositoryJpa(SurveyJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Survey create(Survey survey) {
    return SurveyJpaMapper.toDomain(repository.save(SurveyJpaMapper.toJpa(survey)));
  }

  @Override
  public Optional<Survey> findByIdAndApplicationId(SurveyId id, ApplicationId applicationId) {
    return repository
        .findByIdAndApplicationId(id.value(), applicationId.value())
        .map(SurveyJpaMapper::toDomain);
  }

  @Override
  public Page<Survey> findPage(ListSurveysQuery query) {
    var pageable =
        PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

    var page = repository.findByApplicationId(query.applicationId().value(), pageable);

    return new Page<>(
        page.getContent().stream().map(SurveyJpaMapper::toDomain).toList(),
        page.getTotalElements());
  }

  @Override
  public Survey update(Survey survey) {
    return SurveyJpaMapper.toDomain(repository.save(SurveyJpaMapper.toJpa(survey)));
  }

  @Override
  public Optional<Survey> lockByIdAndApplicationId(SurveyId id, ApplicationId applicationId) {
    return repository
        .lockByIdAndApplicationId(id.value(), applicationId.value())
        .map(SurveyJpaMapper::toDomain);
  }

  @Override
  public boolean endIfLive(SurveyId id) {
    return repository.endIfLive(id.value()) == 1;
  }

  @Override
  public List<Survey> findLiveListeningTo(
      ApplicationId applicationId, EventName event, Instant now) {
    return repository.findLiveListeningTo(applicationId.value(), event.value(), now).stream()
        .map(SurveyJpaMapper::toDomain)
        .toList();
  }

  @Override
  public void delete(SurveyId id) {
    repository.deleteById(id.value());
  }
}
