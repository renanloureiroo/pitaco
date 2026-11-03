package com.renanloureiroo.pitaco.modules.results.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import java.util.List;
import java.util.Optional;

public record SurveyBehaviorOutput(
    boolean everPublished,
    long displayed,
    long instrumented,
    List<QuestionBehaviorOutput> questions,
    DismissalsOutput dismissals,
    ResultsFilterOutput filter,
    List<MetricDefinitionOutput> definitions) {

  public record QuestionBehaviorOutput(
      QuestionKey key,
      String statement,
      QuestionType type,
      int position,
      long viewed,
      long answered,
      long skipped,
      long abandoned,
      ActiveTimeOutput activeTime,
      long revisited,
      Optional<Double> revisitRate,
      long selected,
      long changed,
      Optional<Double> answerChangeRate,
      long validationBlockedDisplays,
      long validationBlocks) {}

  public record ActiveTimeOutput(long samples, Optional<Long> medianMs, Optional<Long> p90Ms) {}

  public record DismissalsOutput(long total, List<ViaCountOutput> byVia, long unspecified) {}

  public record ViaCountOutput(String via, long count, Optional<Double> share) {}

  public record MetricDefinitionOutput(String metric, String definition) {}
}
