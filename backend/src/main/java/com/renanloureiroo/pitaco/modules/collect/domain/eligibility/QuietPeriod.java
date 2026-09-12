package com.renanloureiroo.pitaco.modules.collect.domain.eligibility;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.time.Duration;
import java.time.Instant;

// O descanso é do respondente, não da pesquisa: conta a partir da última exibição de qualquer
// pesquisa da aplicação — respondida, dispensada ou abandonada, porque em todas ela apareceu.
public record QuietPeriod(Duration length) {

  private static final String INVALID_CODE = "quiet_period.invalid";

  public QuietPeriod {
    if (length == null || length.isNegative() || length.isZero()) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Intervalo de descanso deve ser positivo");
    }
  }

  public static QuietPeriod ofDays(int days) {
    return new QuietPeriod(Duration.ofDays(days));
  }

  // O instante exato em que o intervalo se completa já libera.
  public boolean isRestingAt(Instant lastDisplayAt, Instant now) {
    return now.isBefore(lastDisplayAt.plus(length));
  }
}
