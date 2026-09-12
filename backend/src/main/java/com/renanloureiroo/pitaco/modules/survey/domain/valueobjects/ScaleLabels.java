package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import java.util.Optional;

// O texto dos extremos da escala. Trocar um rótulo não muda o que se mede — é cosmético —, e por
// isso vive separado da faixa, que entra na comparabilidade.
public record ScaleLabels(Optional<String> min, Optional<String> max) {

  public static final int MAX_LENGTH = 60;

  private static final String INVALID_CODE = "question.scale_labels_invalid";

  public ScaleLabels {
    min = normalized(min);
    max = normalized(max);
  }

  public static ScaleLabels none() {
    return new ScaleLabels(Optional.empty(), Optional.empty());
  }

  public static ScaleLabels of(String min, String max) {
    return new ScaleLabels(Optional.ofNullable(min), Optional.ofNullable(max));
  }

  public boolean isEmpty() {
    return min.isEmpty() && max.isEmpty();
  }

  private static Optional<String> normalized(Optional<String> label) {
    if (label == null) {
      return Optional.empty();
    }

    var stripped = label.map(String::strip).filter(text -> !text.isEmpty());
    if (stripped.filter(text -> text.length() > MAX_LENGTH).isPresent()) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Rótulo da escala não pode passar de " + MAX_LENGTH + " caracteres");
    }
    return stripped;
  }
}
