package com.renanloureiroo.pitaco.infra.http.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.catalina.core.StandardContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

// A condição é provada num contexto mínimo, só com esta configuração: um segundo @E2E com a UI
// desligada subiria outra pilha de containers para provar uma linha.
@DisplayName("SwaggerUiRedirectConfiguration")
class SwaggerUiRedirectConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(SwaggerUiRedirectConfiguration.class)
          .withPropertyValues("server.servlet.context-path=/api");

  @Test
  @DisplayName("Com a UI desligada, que é o padrão de produção, nada redireciona")
  void desligada_nao_redireciona() {
    runner.run(context -> assertThat(context).doesNotHaveBean(SwaggerUiRedirectCustomizer.class));
    runner
        .withPropertyValues("springdoc.swagger-ui.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(SwaggerUiRedirectCustomizer.class));
  }

  @Test
  @DisplayName("Com a UI ligada, instala a valve na engine e desliga o salto de /api para /api/")
  void ligada_instala_a_valve() {
    runner
        .withPropertyValues("springdoc.swagger-ui.enabled=true")
        .run(
            context -> {
              var factory = new TomcatServletWebServerFactory();
              context.getBean(SwaggerUiRedirectCustomizer.class).customize(factory);

              assertThat(factory.getEngineValves())
                  .singleElement()
                  .isInstanceOfSatisfying(
                      SwaggerUiRedirectValve.class,
                      valve -> assertThat(valve.location()).isEqualTo("/api/swagger-ui.html"));

              var tomcatContext = new StandardContext();
              factory.getContextCustomizers().forEach(c -> c.customize(tomcatContext));
              assertThat(tomcatContext.getMapperContextRootRedirectEnabled()).isFalse();
            });
  }
}
