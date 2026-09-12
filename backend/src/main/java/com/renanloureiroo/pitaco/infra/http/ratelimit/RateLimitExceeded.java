package com.renanloureiroo.pitaco.infra.http.ratelimit;

import com.renanloureiroo.pitaco.core.error.RateLimitedException;

public final class RateLimitExceeded extends RateLimitedException {

  private static final String CODE = "rate_limit.exceeded";

  public RateLimitExceeded(long retryAfterSeconds) {
    super(CODE, "Limite de requisições excedido; tente novamente mais tarde", retryAfterSeconds);
  }
}
