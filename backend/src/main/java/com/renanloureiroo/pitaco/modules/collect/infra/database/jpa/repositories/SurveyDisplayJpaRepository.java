package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SurveyDisplayJpaEntity;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.projections.DisplaySummaryProjection;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.projections.RespondentDisplayProjection;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

  // A junção com survey_versions traz o número da versão uma vez por página, nunca por linha
  // (D-03). A ordenação vem na própria consulta para casar com o índice.
  @Query(
      value =
          """
          select new com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.projections.DisplaySummaryProjection(
                 d.id, d.versionId, v.number, d.comparabilityGroup, d.outcome, d.sdkVersion,
                 d.openedAt, d.closedAt)
            from SurveyDisplayJpaEntity d
            join SurveyVersionJpaEntity v on v.id = d.versionId
           where d.applicationId = :applicationId
             and d.surveyId = :surveyId
             and (:versionId is null or d.versionId = :versionId)
             and (:outcome is null or d.outcome = :outcome)
             and (cast(:openedFrom as Instant) is null or d.openedAt >= :openedFrom)
             and (cast(:openedTo as Instant) is null or d.openedAt <= :openedTo)
           order by d.openedAt desc, d.id desc
          """,
      countQuery =
          """
          select count(d)
            from SurveyDisplayJpaEntity d
           where d.applicationId = :applicationId
             and d.surveyId = :surveyId
             and (:versionId is null or d.versionId = :versionId)
             and (:outcome is null or d.outcome = :outcome)
             and (cast(:openedFrom as Instant) is null or d.openedAt >= :openedFrom)
             and (cast(:openedTo as Instant) is null or d.openedAt <= :openedTo)
          """)
  Page<DisplaySummaryProjection> findSummaryPage(
      @Param("applicationId") String applicationId,
      @Param("surveyId") String surveyId,
      @Param("versionId") String versionId,
      @Param("outcome") String outcome,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      Pageable pageable);

  @Query(
      value =
          """
          select new com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.projections.RespondentDisplayProjection(
                 d.id, d.surveyId, d.versionId, v.number, d.comparabilityGroup, d.outcome,
                 d.sdkVersion, d.openedAt, d.closedAt)
            from SurveyDisplayJpaEntity d
            join SurveyVersionJpaEntity v on v.id = d.versionId
           where d.applicationId = :applicationId
             and d.respondentId = :respondentId
             and (:outcome is null or d.outcome = :outcome)
             and (cast(:openedFrom as Instant) is null or d.openedAt >= :openedFrom)
             and (cast(:openedTo as Instant) is null or d.openedAt <= :openedTo)
           order by d.openedAt desc, d.id desc
          """,
      countQuery =
          """
          select count(d)
            from SurveyDisplayJpaEntity d
           where d.applicationId = :applicationId
             and d.respondentId = :respondentId
             and (:outcome is null or d.outcome = :outcome)
             and (cast(:openedFrom as Instant) is null or d.openedAt >= :openedFrom)
             and (cast(:openedTo as Instant) is null or d.openedAt <= :openedTo)
          """)
  Page<RespondentDisplayProjection> findRespondentSummaryPage(
      @Param("applicationId") String applicationId,
      @Param("respondentId") String respondentId,
      @Param("outcome") String outcome,
      @Param("openedFrom") Instant openedFrom,
      @Param("openedTo") Instant openedTo,
      Pageable pageable);
}
