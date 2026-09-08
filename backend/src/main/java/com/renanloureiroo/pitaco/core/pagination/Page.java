package com.renanloureiroo.pitaco.core.pagination;

import java.util.List;
import java.util.function.Function;

// O que uma consulta paginada devolve: os itens do recorte e o total do conjunto inteiro que
// atende ao filtro. Quantas páginas isso dá depende do tamanho pedido, que não mora aqui.
public record Page<T>(List<T> items, long total) {

  public Page {
    items = List.copyOf(items);
  }

  public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
    return new Page<>(items.stream().<R>map(mapper).toList(), total);
  }

  public int totalPages(int size) {
    return size == 0 ? 0 : (int) Math.ceil((double) total / size);
  }
}
