package com.renanloureiroo.pitaco.core.catalog;

import com.renanloureiroo.pitaco.core.error.DomainException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

// Um campo do `data` fechado de um tipo de evento. O valor que não cabe na forma declarada some,
// e o evento continua: é o que deixa texto livre de fora sem recusar o lote.
public record InteractionField(String name, InteractionFieldKind kind, List<String> choices) {

  public static final int MAX_ANSWER_VALUE_LENGTH = 120;

  public InteractionField {
    choices = choices == null ? List.of() : List.copyOf(choices);
  }

  public static InteractionField count(String name) {
    return new InteractionField(name, InteractionFieldKind.COUNT, List.of());
  }

  public static InteractionField positive(String name) {
    return new InteractionField(name, InteractionFieldKind.POSITIVE, List.of());
  }

  public static InteractionField duration(String name) {
    return new InteractionField(name, InteractionFieldKind.DURATION, List.of());
  }

  public static InteractionField choice(String name, String... values) {
    return new InteractionField(name, InteractionFieldKind.CHOICE, List.of(values));
  }

  public static InteractionField questionKey(String name) {
    return new InteractionField(name, InteractionFieldKind.QUESTION_KEY, List.of());
  }

  public static InteractionField answerValue(String name) {
    return new InteractionField(name, InteractionFieldKind.ANSWER_VALUE, List.of());
  }

  public static InteractionField eventName(String name) {
    return new InteractionField(name, InteractionFieldKind.EVENT_NAME, List.of());
  }

  public static InteractionField flag(String name) {
    return new InteractionField(name, InteractionFieldKind.FLAG, List.of());
  }

  public Optional<Object> normalize(Object raw) {
    if (raw == null) {
      return Optional.empty();
    }

    return switch (kind) {
      case COUNT -> integral(raw).filter(value -> value >= 0 && value <= Integer.MAX_VALUE).map(Object.class::cast);
      case POSITIVE -> integral(raw).filter(value -> value >= 1 && value <= Integer.MAX_VALUE).map(Object.class::cast);
      case DURATION -> integral(raw).filter(value -> value >= 0).map(Object.class::cast);
      case CHOICE -> text(raw).filter(choices::contains).map(Object.class::cast);
      case QUESTION_KEY -> text(raw).flatMap(InteractionField::questionKeyOf).map(Object.class::cast);
      case ANSWER_VALUE -> answerValue(raw);
      case EVENT_NAME -> text(raw).flatMap(InteractionField::eventNameOf).map(Object.class::cast);
      case FLAG -> raw instanceof Boolean flag ? Optional.of(flag) : Optional.empty();
    };
  }

  // Número de opção ou nota, nunca texto livre: texto só entra se couber no valor de uma opção.
  private static Optional<Object> answerValue(Object raw) {
    if (raw instanceof String text) {
      var stripped = text.strip();
      return stripped.isEmpty() || stripped.length() > MAX_ANSWER_VALUE_LENGTH
          ? Optional.empty()
          : Optional.of(stripped);
    }
    return integral(raw)
        .filter(value -> value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE)
        .map(Object.class::cast);
  }

  // O JavaScript só tem double: 12.0 é o inteiro 12, 12.5 não é inteiro nenhum.
  private static Optional<Long> integral(Object raw) {
    return switch (raw) {
      case Integer value -> Optional.of(value.longValue());
      case Long value -> Optional.of(value);
      case Short value -> Optional.of(value.longValue());
      case BigInteger value ->
          value.bitLength() < Long.SIZE ? Optional.of(value.longValue()) : Optional.empty();
      case Double value -> wholeOf(BigDecimal.valueOf(value));
      case Float value -> wholeOf(BigDecimal.valueOf(value));
      case BigDecimal value -> wholeOf(value);
      default -> Optional.empty();
    };
  }

  private static Optional<Long> wholeOf(BigDecimal value) {
    try {
      return Optional.of(value.longValueExact());
    } catch (ArithmeticException notWhole) {
      return Optional.empty();
    }
  }

  private static Optional<String> text(Object raw) {
    return raw instanceof String text ? Optional.of(text) : Optional.empty();
  }

  private static Optional<String> questionKeyOf(String raw) {
    try {
      return Optional.of(QuestionKey.of(raw).value());
    } catch (DomainException invalid) {
      return Optional.empty();
    }
  }

  private static Optional<String> eventNameOf(String raw) {
    try {
      return Optional.of(EventName.of(raw).value());
    } catch (DomainException invalid) {
      return Optional.empty();
    }
  }
}
