package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.ScaleRange;

public record ScaleRangeOutput(int min, int max) {

  static ScaleRangeOutput of(ScaleRange range) {
    return new ScaleRangeOutput(range.min(), range.max());
  }
}
