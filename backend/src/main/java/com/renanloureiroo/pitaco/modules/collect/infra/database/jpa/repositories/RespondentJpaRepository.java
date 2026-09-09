package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.RespondentJpaEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RespondentJpaRepository extends JpaRepository<RespondentJpaEntity, String> {

  Optional<RespondentJpaEntity> findByApplicationIdAndIdentityKindAndIdentityValue(
      String applicationId, String identityKind, String identityValue);
}
