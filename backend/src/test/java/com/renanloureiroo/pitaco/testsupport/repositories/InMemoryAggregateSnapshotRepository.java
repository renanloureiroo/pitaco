package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.modules.privacy.application.repositories.AggregateSnapshotRepository;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.AggregateSnapshot;
import java.util.ArrayList;
import java.util.List;

public class InMemoryAggregateSnapshotRepository implements AggregateSnapshotRepository {

  private final List<AggregateSnapshot> snapshots = new ArrayList<>();

  public List<AggregateSnapshot> findAll() {
    return List.copyOf(snapshots);
  }

  @Override
  public AggregateSnapshot create(AggregateSnapshot snapshot) {
    snapshots.add(snapshot);
    return snapshot;
  }
}
