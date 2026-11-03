package com.renanloureiroo.pitaco.modules.results.infra.database.jpa;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SurveyDisplayJpaEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// SQL nativo pelo mesmo motivo de SurveyResultsJpaRepository: percentil, distinct on e jsonb são
// coisas que o JPQL não faz.
public interface SurveyBehaviorJpaRepository extends JpaRepository<SurveyDisplayJpaEntity, String> {

  @Query(value = BehaviorSql.TOTALS, nativeQuery = true)
  List<Object[]> totals(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue);

  @Query(value = BehaviorSql.FUNNEL, nativeQuery = true)
  List<Object[]> funnels(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue);

  @Query(value = BehaviorSql.ABANDONMENTS, nativeQuery = true)
  List<Object[]> abandonments(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue,
      @Param("abandonedBefore") Instant abandonedBefore);

  @Query(value = BehaviorSql.ACTIVE_TIMES, nativeQuery = true)
  List<Object[]> activeTimes(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue);

  @Query(value = BehaviorSql.DISMISSALS, nativeQuery = true)
  List<Object[]> dismissals(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue);
}
