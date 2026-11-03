package com.renanloureiroo.pitaco.core.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RequiredText")
class RequiredTextTest {

  private static final String CODE = "assunto.invalid";

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t\n"})
  void recusa_texto_ausente_ou_em_branco(String blank) {
    assertThatThrownBy(() -> RequiredText.of(blank, 10, CODE, "Nome"))
        .isInstanceOf(DomainException.class)
        .hasMessage("Nome é obrigatório")
        .satisfies(RequiredTextTest::validacaoComOCode);
  }

  @Test
  void descarta_o_espaco_em_volta() {
    assertThat(RequiredText.of("  acme  ", 10, CODE, "Nome")).isEqualTo("acme");
  }

  @Test
  @DisplayName("O limite é medido depois do strip")
  void mede_o_limite_depois_do_strip() {
    assertThat(RequiredText.of("  " + "a".repeat(10) + "  ", 10, CODE, "Nome")).hasSize(10);
  }

  @Test
  void recusa_acima_do_limite() {
    assertThatThrownBy(() -> RequiredText.of("a".repeat(11), 10, CODE, "Nome"))
        .isInstanceOf(DomainException.class)
        .hasMessage("Nome não pode passar de 10 caracteres")
        .satisfies(RequiredTextTest::validacaoComOCode);
  }

  @Test
  @DisplayName("Opcional aceita a ausência, mas não o branco")
  void opcional_aceita_ausencia() {
    assertThat(RequiredText.optional(null, 10, CODE, "Nome")).isNull();
    assertThatThrownBy(() -> RequiredText.optional("   ", 10, CODE, "Nome"))
        .isInstanceOf(DomainException.class);
  }

  private static void validacaoComOCode(Throwable error) {
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
    assertThat(domainError.code()).isEqualTo(CODE);
  }
}
