package com.renanloureiroo.pitaco.infra.http.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da documentação OpenAPI.
 *
 * <p>Os caminhos e o comportamento da UI ficam em {@code application.yml}, sob
 * {@code springdoc}; aqui mora apenas o que descreve a API em si.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /**
     * Versão do contrato exposto, não do artefato: ela muda quando a API quebra
     * compatibilidade, não a cada release.
     */
    private static final String API_VERSION = "v1";

    @Bean
    public OpenAPI pitacoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Pitaco API")
                .version(API_VERSION)
                .description("""
                        API do Pitaco.

                        Erros seguem o formato RFC 9457 (application/problem+json), \
                        com duas propriedades adicionais: `code`, identificador estável \
                        do erro, e `traceId`, para correlação com o trace da requisição."""));
    }
}
