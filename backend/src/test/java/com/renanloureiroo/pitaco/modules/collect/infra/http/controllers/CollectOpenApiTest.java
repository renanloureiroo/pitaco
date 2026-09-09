package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

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

// O contrato de specs/004-response-collection/contracts/public-collect-api.md é o alvo: cada
// operação pública precisa anunciar todos os status que pode devolver.
@E2E
@DisplayName("OpenAPI da superfície pública")
class CollectOpenApiTest {

  private static final Map<String, List<String>> EXPECTED_STATUSES =
      Map.of(
          "/collect/eligibility.post", List.of("200", "400", "401"),
          "/collect/displays.post", List.of("200", "201", "400", "401", "404", "409", "422"),
          "/collect/displays/{displayId}/submission.post",
              List.of("204", "400", "401", "404", "409", "422"));

  @Autowired RestTestClient client;

  private JsonNode document() {
    var body =
        client
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    try {
      return new ObjectMapper().readTree(body);
    } catch (JsonProcessingException invalid) {
      throw new IllegalStateException("Documento OpenAPI inválido", invalid);
    }
  }

  @Test
  @DisplayName("As três operações públicas estão publicadas")
  void publica_as_tres_operacoes() {
    var paths = document().get("paths");

    EXPECTED_STATUSES
        .keySet()
        .forEach(
            key -> {
              var path = key.substring(0, key.lastIndexOf('.'));
              assertThat(paths.has(path)).as(path).isTrue();
            });
  }

  @Test
  @DisplayName("Cada operação anuncia exatamente os códigos de status do contrato")
  void anuncia_cada_codigo_de_status() {
    var paths = document().get("paths");

    EXPECTED_STATUSES.forEach(
        (key, statuses) -> {
          var path = key.substring(0, key.lastIndexOf('.'));
          var method = key.substring(key.lastIndexOf('.') + 1);
          var responses = paths.get(path).get(method).get("responses");

          var declarados = new ArrayList<String>();
          responses.fieldNames().forEachRemaining(declarados::add);

          assertThat(declarados)
              .as(method + " " + path)
              .containsExactlyInAnyOrderElementsOf(statuses);
        });
  }

  @Test
  @DisplayName("Toda operação pública exige o header da chave")
  void exige_o_header_da_chave() {
    var paths = document().get("paths");

    EXPECTED_STATUSES
        .keySet()
        .forEach(
            key -> {
              var path = key.substring(0, key.lastIndexOf('.'));
              var method = key.substring(key.lastIndexOf('.') + 1);

              var headers = new ArrayList<String>();
              paths
                  .get(path)
                  .get(method)
                  .get("parameters")
                  .forEach(
                      parameter -> {
                        if ("header".equals(parameter.get("in").asText())) {
                          headers.add(parameter.get("name").asText());
                        }
                      });

              assertThat(headers).as(method + " " + path).contains("X-Pitaco-Key");
            });
  }

  @Test
  @DisplayName("Os schemas da entrega e da exibição são gerados")
  void gera_os_schemas() {
    var schemas = document().get("components").get("schemas");

    assertThat(schemas.has("EligibilityResponseDTO")).isTrue();
    assertThat(schemas.has("DeliverableSurvey")).isTrue();
    assertThat(schemas.has("DeliverableQuestion")).isTrue();
    assertThat(schemas.has("SurveyDisplay")).isTrue();
    assertThat(schemas.has("EligibilityRequestDTO")).isTrue();
    assertThat(schemas.has("OpenDisplayRequestDTO")).isTrue();
    assertThat(schemas.has("SubmissionRequestDTO")).isTrue();
  }
}
