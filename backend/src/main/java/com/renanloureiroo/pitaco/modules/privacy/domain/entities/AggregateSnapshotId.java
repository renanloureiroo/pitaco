package com.renanloureiroo.pitaco.modules.privacy.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class AggregateSnapshotId extends Id {

  private static final String INVALID_CODE = "aggregate_snapshot.id_invalid";

  private AggregateSnapshotId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de agregado congelado inválido");
    }
  }

  public static AggregateSnapshotId generate() {
    return new AggregateSnapshotId(newValue());
  }

  public static AggregateSnapshotId of(String value) {
    return new AggregateSnapshotId(value);
  }
}
