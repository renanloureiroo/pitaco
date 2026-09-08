package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

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

@DisplayName("QuestionKey")
class QuestionKeyTest {

  @Test
  void gera_um_uuid_valido() {
    var generated = QuestionKey.generate();

    assertThat(UUID.fromString(generated.value())).hasToString(generated.value());
  }

  @Test
  @DisplayName("Duas chaves geradas nunca coincidem: é a linhagem da pergunta")
  void cada_chave_gerada_e_unica() {
    assertThat(QuestionKey.generate()).isNotEqualTo(QuestionKey.generate());
  }

  @Test
  void a_chave_gerada_volta_pelo_of() {
    var generated = QuestionKey.generate();

    assertThat(QuestionKey.of(generated.value())).isEqualTo(generated);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "nao-e-uuid", "123", "3f9a1c72-5d84-4a1e-9b0f-2c6e"})
  void rejeita_chave_fora_do_formato_uuid(String invalid) {
    assertThatThrownBy(() -> QuestionKey.of(invalid))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
              assertThat(domainError.code()).isEqualTo("question.key_invalid");
            });
  }
}
