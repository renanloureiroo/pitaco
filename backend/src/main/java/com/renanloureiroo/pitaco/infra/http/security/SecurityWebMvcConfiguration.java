package com.renanloureiroo.pitaco.infra.http.security;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// As duas superfícies se separam por prefixo de rota (D-03). O /api não aparece aqui: vem do
// context-path e é removido antes do casamento de padrão.
@Configuration(proxyBeanMethods = false)
public class SecurityWebMvcConfiguration implements WebMvcConfigurer {

  private static final String PUBLIC_SURFACE = "/collect/**";
  private static final String ADMIN_SURFACE = "/applications/**";

  private final ApiKeyAuthenticationInterceptor apiKeyAuthentication;
  private final AdminSurfaceInterceptor adminSurface;
  private final AuthenticatedApplicationArgumentResolver authenticatedApplication;

  public SecurityWebMvcConfiguration(
      ApiKeyAuthenticationInterceptor apiKeyAuthentication,
      AdminSurfaceInterceptor adminSurface,
      AuthenticatedApplicationArgumentResolver authenticatedApplication) {
    this.apiKeyAuthentication = apiKeyAuthentication;
    this.adminSurface = adminSurface;
    this.authenticatedApplication = authenticatedApplication;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(apiKeyAuthentication).addPathPatterns(PUBLIC_SURFACE);
    registry.addInterceptor(adminSurface).addPathPatterns(ADMIN_SURFACE);
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(authenticatedApplication);
  }
}
