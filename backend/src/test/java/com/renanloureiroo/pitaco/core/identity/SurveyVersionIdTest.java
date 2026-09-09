package com.renanloureiroo.pitaco.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SurveyVersionId")
class SurveyVersionIdTest {

  @Test
  void gera_um_uuid_valido() {
    var generated = SurveyVersionId.generate();

    assertThat(UUID.fromString(generated.value())).hasToString(generated.value());
  }

  @Test
  void o_identificador_gerado_volta_pelo_of() {
    var generated = SurveyVersionId.generate();

    assertThat(SurveyVersionId.of(generated.value())).isEqualTo(generated);
  }

  @Test
  void aceita_um_uuid() {
    var value = UUID.randomUUID().toString();

    assertThat(SurveyVersionId.of(value).value()).isEqualTo(value);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "nao-e-uuid",
        "123",
        "3f9a1c72-5d84-4a1e-9b0f-2c6e",
        "3f9a1c72-5d84-4a1e-9b0f-2c6e8d5a7b3g"
      })
  void rejeita_identificador_fora_do_formato_uuid(String invalid) {
    assertThatThrownBy(() -> SurveyVersionId.of(invalid))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
              assertThat(domainError.code()).isEqualTo("survey_version.id_invalid");
            });
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void identificador_ausente_continua_sendo_recusado_pelo_core(String invalid) {
    assertThatThrownBy(() -> SurveyVersionId.of(invalid))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
              assertThat(domainError.code()).isEqualTo("id.invalid");
            });
  }
}
