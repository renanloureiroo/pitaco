package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import java.util.List;
import java.util.Optional;

// O valor é decomposto em três campos mutuamente exclusivos: o switch sobre o AnswerValue selado
// é exaustivo em compilação, e o cliente lê o campo que o tipo da pergunta determina.
public record AnswerReadOutput(
    QuestionKey questionKey,
    AnswerReadStatus status,
    Optional<String> text,
    Optional<Integer> number,
    List<String> options) {

  public AnswerReadOutput {
    options = List.copyOf(options);
  }

  public static AnswerReadOutput withoutValue(QuestionKey questionKey, AnswerReadStatus status) {
    return new AnswerReadOutput(
        questionKey, status, Optional.empty(), Optional.empty(), List.of());
  }
}
