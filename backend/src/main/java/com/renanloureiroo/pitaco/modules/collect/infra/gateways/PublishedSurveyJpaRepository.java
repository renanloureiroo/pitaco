package com.renanloureiroo.pitaco.modules.collect.infra.gateways;

import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyVersionJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// As três consultas da travessia até a autoria. Ficam aqui, do lado de `collect`, porque são
// necessidade da coleta: a autoria não muda para servi-las.
public interface PublishedSurveyJpaRepository
    extends JpaRepository<SurveyVersionJpaEntity, String> {

  // Camadas 2 a 4 da FR-011 numa consulta só, com os critérios de segmentação na mesma
  // travessia. A janela abre no início inclusive e fecha no fim exclusive.
  @Query(
      """
      select distinct v from SurveyVersionJpaEntity v
        left join fetch v.rules
        join SurveyJpaEntity s on s.id = v.surveyId
       where v.status = 'PUBLISHED'
         and v.triggerEventName = :event
         and s.applicationId = :applicationId
         and s.lifecycle = 'PUBLISHED'
         and s.publishedVersionNumber = v.number
         and v.triggerWindowStart <= :now
         and (v.triggerWindowEnd is null or v.triggerWindowEnd > :now)
      """)
  List<SurveyVersionJpaEntity> findCandidates(
      @Param("applicationId") String applicationId,
      @Param("event") String event,
      @Param("now") Instant now);

  // A exposição é da pesquisa, não da versão: vem numa segunda ida só quando há candidato, em
  // vez de uma tupla com fetch join que o Hibernate não deduplica.
  @Query(
      """
      select s.id, s.priority, s.ignoresQuietPeriod
        from SurveyJpaEntity s
       where s.id in :surveyIds
      """)
  List<Object[]> findExposures(@Param("surveyIds") java.util.Collection<String> surveyIds);

  // Rascunho nunca chega ao SDK: o filtro por status é o que fecha FR-021 aqui também.
  @Query(
      """
      select distinct v from SurveyVersionJpaEntity v
        left join fetch v.questions q
        left join fetch q.options
        left join fetch q.conditionValues
       where v.id = :versionId
         and v.status = 'PUBLISHED'
      """)
  Optional<SurveyVersionJpaEntity> findPublishedContent(@Param("versionId") String versionId);

  @Query(
      """
      select v from SurveyVersionJpaEntity v
        join SurveyJpaEntity s on s.id = v.surveyId
       where v.id = :versionId
         and s.applicationId = :applicationId
         and v.status = 'PUBLISHED'
      """)
  Optional<SurveyVersionJpaEntity> findPublishedVersion(
      @Param("versionId") String versionId, @Param("applicationId") String applicationId);

  // O aviso é da pesquisa: vem numa ida à parte, só quando há conteúdo a entregar.
  @Query(
      """
      select s.freeTextNoticeEnabled, s.freeTextNoticeText
        from SurveyJpaEntity s
       where s.id = :surveyId
      """)
  List<Object[]> findFreeTextNotice(@Param("surveyId") String surveyId);

  // Uma linha ou nenhuma; lista porque o Spring Data dá uma dimensão a mais a Optional<Object[]>.
  @Query(
      """
      select v.id, v.triggerEventName from SurveyVersionJpaEntity v
        join SurveyJpaEntity s on s.id = v.surveyId
       where s.id = :surveyId
         and v.number = s.publishedVersionNumber
         and v.status = 'PUBLISHED'
      """)
  List<Object[]> findCurrentPublication(@Param("surveyId") String surveyId);
}
