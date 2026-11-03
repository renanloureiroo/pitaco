package com.renanloureiroo.pitaco.modules.app.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NameTest {

  @Test
  void aceita_texto_legivel() {
    assertThat(Name.of("Acme App").value()).isEqualTo("Acme App");
  }

  @Test
  void descarta_o_espaco_em_volta() {
    assertThat(Name.of("  Acme App  ").value()).isEqualTo("Acme App");
  }

  @Test
  void nomes_que_so_diferem_no_espaco_em_volta_sao_o_mesmo_nome() {
    assertThat(Name.of("  Acme App  "))
        .isEqualTo(Name.of("Acme App"))
        .hasSameHashCodeAs(Name.of("Acme App"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejeita_nome_ausente(String invalid) {
    assertThatThrownBy(() -> Name.of(invalid))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
              assertThat(domainError.code()).isEqualTo("application.name_invalid");
            });
  }

  @Test
  void rejeita_nome_longo_demais() {
    assertThat(Name.of("a".repeat(120)).value()).hasSize(120);
    assertThatThrownBy(() -> Name.of("a".repeat(121))).isInstanceOf(DomainException.class);
  }

  @Test
  void imprime_o_proprio_texto() {
    assertThat(Name.of("Acme App")).hasToString("Acme App");
  }
}
