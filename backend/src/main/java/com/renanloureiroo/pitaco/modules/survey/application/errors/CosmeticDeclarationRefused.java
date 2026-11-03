package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.ChangeClassification;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Nomeia a chave da pergunta e qual das quatro diferenças derrubou a declaração — a borda a
// devolve na extensão `differences`.
public final class CosmeticDeclarationRefused extends ApplicationException {

  private static final String CODE = "survey_version.cosmetic_refused";

  private final List<ChangeClassification.Difference> differences;

  public CosmeticDeclarationRefused(List<ChangeClassification.Difference> differences) {
    super(
        ErrorType.BUSINESS_RULE,
        CODE,
        "A mudança declarada como cosmética alterou a estrutura das perguntas");
    this.differences = List.copyOf(differences);
  }

  public List<ChangeClassification.Difference> differences() {
    return differences;
  }

  @Override
  public Map<String, Object> extensions() {
    return Map.of(
        "differences",
        differences.stream()
            .map(
                difference ->
                    Map.of(
                        "questionKey", difference.questionKey().value(),
                        "difference", difference.kind().name().toLowerCase(Locale.ROOT)))
            .toList());
  }
}
