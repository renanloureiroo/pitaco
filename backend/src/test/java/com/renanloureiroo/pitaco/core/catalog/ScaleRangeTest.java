package com.renanloureiroo.pitaco.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ScaleRange")
class ScaleRangeTest {

  @ParameterizedTest
  @CsvSource({"1,5", "0,10", "-5,5"})
  void aceita_faixa_com_minimo_menor_que_o_maximo(int min, int max) {
    var range = new ScaleRange(min, max);

    assertThat(range.min()).isEqualTo(min);
    assertThat(range.max()).isEqualTo(max);
  }

  @ParameterizedTest
  @CsvSource({"5,5", "5,1"})
  void rejeita_minimo_maior_ou_igual_ao_maximo(int min, int max) {
    assertThatThrownBy(() -> new ScaleRange(min, max)).satisfies(ScaleRangeTest::faixaInvalida);
  }

  @Test
  @DisplayName("A faixa de NPS é fixa em 0 a 10")
  void a_faixa_de_nps_e_fixa() {
    assertThat(ScaleRange.NPS).isEqualTo(new ScaleRange(0, 10));
  }

  private static void faixaInvalida(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    var domainError = (DomainException) error;
    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
    assertThat(domainError.code()).isEqualTo("question.scale_range_invalid");
  }
}
