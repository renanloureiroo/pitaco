package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TriggerWindow")
class TriggerWindowTest {

  private static final Instant START = Instant.parse("2026-09-08T12:00:00Z");

  @Test
  void aceita_janela_com_fim_posterior_ao_inicio() {
    var window = TriggerWindow.of(START, START.plusSeconds(60));

    assertThat(window.start()).isEqualTo(START);
    assertThat(window.end()).contains(START.plusSeconds(60));
  }

  @Test
  @DisplayName("Fim ausente significa tempo indeterminado")
  void aceita_janela_sem_fim() {
    assertThat(TriggerWindow.of(START, null).end()).isEmpty();
  }

  @Test
  void rejeita_janela_sem_inicio() {
    assertThatThrownBy(() -> TriggerWindow.of(null, null))
        .satisfies(TriggerWindowTest::janelaInvalida);
  }

  @Test
  void rejeita_fim_igual_ao_inicio() {
    assertThatThrownBy(() -> TriggerWindow.of(START, START))
        .satisfies(TriggerWindowTest::janelaInvalida);
  }

  @Test
  void rejeita_fim_anterior_ao_inicio() {
    assertThatThrownBy(() -> TriggerWindow.of(START, START.minusSeconds(1)))
        .satisfies(TriggerWindowTest::janelaInvalida);
  }

  @Test
  void rejeita_fim_nulo_dentro_do_optional() {
    assertThatThrownBy(() -> new TriggerWindow(START, null))
        .satisfies(TriggerWindowTest::janelaInvalida);
  }

  @Test
  void aberta_a_partir_do_inicio_inclusive() {
    var window = TriggerWindow.of(START, START.plusSeconds(60));

    assertThat(window.isOpenAt(START.minusSeconds(1))).isFalse();
    assertThat(window.isOpenAt(START)).isTrue();
    assertThat(window.isOpenAt(START.plusSeconds(59))).isTrue();
    assertThat(window.isOpenAt(START.plusSeconds(60))).isFalse();
  }

  @Test
  void janela_sem_fim_nunca_fecha() {
    var window = new TriggerWindow(START, Optional.empty());

    assertThat(window.hasClosedAt(START.plusSeconds(1_000_000))).isFalse();
    assertThat(window.isOpenAt(START.plusSeconds(1_000_000))).isTrue();
  }

  private static void janelaInvalida(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
    assertThat(domainError.code()).isEqualTo("trigger.window_invalid");
  }
}
