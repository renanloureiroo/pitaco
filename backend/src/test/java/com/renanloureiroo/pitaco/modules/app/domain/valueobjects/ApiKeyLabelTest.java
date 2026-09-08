package com.renanloureiroo.pitaco.modules.app.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ApiKeyLabel")
class ApiKeyLabelTest {

  @ParameterizedTest
  @ValueSource(strings = {"app iOS", "site de marketing", "a"})
  void aceita_rotulo_legivel(String valid) {
    assertThat(ApiKeyLabel.of(valid).value()).isEqualTo(valid);
  }

  @Test
  void descarta_o_espaco_em_volta() {
    assertThat(ApiKeyLabel.of("  app iOS  ").value()).isEqualTo("app iOS");
  }

  @Test
  void aceita_o_rotulo_no_limite() {
    assertThat(ApiKeyLabel.of("a".repeat(80)).value()).hasSize(80);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejeita_rotulo_ausente(String invalid) {
    assertThatThrownBy(() -> ApiKeyLabel.of(invalid)).satisfies(ApiKeyLabelTest::rotuloInvalido);
  }

  @Test
  void rejeita_rotulo_longo_demais() {
    assertThatThrownBy(() -> ApiKeyLabel.of("a".repeat(81)))
        .satisfies(ApiKeyLabelTest::rotuloInvalido);
  }

  @Test
  void o_espaco_em_volta_nao_conta_para_o_limite() {
    assertThat(ApiKeyLabel.of("  " + "a".repeat(80) + "  ").value()).hasSize(80);
  }

  @Test
  void imprime_o_proprio_texto() {
    assertThat(ApiKeyLabel.of("app iOS")).hasToString("app iOS");
  }

  private static void rotuloInvalido(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
    assertThat(domainError.code()).isEqualTo("api_key.label_invalid");
  }
}
