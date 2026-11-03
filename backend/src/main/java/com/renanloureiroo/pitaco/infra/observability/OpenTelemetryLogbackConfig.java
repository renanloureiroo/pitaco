package com.renanloureiroo.pitaco.infra.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.context.annotation.Configuration;

/**
 * Liga o appender declarado no logback-spring.xml à instância de OpenTelemetry
 * criada pelo Spring Boot.
 *
 * <p>O Logback sobe antes do contexto do Spring, então o appender nasce sem
 * destino e descarta o que recebe até esta instalação acontecer. Por isso a
 * chamada fica no construtor, e não em um {@code @PostConstruct} ou listener
 * tardio: quanto mais cedo, menos linha de startup se perde. As primeiras
 * linhas do boot ficam só no console de qualquer forma — é uma limitação da
 * ponte, não um defeito de configuração.
 */
@Configuration(proxyBeanMethods = false)
public class OpenTelemetryLogbackConfig {

  public OpenTelemetryLogbackConfig(OpenTelemetry openTelemetry) {
    OpenTelemetryAppender.install(openTelemetry);
  }
}
