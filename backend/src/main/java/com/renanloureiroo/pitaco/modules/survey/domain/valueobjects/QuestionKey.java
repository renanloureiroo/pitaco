package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.UUID;

// Linhagem, não identidade: a mesma chave atravessa versões, enquanto o QuestionId muda a cada
// cópia. Por isso é value object e não Id.
public record QuestionKey(String value) {

  private static final String INVALID_CODE = "question.key_invalid";

  public QuestionKey {
    if (value == null || !isUuid(value)) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Chave de pergunta inválida");
    }
  }

  public static QuestionKey generate() {
    return new QuestionKey(UUID.randomUUID().toString());
  }

  public static QuestionKey of(String value) {
    return new QuestionKey(value);
  }

  private static boolean isUuid(String value) {
    try {
      return UUID.fromString(value).toString().equalsIgnoreCase(value);
    } catch (IllegalArgumentException notUuid) {
      return false;
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
