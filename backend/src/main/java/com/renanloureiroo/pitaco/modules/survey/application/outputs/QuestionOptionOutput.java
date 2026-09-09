package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.QuestionOption;

public record QuestionOptionOutput(String label, String value, int position) {

  static QuestionOptionOutput of(QuestionOption option) {
    return new QuestionOptionOutput(option.label(), option.value(), option.position());
  }
}
