package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SamplingRate")
class SamplingRateTest {

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 0.25, 0.5, 1.0})
  void aceita_proporcao_dentro_do_intervalo(double valid) {
    assertThat(SamplingRate.of(valid).value()).isEqualTo(valid);
  }

  @Test
  @DisplayName("Os extremos 0 e 1 são aceitos")
  void aceita_os_extremos() {
    assertThat(SamplingRate.of(0.0).value()).isZero();
    assertThat(SamplingRate.of(1.0).value()).isEqualTo(1.0);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.0001, -1.0, 1.0001, 2.0, Double.NaN})
  void rejeita_proporcao_fora_do_intervalo(double invalid) {
    assertThatThrownBy(() -> SamplingRate.of(invalid))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
              assertThat(domainError.code()).isEqualTo("trigger.sampling_rate_invalid");
            });
  }
}
