package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.entities.RetentionRunJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// SQL nativo sobre as tabelas da coleta: a retenção só lê e apaga respostas, sem passar pelo
// domínio de quem as gravou — o mesmo acordo que a leitura de resultados já tem com elas.
public interface RetentionJpaRepository extends JpaRepository<RetentionRunJpaEntity, String> {

  @Query(
      value =
          """
          select a.id, d.survey_id, d.version_id, a.display_id, a.question_key, a.status,
                 a.numeric_value,
                 (select string_agg(o.option_value, chr(31) order by o.position)
                    from survey_answer_options o
                   where o.answer_id = a.id)
            from survey_answers a
            join survey_displays d on d.id = a.display_id
           where d.application_id = :applicationId
             and a.answered_at < :before
           order by a.answered_at, a.id
           limit :limit
          """,
      nativeQuery = true)
  List<Object[]> expiringAnswers(
      @Param("applicationId") String applicationId,
      @Param("before") Instant before,
      @Param("limit") int limit);

  @Modifying
  @Query(value = "delete from survey_answers where id in (:ids)", nativeQuery = true)
  int deleteAnswers(@Param("ids") Collection<String> ids);

  @Modifying
  @Query(
      value =
          """
          update survey_answers
             set text_value = null
           where id in (select a.id
                          from survey_answers a
                          join survey_displays d on d.id = a.display_id
                         where d.application_id = :applicationId
                           and a.answered_at < :before
                           and a.text_value is not null
                         order by a.answered_at, a.id
                         limit :limit)
          """,
      nativeQuery = true)
  int clearTexts(
      @Param("applicationId") String applicationId,
      @Param("before") Instant before,
      @Param("limit") int limit);

  @Query(
      value =
          """
          select count(*)
            from survey_answers a
            join survey_displays d on d.id = a.display_id
           where d.application_id = :applicationId
             and a.answered_at < :before
          """,
      nativeQuery = true)
  long countAnswersBefore(
      @Param("applicationId") String applicationId, @Param("before") Instant before);

  @Query(
      value =
          """
          select count(*)
            from survey_answers a
            join survey_displays d on d.id = a.display_id
           where d.application_id = :applicationId
             and a.answered_at < :before
             and a.text_value is not null
          """,
      nativeQuery = true)
  long countTextsBefore(
      @Param("applicationId") String applicationId, @Param("before") Instant before);
}
