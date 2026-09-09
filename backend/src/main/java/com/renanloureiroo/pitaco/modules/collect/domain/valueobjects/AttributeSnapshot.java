package com.renanloureiroo.pitaco.modules.collect.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.text.RequiredText;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// Instantâneo do que a consulta informou, não perfil do respondente: os atributos pertencem à
// exibição e não sobrevivem a ela (FR-009).
public record AttributeSnapshot(Map<String, String> values) {

  public static final int MAX_ATTRIBUTES = 50;
  public static final int MAX_NAME_LENGTH = 80;
  public static final int MAX_VALUE_LENGTH = 200;

  private static final String INVALID_CODE = "attributes.invalid";

  public AttributeSnapshot {
    if (values == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Conjunto de atributos é obrigatório");
    }
    if (values.size() > MAX_ATTRIBUTES) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "No máximo " + MAX_ATTRIBUTES + " atributos por consulta");
    }

    var normalized = new LinkedHashMap<String, String>();
    values.forEach((name, value) -> normalized.put(name(name), value(value)));

    values = Collections.unmodifiableMap(normalized);
  }

  public static AttributeSnapshot of(Map<String, String> values) {
    return new AttributeSnapshot(values);
  }

  public static AttributeSnapshot empty() {
    return new AttributeSnapshot(Map.of());
  }

  public Optional<String> valueOf(String name) {
    return Optional.ofNullable(values.get(name));
  }

  private static String name(String name) {
    return RequiredText.of(name, MAX_NAME_LENGTH, INVALID_CODE, "Nome do atributo");
  }

  private static String value(String value) {
    if (value == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Valor do atributo é obrigatório");
    }
    if (value.length() > MAX_VALUE_LENGTH) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Valor do atributo não pode passar de " + MAX_VALUE_LENGTH + " caracteres");
    }
    return value;
  }
}
