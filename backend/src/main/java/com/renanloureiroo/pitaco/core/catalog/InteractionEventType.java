package com.renanloureiroo.pitaco.core.catalog;

import static com.renanloureiroo.pitaco.core.catalog.InteractionField.answerValue;
import static com.renanloureiroo.pitaco.core.catalog.InteractionField.choice;
import static com.renanloureiroo.pitaco.core.catalog.InteractionField.count;
import static com.renanloureiroo.pitaco.core.catalog.InteractionField.duration;
import static com.renanloureiroo.pitaco.core.catalog.InteractionField.eventName;
import static com.renanloureiroo.pitaco.core.catalog.InteractionField.flag;
import static com.renanloureiroo.pitaco.core.catalog.InteractionField.positive;
import static com.renanloureiroo.pitaco.core.catalog.InteractionField.questionKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// O catálogo de eventos de interação, versão 1: igual para toda aplicação, e o app hospedeiro não
// cria tipo nem altera payload. Tipo novo exige CATALOG_VERSION nova, e o servidor continua
// aceitando as anteriores. É daqui que sai o esquema publicado no OpenAPI.
public enum InteractionEventType {
  SURVEY_PRESENTED(
      false,
      choice("presentation", "bottom-sheet", "modal", "inline"),
      count("questionCount"),
      count("renderableCount"),
      eventName("triggerEvent")),
  QUESTION_VIEWED(true, positive("position"), positive("visit"), choice("from", "start", "next", "back")),
  ANSWER_SELECTED(true, answerValue("value")),
  ANSWER_CHANGED(true, answerValue("from"), answerValue("to")),
  ANSWER_DESELECTED(true, answerValue("value")),
  TEXT_FOCUSED(true),
  TEXT_EDITED(true, count("length")),
  TEXT_BLURRED(true, count("length")),
  VALIDATION_BLOCKED(true, choice("reason", "required_missing")),
  QUESTION_SKIPPED(true),
  QUESTION_NOT_APPLICABLE(true, questionKey("sourceKey")),
  NAVIGATED_NEXT(true, questionKey("toKey")),
  NAVIGATED_BACK(true, questionKey("toKey")),
  QUESTION_LEFT(
      true,
      positive("visit"),
      choice("to", "next", "back", "dismiss", "complete"),
      duration("durationMs"),
      duration("activeMs"),
      flag("answered")),
  SURVEY_BACKGROUNDED(false),
  SURVEY_FOREGROUNDED(false, duration("backgroundMs")),
  SURVEY_DISMISSED(
      false,
      choice(
          "via", "close_button", "swipe", "backdrop", "hardware_back", "navigation", "programmatic"),
      positive("position"),
      count("answeredCount")),
  SURVEY_COMPLETED(
      false,
      count("answeredCount"),
      count("skippedCount"),
      count("notApplicableCount"),
      duration("activeMs"));

  public static final int CATALOG_VERSION = 1;

  private final boolean questionScoped;
  private final List<InteractionField> fields;

  InteractionEventType(boolean questionScoped, InteractionField... fields) {
    this.questionScoped = questionScoped;
    this.fields = List.of(fields);
  }

  // Comparação exata: o nome no fio é contrato, e um SDK mais novo com tipo desconhecido tem o
  // evento descartado, nunca o lote recusado.
  public static Optional<InteractionEventType> fromWire(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    for (var type : values()) {
      if (type.wire().equals(raw)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }

  public boolean questionScoped() {
    return questionScoped;
  }

  public boolean carriesAnswerValue() {
    return this == ANSWER_SELECTED || this == ANSWER_CHANGED || this == ANSWER_DESELECTED;
  }

  public List<InteractionField> fields() {
    return fields;
  }

  public Optional<InteractionField> field(String name) {
    return fields.stream().filter(field -> field.name().equals(name)).findFirst();
  }

  // Só os campos declarados, na ordem do catálogo, e só os que cabem na forma declarada. Campo
  // extra, ou com conteúdo fora da forma, não chega a existir.
  public Map<String, Object> sanitize(Map<String, ?> raw) {
    var sanitized = new LinkedHashMap<String, Object>();
    if (raw == null) {
      return Collections.unmodifiableMap(sanitized);
    }
    for (var field : fields) {
      field.normalize(raw.get(field.name())).ifPresent(value -> sanitized.put(field.name(), value));
    }
    return Collections.unmodifiableMap(sanitized);
  }
}
