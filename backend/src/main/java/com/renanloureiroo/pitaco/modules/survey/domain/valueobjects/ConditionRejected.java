package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// Toda recusa de condição sai 422 com o campo que a causou, para o painel mostrar o erro junto
// dele; quando a culpa é de outra pergunta, a chave dela vai junto.
public final class ConditionRejected extends DomainException {

  public static final String SOURCE_INVALID = "question.condition_source_invalid";
  public static final String OPERATOR_INVALID = "question.condition_operator_invalid";
  public static final String VALUE_INVALID = "question.condition_value_invalid";
  public static final String SOURCE_IN_USE = "question.condition_source_in_use";
  public static final String ORDER_INVALID = "question.condition_order_invalid";

  private static final String SOURCE_FIELD = "condition.sourceKey";
  private static final String OPERATOR_FIELD = "condition.operator";
  private static final String VALUES_FIELD = "condition.values";
  private static final String CONDITION_FIELD = "condition";

  private final String field;
  private final QuestionKey dependent;

  private ConditionRejected(String code, String field, QuestionKey dependent, String message) {
    super(ErrorType.BUSINESS_RULE, code, message);
    this.field = field;
    this.dependent = dependent;
  }

  public static ConditionRejected source(String message) {
    return new ConditionRejected(SOURCE_INVALID, SOURCE_FIELD, null, message);
  }

  public static ConditionRejected operator(String message) {
    return new ConditionRejected(OPERATOR_INVALID, OPERATOR_FIELD, null, message);
  }

  public static ConditionRejected value(String message) {
    return new ConditionRejected(VALUE_INVALID, VALUES_FIELD, null, message);
  }

  public static ConditionRejected sourceInUse(QuestionKey dependent) {
    return new ConditionRejected(
        SOURCE_IN_USE,
        CONDITION_FIELD,
        dependent,
        "A pergunta é origem da condição de outra pergunta, e a mudança deixaria essa condição"
            + " inválida");
  }

  public static ConditionRejected orderInvalid(QuestionKey dependent) {
    return new ConditionRejected(
        ORDER_INVALID,
        CONDITION_FIELD,
        dependent,
        "Uma pergunta condicionada precisa continuar depois da pergunta de origem");
  }

  public String field() {
    return field;
  }

  public Optional<QuestionKey> dependent() {
    return Optional.ofNullable(dependent);
  }

  @Override
  public Map<String, Object> extensions() {
    var extensions = new LinkedHashMap<String, Object>();
    extensions.put("field", field);
    if (dependent != null) {
      extensions.put("questionKey", dependent.value());
    }
    return Map.copyOf(extensions);
  }
}
