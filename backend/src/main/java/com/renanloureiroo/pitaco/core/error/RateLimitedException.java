package com.renanloureiroo.pitaco.core.error;

/** Solicitante excedeu o limite de requisições; carrega em quanto tempo pode tentar de novo. */
public class RateLimitedException extends ApplicationException {

  private final long retryAfterSeconds;

  public RateLimitedException(String code, String message, long retryAfterSeconds) {
    super(ErrorType.RATE_LIMITED, code, message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
