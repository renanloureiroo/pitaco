package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Exposure")
class ExposureTest {

  @Test
  @DisplayName("O padrão é prioridade zero, sem cota e respeitando o descanso")
  void padrao() {
    var standard = Exposure.standard();

    assertThat(standard.priority()).isZero();
    assertThat(standard.responseQuota()).isEmpty();
    assertThat(standard.ignoresQuietPeriod()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {-100, 0, 100})
  void aceita_prioridade_nos_limites(int priority) {
    assertThat(Exposure.standard().withPriority(priority).priority()).isEqualTo(priority);
  }

  @ParameterizedTest
  @ValueSource(ints = {-101, 101})
  void recusa_prioridade_fora_da_faixa(int priority) {
    assertThatThrownBy(() -> Exposure.standard().withPriority(priority))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> assertThat(((DomainException) error).code()).isEqualTo("survey.priority_invalid"));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void recusa_cota_abaixo_de_um(int quota) {
    assertThatThrownBy(() -> Exposure.standard().withResponseQuota(quota))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error ->
                assertThat(((DomainException) error).code())
                    .isEqualTo("survey.response_quota_invalid"));
  }

  @Test
  @DisplayName("Cada alteração mexe só no próprio campo")
  void alteracoes_sao_independentes() {
    var exposure =
        Exposure.standard().withPriority(10).withResponseQuota(50).ignoringQuietPeriod(true);

    assertThat(exposure).isEqualTo(new Exposure(10, Optional.of(50), true));
    assertThat(exposure.withoutResponseQuota()).isEqualTo(new Exposure(10, Optional.empty(), true));
  }
}
