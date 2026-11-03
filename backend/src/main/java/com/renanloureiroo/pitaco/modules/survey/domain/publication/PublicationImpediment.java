package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import java.util.Optional;

public record PublicationImpediment(String code, String field, Optional<QuestionKey> questionKey) {

  public static final String NO_QUESTIONS = "survey.no_questions";
  public static final String STATEMENT_MISSING = "question.statement_missing";
  public static final String OPTIONS_MISSING = "question.options_missing";
  public static final String TRIGGER_MISSING = "trigger.missing";
  public static final String WINDOW_INVALID = "trigger.window_invalid";

  public static PublicationImpediment of(String code, String field) {
    return new PublicationImpediment(code, field, Optional.empty());
  }

  public static PublicationImpediment ofQuestion(String code, String field, QuestionKey key) {
    return new PublicationImpediment(code, field, Optional.of(key));
  }
}
