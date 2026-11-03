package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import java.util.Optional;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

// Jackson entrega Optional vazio tanto para campo ausente quanto para null, e o PATCH precisa
// dos dois separados: ausente não mexe, null remove. Ler a árvore é o jeito de enxergar a
// diferença sem inventar um tipo de campo próprio.
final class UpdateApplicationRequestDeserializer
    extends ValueDeserializer<UpdateApplicationRequestDTO> {

  @Override
  public UpdateApplicationRequestDTO deserialize(
      JsonParser parser, DeserializationContext context) {
    JsonNode node = context.readTree(parser);

    if (!node.isObject()) {
      return context.reportInputMismatch(
          UpdateApplicationRequestDTO.class, "Corpo precisa ser um objeto JSON");
    }

    return new UpdateApplicationRequestDTO(
        text(node, "name", context),
        days(node, "quietPeriodDays", context),
        days(node, "retentionDays", context),
        days(node, "openTextRetentionDays", context));
  }

  // O nome não é removível: null aqui é corpo malformado, não um estado do PATCH.
  private static Optional<String> text(
      JsonNode node, String field, DeserializationContext context) {
    var value = node.get(field);
    if (value == null) {
      return null;
    }
    if (!value.isString()) {
      return context.reportInputMismatch(
          UpdateApplicationRequestDTO.class, "Campo %s precisa ser texto", field);
    }
    return Optional.of(value.asString());
  }

  private static Optional<Integer> days(
      JsonNode node, String field, DeserializationContext context) {
    var value = node.get(field);
    if (value == null) {
      return null;
    }
    if (value.isNull()) {
      return Optional.empty();
    }
    if (!value.isIntegralNumber() || !value.canConvertToInt()) {
      return context.reportInputMismatch(
          UpdateApplicationRequestDTO.class, "Campo %s precisa ser um inteiro", field);
    }
    return Optional.of(value.asInt());
  }
}
