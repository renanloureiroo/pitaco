package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.RuleOperation;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import java.util.List;
import java.util.Optional;

public record SegmentationRuleOutput(
    String id, String attribute, RuleOperation operation, Optional<String> value) {

  public static SegmentationRuleOutput of(SegmentationRule rule) {
    return new SegmentationRuleOutput(
        rule.id().value(), rule.attribute(), rule.operation(), rule.value());
  }

  public static List<SegmentationRuleOutput> ofAll(List<SegmentationRule> rules) {
    return rules.stream().map(SegmentationRuleOutput::of).toList();
  }
}
