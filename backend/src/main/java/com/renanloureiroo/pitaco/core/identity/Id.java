package com.renanloureiroo.pitaco.core.identity;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.UUID;

/**
 * Base de todo identificador de agregado.
 *
 * <p>O identificador é um texto opaco: como ele é gerado é detalhe interno e não faz parte do
 * contrato. Quem consome só precisa saber que é estável, único e comparável.
 *
 * <p>Cada agregado declara o próprio tipo — {@code AnswerId}, {@code UserId} — de modo que
 * identificadores de agregados diferentes não sejam intercambiáveis nem em compilação nem em
 * runtime: a igualdade compara o tipo concreto além do valor.
 */
public abstract class Id {

  private static final String INVALID_CODE = "id.invalid";

  private final String value;

  protected Id(String value) {
    if (value == null || value.isBlank()) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador não pode ser vazio");
    }
    this.value = value;
  }

  /** Novo identificador, para as factories das subclasses. */
  protected static String newValue() {
    return UUID.randomUUID().toString();
  }

  public String value() {
    return value;
  }

  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    return value.equals(((Id) other).value);
  }

  @Override
  public final int hashCode() {
    return 31 * getClass().hashCode() + value.hashCode();
  }

  @Override
  public final String toString() {
    return value;
  }
}
