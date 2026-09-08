package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record TriggerOutput(
    String eventName,
    Instant windowStart,
    Optional<Instant> windowEnd,
    double samplingRate,
    List<SegmentationRuleOutput> rules) {

  public TriggerOutput {
    rules = List.copyOf(rules);
  }

  public static TriggerOutput of(Trigger trigger, List<SegmentationRule> rules) {
    return new TriggerOutput(
        trigger.event().value(),
        trigger.window().start(),
        trigger.window().end(),
        trigger.rate().value(),
        SegmentationRuleOutput.ofAll(rules));
  }
}
