package com.renanloureiroo.pitaco.modules.collect.domain.entities;

// ABANDONED nunca é persistido: é derivado da abertura vencida, no mesmo molde de SurveyState
// na autoria (D-11).
public enum DisplayOutcome {
  STARTED,
  COMPLETED,
  DISMISSED,
  ABANDONED;

  public boolean isFinal() {
    return this == COMPLETED || this == DISMISSED;
  }
}
