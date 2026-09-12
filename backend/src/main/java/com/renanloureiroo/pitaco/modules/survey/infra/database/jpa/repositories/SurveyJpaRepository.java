package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyJpaRepository extends JpaRepository<SurveyJpaEntity, String> {

  Optional<SurveyJpaEntity> findByIdAndApplicationId(String id, String applicationId);

  Page<SurveyJpaEntity> findByApplicationId(String applicationId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from SurveyJpaEntity s where s.id = :id and s.applicationId = :applicationId")
  Optional<SurveyJpaEntity> lockByIdAndApplicationId(
      @Param("id") String id, @Param("applicationId") String applicationId);

  // Condicional no próprio update: de duas transações que chegam juntas, a segunda espera a
  // trava da linha, reavalia o where sobre o que a primeira gravou e não encontra nada.
  @Modifying(flushAutomatically = true)
  @Query(
      "update SurveyJpaEntity s set s.lifecycle = 'ENDED'"
          + " where s.id = :id and s.lifecycle in ('PUBLISHED', 'PAUSED')")
  int endIfLive(@Param("id") String id);

  @Query(
      """
      select s from SurveyJpaEntity s, SurveyVersionJpaEntity v
       where v.surveyId = s.id
         and v.number = s.publishedVersionNumber
         and v.status = 'PUBLISHED'
         and s.applicationId = :applicationId
         and s.lifecycle = 'PUBLISHED'
         and v.triggerEventName = :event
         and (v.triggerWindowEnd is null or v.triggerWindowEnd > :now)
       order by s.createdAt asc, s.id asc
      """)
  List<SurveyJpaEntity> findLiveListeningTo(
      @Param("applicationId") String applicationId,
      @Param("event") String event,
      @Param("now") Instant now);
}
