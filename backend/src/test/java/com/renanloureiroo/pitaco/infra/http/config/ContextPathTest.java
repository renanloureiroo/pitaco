package com.renanloureiroo.pitaco.infra.http.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

// Requisição crua na porta do servidor, e não pelo RestTestClient, cuja base já inclui o /api:
// só assim a ausência do prefixo também fica provada. Sem controller de teste — um @Import aqui
// abriria um segundo contexto, com outra pilha de containers, só para este arquivo.
@E2E
@DisplayName("context-path /api")
class ContextPathTest {

  @Value("${local.server.port}")
  int port;

  private int statusOf(String path) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
    try (var http = HttpClient.newHttpClient()) {
      return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
  }

  @Test
  @DisplayName("Serve as rotas da API sob /api, e nada fora dele")
  void serve_as_rotas_sob_o_context_path() throws Exception {
    assertThat(statusOf("/api/applications")).isEqualTo(200);
    assertThat(statusOf("/applications")).isEqualTo(404);
  }

  @Test
  @DisplayName("Serve o ferramental sob o mesmo prefixo")
  void serve_o_ferramental_sob_o_mesmo_context_path() throws Exception {
    assertThat(statusOf("/api/v3/api-docs")).isEqualTo(200);
    assertThat(statusOf("/api/swagger-ui/index.html")).isEqualTo(200);
    assertThat(statusOf("/api/actuator/health")).isEqualTo(200);
    assertThat(statusOf("/actuator/health")).isEqualTo(404);
  }
}
