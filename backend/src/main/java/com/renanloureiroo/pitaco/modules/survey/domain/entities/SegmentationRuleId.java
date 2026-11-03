package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class SegmentationRuleId extends Id {

  private static final String INVALID_CODE = "segmentation_rule.id_invalid";

  private SegmentationRuleId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de regra de segmentação inválido");
    }
  }

  public static SegmentationRuleId generate() {
    return new SegmentationRuleId(newValue());
  }

  public static SegmentationRuleId of(String value) {
    return new SegmentationRuleId(value);
  }
}
