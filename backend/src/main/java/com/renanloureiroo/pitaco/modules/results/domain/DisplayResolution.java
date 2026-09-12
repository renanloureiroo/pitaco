package com.renanloureiroo.pitaco.modules.results.domain;

import java.time.Instant;

// O desfecho que a leitura apresenta: o gravado, ou o abandono derivado da abertura vencida —
// a mesma regra da coleta, reproduzida aqui porque o módulo de leitura não a importa de lá.
public enum DisplayResolution {
  COMPLETED,
  DISMISSED,
  ABANDONED,
  IN_PROGRESS;

  private static final String STARTED = "STARTED";

  public static DisplayResolution of(String storedOutcome, Instant openedAt, Instant abandonedBefore) {
    if (!STARTED.equals(storedOutcome)) {
      return valueOf(storedOutcome);
    }
    return openedAt.isBefore(abandonedBefore) ? ABANDONED : IN_PROGRESS;
  }
}
