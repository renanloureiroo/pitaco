package com.renanloureiroo.pitaco.infra.http.ratelimit;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

// Corre antes da autenticação: uma origem inundando a API não deve custar uma consulta de chave
// por requisição. Lê a requisição crua: o ForwardedHeaderFilter troca o getRemoteAddr pelo
// primeiro X-Forwarded-For, que é do cliente, e esconde o cabeçalho de quem vem depois dele.
public class OriginRateLimitInterceptor implements HandlerInterceptor {

  private final FixedWindowRateLimiter limiter;
  private final OriginResolver origins;

  public OriginRateLimitInterceptor(FixedWindowRateLimiter limiter, OriginResolver origins) {
    this.limiter = limiter;
    this.origins = origins;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {

    var raw = unwrapped(request);
    var decision = limiter.tryAcquire(origins.resolve(raw.getRemoteAddr(), raw::getHeader));
    if (!decision.allowed()) {
      throw new RateLimitExceeded(decision.retryAfterSeconds());
    }

    return true;
  }

  private static HttpServletRequest unwrapped(HttpServletRequest request) {
    ServletRequest current = request;
    while (current instanceof ServletRequestWrapper wrapper) {
      current = wrapper.getRequest();
    }
    return current instanceof HttpServletRequest http ? http : request;
  }
}
