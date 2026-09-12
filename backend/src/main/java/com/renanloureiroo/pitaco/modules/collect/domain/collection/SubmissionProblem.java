package com.renanloureiroo.pitaco.modules.collect.domain.collection;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import java.util.Optional;

public record SubmissionProblem(String code, Optional<QuestionKey> questionKey) {

  public static final String QUESTION_UNKNOWN = "answer.question_unknown";
  public static final String QUESTION_DUPLICATED = "answer.question_duplicated";
  public static final String REQUIRED_MISSING = "answer.required_missing";
  public static final String VALUE_MISSING = "answer.value_missing";
  public static final String VALUE_TYPE_MISMATCH = "answer.value_type_mismatch";
  public static final String OPTION_UNKNOWN = "answer.option_unknown";
  public static final String OPTIONS_EMPTY = "answer.options_empty";
  public static final String OPTIONS_DUPLICATED = "answer.options_duplicated";
  public static final String VALUE_OUT_OF_RANGE = "answer.value_out_of_range";
  public static final String TEXT_TOO_LONG = "answer.text_too_long";
  public static final String NOT_APPLICABLE_UNCONDITIONAL = "answer.not_applicable_unconditional";

  public static SubmissionProblem of(String code, QuestionKey questionKey) {
    return new SubmissionProblem(code, Optional.ofNullable(questionKey));
  }
}
