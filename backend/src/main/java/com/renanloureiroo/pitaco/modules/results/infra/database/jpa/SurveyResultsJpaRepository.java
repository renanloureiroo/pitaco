package com.renanloureiroo.pitaco.modules.results.infra.database.jpa;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SurveyDisplayJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// SQL nativo de propósito: agregação por tipo de pergunta, série por dia e cursor por tupla
// são coisas que o JPQL só faria pior. Linhas cruas; o adaptador dá nome às colunas.
public interface SurveyResultsJpaRepository extends JpaRepository<SurveyDisplayJpaEntity, String> {

  // Lista de uma linha: o retorno Object[] direto ganharia uma dimensão a mais no Spring Data.
  @Query(value = ResultsSql.DISPLAY_COUNTS, nativeQuery = true)
  List<Object[]> displayCounts(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue,
      @Param("abandonedBefore") Instant abandonedBefore);

  @Query(value = ResultsSql.TIMELINE, nativeQuery = true)
  List<Object[]> timeline(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue);

  @Query(value = ResultsSql.ANSWER_COUNTS, nativeQuery = true)
  List<Object[]> answerCounts(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue);

  @Query(value = ResultsSql.OPTION_COUNTS, nativeQuery = true)
  List<Object[]> optionCounts(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue);

  @Query(value = ResultsSql.NUMERIC_COUNTS, nativeQuery = true)
  List<Object[]> numericCounts(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue);

  @Query(value = ResultsSql.ATTRIBUTE_CATALOG, nativeQuery = true)
  List<Object[]> attributeCatalog(
      @Param("applicationId") String applicationId, @Param("surveyId") String surveyId);

  @Query(value = ResultsSql.OPEN_ANSWERS, nativeQuery = true)
  List<Object[]> openAnswers(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue,
      @Param("notBefore") Instant notBefore,
      @Param("term") String term,
      @Param("limit") int limit,
      @Param("offset") long offset);

  @Query(value = ResultsSql.OPEN_ANSWERS_COUNT, nativeQuery = true)
  long countOpenAnswers(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue,
      @Param("notBefore") Instant notBefore,
      @Param("term") String term);

  @Query(value = ResultsSql.ANSWERS_OF, nativeQuery = true)
  List<Object[]> answersOf(@Param("displayIds") Collection<String> displayIds);

  @Query(value = ResultsSql.ATTRIBUTE_NAMES, nativeQuery = true)
  List<String> attributeNames(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue);

  @Query(value = ResultsSql.DISPLAYS_AFTER, nativeQuery = true)
  List<Object[]> displaysAfter(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue,
      @Param("afterOpenedAt") Instant afterOpenedAt,
      @Param("afterId") String afterId,
      @Param("limit") int limit);

  @Query(value = ResultsSql.ATTRIBUTES_OF, nativeQuery = true)
  List<Object[]> attributesOf(@Param("displayIds") Collection<String> displayIds);

  @Query(value = ResultsSql.QUESTIONS, nativeQuery = true)
  List<Object[]> questions(
      @Param("surveyId") String surveyId, @Param("versionNumber") Integer versionNumber);

  @Query(value = ResultsSql.QUESTION_SHAPES, nativeQuery = true)
  List<Object[]> questionShapes(@Param("surveyId") String surveyId);

  @Query(value = ResultsSql.DISPLAYED_VERSIONS, nativeQuery = true)
  List<Number> displayedVersions(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionNumber") Integer versionNumber,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      @Param("attribute") String attribute,
      @Param("attributeValue") String attributeValue);

  @Query(value = ResultsSql.QUESTION_OPTIONS, nativeQuery = true)
  List<Object[]> questionOptions(@Param("questionIds") Collection<String> questionIds);

  @Query(
      value =
          """
          select c.question_key, c.dimension, c.value, sum(c.count)
            from aggregate_snapshot_counts c
            join aggregate_snapshots s on s.id = c.snapshot_id
            join survey_versions v on v.id = s.version_id
           where s.survey_id = :surveyId
             and (cast(:versionNumber as integer) is null or v.number = cast(:versionNumber as integer))
           group by c.question_key, c.dimension, c.value
          """,
      nativeQuery = true)
  List<Object[]> retainedCounts(
      @Param("surveyId") String surveyId, @Param("versionNumber") Integer versionNumber);

  // Sempre uma linha; o máximo vem nulo quando nada foi congelado.
  @Query(
      value =
          """
          select coalesce(sum(s.responding_displays), 0), max(s.discarded_before)
            from aggregate_snapshots s
            join survey_versions v on v.id = s.version_id
           where s.survey_id = :surveyId
             and (cast(:versionNumber as integer) is null or v.number = cast(:versionNumber as integer))
          """,
      nativeQuery = true)
  List<Object[]> retainedSummary(
      @Param("surveyId") String surveyId, @Param("versionNumber") Integer versionNumber);
}
