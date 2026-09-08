package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class SurveyVersionId extends Id {

  private static final String INVALID_CODE = "survey_version.id_invalid";

  private SurveyVersionId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de versão de pesquisa inválido");
    }
  }

  public static SurveyVersionId generate() {
    return new SurveyVersionId(newValue());
  }

  public static SurveyVersionId of(String value) {
    return new SurveyVersionId(value);
  }
}
