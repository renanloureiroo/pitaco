package com.renanloureiroo.pitaco.modules.app.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;

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
    if (value == null || value.isBlank()) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Nome é obrigatório");
    }
    value = value.strip();
    if (value.length() > MAX_LENGTH) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Nome não pode passar de " + MAX_LENGTH + " caracteres");
    }
  }

  public static Name of(String value) {
    return new Name(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
