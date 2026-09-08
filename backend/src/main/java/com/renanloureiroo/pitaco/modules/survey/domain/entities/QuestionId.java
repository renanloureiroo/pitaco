package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class QuestionId extends Id {

  private static final String INVALID_CODE = "question.id_invalid";

  private QuestionId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de pergunta inválido");
    }
  }

  public static QuestionId generate() {
    return new QuestionId(newValue());
  }

  public static QuestionId of(String value) {
    return new QuestionId(value);
  }
}
