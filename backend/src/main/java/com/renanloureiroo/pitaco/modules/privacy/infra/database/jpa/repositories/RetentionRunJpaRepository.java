package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.entities.RetentionRunJpaEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RetentionRunJpaRepository extends JpaRepository<RetentionRunJpaEntity, String> {

  @Query("select max(r.ranAt) from RetentionRunJpaEntity r where r.applicationId = :applicationId")
  Optional<Instant> lastRunAt(@Param("applicationId") String applicationId);
}
