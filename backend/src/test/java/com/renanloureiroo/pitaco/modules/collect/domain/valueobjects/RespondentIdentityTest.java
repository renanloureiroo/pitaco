package com.renanloureiroo.pitaco.modules.collect.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RespondentIdentity")
class RespondentIdentityTest {

  private static final String REFERENCE = "u-8f1c";
  private static final String DEVICE = "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11";

  @Test
  @DisplayName("A referência do app prevalece sobre o dispositivo")
  void referencia_do_app_prevalece() {
    var identity = RespondentIdentity.of(Optional.of(REFERENCE), Optional.of(DEVICE));

    assertThat(identity.kind()).isEqualTo(RespondentIdentityKind.APP_REFERENCE);
    assertThat(identity.value()).isEqualTo(REFERENCE);
  }

  @Test
  @DisplayName("Só o dispositivo vale na falta da referência")
  void dispositivo_vale_sozinho() {
    var identity = RespondentIdentity.of(Optional.empty(), Optional.of(DEVICE));

    assertThat(identity.kind()).isEqualTo(RespondentIdentityKind.DEVICE);
    assertThat(identity.value()).isEqualTo(DEVICE);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("Referência vazia cai para o dispositivo")
  void referencia_vazia_cai_para_o_dispositivo(String blank) {
    var identity = RespondentIdentity.of(Optional.ofNullable(blank), Optional.of(DEVICE));

    assertThat(identity.kind()).isEqualTo(RespondentIdentityKind.DEVICE);
  }

  @Test
  @DisplayName("Sem referência e sem dispositivo não há respondente")
  void recusa_ausencia_de_identificacao() {
    assertThatThrownBy(() -> RespondentIdentity.of(Optional.empty(), Optional.empty()))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error ->
                assertThat(((DomainException) error).code())
                    .isEqualTo("respondent.identity_required"));
  }

  @Test
  void recusa_identificacao_so_com_espacos_nos_dois_campos() {
    assertThatThrownBy(() -> RespondentIdentity.of(Optional.of("  "), Optional.of("   ")))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error ->
                assertThat(((DomainException) error).code())
                    .isEqualTo("respondent.identity_required"));
  }

  @Test
  void descarta_o_espaco_em_volta_do_valor() {
    assertThat(RespondentIdentity.of(Optional.of("  u-8f1c  "), Optional.empty()).value())
        .isEqualTo(REFERENCE);
  }

  @Test
  void aceita_o_valor_no_limite_de_duzentos_caracteres() {
    var limite = "u".repeat(200);

    assertThat(RespondentIdentity.of(Optional.of(limite), Optional.empty()).value())
        .isEqualTo(limite);
  }

  @Test
  void recusa_valor_acima_do_limite() {
    assertThatThrownBy(
            () -> RespondentIdentity.of(Optional.of("u".repeat(201)), Optional.empty()))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error ->
                assertThat(((DomainException) error).code())
                    .isEqualTo("respondent.identity_invalid"));
  }
}
