package com.renanloureiroo.pitaco.modules.collect.application.repositories;

import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionDedupKey;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionEvent;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionReason;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SuppressionEventRepository {

  SuppressionEvent create(SuppressionEvent event);

  // Trava consultiva da transação sobre a chave de deduplicação: duas supressões simultâneas do
  // mesmo dispositivo na mesma versão passam uma de cada vez pela checagem de repetição.
  void lockDedup(SurveyVersionId versionId, SuppressionDedupKey dedupKey);

  boolean existsSince(SurveyVersionId versionId, SuppressionDedupKey dedupKey, Instant since);

  // Período inclusivo nos dois extremos, como a listagem de exibições. bySdkVersion vem da versão
  // mais nova para a mais antiga, com a ausência de versão por último.
  SuppressionCounts countFor(SurveyId surveyId, Instant from, Instant to);

  record SuppressionCounts(
      long total, Map<SuppressionReason, Long> byReason, List<VersionCount> bySdkVersion) {

    public SuppressionCounts {
      byReason = Map.copyOf(byReason);
      bySdkVersion = List.copyOf(bySdkVersion);
    }
  }

  record VersionCount(Optional<String> sdkVersion, long count) {}
}
