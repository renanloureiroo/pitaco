package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SurveyDisplayEventJpaEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurveyDisplayEventJpaRepository
    extends JpaRepository<SurveyDisplayEventJpaEntity, String> {

  @Query(
      nativeQuery = true,
      value =
          "select 1 from (select pg_advisory_xact_lock(hashtextextended(:material, 0))) as locked")
  Integer lock(@Param("material") String material);

  long countByDisplayId(String displayId);

  @Query(
      "select e.seq from SurveyDisplayEventJpaEntity e"
          + " where e.displayId = :displayId and e.seq in :seqs")
  List<Integer> storedSeqs(
      @Param("displayId") String displayId, @Param("seqs") Collection<Integer> seqs);

  // O lote viaja como um array JSON e vira linhas no próprio banco: um comando, qualquer tamanho
  // de lote, e o conflito no par (exibição, seq) não grava nada.
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          insert into survey_display_events
                 (id, display_id, seq, catalog_version, type, question_key, occurred_at,
                  elapsed_ms, received_at, data)
          select r.id, r.display_id, r.seq, r.catalog_version, r.type, r.question_key,
                 r.occurred_at, r.elapsed_ms, r.received_at, coalesce(r.data, cast('{}' as jsonb))
            from jsonb_to_recordset(cast(:rows as jsonb))
                 as r(id varchar, display_id varchar, seq int, catalog_version int, type varchar,
                      question_key varchar, occurred_at timestamptz, elapsed_ms bigint,
                      received_at timestamptz, data jsonb)
          on conflict (display_id, seq) do nothing
          """)
  int insertAll(@Param("rows") String rows);
}
