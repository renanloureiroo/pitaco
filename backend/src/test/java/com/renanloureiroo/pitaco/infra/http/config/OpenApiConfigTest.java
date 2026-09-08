package com.renanloureiroo.pitaco.infra.http.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
class OpenApiConfigTest {

  @Autowired RestTestClient client;

  @Test
  void expoe_o_contrato_openapi_com_os_metadados_da_api() {
    client
        .get()
        .uri("/v3/api-docs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(
            body ->
                assertThat(body)
                    .contains("\"title\":\"Pitaco API\"")
                    .contains("\"version\":\"v1\""));
  }

  @Test
  void anuncia_o_servidor_em_https_quando_o_proxy_terminou_o_tls() {
    client
        .get()
        .uri("/v3/api-docs")
        .header("X-Forwarded-Proto", "https")
        .header("X-Forwarded-Host", "pitaco.renanloureiro.me")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("\"url\":\"https://pitaco.renanloureiro.me/api\""));
  }

  @Test
  void serve_a_interface_do_swagger_ui() {
    client.get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk();
  }
}
