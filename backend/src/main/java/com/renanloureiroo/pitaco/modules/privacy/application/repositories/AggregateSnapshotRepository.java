package com.renanloureiroo.pitaco.modules.privacy.application.repositories;

import com.renanloureiroo.pitaco.modules.privacy.domain.entities.AggregateSnapshot;

public interface AggregateSnapshotRepository {

  AggregateSnapshot create(AggregateSnapshot snapshot);
}
