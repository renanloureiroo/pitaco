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

@DisplayName("QuestionStatement")
class QuestionStatementTest {

  @Test
  void aceita_enunciado_legivel() {
    assertThat(QuestionStatement.of("O que achou do atendimento?").value())
        .isEqualTo("O que achou do atendimento?");
  }

  @Test
  void descarta_o_espaco_em_volta() {
    assertThat(QuestionStatement.of("  Qual sua nota?  ").value()).isEqualTo("Qual sua nota?");
  }

  @Test
  void aceita_o_enunciado_no_limite() {
    assertThat(QuestionStatement.of("a".repeat(500)).value()).hasSize(500);
  }

  @Test
  void o_espaco_em_volta_nao_conta_para_o_limite() {
    assertThat(QuestionStatement.of("  " + "a".repeat(500) + "  ").value()).hasSize(500);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejeita_enunciado_ausente(String invalid) {
    assertThatThrownBy(() -> QuestionStatement.of(invalid))
        .satisfies(QuestionStatementTest::enunciadoInvalido);
  }

  @Test
  void rejeita_enunciado_longo_demais() {
    assertThatThrownBy(() -> QuestionStatement.of("a".repeat(501)))
        .satisfies(QuestionStatementTest::enunciadoInvalido);
  }

  private static void enunciadoInvalido(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
    assertThat(domainError.code()).isEqualTo("question.statement_invalid");
  }
}
