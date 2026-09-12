package com.renanloureiroo.pitaco.modules.results.domain.comparability;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import java.util.Optional;
import java.util.Set;

// O que muda o sentido de uma resposta, e só isso: tipo, opções pelo valor, faixa e condição.
// Enunciado, rótulo e ordem ficam de fora pelo mesmo motivo que ficam na autoria — o sistema não
// julga texto, verifica estrutura.
public record QuestionShape(
    QuestionKey key,
    int versionNumber,
    QuestionType type,
    Set<String> optionValues,
    Optional<ScaleRange> range,
    Optional<String> condition) {

  public QuestionShape {
    optionValues = Set.copyOf(optionValues);
    range = range == null ? Optional.empty() : range;
    condition = condition == null ? Optional.empty() : condition;
  }

  public boolean sameMeaningAs(QuestionShape other) {
    return type == other.type
        && optionValues.equals(other.optionValues)
        && range.equals(other.range)
        && condition.equals(other.condition);
  }
}
