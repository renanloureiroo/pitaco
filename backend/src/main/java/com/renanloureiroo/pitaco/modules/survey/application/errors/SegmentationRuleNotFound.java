package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.NotFoundException;

public final class SegmentationRuleNotFound extends NotFoundException {

  private static final String CODE = "segmentation_rule.not_found";

  private final String ruleId;

  public SegmentationRuleNotFound(String ruleId) {
    super(CODE, "Regra de segmentação não encontrada");
    this.ruleId = ruleId;
  }

  public String ruleId() {
    return ruleId;
  }
}
