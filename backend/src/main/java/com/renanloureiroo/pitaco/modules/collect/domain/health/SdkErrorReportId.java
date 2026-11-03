package com.renanloureiroo.pitaco.modules.collect.domain.health;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class SdkErrorReportId extends Id {

  private static final String INVALID_CODE = "sdk_error.id_invalid";

  private SdkErrorReportId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de relatório de erro inválido");
    }
  }

  public static SdkErrorReportId generate() {
    return new SdkErrorReportId(newValue());
  }

  public static SdkErrorReportId of(String value) {
    return new SdkErrorReportId(value);
  }
}
