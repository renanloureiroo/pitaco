package com.renanloureiroo.pitaco.modules.results.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("OpenAPI das leituras de resultados")
class ResultsOpenApiTest {

  private static final String RESULTS = "/applications/{applicationId}/surveys/{surveyId}/results";
  private static final Map<String, List<String>> EXPECTED_STATUSES =
      Map.of(
          RESULTS, List.of("200", "400", "403", "404"),
          RESULTS + "/open-answers", List.of("200", "400", "403", "404"),
          RESULTS + "/export", List.of("200", "400", "403", "404"));

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
  @DisplayName("Cada leitura está publicada e anuncia exatamente os códigos de status do contrato")
  void anuncia_cada_codigo_de_status() {
    var paths = document().get("paths");

    EXPECTED_STATUSES.forEach(
        (path, statuses) -> {
          assertThat(paths.has(path)).as(path).isTrue();
          var responses = paths.get(path).get("get").get("responses");
          var declared = new ArrayList<String>();
          responses.fieldNames().forEachRemaining(declared::add);
          assertThat(declared).as(path).containsExactlyInAnyOrderElementsOf(statuses);
        });
  }
}
