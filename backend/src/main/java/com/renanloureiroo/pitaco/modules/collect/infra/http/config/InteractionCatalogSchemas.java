package com.renanloureiroo.pitaco.modules.collect.infra.http.config;

import com.renanloureiroo.pitaco.core.catalog.InteractionEventType;
import com.renanloureiroo.pitaco.core.catalog.InteractionField;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// O catálogo publicado no OpenAPI é gerado do enum do core, e não anotado à mão: o SDK gera os
// tipos daqui e o teste de contrato compara com eles, então existe uma fonte só.
public final class InteractionCatalogSchemas {

  public static final String EVENT = "InteractionEvent";
  public static final String EVENT_TYPE = "InteractionEventType";
  public static final String CATALOG_VERSION_EXTENSION = "x-pitaco-catalog-version";

  private static final String REF_PREFIX = "#/components/schemas/";

  private InteractionCatalogSchemas() {}

  public static void register(OpenAPI openApi) {
    if (openApi.getComponents() == null) {
      openApi.setComponents(new Components());
    }
    var components = openApi.getComponents();

    var wires = new ArrayList<Object>();
    Arrays.stream(InteractionEventType.values()).map(InteractionEventType::wire).forEach(wires::add);
    var typeSchema =
        typed("string")
            ._enum(wires)
            .description(
                "Os tipos do catálogo de eventos de interação, versão "
                    + InteractionEventType.CATALOG_VERSION
                    + ". Tipo novo exige catalogVersion nova");
    typeSchema.addExtension(CATALOG_VERSION_EXTENSION, InteractionEventType.CATALOG_VERSION);
    components.addSchemas(EVENT_TYPE, typeSchema);

    var variants = new ArrayList<Schema>();
    var mapping = new LinkedHashMap<String, String>();
    for (var type : InteractionEventType.values()) {
      var name = pascal(type.wire());
      components.addSchemas(name + "Data", dataOf(type));
      components.addSchemas(name + "Event", eventOf(type, name + "Data"));
      variants.add(new Schema<>().$ref(REF_PREFIX + name + "Event"));
      mapping.put(type.wire(), REF_PREFIX + name + "Event");
    }

    components.addSchemas(
        EVENT,
        new Schema<>()
            .oneOf(variants)
            .discriminator(new Discriminator().propertyName("type").mapping(mapping))
            .description(
                "Um evento do catálogo: envelope comum e `data` fechado por tipo, discriminado "
                    + "por `type`"));
  }

  private static Schema<Object> eventOf(InteractionEventType type, String dataName) {
    var required = new ArrayList<String>(List.of("catalogVersion", "type", "displayId", "seq"));
    required.addAll(List.of("occurredAt", "elapsedMs", "data"));

    var event =
        typed("object")
            .description("Evento `" + type.wire() + "`")
            .addProperty(
                "catalogVersion",
                typed("integer")
                    .minimum(BigDecimal.ONE)
                    .description(
                        "Versão do catálogo em que o SDK emitiu o evento; o servidor conhece até "
                            + InteractionEventType.CATALOG_VERSION))
            .addProperty("type", typed("string")._enum(List.of(type.wire())))
            .addProperty(
                "displayId",
                typed("string").format("uuid").description("O mesmo id gerado para a exibição"))
            .addProperty(
                "seq",
                typed("integer")
                    .minimum(BigDecimal.ONE)
                    .description("Monotônico por exibição, começa em 1; chave de idempotência"))
            .addProperty(
                "occurredAt",
                typed("string").format("date-time").description("Relógio do dispositivo"))
            .addProperty(
                "elapsedMs",
                typed("integer")
                    .format("int64")
                    .minimum(BigDecimal.ZERO)
                    .description("Relógio monotônico desde survey_presented"));

    if (type.questionScoped()) {
      event.addProperty(
          "questionKey", typed("string").format("uuid").description("Chave estável da pergunta"));
      required.add("questionKey");
    }

    event.addProperty("data", new Schema<>().$ref(REF_PREFIX + dataName));
    event.setRequired(required);
    return event;
  }

  private static Schema<Object> dataOf(InteractionEventType type) {
    var data = typed("object").description("Payload fechado de `" + type.wire() + "`");
    for (var field : type.fields()) {
      data.addProperty(field.name(), schemaOf(field));
    }
    if (!type.fields().isEmpty()) {
      data.setRequired(type.fields().stream().map(InteractionField::name).toList());
    }
    data.setAdditionalProperties(false);
    return data;
  }

  private static Schema<?> schemaOf(InteractionField field) {
    return switch (field.kind()) {
      case COUNT -> typed("integer").minimum(BigDecimal.ZERO);
      case POSITIVE -> typed("integer").minimum(BigDecimal.ONE);
      case DURATION -> typed("integer").format("int64").minimum(BigDecimal.ZERO).description("Milissegundos");
      case CHOICE -> typed("string")._enum(new ArrayList<Object>(field.choices()));
      case QUESTION_KEY -> typed("string").format("uuid");
      case EVENT_NAME -> typed("string").maxLength(80);
      case FLAG -> typed("boolean");
      case ANSWER_VALUE ->
          new Schema<>()
              .oneOf(
                  List.of(
                      typed("string").maxLength(InteractionField.MAX_ANSWER_VALUE_LENGTH),
                      typed("integer")))
              .description("O value da opção ou o número escolhido, nunca o rótulo");
    };
  }

  // O documento sai em OpenAPI 3.1, que lê `types`; `type` fica para quem o servir em 3.0.
  private static Schema<Object> typed(String type) {
    var schema = new Schema<Object>();
    schema.setType(type);
    schema.setTypes(new LinkedHashSet<>(Set.of(type)));
    return schema;
  }

  private static String pascal(String wire) {
    var result = new StringBuilder();
    for (var part : wire.split("_")) {
      result.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
    }
    return result.toString();
  }
}
