package com.renanloureiroo.pitaco.modules.survey.infra.gateways;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.ApplicationAttributeJpaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Do lado da autoria, como a PublishedSurveyJpaRepository mora do lado da coleta: é necessidade
// de quem lê, e o catálogo não muda para servi-la. Uma linha por (atributo, valor), com o valor
// nulo quando o atributo ainda não tem nenhum.
public interface SurveyAttributeCatalogJpaRepository
    extends JpaRepository<ApplicationAttributeJpaEntity, String> {

  @Query(
      """
      select a.name, v.value
        from ApplicationAttributeJpaEntity a
        left join ApplicationAttributeValueJpaEntity v on v.attributeId = a.id
       where a.applicationId = :applicationId
         and a.name in :names
      """)
  List<Object[]> findKnown(
      @Param("applicationId") String applicationId, @Param("names") Collection<String> names);
}
