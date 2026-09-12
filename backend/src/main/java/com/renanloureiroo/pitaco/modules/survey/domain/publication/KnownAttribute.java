package com.renanloureiroo.pitaco.modules.survey.domain.publication;

import java.util.Set;

// O que a autoria sabe de um atributo que o app já enviou. Saturado é o catálogo que parou de
// acumular valores novos: dele não se conclui que um valor nunca foi visto.
public record KnownAttribute(String name, Set<String> values, boolean saturated) {

  public KnownAttribute {
    values = Set.copyOf(values);
  }
}
