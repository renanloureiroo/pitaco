package com.renanloureiroo.pitaco.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdTest {

  static final class AnswerId extends Id {
    private AnswerId(String value) {
      super(value);
    }

    static AnswerId generate() {
      return new AnswerId(newValue());
    }

    static AnswerId of(String value) {
      return new AnswerId(value);
    }
  }

  static final class UserId extends Id {
    private UserId(String value) {
      super(value);
    }

    static UserId of(String value) {
      return new UserId(value);
    }
  }

  @Test
  void gera_identificadores_distintos() {
    assertThat(AnswerId.generate()).isNotEqualTo(AnswerId.generate());
  }

  @Test
  void expoe_o_identificador_como_texto() {
    var answerId = AnswerId.generate();

    assertThat(answerId.value()).isNotBlank().isEqualTo(answerId.toString());
  }

  @Test
  void identificadores_de_agregados_diferentes_nao_sao_iguais() {
    var shared = AnswerId.generate().value();

    assertThat(AnswerId.of(shared)).isNotEqualTo(UserId.of(shared));
  }

  @Test
  void identificadores_do_mesmo_tipo_e_valor_sao_iguais() {
    var shared = AnswerId.generate().value();

    assertThat(AnswerId.of(shared))
        .isEqualTo(AnswerId.of(shared))
        .hasSameHashCodeAs(AnswerId.of(shared));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void rejeita_identificador_vazio(String invalid) {
    assertThatThrownBy(() -> AnswerId.of(invalid))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
              assertThat(domainError.code()).isEqualTo("id.invalid");
            });
  }
}
