package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.core.catalog.SegmentationCriterion;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SegmentationRuleId;
import java.util.Optional;

public record SegmentationRule(
    SegmentationRuleId id, String attribute, RuleOperation operation, Optional<String> value) {

  private static final String INVALID_CODE = "segmentation_rule.invalid";

  public SegmentationRule {
    if (id == null) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Regra precisa de identificador");
    }

    // A invariante do critério é uma só e mora no core; a autoria só reetiqueta o erro, porque
    // segmentation_rule.invalid já é contrato público desta borda.
    var criterion = validated(attribute, operation, value);
    attribute = criterion.attribute();
    value = criterion.value();
  }

  private static SegmentationCriterion validated(
      String attribute, RuleOperation operation, Optional<String> value) {
    try {
      return new SegmentationCriterion(attribute, operation, value);
    } catch (DomainException invalid) {
      throw new DomainException(invalid.type(), INVALID_CODE, invalid.getMessage(), invalid);
    }
  }

  public static SegmentationRule create(
      String attribute, RuleOperation operation, Optional<String> value) {
    return new SegmentationRule(SegmentationRuleId.generate(), attribute, operation, value);
  }

  public SegmentationCriterion criterion() {
    return new SegmentationCriterion(attribute, operation, value);
  }

  public SegmentationRule copyForNewVersion() {
    return new SegmentationRule(SegmentationRuleId.generate(), attribute, operation, value);
  }
}
