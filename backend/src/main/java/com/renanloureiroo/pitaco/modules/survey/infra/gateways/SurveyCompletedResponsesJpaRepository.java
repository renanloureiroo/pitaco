package com.renanloureiroo.pitaco.modules.survey.infra.gateways;

import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Do lado da autoria, como o SurveySdkTrafficJpaRepository: é necessidade de quem lê.
public interface SurveyCompletedResponsesJpaRepository
    extends JpaRepository<SurveyJpaEntity, String> {

  @Query(
      nativeQuery = true,
      value = "select count(*) from survey_displays where survey_id = :surveyId"
          + " and outcome = 'COMPLETED'")
  long countCompleted(@Param("surveyId") String surveyId);
}
