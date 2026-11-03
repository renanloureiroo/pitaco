package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import java.util.Optional;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

// Jackson entrega Optional vazio tanto para ausente quanto para null; a árvore é o que separa
// os dois. Só a cota e o texto do aviso admitem null — nos demais, null é corpo malformado.
final class UpdateSurveyRequestDeserializer extends ValueDeserializer<UpdateSurveyRequestDTO> {

  @Override
  public UpdateSurveyRequestDTO deserialize(JsonParser parser, DeserializationContext context) {
    JsonNode node = context.readTree(parser);

    if (!node.isObject()) {
      return context.reportInputMismatch(
          UpdateSurveyRequestDTO.class, "Corpo precisa ser um objeto JSON");
    }

    return new UpdateSurveyRequestDTO(
        text(node, "name", context),
        integer(node, "priority", false, context),
        integer(node, "responseQuota", true, context),
        bool(node, "ignoresQuietPeriod", context),
        bool(node, "freeTextNoticeEnabled", context),
        removableText(node, "freeTextNoticeText", context));
  }

  private static Optional<String> text(
      JsonNode node, String field, DeserializationContext context) {
    var value = node.get(field);
    if (value == null) {
      return null;
    }
    if (!value.isString()) {
      return context.reportInputMismatch(
          UpdateSurveyRequestDTO.class, "Campo %s precisa ser texto", field);
    }
    return Optional.of(value.asString());
  }

  private static Optional<String> removableText(
      JsonNode node, String field, DeserializationContext context) {
    var value = node.get(field);
    if (value != null && value.isNull()) {
      return Optional.empty();
    }
    return text(node, field, context);
  }

  private static Optional<Integer> integer(
      JsonNode node, String field, boolean removable, DeserializationContext context) {
    var value = node.get(field);
    if (value == null) {
      return null;
    }
    if (value.isNull() && removable) {
      return Optional.empty();
    }
    if (!value.isIntegralNumber() || !value.canConvertToInt()) {
      return context.reportInputMismatch(
          UpdateSurveyRequestDTO.class, "Campo %s precisa ser um inteiro", field);
    }
    return Optional.of(value.asInt());
  }

  private static Optional<Boolean> bool(
      JsonNode node, String field, DeserializationContext context) {
    var value = node.get(field);
    if (value == null) {
      return null;
    }
    if (!value.isBoolean()) {
      return context.reportInputMismatch(
          UpdateSurveyRequestDTO.class, "Campo %s precisa ser verdadeiro ou falso", field);
    }
    return Optional.of(value.asBoolean());
  }
}
