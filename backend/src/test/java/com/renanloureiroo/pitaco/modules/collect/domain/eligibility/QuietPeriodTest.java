package com.renanloureiroo.pitaco.modules.collect.domain.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("QuietPeriod")
class QuietPeriodTest {

  private static final Instant LAST = Instant.parse("2026-09-01T10:00:00Z");

  @Test
  @DisplayName("Dentro do intervalo, o respondente descansa")
  void dentro_do_intervalo() {
    assertThat(QuietPeriod.ofDays(7).isRestingAt(LAST, LAST.plus(Duration.ofDays(1)))).isTrue();
  }

  @Test
  @DisplayName("O instante exato em que o intervalo se completa já libera")
  void limite_exato_libera() {
    var period = QuietPeriod.ofDays(7);

    assertThat(period.isRestingAt(LAST, LAST.plus(Duration.ofDays(7)).minusSeconds(1))).isTrue();
    assertThat(period.isRestingAt(LAST, LAST.plus(Duration.ofDays(7)))).isFalse();
    assertThat(period.isRestingAt(LAST, LAST.plus(Duration.ofDays(8)))).isFalse();
  }

  @Test
  void recusa_intervalo_nao_positivo() {
    assertThatThrownBy(() -> QuietPeriod.ofDays(0)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> new QuietPeriod(Duration.ofDays(-1)))
        .isInstanceOf(DomainException.class);
  }
}
