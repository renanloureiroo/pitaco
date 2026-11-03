package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SurveyName")
class SurveyNameTest {

  @ParameterizedTest
  @ValueSource(strings = {"NPS pós-checkout", "Satisfação com o suporte", "a"})
  void aceita_nome_legivel(String valid) {
    assertThat(SurveyName.of(valid).value()).isEqualTo(valid);
  }

  @Test
  void descarta_o_espaco_em_volta() {
    assertThat(SurveyName.of("  NPS pós-checkout  ").value()).isEqualTo("NPS pós-checkout");
  }

  @Test
  void aceita_o_nome_no_limite() {
    assertThat(SurveyName.of("a".repeat(120)).value()).hasSize(120);
  }

  @Test
  void o_espaco_em_volta_nao_conta_para_o_limite() {
    assertThat(SurveyName.of("  " + "a".repeat(120) + "  ").value()).hasSize(120);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejeita_nome_ausente(String invalid) {
    assertThatThrownBy(() -> SurveyName.of(invalid)).satisfies(SurveyNameTest::nomeInvalido);
  }

  @Test
  void rejeita_nome_longo_demais() {
    assertThatThrownBy(() -> SurveyName.of("a".repeat(121)))
        .satisfies(SurveyNameTest::nomeInvalido);
  }

  @Test
  void imprime_o_proprio_texto() {
    assertThat(SurveyName.of("NPS pós-checkout")).hasToString("NPS pós-checkout");
  }

  private static void nomeInvalido(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
    assertThat(domainError.code()).isEqualTo("survey.name_invalid");
  }
}
