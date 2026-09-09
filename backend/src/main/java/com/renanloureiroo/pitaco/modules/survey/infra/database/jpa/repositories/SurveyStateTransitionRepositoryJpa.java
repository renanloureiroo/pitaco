package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyStateTransitionRepository;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyStateTransition;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.mappers.SurveyStateTransitionJpaMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class SurveyStateTransitionRepositoryJpa implements SurveyStateTransitionRepository {

  private final SurveyStateTransitionJpaRepository repository;

  public SurveyStateTransitionRepositoryJpa(SurveyStateTransitionJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public SurveyStateTransition record(SurveyStateTransition transition) {
    repository.save(SurveyStateTransitionJpaMapper.toJpa(transition));
    return transition;
  }

  @Override
  public List<SurveyStateTransition> findBySurveyId(SurveyId surveyId) {
    return repository.findBySurveyIdOrderByOccurredAtAsc(surveyId.value()).stream()
        .map(SurveyStateTransitionJpaMapper::toDomain)
        .toList();
  }
}
