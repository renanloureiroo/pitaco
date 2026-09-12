package com.renanloureiroo.pitaco.modules.results.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.modules.results.domain.aggregation.QuestionAggregate;
import com.renanloureiroo.pitaco.modules.results.domain.comparability.Comparability;
import java.util.Optional;

// A comparabilidade só existe no consolidado: lida uma versão por vez, não há soma a desconfiar.
public record QuestionResultOutput(
    QuestionKey key,
    String statement,
    QuestionType type,
    int position,
    long answered,
    long skipped,
    long notApplicable,
    Optional<QuestionAggregate> aggregate,
    Optional<Comparability> comparability) {

  public QuestionResultOutput {
    comparability = comparability == null ? Optional.empty() : comparability;
  }

  public QuestionResultOutput(
      QuestionKey key,
      String statement,
      QuestionType type,
      int position,
      long answered,
      long skipped,
      long notApplicable,
      Optional<QuestionAggregate> aggregate) {
    this(
        key,
        statement,
        type,
        position,
        answered,
        skipped,
        notApplicable,
        aggregate,
        Optional.empty());
  }

  public QuestionResultOutput withComparability(Comparability value) {
    return new QuestionResultOutput(
        key,
        statement,
        type,
        position,
        answered,
        skipped,
        notApplicable,
        aggregate,
        Optional.of(value));
  }
}
