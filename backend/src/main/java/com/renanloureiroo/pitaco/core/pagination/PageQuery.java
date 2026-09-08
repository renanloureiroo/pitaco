package com.renanloureiroo.pitaco.core.pagination;

// O recorte de página que toda consulta paginada carrega. A consulta de cada porta o implementa
// e acrescenta os próprios filtros.
public interface PageQuery {

  int page();

  int size();

  default long offset() {
    return (long) page() * size();
  }
}
