package com.renanloureiroo.pitaco.modules.collect.domain.collection;

import java.util.List;

// A forma crua que chegou do SDK, ainda sem confronto com o tipo da pergunta: é o que permite
// relatar `value_type_mismatch` em vez de estourar a invariante do value object antes da
// validação terminar.
public sealed interface RawAnswerValue {

  record RawText(String value) implements RawAnswerValue {}

  record RawNumber(int value) implements RawAnswerValue {}

  record RawChoices(List<String> values) implements RawAnswerValue {

    public RawChoices {
      values = values == null ? List.of() : List.copyOf(values);
    }
  }
}
