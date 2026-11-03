package com.renanloureiroo.pitaco.infra.http.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// A mesma propriedade que liga a UI liga o redirecionamento: sem segunda fonte de verdade, e com a
// UI desligada as entradas da API respondem exatamente como antes.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.swagger-ui.enabled", havingValue = "true")
public class SwaggerUiRedirectConfiguration {

  @Bean
  SwaggerUiRedirectCustomizer swaggerUiRedirectCustomizer(
      @Value("${server.servlet.context-path:}") String contextPath,
      @Value("${springdoc.swagger-ui.path:/swagger-ui.html}") String uiPath) {
    return new SwaggerUiRedirectCustomizer(new SwaggerUiRedirectValve(contextPath, uiPath));
  }
}
