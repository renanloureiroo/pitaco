package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.entities.AggregateSnapshotJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AggregateSnapshotJpaRepository
    extends JpaRepository<AggregateSnapshotJpaEntity, String> {}
