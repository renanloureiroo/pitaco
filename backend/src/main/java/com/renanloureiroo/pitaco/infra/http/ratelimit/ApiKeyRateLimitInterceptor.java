package com.renanloureiroo.pitaco.infra.http.ratelimit;

import com.renanloureiroo.pitaco.infra.http.security.ApiKeyAuthenticationInterceptor;
import com.renanloureiroo.pitaco.infra.http.security.AuthenticatedApplication;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// Corre depois da autenticação e conta pela chave, não pela aplicação: uma chave vazada é
// contida sem derrubar as demais chaves do mesmo app.
public class ApiKeyRateLimitInterceptor implements HandlerInterceptor {

  private final FixedWindowRateLimiter limiter;

  public ApiKeyRateLimitInterceptor(FixedWindowRateLimiter limiter) {
    this.limiter = limiter;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {

    if (request.getAttribute(ApiKeyAuthenticationInterceptor.REQUEST_ATTRIBUTE)
        instanceof AuthenticatedApplication authenticated) {
      var decision = limiter.tryAcquire(authenticated.apiKeyId());
      if (!decision.allowed()) {
        throw new RateLimitExceeded(decision.retryAfterSeconds());
      }
    }

    return true;
  }
}
