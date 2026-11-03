package com.renanloureiroo.pitaco.modules.results.application.readmodels;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.ResultsFilter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Lê os eventos de interação sob o mesmo recorte dos resultados. Cada método é uma consulta
// agrupada, com mediana e p90 calculados no banco: nada é somado em memória linha a linha.
public interface SurveyBehaviorReadModel {

  BehaviorTotals totalsOf(ResultsFilter filter);

  List<QuestionFunnel> funnelsOf(ResultsFilter filter);

  // Dispensada, ou aberta antes de `abandonedBefore` e sem desfecho, com a pergunta como a última
  // vista.
  List<QuestionAbandonment> abandonmentsOf(ResultsFilter filter, Instant abandonedBefore);

  List<QuestionActiveTime> activeTimesOf(ResultsFilter filter);

  // Uma linha por via, a da última dispensa de cada exibição; via ausente vem vazia.
  List<DismissalCount> dismissalsOf(ResultsFilter filter);

  record BehaviorTotals(long displayed, long instrumented) {}

  record QuestionFunnel(
      QuestionKey key,
      long viewed,
      long revisited,
      long answered,
      long skipped,
      long selected,
      long changed,
      long blockedDisplays,
      long blocks) {}

  record QuestionAbandonment(QuestionKey key, long abandoned) {}

  record QuestionActiveTime(QuestionKey key, long samples, double medianMs, double p90Ms) {}

  record DismissalCount(Optional<String> via, long count) {}
}
