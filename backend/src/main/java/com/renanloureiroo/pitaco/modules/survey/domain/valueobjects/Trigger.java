package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;

public record Trigger(EventName event, TriggerWindow window, SamplingRate rate) {

  private static final String INVALID_CODE = "trigger.invalid";

  public Trigger {
    require(event, "Evento do disparo é obrigatório");
    require(window, "Janela do disparo é obrigatória");
    require(rate, "Proporção do disparo é obrigatória");
  }

  private static void require(Object part, String message) {
    if (part == null) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, message);
    }
  }
}
