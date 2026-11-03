package com.renanloureiroo.pitaco.infra.http.config;

import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.Ordered;

// Por último: o customizador do Boot religa o redirecionamento de /api para /api/ a partir de
// server.tomcat.redirect-context-root, e só o que é aplicado depois dele vale.
public class SwaggerUiRedirectCustomizer
    implements WebServerFactoryCustomizer<TomcatServletWebServerFactory>, Ordered {

  private final SwaggerUiRedirectValve valve;

  public SwaggerUiRedirectCustomizer(SwaggerUiRedirectValve valve) {
    this.valve = valve;
  }

  @Override
  public void customize(TomcatServletWebServerFactory factory) {
    factory.addEngineValves(valve);
    // O Tomcat responde /api com um 302 para /api/ antes de qualquer valve; desligado, o /api
    // chega à valve e vai direto para a UI, sem o salto intermediário.
    factory.addContextCustomizers(context -> context.setMapperContextRootRedirectEnabled(false));
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
