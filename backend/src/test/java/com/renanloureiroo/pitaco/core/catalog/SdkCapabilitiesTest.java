package com.renanloureiroo.pitaco.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("SdkCapabilities")
class SdkCapabilitiesTest {

  @ParameterizedTest
  @EnumSource(QuestionType.class)
  @DisplayName("Todo tipo de pergunta tem versão mínima declarada, nunca abaixo da primeira")
  void todo_tipo_tem_versao(QuestionType type) {
    assertThat(SdkCapabilities.minimumFor(type)).isGreaterThanOrEqualTo(SdkVersion.BASELINE);
  }

  @ParameterizedTest
  @EnumSource(SdkFeature.class)
  @DisplayName("Todo recurso tem versão mínima declarada, nunca abaixo da primeira")
  void todo_recurso_tem_versao(SdkFeature feature) {
    assertThat(SdkCapabilities.minimumFor(feature)).isGreaterThanOrEqualTo(SdkVersion.BASELINE);
  }

  @Test
  @DisplayName("Sem tipo nem recurso, a exigência é a primeira versão")
  void sem_nada_e_a_primeira() {
    assertThat(SdkCapabilities.minimumFor(List.of(), List.of())).isEqualTo(SdkVersion.BASELINE);
  }

  @Test
  @DisplayName("O conjunto exige a maior das versões mínimas de cada parte")
  void conjunto_exige_a_maior() {
    var required =
        SdkCapabilities.minimumFor(
            EnumSet.allOf(QuestionType.class), EnumSet.allOf(SdkFeature.class));

    var highest =
        EnumSet.allOf(QuestionType.class).stream()
            .map(SdkCapabilities::minimumFor)
            .reduce(SdkVersion.BASELINE, SdkVersion::highest);
    highest =
        EnumSet.allOf(SdkFeature.class).stream()
            .map(SdkCapabilities::minimumFor)
            .reduce(highest, SdkVersion::highest);

    assertThat(required).isEqualTo(highest);
  }

  @Test
  @DisplayName("Na primeira versão do SDK, tudo que existe hoje é suportado pela 1.0.0")
  void tudo_nasce_na_primeira_versao() {
    assertThat(
            SdkCapabilities.minimumFor(
                EnumSet.allOf(QuestionType.class), EnumSet.allOf(SdkFeature.class)))
        .isEqualTo(SdkVersion.of("1.0.0"));
  }
}
