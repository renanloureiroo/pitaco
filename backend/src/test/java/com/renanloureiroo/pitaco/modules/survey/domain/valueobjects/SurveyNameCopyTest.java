package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SurveyName — nome da cópia")
class SurveyNameCopyTest {

  private static final String PREFIX = "Cópia de ";

  @Test
  void prefixa_o_nome_original() {
    assertThat(SurveyName.of("NPS pós-checkout").copy()).isEqualTo(SurveyName.of("Cópia de NPS pós-checkout"));
  }

  @Test
  @DisplayName("Cabe exatamente no limite de 120 sem cortar; um caractere a mais é cortado no fim")
  void cabe_no_limite() {
    var justo = "x".repeat(120 - PREFIX.length());
    assertThat(SurveyName.of(justo).copy().value()).isEqualTo(PREFIX + justo);

    var longo = "x".repeat(120 - PREFIX.length() + 1);
    var copia = SurveyName.of(longo).copy().value();
    assertThat(copia).hasSize(120).startsWith(PREFIX);
  }
}
