package com.renanloureiroo.pitaco.modules.privacy.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class DeletionAuditId extends Id {

  private static final String INVALID_CODE = "deletion_audit.id_invalid";

  private DeletionAuditId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de registro de exclusão inválido");
    }
  }

  public static DeletionAuditId generate() {
    return new DeletionAuditId(newValue());
  }

  public static DeletionAuditId of(String value) {
    return new DeletionAuditId(value);
  }
}
