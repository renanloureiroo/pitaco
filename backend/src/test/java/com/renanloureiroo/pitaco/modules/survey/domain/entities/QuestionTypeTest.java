package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("QuestionType")
class QuestionTypeTest {

  @ParameterizedTest
  @CsvSource({
    "SINGLE_CHOICE,   true,  true,  false",
    "MULTIPLE_CHOICE, true,  true,  false",
    "RATING,          false, false, true",
    "SCALE,           false, false, true",
    "NPS,             false, false, true",
    "FREE_TEXT,       false, false, false"
  })
  @DisplayName("Cada tipo carrega a própria tabela de exigências")
  void a_tabela_de_exigencias_dos_seis_tipos(
      QuestionType type, boolean acceptsOptions, boolean requiresOptions, boolean requiresRange) {
    assertThat(type.acceptsOptions()).isEqualTo(acceptsOptions);
    assertThat(type.requiresOptions()).isEqualTo(requiresOptions);
    assertThat(type.requiresRange()).isEqualTo(requiresRange);
  }

  @Test
  void sao_exatamente_seis_tipos() {
    assertThat(QuestionType.values()).hasSize(6);
  }

  @ParameterizedTest
  @EnumSource(QuestionType.class)
  @DisplayName("Tipo que exige opção necessariamente aceita opção")
  void quem_exige_opcao_aceita_opcao(QuestionType type) {
    assertThat(!type.requiresOptions() || type.acceptsOptions()).isTrue();
  }

  @Test
  @DisplayName("Só o NPS impõe a faixa fixa")
  void so_o_nps_impoe_faixa_fixa() {
    assertThat(QuestionType.NPS.hasFixedRange()).isTrue();
    assertThat(QuestionType.SCALE.hasFixedRange()).isFalse();
    assertThat(QuestionType.RATING.hasFixedRange()).isFalse();
  }
}
