package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.privacy.application.repositories.AggregateSnapshotRepository;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.AggregateSnapshot;
import com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.mappers.PrivacyJpaMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AggregateSnapshotRepositoryJpa implements AggregateSnapshotRepository {

  private final AggregateSnapshotJpaRepository repository;

  public AggregateSnapshotRepositoryJpa(AggregateSnapshotJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public AggregateSnapshot create(AggregateSnapshot snapshot) {
    repository.save(PrivacyJpaMapper.toJpa(snapshot));
    return snapshot;
  }
}
