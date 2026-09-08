package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;

public record ScaleRange(int min, int max) {

  public static final ScaleRange NPS = new ScaleRange(0, 10);

  private static final String INVALID_CODE = "question.scale_range_invalid";

  public ScaleRange {
    if (min >= max) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Mínimo da faixa deve ser menor que o máximo");
    }
  }
}
