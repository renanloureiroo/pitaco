package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.entities.DeletionAuditJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErasureJpaRepository extends JpaRepository<DeletionAuditJpaEntity, String> {

  // Lista de uma linha ou nenhuma: Optional<Object[]> ganharia uma dimensão a mais no Spring Data.
  @Query(
      value =
          """
          select r.id,
                 (select count(*) from survey_displays d where d.respondent_id = r.id),
                 (select count(*)
                    from survey_answers a
                    join survey_displays d on d.id = a.display_id
                   where d.respondent_id = r.id)
            from respondents r
           where r.application_id = :applicationId
             and r.identity_kind = :kind
             and r.identity_value = :value
          """,
      nativeQuery = true)
  List<Object[]> find(
      @Param("applicationId") String applicationId,
      @Param("kind") String kind,
      @Param("value") String value);

  @Modifying
  @Query(value = "delete from respondents where id = :id", nativeQuery = true)
  int erase(@Param("id") String id);
}
