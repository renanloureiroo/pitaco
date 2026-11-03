package com.renanloureiroo.pitaco.modules.privacy.domain.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.AggregateSnapshot;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.SnapshotCount.Dimension;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RetentionSnapshots")
class RetentionSnapshotsTest {

  private static final Instant CUTOFF = Instant.parse("2026-08-01T00:00:00Z");
  private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

  private final SurveyId survey = SurveyId.generate();
  private final SurveyVersionId v1 = SurveyVersionId.generate();
  private final SurveyVersionId v2 = SurveyVersionId.generate();
  private final QuestionKey nps = QuestionKey.generate();
  private final QuestionKey choice = QuestionKey.generate();

  private ExpiringAnswer answer(
      SurveyVersionId version, String display, QuestionKey key, String status, Integer number, String... options) {
    return new ExpiringAnswer(
        UUID.randomUUID().toString(),
        survey,
        version,
        display,
        key,
        status,
        Optional.ofNullable(number),
        List.of(options));
  }

  private static long countOf(AggregateSnapshot snapshot, QuestionKey key, Dimension dimension, String value) {
    return snapshot.getCounts().stream()
        .filter(count -> count.key().equals(key) && count.dimension() == dimension && count.value().equals(value))
        .mapToLong(SnapshotCount::count)
        .sum();
  }

  @Test
  @DisplayName("Um agregado por versão, com status, números e opções somados")
  void agrega_por_versao() {
    var snapshots =
        RetentionSnapshots.of(
            List.of(
                answer(v1, "d1", nps, "ANSWERED", 9),
                answer(v1, "d1", choice, "ANSWERED", null, "a", "b"),
                answer(v1, "d2", nps, "ANSWERED", 9),
                answer(v1, "d2", choice, "SKIPPED", null),
                answer(v1, "d3", choice, "NOT_APPLICABLE", null),
                answer(v2, "d4", nps, "ANSWERED", 3)),
            CUTOFF,
            NOW);

    assertThat(snapshots).hasSize(2);
    var first = snapshots.stream().filter(s -> s.getVersionId().equals(v1)).findFirst().orElseThrow();

    assertThat(first.getDiscardedBefore()).isEqualTo(CUTOFF);
    assertThat(first.getComputedAt()).isEqualTo(NOW);
    assertThat(first.getRespondingDisplays()).isEqualTo(2);
    assertThat(countOf(first, nps, Dimension.STATUS, "ANSWERED")).isEqualTo(2);
    assertThat(countOf(first, nps, Dimension.NUMBER, "9")).isEqualTo(2);
    assertThat(countOf(first, choice, Dimension.OPTION, "a")).isEqualTo(1);
    assertThat(countOf(first, choice, Dimension.OPTION, "b")).isEqualTo(1);
    assertThat(countOf(first, choice, Dimension.STATUS, "SKIPPED")).isEqualTo(1);
    assertThat(countOf(first, choice, Dimension.STATUS, "NOT_APPLICABLE")).isEqualTo(1);

    var second = snapshots.stream().filter(s -> s.getVersionId().equals(v2)).findFirst().orElseThrow();
    assertThat(second.getRespondingDisplays()).isEqualTo(1);
    assertThat(countOf(second, nps, Dimension.NUMBER, "3")).isEqualTo(1);
  }

  @Test
  @DisplayName("Exibição só com respostas puladas não conta como exibição com resposta")
  void pulada_nao_e_resposta() {
    var snapshots =
        RetentionSnapshots.of(List.of(answer(v1, "d1", nps, "SKIPPED", null)), CUTOFF, NOW);

    assertThat(snapshots.getFirst().getRespondingDisplays()).isZero();
    assertThat(snapshots.getFirst().getCounts())
        .containsExactly(new SnapshotCount(nps, Dimension.STATUS, "SKIPPED", 1));
  }

  @Test
  @DisplayName("Sem respostas, nada é congelado")
  void vazio() {
    assertThat(RetentionSnapshots.of(List.of(), CUTOFF, NOW)).isEmpty();
  }
}
