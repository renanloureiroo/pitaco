package com.renanloureiroo.pitaco.modules.collect.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.List;

// Selada para que o switch do mapper e o da validação sejam exaustivos em compilação (D-13).
public sealed interface AnswerValue {

  record TextValue(AnswerText text) implements AnswerValue {

    public TextValue {
      if (text == null) {
        throw new DomainException(
            ErrorType.VALIDATION, "answer.text_invalid", "Texto da resposta é obrigatório");
      }
    }
  }

  record NumericValue(int value) implements AnswerValue {}

  record ChoiceValue(List<String> options) implements AnswerValue {

    private static final String INVALID_CODE = "answer.choice_invalid";

    public ChoiceValue {
      if (options == null || options.isEmpty()) {
        throw new DomainException(
            ErrorType.VALIDATION, INVALID_CODE, "A resposta precisa de ao menos uma opção");
      }
      if (options.stream().distinct().count() != options.size()) {
        throw new DomainException(
            ErrorType.VALIDATION, INVALID_CODE, "A mesma opção não pode ser escolhida duas vezes");
      }
      options = List.copyOf(options);
    }
  }
}
