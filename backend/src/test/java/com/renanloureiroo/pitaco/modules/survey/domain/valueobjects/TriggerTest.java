package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@DisplayName("Trigger")
class TriggerTest {

  private static final EventName EVENT = EventName.of("checkout.completed");
  private static final TriggerWindow WINDOW =
      TriggerWindow.of(Instant.parse("2026-09-08T12:00:00Z"), null);
  private static final SamplingRate RATE = SamplingRate.of(0.25);

  @Test
  void aceita_as_tres_partes_presentes() {
    var trigger = new Trigger(EVENT, WINDOW, RATE);

    assertThat(trigger.event()).isEqualTo(EVENT);
    assertThat(trigger.window()).isEqualTo(WINDOW);
    assertThat(trigger.rate()).isEqualTo(RATE);
  }

  @Test
  void rejeita_evento_ausente() {
    assertThatThrownBy(() -> new Trigger(null, WINDOW, RATE))
        .satisfies(TriggerTest::disparoInvalido);
  }

  @Test
  void rejeita_janela_ausente() {
    assertThatThrownBy(() -> new Trigger(EVENT, null, RATE))
        .satisfies(TriggerTest::disparoInvalido);
  }

  @Test
  void rejeita_proporcao_ausente() {
    assertThatThrownBy(() -> new Trigger(EVENT, WINDOW, null))
        .satisfies(TriggerTest::disparoInvalido);
  }

  private static void disparoInvalido(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
    assertThat(domainError.code()).isEqualTo("trigger.invalid");
  }
}
