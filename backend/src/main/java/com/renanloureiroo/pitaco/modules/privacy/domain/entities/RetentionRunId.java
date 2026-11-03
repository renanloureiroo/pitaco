package com.renanloureiroo.pitaco.modules.privacy.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class RetentionRunId extends Id {

  private static final String INVALID_CODE = "retention_run.id_invalid";

  private RetentionRunId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de execução de retenção inválido");
    }
  }

  public static RetentionRunId generate() {
    return new RetentionRunId(newValue());
  }

  public static RetentionRunId of(String value) {
    return new RetentionRunId(value);
  }
}
