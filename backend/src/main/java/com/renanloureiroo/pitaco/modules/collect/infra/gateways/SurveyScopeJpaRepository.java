package com.renanloureiroo.pitaco.modules.collect.infra.gateways;

import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

// Do lado de `collect`, como a PublishedSurveyJpaRepository: é necessidade da coleta, e a
// autoria não muda para servi-la.
public interface SurveyScopeJpaRepository extends JpaRepository<SurveyJpaEntity, String> {

  boolean existsByIdAndApplicationId(String id, String applicationId);
}
