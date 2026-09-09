package com.renanloureiroo.pitaco.core.catalog;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.text.RequiredText;
import java.util.Optional;

// O critério sem o identificador da regra: a autoria gerencia regras, a coleta apenas as avalia.
public record SegmentationCriterion(
    String attribute, RuleOperation operation, Optional<String> value) {

  public static final int MAX_ATTRIBUTE_LENGTH = 80;
  public static final int MAX_VALUE_LENGTH = 200;

  private static final String INVALID_CODE = "segmentation_criterion.invalid";

  public SegmentationCriterion {
    if (operation == null) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Operação é obrigatória");
    }
    attribute = RequiredText.of(attribute, MAX_ATTRIBUTE_LENGTH, INVALID_CODE, "Atributo");

    value = value == null ? Optional.empty() : value.map(String::strip).filter(v -> !v.isBlank());

    if (operation.requiresValue() && value.isEmpty()) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Esta operação exige um valor de comparação");
    }
    if (!operation.requiresValue() && value.isPresent()) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Esta operação não admite valor de comparação");
    }
    value = value.map(text -> RequiredText.of(text, MAX_VALUE_LENGTH, INVALID_CODE, "Valor"));
  }

  public static SegmentationCriterion of(
      String attribute, RuleOperation operation, Optional<String> value) {
    return new SegmentationCriterion(attribute, operation, value);
  }
}
