package com.renanloureiroo.pitaco.infra.http.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// Balde próprio por chave para os relatórios de erro do SDK: uma tempestade de falhas não pode
// consumir a cota das consultas de elegibilidade, que são o que o app hospedeiro precisa.
public class SdkErrorRateLimitInterceptor implements HandlerInterceptor {

  private final ApiKeyRateLimitInterceptor perKey;

  public SdkErrorRateLimitInterceptor(FixedWindowRateLimiter limiter) {
    this.perKey = new ApiKeyRateLimitInterceptor(limiter);
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    return perKey.preHandle(request, response, handler);
  }
}
