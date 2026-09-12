package com.renanloureiroo.pitaco.modules.collect.infra.gateways;

import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Do lado de `collect`, como a PublishedSurveyJpaRepository: é necessidade da coleta, e a
// autoria não muda para servi-la.
public interface SurveyScopeJpaRepository extends JpaRepository<SurveyJpaEntity, String> {

  boolean existsByIdAndApplicationId(String id, String applicationId);

  @Query("select s.responseQuota from SurveyJpaEntity s where s.id = :id")
  Optional<Integer> findResponseQuota(@Param("id") String id);

  @Query(
      nativeQuery = true,
      value = "select response_quota from surveys where id = :id for update")
  Optional<Integer> lockResponseQuota(@Param("id") String id);
}
