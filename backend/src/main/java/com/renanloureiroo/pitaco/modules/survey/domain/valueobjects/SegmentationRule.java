package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.RuleOperation;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SegmentationRuleId;
import java.util.Optional;

public record SegmentationRule(
    SegmentationRuleId id, String attribute, RuleOperation operation, Optional<String> value) {

  private static final int MAX_ATTRIBUTE_LENGTH = 80;
  private static final int MAX_VALUE_LENGTH = 200;

  private static final String INVALID_CODE = "segmentation_rule.invalid";

  public SegmentationRule {
    if (id == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Regra precisa de identificador");
    }
    if (operation == null) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Operação é obrigatória");
    }
    if (attribute == null || attribute.isBlank()) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Atributo é obrigatório");
    }
    attribute = attribute.strip();
    if (attribute.length() > MAX_ATTRIBUTE_LENGTH) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Atributo não pode passar de " + MAX_ATTRIBUTE_LENGTH + " caracteres");
    }

    value = value == null ? Optional.empty() : value.map(String::strip).filter(v -> !v.isBlank());

    if (operation.requiresValue() && value.isEmpty()) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Esta operação exige um valor de comparação");
    }
    if (!operation.requiresValue() && value.isPresent()) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Esta operação não admite valor de comparação");
    }
    if (value.isPresent() && value.get().length() > MAX_VALUE_LENGTH) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Valor não pode passar de " + MAX_VALUE_LENGTH + " caracteres");
    }
  }

  public static SegmentationRule create(
      String attribute, RuleOperation operation, Optional<String> value) {
    return new SegmentationRule(SegmentationRuleId.generate(), attribute, operation, value);
  }

  public SegmentationRule copyForNewVersion() {
    return new SegmentationRule(SegmentationRuleId.generate(), attribute, operation, value);
  }
}
