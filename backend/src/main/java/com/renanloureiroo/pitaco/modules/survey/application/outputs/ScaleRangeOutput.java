package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleRange;

public record ScaleRangeOutput(int min, int max) {

  static ScaleRangeOutput of(ScaleRange range) {
    return new ScaleRangeOutput(range.min(), range.max());
  }
}
