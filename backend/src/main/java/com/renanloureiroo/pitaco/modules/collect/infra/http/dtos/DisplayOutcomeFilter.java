package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;

// ABANDONED não é oferecido: nunca é gravado, e documentá-lo como filtro seria documentar uma
// opção que sempre devolve vazio (D-11).
public enum DisplayOutcomeFilter {
  STARTED,
  COMPLETED,
  DISMISSED;

  public DisplayOutcome toDomain() {
    return DisplayOutcome.valueOf(name());
  }
}
