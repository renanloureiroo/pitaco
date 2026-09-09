package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.RespondentJpaEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RespondentJpaRepository extends JpaRepository<RespondentJpaEntity, String> {

  Optional<RespondentJpaEntity> findByIdAndApplicationId(String id, String applicationId);

  Optional<RespondentJpaEntity> findByApplicationIdAndIdentityKindAndIdentityValue(
      String applicationId, String identityKind, String identityValue);

  Page<RespondentJpaEntity> findByApplicationId(String applicationId, Pageable pageable);
}
