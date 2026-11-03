package com.renanloureiroo.pitaco.core.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Page")
class PageTest {

  @Test
  @DisplayName("Traduz os itens sem mexer no total do conjunto")
  void traduz_os_itens_preservando_o_total() {
    var page = new Page<>(List.of(1, 2, 3), 42);

    var mapped = page.map(String::valueOf);

    assertThat(mapped.items()).containsExactly("1", "2", "3");
    assertThat(mapped.total()).isEqualTo(42);
  }

  @ParameterizedTest
  @DisplayName("Conta as páginas a partir do total e do tamanho pedido")
  @CsvSource({"0, 20, 0", "1, 20, 1", "20, 20, 1", "21, 20, 2", "5, 2, 3"})
  void conta_as_paginas(long total, int size, int expected) {
    assertThat(new Page<>(List.of(), total).totalPages(size)).isEqualTo(expected);
  }

  @Test
  @DisplayName("Tamanho zero não quebra a contagem de páginas")
  void tamanho_zero_nao_quebra() {
    assertThat(new Page<>(List.of(), 3).totalPages(0)).isZero();
  }

  @Test
  @DisplayName("Os itens não mudam depois de construída a página")
  void os_itens_nao_mudam_depois_de_construida() {
    var source = new ArrayList<>(List.of("a"));
    var page = new Page<>(source, 1);

    source.add("b");

    assertThat(page.items()).containsExactly("a");
    assertThatThrownBy(() -> page.items().add("c"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
