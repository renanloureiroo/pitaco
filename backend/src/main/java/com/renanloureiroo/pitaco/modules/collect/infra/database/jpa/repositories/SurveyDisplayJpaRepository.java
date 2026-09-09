package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SurveyDisplayJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyDisplayJpaRepository extends JpaRepository<SurveyDisplayJpaEntity, String> {

  Optional<SurveyDisplayJpaEntity> findByIdAndApplicationId(String id, String applicationId);

  // Projeção das quatro colunas do índice idx_survey_displays_history: uma ida ao banco para
  // todos os candidatos, sem carregar o instantâneo de atributos de cada exibição.
  @Query(
      """
      select s.surveyId, s.comparabilityGroup, s.outcome, s.openedAt
        from SurveyDisplayJpaEntity s
       where s.respondentId = :respondentId
         and s.surveyId in :surveyIds
      """)
  List<Object[]> findHistory(
      @Param("respondentId") String respondentId,
      @Param("surveyIds") Collection<String> surveyIds);
}
