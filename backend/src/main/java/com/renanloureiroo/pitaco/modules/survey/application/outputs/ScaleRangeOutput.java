package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleLabels;
import java.util.Optional;

public record ScaleRangeOutput(
    int min, int max, Optional<String> minLabel, Optional<String> maxLabel) {

  public ScaleRangeOutput(int min, int max) {
    this(min, max, Optional.empty(), Optional.empty());
  }

  static ScaleRangeOutput of(ScaleRange range, ScaleLabels labels) {
    return new ScaleRangeOutput(range.min(), range.max(), labels.min(), labels.max());
  }
}
