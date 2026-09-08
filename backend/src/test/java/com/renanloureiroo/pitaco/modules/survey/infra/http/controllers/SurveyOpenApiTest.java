package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

// O contrato de specs/003-survey-authoring/contracts/surveys.openapi.yaml é o alvo; este teste
// afirma que o documento gerado tem os mesmos caminhos e os mesmos códigos de status.
@E2E
@DisplayName("OpenAPI da autoria de pesquisa")
class SurveyOpenApiTest {

  private static final String BASE = "/applications/{applicationId}/surveys";

  private static final Map<String, List<String>> EXPECTED_STATUSES =
      Map.ofEntries(
          Map.entry(BASE + ".post", List.of("201", "400", "404", "422")),
          Map.entry(BASE + ".get", List.of("200", "400", "404")),
          Map.entry(BASE + "/{surveyId}.get", List.of("200", "404")),
          Map.entry(BASE + "/{surveyId}.patch", List.of("200", "400", "404")),
          Map.entry(BASE + "/{surveyId}.delete", List.of("204", "404", "422")),
          Map.entry(BASE + "/{surveyId}/questions.post", List.of("201", "400", "404", "422")),
          Map.entry(
              BASE + "/{surveyId}/questions/{questionId}.put", List.of("200", "400", "404", "422")),
          Map.entry(
              BASE + "/{surveyId}/questions/{questionId}.delete", List.of("204", "404", "422")),
          Map.entry(BASE + "/{surveyId}/questions/order.put", List.of("200", "400", "404", "422")),
          Map.entry(BASE + "/{surveyId}/trigger.put", List.of("200", "400", "404", "422")),
          Map.entry(BASE + "/{surveyId}/trigger/rules.post", List.of("201", "400", "404", "422")),
          Map.entry(
              BASE + "/{surveyId}/trigger/rules/{ruleId}.delete", List.of("204", "404", "422")),
          Map.entry(BASE + "/{surveyId}/publication-impediments.get", List.of("200", "404")),
          Map.entry(
              BASE + "/{surveyId}/publication.post", List.of("201", "400", "404", "409", "422")),
          Map.entry(BASE + "/{surveyId}/pause.post", List.of("200", "404", "422")),
          Map.entry(BASE + "/{surveyId}/resume.post", List.of("200", "404", "422")),
          Map.entry(BASE + "/{surveyId}/end.post", List.of("200", "404", "422")),
          Map.entry(BASE + "/{surveyId}/transitions.get", List.of("200", "404")),
          Map.entry(BASE + "/{surveyId}/versions.get", List.of("200", "400", "404")),
          Map.entry(BASE + "/{surveyId}/versions.post", List.of("201", "404", "409", "422")),
          Map.entry(BASE + "/{surveyId}/versions/draft.delete", List.of("204", "404")),
          Map.entry(BASE + "/{surveyId}/versions/{number}.get", List.of("200", "404")),
          Map.entry(BASE + "/{surveyId}/versions/comparability.get", List.of("200", "404")));

  @Autowired RestTestClient client;

  // springdoc serve o documento como application/json sem conversor registrado para JsonNode
  // no cliente de teste; o corpo cru é lido como texto e desserializado aqui.
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
  @DisplayName("Os 23 caminhos do contrato estão publicados")
  void publica_os_vinte_e_tres_caminhos() {
    var paths = document().get("paths");

    var declarados =
        EXPECTED_STATUSES.keySet().stream()
            .map(key -> key.substring(0, key.lastIndexOf('.')))
            .distinct()
            .toList();

    assertThat(EXPECTED_STATUSES).hasSize(23);
    assertThat(declarados).allSatisfy(path -> assertThat(paths.has(path)).as(path).isTrue());
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

          var declarados = new java.util.ArrayList<String>();
          responses.fieldNames().forEachRemaining(declarados::add);

          assertThat(declarados)
              .as(method + " " + path)
              .containsExactlyInAnyOrderElementsOf(statuses);
        });
  }

  @Test
  @DisplayName("Os parâmetros de paginação seguem o que a feature 002 fixou")
  void a_paginacao_segue_o_padrao() {
    var query = document().get("paths").get(BASE).get("get").get("parameters");

    var nomes = new java.util.ArrayList<String>();
    query.forEach(parameter -> nomes.add(parameter.get("name").asText()));

    assertThat(nomes).contains("applicationId", "page", "size");
  }

  @Test
  @DisplayName("Os schemas da feature são gerados com os campos do contrato")
  void gera_os_schemas() {
    var schemas = document().get("components").get("schemas");

    assertThat(schemas.has("SurveyResponseDTO")).isTrue();
    assertThat(schemas.has("QuestionResponseDTO")).isTrue();
    assertThat(schemas.has("TriggerResponseDTO")).isTrue();
    assertThat(schemas.has("SurveyVersionResponseDTO")).isTrue();
    assertThat(schemas.has("SurveyVersionDetailResponseDTO")).isTrue();
    assertThat(schemas.has("PublicationImpedimentsResponseDTO")).isTrue();
    assertThat(schemas.has("SurveyStateTransitionResponseDTO")).isTrue();
    assertThat(schemas.has("VersionComparabilityResponseDTO")).isTrue();

    var state = schemas.get("SurveyResponseDTO").get("properties").get("state");
    var valores = new java.util.ArrayList<String>();
    state.get("enum").forEach(value -> valores.add(value.asText()));
    assertThat(valores)
        .containsExactlyInAnyOrder("draft", "scheduled", "active", "paused", "ended");
  }
}
