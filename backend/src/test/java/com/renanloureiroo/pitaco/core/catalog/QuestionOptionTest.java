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

@DisplayName("QuestionOption")
class QuestionOptionTest {

  @Test
  void aceita_rotulo_e_valor_preenchidos() {
    var option = new QuestionOption("Muito satisfeito", "very_satisfied", 1);

    assertThat(option.label()).isEqualTo("Muito satisfeito");
    assertThat(option.value()).isEqualTo("very_satisfied");
    assertThat(option.position()).isEqualTo(1);
  }

  @Test
  void descarta_o_espaco_em_volta() {
    var option = new QuestionOption("  Muito satisfeito  ", "  very_satisfied  ", 1);

    assertThat(option.label()).isEqualTo("Muito satisfeito");
    assertThat(option.value()).isEqualTo("very_satisfied");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejeita_rotulo_ausente(String invalid) {
    assertThatThrownBy(() -> new QuestionOption(invalid, "valor", 1))
        .satisfies(QuestionOptionTest::opcaoInvalida);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejeita_valor_ausente(String invalid) {
    assertThatThrownBy(() -> new QuestionOption("Rótulo", invalid, 1))
        .satisfies(QuestionOptionTest::opcaoInvalida);
  }

  @Test
  void rejeita_rotulo_longo_demais() {
    assertThatThrownBy(() -> new QuestionOption("a".repeat(201), "valor", 1))
        .satisfies(QuestionOptionTest::opcaoInvalida);
  }

  @Test
  void rejeita_valor_longo_demais() {
    assertThatThrownBy(() -> new QuestionOption("Rótulo", "a".repeat(121), 1))
        .satisfies(QuestionOptionTest::opcaoInvalida);
  }

  @Test
  void rejeita_posicao_nao_positiva() {
    assertThatThrownBy(() -> new QuestionOption("Rótulo", "valor", 0))
        .satisfies(QuestionOptionTest::opcaoInvalida);
  }

  private static void opcaoInvalida(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
    assertThat(domainError.code()).isEqualTo("question.option_invalid");
  }
}
