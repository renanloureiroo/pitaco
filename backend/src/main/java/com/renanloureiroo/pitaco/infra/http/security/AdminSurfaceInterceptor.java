package com.renanloureiroo.pitaco.infra.http.security;

import com.renanloureiroo.pitaco.modules.app.application.errors.ApiKeyForbiddenSurface;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// Apresentar a chave do SDK no painel falha barulhento em vez de ser ignorado em silêncio
// (FR-003, US1.14).
@Component
public class AdminSurfaceInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {

    if (request.getHeader(ApiKeyAuthenticationInterceptor.HEADER) != null) {
      throw new ApiKeyForbiddenSurface();
    }

    return true;
  }
}
