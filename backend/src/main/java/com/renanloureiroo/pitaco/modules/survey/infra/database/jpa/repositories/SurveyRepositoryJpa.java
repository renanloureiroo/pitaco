package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.mappers.SurveyJpaMapper;
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
  public void delete(SurveyId id) {
    repository.deleteById(id.value());
  }
}
