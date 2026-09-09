package com.renanloureiroo.pitaco.core.identity;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class SurveyId extends Id {

  private static final String INVALID_CODE = "survey.id_invalid";

  private SurveyId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de pesquisa inválido");
    }
  }

  public static SurveyId generate() {
    return new SurveyId(newValue());
  }

  public static SurveyId of(String value) {
    return new SurveyId(value);
  }
}
