package com.renanloureiroo.pitaco.core.usecase;

import java.util.Optional;
import java.util.function.Consumer;

// Três estados por campo de um PATCH: ausente não mexe, nulo remove, valor define. Nasceu na
// edição de aplicação e subiu quando a de pesquisa passou a precisar da mesma distinção.
public record Patch<T>(boolean present, Optional<T> value) {

  public static <T> Patch<T> absent() {
    return new Patch<>(false, Optional.empty());
  }

  public static <T> Patch<T> clear() {
    return new Patch<>(true, Optional.empty());
  }

  public static <T> Patch<T> set(T value) {
    return new Patch<>(true, Optional.of(value));
  }

  public void apply(Consumer<T> define, Runnable remove) {
    if (!present) {
      return;
    }
    value.ifPresentOrElse(define, remove);
  }
}
