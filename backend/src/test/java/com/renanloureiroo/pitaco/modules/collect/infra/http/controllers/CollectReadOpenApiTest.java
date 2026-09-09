package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

// O contrato de specs/006-collection-read-list/contracts/collection-read.md é o alvo: cada
// leitura precisa anunciar todos os status que pode devolver, 400, 403 e 404 inclusive.
@E2E
@DisplayName("OpenAPI das leituras de coleta")
class CollectReadOpenApiTest {

  private static final Map<String, List<String>> EXPECTED_STATUSES = expected();

  private static Map<String, List<String>> expected() {
    var expected = new LinkedHashMap<String, List<String>>();
    expected.put(
        "/applications/{applicationId}/surveys/{surveyId}/displays",
        List.of("200", "400", "403", "404"));
    expected.put("/applications/{applicationId}/displays/{displayId}", List.of("200", "403", "404"));
    expected.put("/applications/{applicationId}/respondents", List.of("200", "400", "403", "404"));
    expected.put(
        "/applications/{applicationId}/respondents/{respondentId}/displays",
        List.of("200", "400", "403", "404"));
    return expected;
  }

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
  @DisplayName("As quatro leituras estão publicadas")
  void publica_as_quatro_leituras() {
    var paths = document().get("paths");

    EXPECTED_STATUSES.keySet().forEach(path -> assertThat(paths.has(path)).as(path).isTrue());
  }

  @Test
  @DisplayName("Cada leitura anuncia exatamente os códigos de status do contrato")
  void anuncia_cada_codigo_de_status() {
    var paths = document().get("paths");

    EXPECTED_STATUSES.forEach(
        (path, statuses) -> {
          var responses = paths.get(path).get("get").get("responses");

          var declarados = new ArrayList<String>();
          responses.fieldNames().forEachRemaining(declarados::add);

          assertThat(declarados).as("get " + path).containsExactlyInAnyOrderElementsOf(statuses);
        });
  }

  @Test
  @DisplayName("Nenhuma leitura exige a chave do SDK: são superfície administrativa")
  void nao_exige_o_header_da_chave() {
    var paths = document().get("paths");

    EXPECTED_STATUSES
        .keySet()
        .forEach(
            path -> {
              var headers = new ArrayList<String>();
              var parameters = paths.get(path).get("get").get("parameters");
              if (parameters != null) {
                parameters.forEach(
                    parameter -> {
                      if ("header".equals(parameter.get("in").asText())) {
                        headers.add(parameter.get("name").asText());
                      }
                    });
              }

              assertThat(headers).as("get " + path).doesNotContain("X-Pitaco-Key");
            });
  }

  @Test
  @DisplayName("Os schemas de saída das leituras são gerados")
  void gera_os_schemas() {
    var schemas = document().get("components").get("schemas");

    assertThat(schemas.has("DisplaySummaryResponseDTO")).isTrue();
    assertThat(schemas.has("DisplayDetailResponseDTO")).isTrue();
    assertThat(schemas.has("AnswerReadResponseDTO")).isTrue();
    assertThat(schemas.has("RespondentResponseDTO")).isTrue();
    assertThat(schemas.has("RespondentDisplayResponseDTO")).isTrue();
  }

  @Test
  @DisplayName("O filtro de desfecho não oferece ABANDONED, que nunca é gravado")
  void nao_oferece_abandoned_no_filtro() {
    var outcome =
        document()
            .get("paths")
            .get("/applications/{applicationId}/surveys/{surveyId}/displays")
            .get("get")
            .get("parameters");

    var valores = new ArrayList<String>();
    outcome.forEach(
        parameter -> {
          if ("outcome".equals(parameter.get("name").asText())) {
            parameter.get("schema").get("enum").forEach(value -> valores.add(value.asText()));
          }
        });

    assertThat(valores).containsExactlyInAnyOrder("STARTED", "COMPLETED", "DISMISSED");
  }
}
