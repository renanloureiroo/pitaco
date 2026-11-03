package com.renanloureiroo.pitaco.infra.http.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FixedWindowRateLimiter")
class FixedWindowRateLimiterTest {

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-09-11T10:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Test
  @DisplayName("Aceita até a capacidade e recusa a partir dela, dizendo quando tentar de novo")
  void aceita_ate_a_capacidade() {
    var clock = new MutableClock();
    var limiter = new FixedWindowRateLimiter(2, Duration.ofMinutes(1), clock);

    assertThat(limiter.tryAcquire("a").allowed()).isTrue();
    assertThat(limiter.tryAcquire("a").allowed()).isTrue();

    clock.advance(Duration.ofSeconds(10));
    var refused = limiter.tryAcquire("a");

    assertThat(refused.allowed()).isFalse();
    assertThat(refused.retryAfterSeconds()).isEqualTo(50);
  }

  @Test
  @DisplayName("Cada assunto tem a própria janela: esgotar um não afeta o outro")
  void assuntos_sao_independentes() {
    var limiter = new FixedWindowRateLimiter(1, Duration.ofMinutes(1), new MutableClock());

    assertThat(limiter.tryAcquire("a").allowed()).isTrue();
    assertThat(limiter.tryAcquire("a").allowed()).isFalse();
    assertThat(limiter.tryAcquire("b").allowed()).isTrue();
  }

  @Test
  @DisplayName("A janela vira e a contagem recomeça")
  void a_janela_vira() {
    var clock = new MutableClock();
    var limiter = new FixedWindowRateLimiter(1, Duration.ofMinutes(1), clock);

    assertThat(limiter.tryAcquire("a").allowed()).isTrue();
    assertThat(limiter.tryAcquire("a").allowed()).isFalse();

    clock.advance(Duration.ofMinutes(1));

    assertThat(limiter.tryAcquire("a").allowed()).isTrue();
  }

  @Test
  @DisplayName("Assunto que sumiu é expurgado depois de uma janela inteira")
  void expurga_o_que_sumiu() {
    var clock = new MutableClock();
    var limiter = new FixedWindowRateLimiter(10, Duration.ofMinutes(1), clock);

    limiter.tryAcquire("a");
    limiter.tryAcquire("b");
    assertThat(limiter.trackedSubjects()).isEqualTo(2);

    clock.advance(Duration.ofMinutes(2));
    limiter.tryAcquire("c");

    assertThat(limiter.trackedSubjects()).isEqualTo(1);
  }

  @Test
  @DisplayName("O Retry-After nunca é zero, mesmo no último milissegundo da janela")
  void retry_after_nunca_e_zero() {
    var clock = new MutableClock();
    var limiter = new FixedWindowRateLimiter(1, Duration.ofMinutes(1), clock);

    limiter.tryAcquire("a");
    clock.advance(Duration.ofSeconds(59).plusMillis(999));

    assertThat(limiter.tryAcquire("a").retryAfterSeconds()).isEqualTo(1);
  }

  @Test
  @DisplayName("Diz se um assunto tem janela aberta, sem consumir o saldo dele")
  void tracks_nao_consome() {
    var limiter = new FixedWindowRateLimiter(1, Duration.ofMinutes(1), new MutableClock());

    assertThat(limiter.tracks("a")).isFalse();
    assertThat(limiter.tryAcquire("a").allowed()).isTrue();
    assertThat(limiter.tracks("a")).isTrue();
    assertThat(limiter.tracks("b")).isFalse();
    assertThat(limiter.tryAcquire("b").allowed()).isTrue();
  }
}
