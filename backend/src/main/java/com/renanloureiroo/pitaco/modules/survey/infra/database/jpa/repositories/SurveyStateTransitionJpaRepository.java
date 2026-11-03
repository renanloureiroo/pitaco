package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyStateTransitionJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyStateTransitionJpaRepository
    extends JpaRepository<SurveyStateTransitionJpaEntity, String> {

  List<SurveyStateTransitionJpaEntity> findBySurveyIdOrderByOccurredAtAsc(String surveyId);
}
