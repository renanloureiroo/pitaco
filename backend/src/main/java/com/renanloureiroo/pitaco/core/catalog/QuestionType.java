package com.renanloureiroo.pitaco.core.catalog;

import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import java.util.Optional;

// Seis tipos, três atributos variáveis e nenhum comportamento polimórfico: a diferença entre
// eles é de dados aceitos, não de conduta. Uma hierarquia custaria seis arquivos para
// expressar uma tabela de três colunas.
public enum QuestionType {
  SINGLE_CHOICE(true, true, false, null),
  MULTIPLE_CHOICE(true, true, false, null),
  RATING(false, false, true, null),
  SCALE(false, false, true, null),
  NPS(false, false, true, ScaleRange.NPS),
  FREE_TEXT(false, false, false, null);

  private final boolean acceptsOptions;
  private final boolean requiresOptions;
  private final boolean requiresRange;
  private final ScaleRange fixedRange;

  QuestionType(
      boolean acceptsOptions,
      boolean requiresOptions,
      boolean requiresRange,
      ScaleRange fixedRange) {
    this.acceptsOptions = acceptsOptions;
    this.requiresOptions = requiresOptions;
    this.requiresRange = requiresRange;
    this.fixedRange = fixedRange;
  }

  public boolean acceptsOptions() {
    return acceptsOptions;
  }

  public boolean requiresOptions() {
    return requiresOptions;
  }

  public boolean requiresRange() {
    return requiresRange;
  }

  public boolean hasFixedRange() {
    return fixedRange != null;
  }

  public Optional<ScaleRange> fixedRange() {
    return Optional.ofNullable(fixedRange);
  }
}
