package com.renanloureiroo.pitaco.modules.privacy.domain.retention;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.AggregateSnapshot;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.SnapshotCount.Dimension;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Congela, por versão, o agregado de um lote de respostas prestes a sair. Um agregado por
// versão porque é por versão que o resultado é lido e comparado.
public final class RetentionSnapshots {

  private RetentionSnapshots() {}

  public static List<AggregateSnapshot> of(
      List<ExpiringAnswer> answers, Instant discardedBefore, Instant computedAt) {
    var byVersion = new LinkedHashMap<VersionKey, Accumulator>();

    for (var answer : answers) {
      byVersion
          .computeIfAbsent(
              new VersionKey(answer.surveyId(), answer.versionId()), key -> new Accumulator())
          .add(answer);
    }

    var snapshots = new ArrayList<AggregateSnapshot>();
    byVersion.forEach(
        (version, accumulator) ->
            snapshots.add(
                AggregateSnapshot.create(
                    version.surveyId(),
                    version.versionId(),
                    discardedBefore,
                    accumulator.respondingDisplays.size(),
                    accumulator.counts(),
                    computedAt)));
    return List.copyOf(snapshots);
  }

  private record VersionKey(SurveyId surveyId, SurveyVersionId versionId) {}

  private record CountKey(QuestionKey key, Dimension dimension, String value) {}

  private static final class Accumulator {

    private final Map<CountKey, Long> counts = new LinkedHashMap<>();
    private final Set<String> respondingDisplays = new HashSet<>();

    void add(ExpiringAnswer answer) {
      increment(answer.key(), Dimension.STATUS, answer.status());

      if (!answer.isAnswered()) {
        return;
      }

      respondingDisplays.add(answer.displayId());
      answer.number().ifPresent(number -> increment(answer.key(), Dimension.NUMBER, String.valueOf(number)));
      answer.options().forEach(option -> increment(answer.key(), Dimension.OPTION, option));
    }

    private void increment(QuestionKey key, Dimension dimension, String value) {
      counts.merge(new CountKey(key, dimension, value), 1L, Long::sum);
    }

    List<SnapshotCount> counts() {
      return counts.entrySet().stream()
          .map(
              entry ->
                  new SnapshotCount(
                      entry.getKey().key(),
                      entry.getKey().dimension(),
                      entry.getKey().value(),
                      entry.getValue()))
          .toList();
    }
  }
}
