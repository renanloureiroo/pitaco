package com.renanloureiroo.pitaco.core.entity;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;
import java.util.Objects;

/**
 * Base de toda entidade do domínio.
 *
 * <p>Entidade é definida por identidade, não por conteúdo: dois {@code Pitaco} com o mesmo
 * identificador são o mesmo pitaco, ainda que os atributos divirjam — um foi editado, o outro é uma
 * leitura mais antiga do banco. Comparação por atributo é comportamento de value object; para
 * esses, use {@code record}.
 *
 * <p>A igualdade compara o tipo concreto além do identificador, pela mesma razão que {@link Id} o
 * faz: entidades de agregados diferentes nunca são a mesma coisa, mesmo que compartilhem o valor do
 * identificador.
 *
 * @param <ID> tipo do identificador da entidade
 */
public abstract class Entity<ID extends Id> {

  private static final String MISSING_ID_CODE = "entity.id_required";

  private final ID id;

  protected Entity(ID id) {
    if (id == null) {
      throw new DomainException(
          ErrorType.VALIDATION, MISSING_ID_CODE, "Entidade não pode existir sem identificador");
    }
    this.id = id;
  }

  public ID id() {
    return id;
  }

  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    return id.equals(((Entity<?>) other).id);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(getClass(), id);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "(" + id + ")";
  }
}
