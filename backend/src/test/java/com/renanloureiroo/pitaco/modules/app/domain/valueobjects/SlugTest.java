package com.renanloureiroo.pitaco.modules.app.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SlugTest {

  @ParameterizedTest
  @ValueSource(strings = {"acme", "acme-app", "acme-app-2", "a1"})
  void aceita_minusculas_digitos_e_hifen_entre_termos(String valid) {
    assertThat(Slug.of(valid).value()).isEqualTo(valid);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Acme", "acme_app", "-acme", "acme-", "acme--app", "acme app", "açaí"})
  void rejeita_texto_fora_do_formato(String invalid) {
    assertThatThrownBy(() -> Slug.of(invalid))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
              assertThat(domainError.code()).isEqualTo("application.slug_invalid");
            });
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejeita_slug_ausente(String invalid) {
    assertThatThrownBy(() -> Slug.of(invalid)).isInstanceOf(DomainException.class);
  }

  @Test
  void rejeita_slug_longo_demais() {
    assertThat(Slug.of("a".repeat(50)).value()).hasSize(50);
    assertThatThrownBy(() -> Slug.of("a".repeat(51))).isInstanceOf(DomainException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "Acme App, acme-app",
    "Pitaço Café, pitaco-cafe",
    "  Acme   App!  , acme-app",
    "Acme App 2.0, acme-app-2-0",
    "ACME, acme",
    "Acme_App, acme-app",
  })
  void deriva_o_slug_do_texto(String text, String expected) {
    assertThat(Slug.from(text).value()).isEqualTo(expected);
  }

  @Test
  void corta_o_slug_derivado_no_limite_sem_deixar_hifen_solto() {
    var text = "a".repeat(50) + " b";

    assertThat(Slug.from(text).value()).isEqualTo("a".repeat(50));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejeita_derivacao_sem_texto(String invalid) {
    assertThatThrownBy(() -> Slug.from(invalid)).isInstanceOf(DomainException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"日本語", "###", "-"})
  void rejeita_texto_sem_nada_aproveitavel_para_o_slug(String text) {
    assertThatThrownBy(() -> Slug.from(text))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
              assertThat(domainError.code()).isEqualTo("application.slug_invalid");
            });
  }

  @Test
  void iguala_pelo_texto() {
    assertThat(Slug.of("acme-app"))
        .isEqualTo(Slug.of("acme-app"))
        .hasSameHashCodeAs(Slug.of("acme-app"))
        .isNotEqualTo(Slug.of("acme-web"));
  }

  @Test
  void imprime_o_proprio_texto() {
    assertThat(Slug.of("acme-app")).hasToString("acme-app");
  }
}
