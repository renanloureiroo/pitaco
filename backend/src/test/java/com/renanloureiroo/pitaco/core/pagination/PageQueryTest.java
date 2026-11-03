package com.renanloureiroo.pitaco.core.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("PageQuery")
class PageQueryTest {

  private record Query(int page, int size) implements PageQuery {}

  @ParameterizedTest
  @DisplayName("O deslocamento é a página vezes o tamanho")
  @CsvSource({"0, 20, 0", "1, 20, 20", "3, 50, 150"})
  void calcula_o_deslocamento(int page, int size, long expected) {
    assertThat(new Query(page, size).offset()).isEqualTo(expected);
  }

  @Test
  @DisplayName("O deslocamento não estoura o int em página distante")
  void nao_estoura_o_int() {
    assertThat(new Query(Integer.MAX_VALUE, 100).offset())
        .isEqualTo(214748364700L);
  }
}
