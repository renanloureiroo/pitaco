package com.renanloureiroo.pitaco.modules.collect.infra.http.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InteractionCatalogOpenApiConfiguration {

  @Bean
  OpenApiCustomizer interactionCatalogOpenApi() {
    return InteractionCatalogSchemas::register;
  }
}
