package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.ApplicationEventJpaEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationEventJpaRepository
    extends JpaRepository<ApplicationEventJpaEntity, String> {

  Page<ApplicationEventJpaEntity> findByApplicationId(String applicationId, Pageable pageable);

  @Query(
      "select e.lastSeenAt from ApplicationEventJpaEntity e"
          + " where e.applicationId = :applicationId and e.name = :name")
  Optional<Instant> findLastSeenAt(
      @Param("applicationId") String applicationId, @Param("name") String name);

  // Um comando só para os dois caminhos, sem select antes: a chave única resolve a corrida entre
  // duas primeiras ocorrências, e o `where` do update amortiza a escrita — a linha só é tocada
  // quando o registrado é mais antigo que o limiar, para que o caminho quente não escreva na
  // mesma linha a cada evento do app hospedeiro.
  @Modifying(clearAutomatically = true)
  @Query(
      nativeQuery = true,
      value =
          """
          insert into application_events (id, application_id, name, first_seen_at, last_seen_at)
          values (:id, :applicationId, :name, :now, :now)
          on conflict (application_id, name) do update
             set last_seen_at = excluded.last_seen_at
           where application_events.last_seen_at < :threshold
          """)
  int record(
      @Param("id") String id,
      @Param("applicationId") String applicationId,
      @Param("name") String name,
      @Param("now") Instant now,
      @Param("threshold") Instant threshold);
}
