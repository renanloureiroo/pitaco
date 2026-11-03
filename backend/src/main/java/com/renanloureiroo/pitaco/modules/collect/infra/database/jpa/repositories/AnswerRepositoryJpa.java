package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.application.repositories.AnswerRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Answer;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.mappers.AnswerMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AnswerRepositoryJpa implements AnswerRepository {

  private final SurveyAnswerJpaRepository repository;

  public AnswerRepositoryJpa(SurveyAnswerJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<Answer> findByDisplay(DisplayId displayId) {
    return repository.findByDisplayId(displayId.value()).stream()
        .map(AnswerMapper::toDomain)
        .toList();
  }

  @Override
  public void saveAll(List<Answer> answers) {
    repository.saveAll(answers.stream().map(AnswerMapper::toJpa).toList());
  }
}
