package com.renanloureiroo.pitaco.modules.app.domain.valueobjects;

import com.renanloureiroo.pitaco.core.text.RequiredText;

/**
 * Nome de exibição de uma aplicação.
 *
 * <p>Diferente do {@link Slug}, é rótulo de painel: aceita qualquer texto legível e muda
 * livremente. Espaço em volta é ruído de digitação e some na construção, de modo que dois nomes que
 * só diferem nisso são o mesmo nome.
 */
public record Name(String value) {

  private static final int MAX_LENGTH = 120;

  private static final String INVALID_CODE = "application.name_invalid";

  public Name {
    value = RequiredText.of(value, MAX_LENGTH, INVALID_CODE, "Nome");
  }

  public static Name of(String value) {
    return new Name(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
