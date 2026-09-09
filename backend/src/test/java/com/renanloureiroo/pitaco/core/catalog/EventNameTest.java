package com.renanloureiroo.pitaco.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("EventName")
class EventNameTest {

  @ParameterizedTest
  @ValueSource(strings = {"checkout.completed", "ab", "app_opened", "a1.b2_c3"})
  void aceita_nome_no_formato(String valid) {
    assertThat(EventName.of(valid).value()).isEqualTo(valid);
  }

  @Test
  void aceita_o_nome_no_limite() {
    assertThat(EventName.of("a".repeat(80)).value()).hasSize(80);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "   ",
        "a",
        "Checkout.completed",
        "checkout completed",
        "checkout-completed",
        "1checkout",
        ".checkout",
        "_checkout",
        "checkout@completed"
      })
  void rejeita_nome_fora_do_formato(String invalid) {
    assertThatThrownBy(() -> EventName.of(invalid)).satisfies(EventNameTest::eventoInvalido);
  }

  @Test
  void rejeita_nome_longo_demais() {
    assertThatThrownBy(() -> EventName.of("a".repeat(81))).satisfies(EventNameTest::eventoInvalido);
  }

  private static void eventoInvalido(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
    assertThat(domainError.code()).isEqualTo("trigger.event_name_invalid");
  }
}
