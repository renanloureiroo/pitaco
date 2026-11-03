package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.entities.DeletionAuditJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeletionAuditJpaRepository extends JpaRepository<DeletionAuditJpaEntity, String> {

  Page<DeletionAuditJpaEntity> findByApplicationId(String applicationId, Pageable pageable);
}
