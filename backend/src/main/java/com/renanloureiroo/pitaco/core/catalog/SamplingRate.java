package com.renanloureiroo.pitaco.core.catalog;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;

public record SamplingRate(double value) {

  private static final String INVALID_CODE = "trigger.sampling_rate_invalid";

  public SamplingRate {
    if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Proporção deve estar entre 0 e 1");
    }
  }

  public static SamplingRate of(double value) {
    return new SamplingRate(value);
  }
}
