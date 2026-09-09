package com.renanloureiroo.pitaco.infra.http.security;

import com.renanloureiroo.pitaco.modules.app.application.errors.ApiKeyMissing;
import com.renanloureiroo.pitaco.modules.app.application.usecases.AuthenticateApiKeyUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// O resultado vai como atributo da requisição, nunca em ThreadLocal: com virtual threads ligadas,
// estado preso à thread não acompanha o ciclo da requisição.
@Component
public class ApiKeyAuthenticationInterceptor implements HandlerInterceptor {

  public static final String HEADER = "X-Pitaco-Key";
  public static final String REQUEST_ATTRIBUTE = AuthenticatedApplication.class.getName();

  private final AuthenticateApiKeyUseCase authenticate;

  public ApiKeyAuthenticationInterceptor(AuthenticateApiKeyUseCase authenticate) {
    this.authenticate = authenticate;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {

    var presented = request.getHeader(HEADER);
    if (presented == null || presented.isBlank()) {
      throw new ApiKeyMissing();
    }

    var authenticated = authenticate.execute(new AuthenticateApiKeyUseCase.Input(presented));

    request.setAttribute(
        REQUEST_ATTRIBUTE,
        new AuthenticatedApplication(
            authenticated.applicationId(), authenticated.applicationActive()));

    return true;
  }
}
