package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SurveyAnswerJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyAnswerJpaRepository extends JpaRepository<SurveyAnswerJpaEntity, String> {

  // As opções vêm na mesma consulta: sem o join fetch, o @ElementCollection eager dispara uma
  // consulta por resposta, e a exibição inteira custaria N+1.
  @Query(
      """
      select distinct a from SurveyAnswerJpaEntity a
        left join fetch a.options
       where a.displayId = :displayId
      """)
  List<SurveyAnswerJpaEntity> findByDisplayId(@Param("displayId") String displayId);
}
