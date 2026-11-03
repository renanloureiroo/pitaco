package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrivacyApplicationJpaRepository
    extends JpaRepository<ApplicationJpaEntity, String> {

  @Query(
      value =
          "select id, retention_days, open_text_retention_days from applications where id = :id",
      nativeQuery = true)
  List<Object[]> policy(@Param("id") String id);

  @Query(
      value =
          """
          select id, retention_days, open_text_retention_days
            from applications
           where retention_days is not null or open_text_retention_days is not null
           order by id
          """,
      nativeQuery = true)
  List<Object[]> configuredPolicies();
}
