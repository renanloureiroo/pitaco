package com.renanloureiroo.pitaco.infra.http.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;

// Requisição crua na porta do servidor: a raiz do host fica fora do context-path, fora do alcance
// do RestTestClient, cuja base já inclui o /api. O contexto é o de todo @E2E, com a UI ligada.
@E2E
@DisplayName("Entradas da API levam ao Swagger UI")
class SwaggerUiRedirectE2ETest {

  @Value("${local.server.port}")
  int port;

  private HttpResponse<Void> send(HttpClient.Redirect redirect, String method, String path)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build();
    try (var http = HttpClient.newBuilder().followRedirects(redirect).build()) {
      return http.send(request, HttpResponse.BodyHandlers.discarding());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"/", "/api", "/api/"})
  @DisplayName("302 com Location relativo para a UI")
  void redireciona_para_a_ui(String path) throws Exception {
    var response = send(HttpClient.Redirect.NEVER, "GET", path);

    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location")).contains("/api/swagger-ui.html");
  }

  @Test
  @DisplayName("Seguindo os redirecionamentos, /api termina na UI de verdade")
  void termina_na_ui() throws Exception {
    var response = send(HttpClient.Redirect.NORMAL, "GET", "/api");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.uri().getPath()).startsWith("/api/swagger-ui");
  }

  @Test
  @DisplayName("As outras rotas respondem como antes")
  void outras_rotas_iguais() throws Exception {
    assertThat(send(HttpClient.Redirect.NEVER, "GET", "/api/applications").statusCode())
        .isEqualTo(200);
    assertThat(send(HttpClient.Redirect.NEVER, "GET", "/qualquer").statusCode()).isEqualTo(404);
    assertThat(send(HttpClient.Redirect.NEVER, "POST", "/api").statusCode()).isNotEqualTo(302);
  }
}
