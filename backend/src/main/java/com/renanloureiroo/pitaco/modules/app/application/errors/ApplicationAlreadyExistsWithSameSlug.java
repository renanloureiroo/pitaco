package com.renanloureiroo.pitaco.modules.app.application.errors;

import com.renanloureiroo.pitaco.core.error.ConflictException;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;

public final class ApplicationAlreadyExistsWithSameSlug extends ConflictException {

  private static final String CODE = "application.already_exists_with_same_slug";

  private final Slug slug;

  public ApplicationAlreadyExistsWithSameSlug(Slug slug) {
    super(CODE, "Já existe uma aplicação com o slug \"" + slug + "\"");
    this.slug = slug;
  }

  /** O slug que colidiu — é o que a borda precisa devolver a quem tentou criar. */
  public Slug slug() {
    return slug;
  }
}
