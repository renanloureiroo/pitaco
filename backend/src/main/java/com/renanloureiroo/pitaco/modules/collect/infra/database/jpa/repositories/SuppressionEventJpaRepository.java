package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SuppressionEventJpaEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SuppressionEventJpaRepository
    extends JpaRepository<SuppressionEventJpaEntity, String> {

  // Trava de transação, não de linha: a supressão que ainda não existe não tem linha a travar, e
  // um índice único por dia trocaria a janela deslizante de 24 horas por um corte no calendário.
  @Query(
      nativeQuery = true,
      value =
          "select 1 from (select pg_advisory_xact_lock(hashtextextended(:material, 0))) as locked")
  Integer lockDedup(@Param("material") String material);

  // Servida pelo índice parcial idx_suppression_events_dedup.
  boolean existsByVersionIdAndDedupKeyAndOccurredAtAfter(
      String versionId, String dedupKey, Instant since);

  @Query(
      """
      select s.reason, count(s)
        from SuppressionEventJpaEntity s
       where s.surveyId = :surveyId
         and s.occurredAt >= :from
         and s.occurredAt <= :to
       group by s.reason
      """)
  List<Object[]> countByReason(
      @Param("surveyId") String surveyId, @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      """
      select s.sdkVersion, count(s)
        from SuppressionEventJpaEntity s
       where s.surveyId = :surveyId
         and s.occurredAt >= :from
         and s.occurredAt <= :to
       group by s.sdkVersion
      """)
  List<Object[]> countBySdkVersion(
      @Param("surveyId") String surveyId, @Param("from") Instant from, @Param("to") Instant to);
}
