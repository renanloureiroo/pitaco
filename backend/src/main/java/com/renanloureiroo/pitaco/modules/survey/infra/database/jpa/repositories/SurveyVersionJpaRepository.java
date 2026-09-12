package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyVersionJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyVersionJpaRepository extends JpaRepository<SurveyVersionJpaEntity, String> {

  // Toda leitura de versão traz perguntas, opções e regras na mesma consulta: a porta nunca
  // devolve versão pela metade, e não há travessia preguiçosa em laço.
  String FETCH_CONTENT =
      """
      select distinct v from SurveyVersionJpaEntity v
        left join fetch v.questions q
        left join fetch q.options
        left join fetch q.conditionValues
        left join fetch v.rules
      """;

  @Query(FETCH_CONTENT + " where v.surveyId = :surveyId and v.status = 'DRAFT'")
  Optional<SurveyVersionJpaEntity> findDraft(@Param("surveyId") String surveyId);

  @Query(FETCH_CONTENT + " where v.surveyId = :surveyId and v.number = :number")
  Optional<SurveyVersionJpaEntity> findByNumber(
      @Param("surveyId") String surveyId, @Param("number") int number);

  @Query(
      FETCH_CONTENT
          + " where v.surveyId = :surveyId and v.status = 'PUBLISHED' order by v.number desc")
  List<SurveyVersionJpaEntity> findAllPublished(@Param("surveyId") String surveyId);

  @Query(
      """
      select v.surveyId, v.triggerWindowStart, v.triggerWindowEnd
        from SurveyVersionJpaEntity v
       where v.surveyId in :surveyIds
         and v.status = 'PUBLISHED'
         and v.triggerWindowStart is not null
      """)
  List<Object[]> findPublishedWindows(@Param("surveyIds") Collection<String> surveyIds);

  void deleteBySurveyId(String surveyId);
}
