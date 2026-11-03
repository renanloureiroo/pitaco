package com.renanloureiroo.pitaco.infra.http.security;

import com.renanloureiroo.pitaco.infra.http.ratelimit.ApiKeyRateLimitInterceptor;
import com.renanloureiroo.pitaco.infra.http.ratelimit.OriginRateLimitInterceptor;
import com.renanloureiroo.pitaco.infra.http.ratelimit.SdkErrorRateLimitInterceptor;
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
  private static final String SDK_ERRORS = "/collect/sdk-errors";

  private final OriginRateLimitInterceptor originRateLimit;
  private final ApiKeyAuthenticationInterceptor apiKeyAuthentication;
  private final ApiKeyRateLimitInterceptor apiKeyRateLimit;
  private final SdkErrorRateLimitInterceptor sdkErrorRateLimit;
  private final AdminSurfaceInterceptor adminSurface;
  private final AuthenticatedApplicationArgumentResolver authenticatedApplication;

  public SecurityWebMvcConfiguration(
      OriginRateLimitInterceptor originRateLimit,
      ApiKeyAuthenticationInterceptor apiKeyAuthentication,
      ApiKeyRateLimitInterceptor apiKeyRateLimit,
      SdkErrorRateLimitInterceptor sdkErrorRateLimit,
      AdminSurfaceInterceptor adminSurface,
      AuthenticatedApplicationArgumentResolver authenticatedApplication) {
    this.originRateLimit = originRateLimit;
    this.apiKeyAuthentication = apiKeyAuthentication;
    this.apiKeyRateLimit = apiKeyRateLimit;
    this.sdkErrorRateLimit = sdkErrorRateLimit;
    this.adminSurface = adminSurface;
    this.authenticatedApplication = authenticatedApplication;
  }

  // A ordem de registro é a ordem de execução: origem antes da chave, para que uma inundação
  // anônima nem chegue a consultar o banco.
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(originRateLimit).addPathPatterns(PUBLIC_SURFACE);
    registry.addInterceptor(apiKeyAuthentication).addPathPatterns(PUBLIC_SURFACE);
    registry
        .addInterceptor(apiKeyRateLimit)
        .addPathPatterns(PUBLIC_SURFACE)
        .excludePathPatterns(SDK_ERRORS);
    registry.addInterceptor(sdkErrorRateLimit).addPathPatterns(SDK_ERRORS);
    registry.addInterceptor(adminSurface).addPathPatterns(ADMIN_SURFACE);
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(authenticatedApplication);
  }
}
