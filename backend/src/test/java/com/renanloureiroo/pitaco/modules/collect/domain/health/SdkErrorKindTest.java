package com.renanloureiroo.pitaco.modules.collect.domain.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SdkErrorKind")
class SdkErrorKindTest {

  @Test
  @DisplayName("Lê o nome do fio em minúsculas e o devolve igual")
  void ida_e_volta() {
    for (var kind : SdkErrorKind.values()) {
      assertThat(SdkErrorKind.fromWire(kind.wire())).isEqualTo(kind);
    }
    assertThat(SdkErrorKind.fromWire(" Render_Error ")).isEqualTo(SdkErrorKind.RENDER_ERROR);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"crash", "render-error", "   "})
  @DisplayName("Tipo desconhecido vira unknown, nunca recusa")
  void desconhecido_vira_unknown(String raw) {
    assertThat(SdkErrorKind.fromWire(raw)).isEqualTo(SdkErrorKind.UNKNOWN);
  }
}
