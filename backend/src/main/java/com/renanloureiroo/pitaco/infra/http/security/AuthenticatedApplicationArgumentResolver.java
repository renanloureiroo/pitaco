package com.renanloureiroo.pitaco.infra.http.security;

import com.renanloureiroo.pitaco.modules.app.application.errors.ApiKeyMissing;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class AuthenticatedApplicationArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return AuthenticatedApplication.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer container,
      NativeWebRequest request,
      WebDataBinderFactory binderFactory) {

    var authenticated =
        request.getAttribute(
            ApiKeyAuthenticationInterceptor.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);

    if (authenticated == null) {
      throw new ApiKeyMissing();
    }

    return authenticated;
  }
}
