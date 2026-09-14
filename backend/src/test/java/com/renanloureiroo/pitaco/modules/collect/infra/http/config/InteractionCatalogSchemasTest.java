package com.renanloureiroo.pitaco.modules.collect.infra.http.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.InteractionEventType;
import com.renanloureiroo.pitaco.core.catalog.InteractionField;
import io.swagger.v3.oas.models.OpenAPI;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InteractionCatalogSchemas")
class InteractionCatalogSchemasTest {

  private static OpenAPI registered() {
    var openApi = new OpenAPI();
    InteractionCatalogSchemas.register(openApi);
    return openApi;
  }

  @Test
  @DisplayName("Publica a lista de tipos igual à do catálogo, com a versão como extensão")
  void lista_de_tipos() {
    var type = registered().getComponents().getSchemas().get(InteractionCatalogSchemas.EVENT_TYPE);

    assertThat(type.getEnum())
        .containsExactlyElementsOf(
            Arrays.stream(InteractionEventType.values()).map(InteractionEventType::wire).toList());
    assertThat(type.getExtensions())
        .containsEntry(InteractionCatalogSchemas.CATALOG_VERSION_EXTENSION, InteractionEventType.CATALOG_VERSION);
  }

  @Test
  @DisplayName("O evento é uma união discriminada por type, com uma variante por tipo")
  void uniao_discriminada() {
    var event = registered().getComponents().getSchemas().get(InteractionCatalogSchemas.EVENT);

    assertThat(event.getOneOf()).hasSize(InteractionEventType.values().length);
    assertThat(event.getDiscriminator().getPropertyName()).isEqualTo("type");
    assertThat(event.getDiscriminator().getMapping())
        .containsEntry("survey_presented", "#/components/schemas/SurveyPresentedEvent")
        .containsEntry("question_left", "#/components/schemas/QuestionLeftEvent")
        .hasSize(InteractionEventType.values().length);
  }

  @Test
  @DisplayName("Cada data é fechado, com os campos do catálogo e nenhum outro")
  void data_fechado() {
    var schemas = registered().getComponents().getSchemas();

    assertThat(schemas.get("TextEditedData").getProperties()).containsOnlyKeys("length");
    assertThat(schemas.get("TextEditedData").getAdditionalProperties()).isEqualTo(false);
    assertThat(schemas.get("TextFocusedData").getProperties()).isNullOrEmpty();
    for (var type : InteractionEventType.values()) {
      var name = Arrays.stream(type.wire().split("_")).map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1)).reduce("", String::concat);
      var data = schemas.get(name + "Data");
      var fields = type.fields().stream().map(InteractionField::name).toList();
      if (fields.isEmpty()) {
        assertThat(data.getProperties()).as(type.wire()).isNullOrEmpty();
      } else {
        assertThat(data.getProperties().keySet()).as(type.wire()).containsExactlyElementsOf(fields);
      }
    }
  }

  @Test
  @DisplayName("Evento de pergunta exige questionKey; evento de pesquisa nem o declara")
  void chave_da_pergunta() {
    var schemas = registered().getComponents().getSchemas();

    assertThat(schemas.get("QuestionViewedEvent").getRequired()).contains("questionKey");
    assertThat(schemas.get("SurveyDismissedEvent").getProperties()).doesNotContainKey("questionKey");
    assertThat(schemas.get("SurveyDismissedEvent").getRequired())
        .containsExactlyInAnyOrder("catalogVersion", "type", "displayId", "seq", "occurredAt", "elapsedMs", "data");
  }
}
