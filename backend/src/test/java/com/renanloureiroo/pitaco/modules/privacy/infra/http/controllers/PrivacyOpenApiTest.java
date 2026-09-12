package com.renanloureiroo.pitaco.modules.privacy.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("OpenAPI da privacidade")
class PrivacyOpenApiTest {

  private record Expected(String path, String method, List<String> statuses) {}

  private static final String APPLICATION = "/applications/{applicationId}";
  private static final List<Expected> EXPECTED =
      List.of(
          new Expected(APPLICATION + "/respondents", "delete", List.of("200", "400", "403", "404")),
          new Expected(APPLICATION + "/deletion-audits", "get", List.of("200", "400", "403", "404")),
          new Expected(APPLICATION + "/retention-preview", "get", List.of("200", "403", "404")));

  @Autowired RestTestClient client;

  private JsonNode document() {
    var body =
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk()
            .expectBody(String.class).returnResult().getResponseBody();
    try {
      return new ObjectMapper().readTree(body);
    } catch (JsonProcessingException invalid) {
      throw new IllegalStateException("Documento OpenAPI inválido", invalid);
    }
  }

  @Test
  @DisplayName("Cada rota está publicada e anuncia exatamente os códigos de status do contrato")
  void anuncia_cada_codigo_de_status() {
    var paths = document().get("paths");

    EXPECTED.forEach(
        expected -> {
          assertThat(paths.has(expected.path())).as(expected.path()).isTrue();
          var responses = paths.get(expected.path()).get(expected.method()).get("responses");
          var declared = new ArrayList<String>();
          responses.fieldNames().forEachRemaining(declared::add);
          assertThat(declared).as(expected.path()).containsExactlyInAnyOrderElementsOf(expected.statuses());
        });
  }

  @Test
  @DisplayName("O export de resultados documenta o cabeçalho de dado pessoal")
  void documenta_o_cabecalho_do_export() {
    var headers =
        document()
            .get("paths")
            .get(APPLICATION + "/surveys/{surveyId}/results/export")
            .get("get")
            .get("responses")
            .get("200")
            .get("headers");

    assertThat(headers.has("X-Pitaco-Content-Warning")).isTrue();
  }
}
