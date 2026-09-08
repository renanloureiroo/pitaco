package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class SurveyStateTransitionId extends Id {

  private static final String INVALID_CODE = "survey_state_transition.id_invalid";

  private SurveyStateTransitionId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de transição de estado inválido");
    }
  }

  public static SurveyStateTransitionId generate() {
    return new SurveyStateTransitionId(newValue());
  }

  public static SurveyStateTransitionId of(String value) {
    return new SurveyStateTransitionId(value);
  }
}
