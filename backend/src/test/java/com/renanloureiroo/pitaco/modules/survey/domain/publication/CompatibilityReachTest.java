package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CompatibilityReach")
class CompatibilityReachTest {

  private static final SdkVersion REQUIRED = SdkVersion.of("1.2.0");

  private static SdkTraffic traffic(String version, long requests) {
    return new SdkTraffic(SdkVersion.of(version), requests);
  }

  @Test
  @DisplayName("Sem tráfego, sem aviso")
  void sem_trafego() {
    assertThat(CompatibilityReach.warningFor(REQUIRED, List.of())).isEmpty();
    assertThat(CompatibilityReach.warningFor(REQUIRED, List.of(traffic("1.0.0", 0)))).isEmpty();
  }

  @Test
  @DisplayName("Maioria estrita suportando, sem aviso")
  void maioria_suporta() {
    assertThat(
            CompatibilityReach.warningFor(
                REQUIRED, List.of(traffic("1.1.0", 49), traffic("1.2.0", 51))))
        .isEmpty();
  }

  @Test
  @DisplayName("Empate não é maioria: avisa com a proporção que não suporta")
  void empate_avisa() {
    var warning =
        CompatibilityReach.warningFor(
                REQUIRED, List.of(traffic("1.1.9", 50), traffic("1.3.0", 50)))
            .orElseThrow();

    assertThat(warning.code()).isEqualTo(PublicationWarning.UNSUPPORTED_BY_MAJORITY);
    assertThat(warning.minRequiredVersion()).contains(REQUIRED);
    assertThat(warning.unsupportedShare().orElseThrow()).isCloseTo(0.5, within(1e-9));
  }

  @Test
  @DisplayName("Pré-lançamento da versão exigida não a atende")
  void pre_lancamento_nao_atende() {
    assertThat(
            CompatibilityReach.warningFor(REQUIRED, List.of(traffic("1.2.0-rc.1", 10))))
        .isPresent();
  }
}
