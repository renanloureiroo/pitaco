package com.renanloureiroo.pitaco.modules.privacy.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ErasureTarget")
class ErasureTargetTest {

  @Test
  @DisplayName("A referência do app vira alvo de referência, sem espaço nas pontas")
  void por_referencia() {
    var target = ErasureTarget.of(Optional.of(" u-1 "), Optional.empty());

    assertThat(target).isEqualTo(new ErasureTarget(ErasureTarget.Kind.APP_REFERENCE, "u-1"));
  }

  @Test
  @DisplayName("Sem referência, o dispositivo é o alvo")
  void por_dispositivo() {
    var target = ErasureTarget.of(Optional.of("  "), Optional.of("device-1"));

    assertThat(target).isEqualTo(new ErasureTarget(ErasureTarget.Kind.DEVICE, "device-1"));
  }

  @Test
  @DisplayName("Sem nenhuma das duas identidades, recusa")
  void sem_identidade() {
    assertThatThrownBy(() -> ErasureTarget.of(Optional.empty(), Optional.empty()))
        .isInstanceOf(DomainException.class)
        .extracting("code")
        .isEqualTo("respondent.identity_required");
  }

  @Test
  @DisplayName("Com as duas ao mesmo tempo, recusa em vez de escolher uma")
  void ambigua() {
    assertThatThrownBy(() -> ErasureTarget.of(Optional.of("u-1"), Optional.of("device-1")))
        .isInstanceOf(DomainException.class)
        .extracting("code")
        .isEqualTo("respondent.identity_ambiguous");
  }
}
