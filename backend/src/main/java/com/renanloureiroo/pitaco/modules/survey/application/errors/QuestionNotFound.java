package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.NotFoundException;

public final class QuestionNotFound extends NotFoundException {

  private static final String CODE = "question.not_found";

  private final String questionId;

  public QuestionNotFound(String questionId) {
    super(CODE, "Pergunta não encontrada");
    this.questionId = questionId;
  }

  public String questionId() {
    return questionId;
  }
}
