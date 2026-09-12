package com.renanloureiroo.pitaco.modules.results.infra.gateways;

import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultsScopeJpaRepository extends JpaRepository<SurveyJpaEntity, String> {

  // Existência, publicação, prazo efetivo de texto livre e modelo numa ida só ao banco.
  @Query(
      value =
          """
          select s.published_version_number, coalesce(a.open_text_retention_days, a.retention_days),
                 s.template_kind,
                 exists (select 1
                           from survey_versions v
                           join questions q on q.version_id = v.id
                          where v.survey_id = s.id
                            and v.status = 'PUBLISHED'
                            and q.type = 'FREE_TEXT')
            from surveys s
            join applications a on a.id = s.application_id
           where s.id = :surveyId
             and s.application_id = :applicationId
          """,
      nativeQuery = true)
  // Lista e não Optional<Object[]>: para uma linha só, o Spring Data embrulharia a linha numa
  // segunda dimensão.
  List<Object[]> scope(
      @Param("applicationId") String applicationId, @Param("surveyId") String surveyId);
}
