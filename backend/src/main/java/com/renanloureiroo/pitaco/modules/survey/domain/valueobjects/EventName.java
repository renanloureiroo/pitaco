package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.regex.Pattern;

public record EventName(String value) {

  private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9_.]{1,79}$");

  private static final String INVALID_CODE = "trigger.event_name_invalid";

  public EventName {
    if (value == null || !FORMAT.matcher(value).matches()) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Nome do evento deve começar por letra minúscula e usar apenas letras minúsculas, "
              + "dígitos, ponto e sublinhado, com 2 a 80 caracteres");
    }
  }

  public static EventName of(String value) {
    return new EventName(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
